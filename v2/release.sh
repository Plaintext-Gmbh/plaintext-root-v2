#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# ══════════════════════════════════════════════════════════════════════════════
#  v2/release.sh — v2-Release: Tag, mvn deploy in beide Paket-Repos, KEIN Versions-Commit
#
#  WARUM NICHT ./build release (plaintext-scripts, do_release) WIE IN ROOT
#    1. do_release committet die Versionsnummer in alle 33 pom.xml ("Release version X" und
#       "Prepare next development iteration"). upstream tut das auch — beim naechsten Sync
#       kollidierten beide Seiten auf derselben <version>-Zeile in jeder pom. v2 laesst die
#       POM-Version deshalb unangetastet: sie ist immer die von upstream, und der Sync bleibt
#       konfliktfrei (Messung in docs/v2/analyse.md).
#    2. do_release taggt ohne Praefix (1.790.0) — in v2 kollidierte das mit den v1-Nummern,
#       und versionsbasis() in tui-build-logic.sh liest nur solche Tags. v2-Tags heissen
#       `v2-<version>` und sind fuer versionsbasis() unsichtbar; das ist gewollt.
#    3. do_release braucht entweder .m3-jar-volume (dann SSH-Staging auf den NAS, fuer v2
#       verboten) oder baut ein Docker-Image (dann Docker-Socket, in Woodpecker nicht vorhanden).
#       Beides ist fuer ein reines Maven-Artefakt falsch.
#
#  VERSIONSSCHEMA  v2 = (M+1).m.n  aus der upstream-POM-Version M.m.0
#    upstream 1.789.0  ->  v2 2.789.1, 2.789.2, ... (n = laufende Nummer je upstream-Basis)
#    upstream 1.790.0  ->  v2 2.790.1
#    * Die Major M+1 sagt "v2", die Minor traegt den upstream-Stand, n zaehlt v2-Releases auf
#      dieser Basis (auch v2-eigene Merges und Syncs ohne upstream-Release).
#    * n kommt aus den vorhandenen Tags v2-(M+1).m.* (MAX + 1); ohne Tag 1. Das ist die v2-Fassung
#      von versionsbasis(): der hoechste Tag ist die Untergrenze, nie die POM allein.
#    * upstream-Patch != 0 (1.789.1) kommt in root nicht vor (alle Tags enden auf .0) und wird
#      hier HART abgewiesen statt still umgedeutet.
#
#  REIHENFOLGE (Karte 518 wie in do_release): Tag anlegen und pushen, BEVOR etwas veroeffentlicht
#  wird. Scheitert der Tag-Push, ist nichts draussen. Scheitert mvn deploy danach, gibt es einen
#  Tag ohne (oder mit halbem) Artefakt — das meldet scripts/release-vollstaendig.sh im naechsten
#  Lauf (TAG_PRAEFIX=v2-), genau wie in root.
#
#  SPERRE: Die Wurzel-groupId MUSS ch.plaintext.v2 sein. Sonst wuerde dieser Lauf v1-Koordinaten
#  veroeffentlichen — der Grund, warum Woodpecker fuer v2 erst nach der Umbenennung aktiviert wurde.
#
#  AUFRUF
#    v2/release.sh              Release (in Woodpecker: .woodpecker/deploy.yml)
#    v2/release.sh --trocken    nur rechnen und melden; kein Tag, kein Push, kein Deploy
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
[ "$GRUPPE" = "$ERWARTETE_GRUPPE" ] || fehler "Wurzel-groupId ist '$GRUPPE', erwartet '$ERWARTETE_GRUPPE' — erst v2/umbenennen.sh, sonst wuerden v1-Koordinaten veroeffentlicht"
v2/umbenennen.sh --pruefen >/dev/null || fehler "v2/umbenennen.sh --pruefen ist rot — Release verweigert"

