#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# ══════════════════════════════════════════════════════════════════════════════
#  scripts/release.sh — Release: Tag, mvn deploy in beide Paket-Repos, dann SNAPSHOT-Commit
#
#  Karte 1621: plaintext-root-v2 IST seit dem Wegfall von v1 das Entwicklungsrepo. Der Sync ist
#  abgebaut, Versions-Commits sind damit wieder unproblematisch — der Lauf committet nach dem
#  Release die naechste SNAPSHOT-Version wie do_release in plaintext-scripts, aber ohne
#  ./build (kein NAS-Staging/SSH, kein Docker-CLI in Woodpecker, reines Maven-Artefakt).
#
#  WARUM EIGENER LAUF STATT do_release: Tag-Praefix `v2-` (versionsbasis() in tui-build-logic.sh
#  sieht nur unpraefixte Tags und wuerde aus den v2-Tags 1.0.0 rechnen) und kein ./build.
#
#  VERSIONSSCHEMA (Karte 1621, Entscheid Daniel: Schnitt auf 3.0.0)
#    BASIS   = MAX(POM-Version ohne -SNAPSHOT, hoechster v2-Tag)  — der hoechste Tag ist die
#              Untergrenze, nie die POM allein (Massnahme 11 in tui-build-logic.sh).
#    Schnitt: ist die Major der POM groesser als die Major des hoechsten Tags (z. B. POM
#              3.0.0-SNAPSHOT, Tags bis 2.821.1), wird die POM-Version GENAU so released —
#              das ist der Versionsschnitt. Sonst: NEU = BASIS + 1 Minor.
#    Danach: "Prepare next development iteration <NEU>-SNAPSHOT" — die SNAPSHOT-Version traegt
#    bewusst die GERADE veroeffentlichte Nummer (Hauskonvention, siehe compute_release_versions
#    in tui-build-logic.sh); hochgezaehlt wird erst beim naechsten Release.
#
#  REIHENFOLGE (Karte 518): Tag anlegen und pushen, BEVOR etwas veroeffentlicht wird. Scheitert
#  mvn deploy danach, liefert scripts/release-nachliefern.sh --runden nach (Karte 1596); bleibt
#  es halb, meldet es release-vollstaendig.sh im naechsten Lauf (TAG_PRAEFIX=v2-).
#
#  SPERRE: Die Wurzel-groupId MUSS ch.plaintext.v2 sein, und kein Reaktor-POM darf eine
#  v1-Koordinate (ch.plaintext) tragen — sonst wuerde dieser Lauf v1-Koordinaten veroeffentlichen.
#
#  AUFRUF
#    scripts/release.sh              Release (in Woodpecker: .woodpecker/deploy.yml)
#    scripts/release.sh --trocken    nur rechnen und melden; kein Tag, kein Push, kein Deploy
#  UMGEBUNG
#    MVN_TEST_FLAG       Standard "-DskipITs -DexcludedGroups=quality-gate"; "[fast]" im
#                        Commit-Betreff -> -DskipTests (wie root)
#    MAVEN_ARGS          z.B. "-s /tmp/ci/settings.xml" (Server-IDs plaintext + plaintext-nas)
#    REPOSILITE_BASIS    Standard https://maven.plaintext.ch/releases (Kollisionspruefung)
#    GITHUB_TOKEN        optional: GitHub-Release mit generierten Notes (gh), best effort
#    RELEASE_ZWEIG       Standard master
# ══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

TROCKEN=0
[ "${1:-}" = "--trocken" ] && TROCKEN=1
WURZEL="$(cd "$(dirname "$0")/.." && pwd)"
cd "$WURZEL"
REPOSILITE_BASIS="${REPOSILITE_BASIS:-https://maven.plaintext.ch/releases}"
ZWEIG="${RELEASE_ZWEIG:-master}"
ERWARTETE_GRUPPE="ch.plaintext.v2"
TAG_PRAEFIX="v2-"

sag() { echo "release: $*"; }
fehler() { echo "release: FEHLER: $*" >&2; exit 1; }

