#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# ══════════════════════════════════════════════════════════════════════════════
#  v2/sync.sh — upstream plaintext-root nach plaintext-root-v2 nachziehen (Karte 1563)
#
#  ABLAUF
#    1. upstream/master holen. Ist er schon in master enthalten: nichts zu tun, Exit 0.
#    2. Vorpruefung: master = upstream-Basis + v2/transformation.sh bis auf v2/ und die
#       (c)-Dateien. Sonst rot (Exit 1): eine Handabweichung wuerde sonst still ueberschrieben.
#    3. Auf einem Arbeitszweig `git merge --no-ff --no-commit upstream/master`. Ausserhalb von v2/
#       und (c) gilt danach die upstream-Fassung (git restore --source), darauf die Transformation
#       (Karte 1595: groupId, Sonar/Repo-Lint/Renovate aus, transformation.patch). Ein Commit.
#       Fuer (c)-Pfade mit `merge=ours` in .gitattributes bleibt die v2-Fassung; eine
#       upstream-Aenderung daran wird als HINWEIS gemeldet. Scheitert T (Patch passt nicht) -> Exit 1.
#       Nachpruefung `transformation.sh --pruefen` und `umbenennen.sh --pruefen` muessen gruen sein.
#       Dann Push nach master. Dieser Push traegt KEIN `[skip ci]`: Woodpecker (.woodpecker/deploy.yml)
#       baut und released daraus ein v2-Release. Eine Endlosschleife gibt es nicht — das v2-Release
#       pusht nur einen Tag, keinen Commit, und dieser Job laeuft nur auf schedule/dispatch.
#    4. Konflikt (nur noch moeglich in v2/ oder einer (c)-Datei): Marker bleiben im Baum, werden auf einem Zweig `sync-konflikt/<datum>-<sha>`
#       committet und als PR mit Label `sync-konflikt` eroeffnet. master bleibt unveraendert.
#       Steht schon ein offener sync-konflikt-PR, tut der Lauf nichts (erst aufloesen).
#
#  AUFRUF
#    v2/sync.sh                 normaler Lauf (im Sync-Job und auf dem Jumphost gleich)
#    v2/sync.sh --trocken       alles ausser Push und PR; der Arbeitszweig bleibt zur Ansicht stehen
#  UMGEBUNG
#    UPSTREAM_URL     Standard https://github.com/Plaintext-Gmbh/plaintext-root.git
#    SYNC_ZIELZWEIG   Standard master
#    GH_TOKEN         nur fuer den Konfliktfall (gh pr create, gh label). Fehlt es, wird der
#                     Konfliktzweig gepusht und die PR-Anlage als Handarbeit gemeldet.
#
#  Das Skript veraendert NIE den ausgecheckten Stand von master lokal ausser durch den Push des
#  fertigen Sync; bei jedem Fehler bleibt master dort, wo er war.
# ══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

UPSTREAM_URL="${UPSTREAM_URL:-https://github.com/Plaintext-Gmbh/plaintext-root.git}"
ZIEL="${SYNC_ZIELZWEIG:-master}"
TROCKEN=0
[ "${1:-}" = "--trocken" ] && TROCKEN=1
WURZEL="$(cd "$(dirname "$0")/.." && pwd)"
cd "$WURZEL"

sag() { echo "sync: $*"; }
fehler() { echo "sync: FEHLER: $*" >&2; exit 1; }

# ── 0. Voraussetzungen ───────────────────────────────────────────────────────
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || fehler "kein Git-Arbeitsbaum"
[ -z "$(git status --porcelain)" ] || fehler "Arbeitsbaum nicht sauber — erst aufraeumen"
git config merge.ours.driver true     # .gitattributes merge=ours wirkt nur mit diesem Treiber
git config user.name  >/dev/null 2>&1 || git config user.name  "plaintext-root-v2 sync"
git config user.email >/dev/null 2>&1 || git config user.email "sync@plaintext.ch"

if git remote get-url upstream >/dev/null 2>&1; then
    git remote set-url upstream "$UPSTREAM_URL"