POM_VERSION="$(awk '/<parent>/{p=1} /<\/parent>/{p=0; next} !p && /<version>/{gsub(/.*<version>|<\/version>.*/,""); print; exit}' pom.xml)"
BASIS="${POM_VERSION%-SNAPSHOT}"
[[ "$BASIS" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || fehler "POM-Version '$POM_VERSION' ist nicht M.m.p"
UP_MAJOR="${BASH_REMATCH[1]}"; UP_MINOR="${BASH_REMATCH[2]}"; UP_PATCH="${BASH_REMATCH[3]}"
[ "$UP_PATCH" = "0" ] || fehler "upstream-Patch '$UP_PATCH' in $BASIS ist nicht 0 — das v2-Schema (M+1).m.n kann das nicht abbilden; Schema in v2/release.sh anpassen"
V2_MAJOR=$((UP_MAJOR + 1))

git fetch -q --tags --force origin
LETZTE_N="$(git tag --list "${TAG_PRAEFIX}${V2_MAJOR}.${UP_MINOR}.*" | sed "s/^${TAG_PRAEFIX}${V2_MAJOR}\.${UP_MINOR}\.//" | grep -E '^[0-9]+$' | sort -n | tail -1 || true)"
N=$(( ${LETZTE_N:-0} + 1 ))
NEU="${V2_MAJOR}.${UP_MINOR}.${N}"
TAG="${TAG_PRAEFIX}${NEU}"
HOECHSTER_V2="$(git tag --list "${TAG_PRAEFIX}[0-9]*" | sed "s/^${TAG_PRAEFIX}//" | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -t. -k1,1n -k2,2n -k3,3n | tail -1 || true)"
sag "upstream-Basis $BASIS (POM $POM_VERSION), bisher hoechster v2-Tag ${HOECHSTER_V2:-keiner}, auf dieser Basis ${LETZTE_N:-0} Release(s)"
sag "neue Version $NEU, Tag $TAG"

# Monoton steigend: die neue Nummer muss ueber JEDEM bisherigen v2-Tag liegen (sonst waere
# upstream zurueckgesprungen, oder jemand hat von Hand getaggt).
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
    sag "TROCKEN: wuerde taggen ($TAG), pushen und 'mvn clean deploy' mit Version $NEU ausfuehren. Nichts getan."
    exit 0
fi

# ── 1. Tag ZUERST raus (Karte 518) ──────────────────────────────────────────
git tag -a "$TAG" -m "Release v2 $NEU

upstream-Basis: plaintext-root $BASIS
Commit: $(git rev-parse HEAD)
Koordinaten: $ERWARTETE_GRUPPE (GitHub Packages plaintext-mvn + Reposilite maven.plaintext.ch)"
if ! git push origin "refs/tags/$TAG"; then
    git tag -d "$TAG" >/dev/null 2>&1 || true
    fehler "Tag-Push abgelehnt — nichts veroeffentlicht, Zustand konsistent."
fi
sag "Tag $TAG gepusht."

# ── 2. Version nur im Arbeitsbaum setzen, bauen, veroeffentlichen ───────────
# versions:set schreibt alle 33 pom.xml; sie werden am Ende zurueckgesetzt, nie committet.
wiederherstellen() { git checkout -q -- pom.xml '*/pom.xml' 2>/dev/null || git checkout -q -- . ; }
trap wiederherstellen EXIT
mvn -q -B versions:set -DnewVersion="$NEU" -DgenerateBackupPoms=false
# shellcheck disable=SC2086
if ! mvn clean deploy -B $MVN_TEST_FLAG -Dmaven.build.cache.enabled=false; then
    fehler "mvn clean deploy fehlgeschlagen. Tag $TAG ist draussen, Artefakte fehlen oder sind halb — scripts/release-vollstaendig.sh (TAG_PRAEFIX=v2-) zeigt den Stand."
fi
sag "mvn clean deploy fuer $NEU erfolgreich (GitHub Packages + Reposilite ueber dual-publish-nas)."

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

# ── 4. Zusammenfassung ──────────────────────────────────────────────────────
echo "────────────────────────────────────────────────────────────────"
echo "v2-RELEASE $NEU"
echo "  Tag:             $TAG"
echo "  upstream-Basis:  $BASIS"
echo "  Commit:          $(git rev-parse --short HEAD)"
echo "  groupId:         $ERWARTETE_GRUPPE"
echo "  Module:          $(grep -c '<module>' pom.xml) + Parent"
echo "  Repos:           $REPOSILITE_BASIS/ch/plaintext/v2/  und  https://maven.pkg.github.com/Plaintext-Gmbh/plaintext-mvn"
echo "────────────────────────────────────────────────────────────────"