# ── 0. Sperren und Stand ────────────────────────────────────────────────────
[ -z "$(git status --porcelain)" ] || fehler "Arbeitsbaum nicht sauber"
GRUPPE="$(awk '/<parent>/{p=1} /<\/parent>/{p=0; next} !p && /<groupId>/{gsub(/.*<groupId>|<\/groupId>.*/,""); print; exit}' pom.xml)"
[ "$GRUPPE" = "$ERWARTETE_GRUPPE" ] || fehler "Wurzel-groupId ist '$GRUPPE', erwartet '$ERWARTETE_GRUPPE' — sonst wuerden v1-Koordinaten veroeffentlicht"
if grep -rq "<groupId>ch\.plaintext</groupId>" --include=pom.xml -- . ; then
    grep -rl "<groupId>ch\.plaintext</groupId>" --include=pom.xml -- . | sed 's/^/release: FEHLER: v1-groupId in /' >&2
    fehler "Reaktor-POMs mit v1-groupId gefunden — Release verweigert"
fi

POM_VERSION="$(awk '/<parent>/{p=1} /<\/parent>/{p=0; next} !p && /<version>/{gsub(/.*<version>|<\/version>.*/,""); print; exit}' pom.xml)"
BASIS_POM="${POM_VERSION%-SNAPSHOT}"
[[ "$BASIS_POM" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || fehler "POM-Version '$POM_VERSION' ist nicht M.m.p"

git fetch -q --tags --force origin
HOECHSTER_V2="$(git tag --list "${TAG_PRAEFIX}[0-9]*" | sed "s/^${TAG_PRAEFIX}//" | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -t. -k1,1n -k2,2n -k3,3n | tail -1 || true)"

# BASIS = MAX(POM, hoechster Tag): bricht ein Lauf nach Tag+Push ab, rechnet der naechste trotzdem lueckenlos weiter.
if [ -n "$HOECHSTER_V2" ]; then
    BASIS="$(printf '%s\n%s\n' "$BASIS_POM" "$HOECHSTER_V2" | sort -t. -k1,1n -k2,2n -k3,3n | tail -1)"
else
    BASIS="$BASIS_POM"
fi

# Versionsschnitt: POM-Major ueber der Tag-Major (z. B. 3.0.0 ueber 2.821.1) -> die POM-Version
# GENAU so releasen; sonst ein Minor-Schritt ab BASIS.
POM_MAJOR="${BASIS_POM%%.*}"
TAG_MAJOR=""
[ -n "$HOECHSTER_V2" ] && TAG_MAJOR="${HOECHSTER_V2%%.*}"
if [ -n "$TAG_MAJOR" ] && [ "$POM_MAJOR" -gt "$TAG_MAJOR" ]; then
    NEU="$BASIS_POM"
    sag "Versionsschnitt: POM-Major $POM_MAJOR liegt vor Tag-Major $TAG_MAJOR — release $NEU genau wie in der POM"
else
    IFS='.' read -r MAJOR MINOR PATCH <<< "$BASIS"
    NEU="${MAJOR}.$((MINOR + 1)).0"
fi
NEXT_SNAPSHOT="${NEU}-SNAPSHOT"
TAG="${TAG_PRAEFIX}${NEU}"
sag "POM $POM_VERSION, hoechster v2-Tag ${HOECHSTER_V2:-keiner}, Basis $BASIS"
sag "neue Version $NEU, Tag $TAG, danach SNAPSHOT-Commit $NEXT_SNAPSHOT"

# Monoton steigend: die neue Nummer muss ueber JEDEM bisherigen v2-Tag liegen (sonst waere die
# Linie zurueckgesprungen, oder jemand hat von Hand getaggt).
if [ -n "$HOECHSTER_V2" ] && [ "$(printf '%s\n%s\n' "$HOECHSTER_V2" "$NEU" | sort -t. -k1,1n -k2,2n -k3,3n | tail -1)" != "$NEU" ]; then
    fehler "$NEU liegt nicht ueber dem hoechsten v2-Tag $HOECHSTER_V2 — Versionsfolge waere nicht mehr steigend"
fi
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && fehler "Tag $TAG existiert schon"

# Kollisionspruefung im Release-Repo (Karte 410): ist die Nummer schon veroeffentlicht?
URL="$REPOSILITE_BASIS/$(echo "$ERWARTETE_GRUPPE" | tr . /)/plaintext-root-parent/$NEU/plaintext-root-parent-$NEU.pom"
CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 30 "$URL" || true)"; [ -n "$CODE" ] || CODE=000
case "$CODE" in
    200) fehler "Version $NEU liegt schon im Release-Repo ($URL) — Tag fehlt? Erst klaeren." ;;
    404) sag "Kollisionspruefung: $NEU ist frei." ;;
    *)   sag "Kollisionspruefung nicht moeglich (HTTP $CODE) — weiter, mvn deploy meldet eine Kollision selbst." ;;