else
    git remote add upstream "$UPSTREAM_URL"
fi
git fetch -q origin "$ZIEL"
git fetch -q upstream master
ZIEL_SHA="$(git rev-parse "origin/$ZIEL")"
UP_SHA="$(git rev-parse upstream/master)"
UP_KURZ="${UP_SHA:0:7}"
sag "origin/$ZIEL = ${ZIEL_SHA:0:7}, upstream/master = $UP_KURZ"

if git merge-base --is-ancestor "$UP_SHA" "$ZIEL_SHA"; then
    sag "upstream/master ist bereits in $ZIEL enthalten — nichts zu tun."
    exit 0
fi

BASIS="$(git merge-base "$ZIEL_SHA" "$UP_SHA")"
ANZAHL="$(git rev-list --count "$BASIS..$UP_SHA")"
LETZTES_RELEASE="$(git log --format='%s' "$BASIS..$UP_SHA" | grep -m1 -E '^Release version' || true)"
sag "$ANZAHL upstream-Commits seit Basis ${BASIS:0:7}${LETZTES_RELEASE:+ (juengstes Release: $LETZTES_RELEASE)}"

# Offener Konflikt-PR? Dann nicht noch einen anlegen.
if command -v gh >/dev/null 2>&1 && [ -n "${GH_TOKEN:-}" ]; then
    # am Zweignamen erkennen, nicht am Label: fehlt das Label, bliebe ein offener Konflikt sonst unbemerkt
    OFFEN="$(gh pr list --state open --json number,headRefName --jq '.[] | select(.headRefName | startswith("sync-konflikt/")) | "#\(.number) \(.headRefName)"' 2>/dev/null || true)"
    if [ -n "$OFFEN" ]; then
        sag "offener sync-konflikt-PR vorhanden — erst aufloesen, dieser Lauf tut nichts:"
        echo "$OFFEN"
        exit 0
    fi
fi

# ── 1. Arbeitszweig und Merge ───────────────────────────────────────────────
ARBEIT="sync/arbeit-$$"
git checkout -q -B "$ARBEIT" "$ZIEL_SHA"
aufraeumen() {
    git merge --abort >/dev/null 2>&1 || true
    git checkout -q -f "$ZIEL" 2>/dev/null || git checkout -q -f "$ZIEL_SHA"
    [ "$TROCKEN" = "1" ] || git branch -q -D "$ARBEIT" 2>/dev/null || true
}

# Karte 1595: der upstream-Betreff traegt fast immer den Skip-Marker ("Prepare next development
# iteration … [skip ci]"). Stuende er in der Merge-Meldung, liesse Woodpecker den Sync-Push aus
# und es entstuende kein v2-Release (68e57c45, 24.–26.10.2026). Darum hier entfernen.
ohne_skip() { sed -E 's/[[:space:]]*\[(skip[ -]ci|ci[ -]skip|no[ -]ci|skip[ -]actions|actions[ -]skip)\]//Ig'; }
UP_BETREFF="$(git log -1 --format='%s' "$UP_SHA" | ohne_skip)"
MELDUNG="Sync upstream plaintext-root $UP_KURZ

Merge von upstream/master ($ANZAHL Commits, bis: $UP_BETREFF).
Karte 1595: ausserhalb von v2/ und der (c)-Dateien gilt die upstream-Fassung, darauf
v2/transformation.sh (groupId, Sonar/Repo-Lint/Renovate aus, transformation.patch)."

# Vorpruefung (Karte 1595): steht auf master schon eine Abweichung, die weder T noch (c) deckt,
# wuerde der Sync sie unten still ueberschreiben. Darum vorher rot.
if ! bash v2/transformation.sh --pruefen "$BASIS" "$ZIEL_SHA"; then
    aufraeumen
    fehler "$ZIEL weicht schon vor dem Sync ausserhalb von v2/ und (c) von upstream ${BASIS:0:7} + Transformation ab — erst angleichen (docs/v2/README.md)."