esac

case "$(git log -1 --format=%s)" in
    *"[fast]"*) MVN_TEST_FLAG="${MVN_TEST_FLAG:--DskipTests}" ;;
    *)          MVN_TEST_FLAG="${MVN_TEST_FLAG:--DskipITs -DexcludedGroups=quality-gate}" ;;
esac
sag "Tests: $MVN_TEST_FLAG"

if [ "$TROCKEN" = "1" ]; then
    sag "TROCKEN: wuerde taggen ($TAG), 'mvn clean deploy' mit Version $NEU ausfuehren und danach $NEXT_SNAPSHOT committen. Nichts getan."
    exit 0
fi

# ── 1. Tag ZUERST raus (Karte 518) ──────────────────────────────────────────
git tag -a "$TAG" -m "Release $NEU

Direct development on plaintext-root-v2 (Karte 1621, v1 archived).
Commit: $(git rev-parse HEAD)
Koordinaten: $ERWARTETE_GRUPPE (GitHub Packages plaintext-mvn + Reposilite maven.plaintext.ch)"
if ! git push origin "refs/tags/$TAG"; then
    git tag -d "$TAG" >/dev/null 2>&1 || true
    fehler "Tag-Push abgelehnt — nichts veroeffentlicht, Zustand konsistent."
fi
sag "Tag $TAG gepusht."

# ── 2. Version nur im Arbeitsbaum setzen, bauen, veroeffentlichen ───────────
# versions:set schreibt alle 33 pom.xml. Misslingt der Lauf vor dem SNAPSHOT-Commit, stellt der
# Trap die POMs wieder her; nach dem SNAPSHOT-Commit ist die neue Version draussen (Karte 518:
# ein abgelehnter SNAPSHOT-Push ist nur eine Warnung, der naechste Lauf rechnet ab dem Tag weiter).
wiederherstellen() { git checkout -q -- pom.xml '*/pom.xml' 2>/dev/null || git checkout -q -- . ; }
trap wiederherstellen EXIT
mvn -q -B versions:set -DnewVersion="$NEU" -DgenerateBackupPoms=false
# shellcheck disable=SC2086
if mvn clean deploy -B $MVN_TEST_FLAG -Dmaven.build.cache.enabled=false; then
    sag "mvn clean deploy fuer $NEU erfolgreich (GitHub Packages + Reposilite ueber dual-publish-nas)."
else
    # Karte 1596, wie upstream `./build nachliefern`: der Tag ist draussen, das Release halb (2.805.1/2.805.2:
    # "Read timed out" gegen GitHub Packages, den der HTTP-Client bei einem PUT nie wiederholt). Datei fuer
    # Datei nachliefern, ungebaute Module per -pl bauen, Gegenprobe gegen beide Repos. POMs stehen noch auf $NEU.
    sag "mvn clean deploy rot — Nachlieferung des halben Releases $NEU (scripts/release-nachliefern.sh --runden)"
    MVN_TEST_FLAG="$MVN_TEST_FLAG" TAG_PRAEFIX="$TAG_PRAEFIX" bash scripts/release-nachliefern.sh --runden "$NEU" \
        || fehler "mvn clean deploy rot und Nachlieferung nicht vollstaendig. Tag $TAG ist draussen, Artefakte halb — scripts/release-vollstaendig.sh (TAG_PRAEFIX=v2-) zeigt den Stand."
    sag "Nachlieferung: $NEU liegt vollstaendig in beiden Repos."
fi