fi
# (c)-Pfade aus der Liste in v2/transformation.sh: nur sie und v2/ behalten die v2-Fassung.
HANDGEPFLEGT="$(sed -n 's/^HANDGEPFLEGT=(\(.*\))$/\1/p' v2/transformation.sh)"
[ -n "$HANDGEPFLEGT" ] || fehler "HANDGEPFLEGT in v2/transformation.sh nicht lesbar"

git merge --no-ff --no-commit "$UP_SHA" >/tmp/sync-merge.log 2>&1 || true
grep -E 'CONFLICT' /tmp/sync-merge.log || true
# Alles ausserhalb von v2/ und (c) auf die upstream-Fassung — loest Konflikte dort (POM-Zeilen neben
# der umbenannten groupId, CLAUDE.md-Kopf, build.yml) auf, bevor T sie neu erzeugt; Dateien, die
# upstream nicht (mehr) hat, fallen weg (T erzeugt keine neuen Dateien).
behalten() { local p="$1" h; case "$p" in v2/*) return 0 ;; esac; for h in $HANDGEPFLEGT; do [ "$p" = "$h" ] && return 0; done; return 1; }
UP_PFADE="$(git ls-tree -r --name-only "$UP_SHA")"
NACH_UP=""; WEG=""
while IFS= read -r p; do
    behalten "$p" && continue
    if grep -qxF -- "$p" <<<"$UP_PFADE"; then NACH_UP+="$p"$'\n'; else WEG+="$p"$'\n'; fi
done < <({ echo "$UP_PFADE"; git ls-files; } | sort -u)
[ -z "$NACH_UP" ] || printf '%s' "$NACH_UP" | git --literal-pathspecs checkout "$UP_SHA" --pathspec-from-file=-
[ -z "$WEG" ] || printf '%s' "$WEG" | git --literal-pathspecs rm -q -f --pathspec-from-file=-
if [ -n "$(git diff --name-only --diff-filter=U)" ]; then
    KONFLIKTE="$(git diff --name-only --diff-filter=U)"
    sag "KONFLIKT in $(echo "$KONFLIKTE" | wc -l | tr -d ' ') Datei(en):"
    echo "$KONFLIKTE" | sed 's/^/    /'
    ZWEIG="sync-konflikt/$(date -u +%Y%m%d)-$UP_KURZ"
    bash v2/transformation.sh || sag "Transformation scheiterte ebenfalls (Meldung oben)."
    git add -A
    git commit -q -m "Sync upstream plaintext-root $UP_KURZ — KONFLIKT, Marker im Baum

Diese Dateien tragen Konfliktmarker und muessen von Hand aufgeloest werden:
$(echo "$KONFLIKTE" | sed 's/^/  - /')

Danach: v2/transformation.sh ausfuehren; v2/transformation.sh --pruefen $UP_KURZ und
v2/umbenennen.sh --pruefen muessen gruen sein,
Commit auf diesen Zweig, PR mergen. master ist unveraendert geblieben."
    git branch -q -m "$ARBEIT" "$ZWEIG"
    if [ "$TROCKEN" = "1" ]; then
        sag "trocken: Konfliktzweig $ZWEIG lokal angelegt, kein Push, kein PR."
        git checkout -q "$ZIEL"
        exit 2
    fi
    git push -q -u origin "$ZWEIG"
    sag "Konfliktzweig gepusht: $ZWEIG"
    if command -v gh >/dev/null 2>&1 && [ -n "${GH_TOKEN:-}" ]; then
        gh label create sync-konflikt --color B60205 --description "Sync von upstream plaintext-root mit Konflikt, Handarbeit noetig" --force >/dev/null 2>&1 || true
        # Karte 1563 (23.10.2026): das Label fehlte im Repo, gh pr create --label brach ab und es gab
        # keinen PR. Darum zuerst mit Label, und scheitert das, derselbe PR ohne Label.
        PR_BODY="$(cat <<EOF
## Summary

Der automatische Sync von upstream plaintext-root (\`$UP_KURZ\`, $ANZAHL Commits, bis: $UP_BETREFF) ist auf Konflikte gelaufen. \`master\` ist unveraendert; dieser Zweig traegt den Merge **mit Konfliktmarkern**.

## Changes

Dateien mit Konfliktmarkern:
$(echo "$KONFLIKTE" | sed 's/^/- `/; s/$/`/')

## Testing

Noch nichts — aufloesen, dann:

\`\`\`
v2/transformation.sh && v2/transformation.sh --pruefen '$UP_KURZ' && v2/umbenennen.sh --pruefen
\`\`\`

## Checklist

- [ ] Konfliktmarker aufgeloest, Reaktor-groupId ueberall \`ch.plaintext.v2\` (\`v2/umbenennen.sh --pruefen\` gruen)
- [ ] PR-Pipeline gruen
- [ ] Merge durch den Jumphost (der Merge auf master loest ein v2-Release aus)
EOF
)"
        gh pr create --base "$ZIEL" --head "$ZWEIG" --label sync-konflikt \
            --title "Sync-Konflikt upstream $UP_KURZ" --body "$PR_BODY" \
        || gh pr create --base "$ZIEL" --head "$ZWEIG" \
            --title "Sync-Konflikt upstream $UP_KURZ" --body "$PR_BODY" \
        || sag "gh pr create fehlgeschlagen — PR von Hand anlegen (Zweig $ZWEIG)."
    else
        sag "kein gh/GH_TOKEN — PR von Hand anlegen: Zweig $ZWEIG, Label sync-konflikt."
    fi
    git checkout -q "$ZIEL"
    exit 2
fi
if ! bash v2/transformation.sh; then
    aufraeumen
    fehler "Transformation auf upstream $UP_KURZ gescheitert (Meldung oben) — Sync abgebrochen, $ZIEL unveraendert."
fi
git add -A
git commit -q -m "$MELDUNG"
sag "Merge + Transformation committet."

# ── 2. Hinweis auf uebergangene upstream-Aenderungen an merge=ours-Pfaden ───
EIGENE="$(grep -E 'merge=ours' .gitattributes 2>/dev/null | grep -vE '^\s*#' | awk '{print $1}' || true)"
if [ -n "$EIGENE" ]; then
    # shellcheck disable=SC2086
    UEBERGANGEN="$(git diff --name-only "$BASIS" "$UP_SHA" -- $EIGENE || true)"
    if [ -n "$UEBERGANGEN" ]; then
        sag "HINWEIS: upstream aenderte Dateien, deren v2-Fassung behalten wurde (merge=ours):"
        echo "$UEBERGANGEN" | sed 's/^/    /'
        sag "  Ansehen: git diff $BASIS $UP_SHA -- <pfad>   und bei Bedarf von Hand uebernehmen."
    fi
fi

# ── 3. Nachpruefung: v1 + T = v2 bis auf (c), groupId vollstaendig ─────────
if ! bash v2/transformation.sh --pruefen "$UP_SHA" HEAD || ! v2/umbenennen.sh --pruefen; then
    aufraeumen
    fehler "Pruefung nach Merge und Transformation rot — Sync abgebrochen, $ZIEL unveraendert."
fi

# ── 4. Push ─────────────────────────────────────────────────────────────────
sag "Ergebnis ($(git rev-list --count "$ZIEL_SHA..HEAD") neue Commits auf $ZIEL):"
git log --oneline "$ZIEL_SHA..HEAD" | head -5 | sed 's/^/    /'
if [ "$TROCKEN" = "1" ]; then
    sag "trocken: kein Push. Arbeitszweig $ARBEIT bleibt zur Ansicht stehen."
    git checkout -q "$ZIEL"
    exit 0
fi
git push -q origin "HEAD:$ZIEL"
sag "gepusht nach $ZIEL — Woodpecker (.woodpecker/deploy.yml) released daraus ein v2-Release."
git checkout -q "$ZIEL"
git merge -q --ff-only "$ARBEIT" 2>/dev/null || true
git branch -q -D "$ARBEIT" 2>/dev/null || true