# ── 2b. Oeffentlicher Spiegel plaintext-mvn (Karte 1653) ───────────────────
# v2 laeuft nicht ueber ./build, darum lief die Spiegelung hier nie. Kein zweiter Mechanismus:
# Die Funktion kommt zur Laufzeit aus ./build, nur der Pfad im lokalen Repo ist v2-eigen. Sie
# nimmt die eben veroeffentlichten Dateien aus ~/.m2 und wiederholt keinen Bau. Fehlschlag wie
# in root nur als Warnung: Das Release ist gueltig, `mirror_to_plaintext_mvn` liesse sich nachholen.
RED=""; YELLOW=""; BLUE=""; GREEN=""; NC=""
SPIEGEL="$(sed -n '/^mirror_to_plaintext_mvn() {/,/^}/p' build | sed 's#/repository/ch/plaintext"#/repository/ch/plaintext/v2"#')"
if grep -q '/repository/ch/plaintext/v2"' <<<"$SPIEGEL"; then
    eval "$SPIEGEL"
    mirror_to_plaintext_mvn "$NEU" && sag "plaintext-mvn: $NEU gespiegelt." \
        || sag "WARNUNG: Spiegelung nach plaintext-mvn fehlgeschlagen, Release bleibt gueltig."
else
    sag "WARNUNG: mirror_to_plaintext_mvn in ./build nicht gefunden oder Pfad geaendert, Spiegelung entfaellt."
fi

# ── 3. GitHub-Release mit Notes (best effort, nie fatal) ───────────────────
if command -v gh >/dev/null 2>&1 && [ -n "${GITHUB_TOKEN:-}${GH_TOKEN:-}" ]; then
    REPO="$(git remote get-url origin | sed -E 's#^.*[:/]([^/]+/[^/]+)$#\1#; s#\.git$##')"
    for VERSUCH in 1 2 3; do
        if gh release create "$TAG" -R "$REPO" --verify-tag --title "$TAG" --generate-notes >/dev/null 2>&1; then
            sag "GitHub-Release $TAG angelegt."; break
        fi
        sleep $((VERSUCH * 3))
    done
else
    sag "kein gh/GITHUB_TOKEN — GitHub-Release entfaellt (Tag und Artefakte sind unberuehrt)."
fi

# ── 3b. Naechste SNAPSHOT-Version committen (Karte 1621) ────────────────────
# Der Sync ist abgebaut, Versions-Commits sind wieder unproblematisch. Der Commit traegt das
# native "[skip ci]" (mit Leerzeichen): GitHub und Woodpecker starten dafuer keinen Lauf, es
# gibt keinen Release-Loop. Scheitert der Push, weil master sich bewegt hat: Warnung, kein
# Fehler — der naechste Lauf rechnet ab dem gerade gesetzten Tag weiter (Massnahme 11).
trap - EXIT
mvn -q -B versions:set -DnewVersion="$NEXT_SNAPSHOT" -DgenerateBackupPoms=false
git add -u
git commit -q -m "Prepare next development iteration $NEXT_SNAPSHOT [skip ci]"
if git push origin "HEAD:$ZWEIG"; then
    sag "SNAPSHOT-Commit $NEXT_SNAPSHOT nach $ZWEIG gepusht."
else
    sag "WARNUNG: SNAPSHOT-Push abgelehnt (master bewegt?) — Release $NEU ist vollstaendig; der naechste Lauf rechnet ab Tag $TAG weiter."
    git reset -q --hard HEAD~1
fi

# ── 4. Zusammenfassung ──────────────────────────────────────────────────────
echo "────────────────────────────────────────────────────────────────"
echo "RELEASE $NEU (plaintext-root-v2)"
echo "  Tag:             $TAG"
echo "  Commit:          $(git rev-parse --short HEAD)"
echo "  groupId:         $ERWARTETE_GRUPPE"
echo "  Module:          $(grep -c '<module>' pom.xml) + Parent"
echo "  naechster Snap:  $NEXT_SNAPSHOT"
echo "  Repos:           $REPOSILITE_BASIS/ch/plaintext/v2/  und  https://maven.pkg.github.com/Plaintext-Gmbh/plaintext-mvn"
echo "────────────────────────────────────────────────────────────────"
