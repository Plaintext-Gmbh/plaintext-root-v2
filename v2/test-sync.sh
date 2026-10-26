#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# v2/test-sync.sh — Szenarien fuer v2/sync.sh mit lokalen Repos, ohne Netz und ohne gh (Karte 1595).
#   U = upstream (Baum der v1-Basis), V = v2 (U + Baum von HEAD), O = bare origin von V.
#   A  upstream (Betreff mit Skip-Marker) aendert eine POM-Zeile neben der umbenannten groupId, den CLAUDE.md-Kopf,
#      build.yml ausserhalb des Patches und legt eine Datei an -> Exit 0, Ergebnis = T(upstream)
#   B  master traegt eine Handabweichung (README.md)          -> Exit 1, Vorpruefung rot
#   C  upstream aendert eine Stelle von transformation.patch  -> Exit 1, Transformation rot
#   D  upstream legt eine (c)-Datei an (docs/v2/README.md)    -> Exit 2, Konfliktzweig
# Aufruf: v2/test-sync.sh [v1-ref]   (Standard: git merge-base HEAD upstream/master)
set -uo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
BASIS="${1:-$(git -C "$REPO" merge-base HEAD upstream/master)}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
ok=0; falsch=0
pruef() { if eval "$2"; then echo "ok      $1"; ok=$((ok + 1)); else echo "FALSCH  $1"; falsch=$((falsch + 1)); fi; }
g() { git -c user.name=test -c user.email=test@example.invalid "$@"; }

aufbauen() {   # $1 = Szenario-Verzeichnis
    local d="$1"
    mkdir -p "$d/U" "$d/V"
    git -C "$REPO" archive "$BASIS" | tar -x -C "$d/U"
    g -C "$d/U" init -q -b master && g -C "$d/U" add -A && g -C "$d/U" commit -qm "v1"
    g clone -q "$d/U" "$d/V2tmp" && mv "$d/V2tmp/.git" "$d/V/.git" && rm -rf "$d/V2tmp"
    git -C "$REPO" archive HEAD | tar -x -C "$d/V"
    g -C "$d/V" add -A && g -C "$d/V" commit -qm "v2"
    g clone -q --bare "$d/V" "$d/O"
    g -C "$d/V" remote set-url origin "$d/O"
}
upstream_commit() { g -C "$1/U" add -A && g -C "$1/U" commit -qm "$2"; }
sync() {   # $1 = Szenario; Ausgabe nach $1/log, Exit-Code zurueck
    (cd "$1/V" && env -u GH_TOKEN GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=t@example.invalid \
        GIT_COMMITTER_NAME=test GIT_COMMITTER_EMAIL=t@example.invalid \
        UPSTREAM_URL="$1/U" bash v2/sync.sh --trocken) >"$1/log" 2>&1
}

# ── A ───────────────────────────────────────────────────────────────────────
A="$TMP/A"; aufbauen "$A"
sed -i '0,/<groupId>ch.plaintext<\/groupId>/s||&\n        <!-- upstream-Zeile direkt unter der groupId -->|' "$A/U/plaintext-root-common/pom.xml"
sed -i '1s/.*/# plaintext-root — Claude orientation (neu)/' "$A/U/CLAUDE.md"
sed -i 's/REPORT_KEEP: "30"/REPORT_KEEP: "31"/' "$A/U/.woodpecker/build.yml"
echo neu > "$A/U/neu-upstream.txt"
upstream_commit "$A" "Prepare next development iteration 1.999.0-SNAPSHOT [skip ci] [CI SKIP]"
sync "$A"; rc=$?
ZW="$(git -C "$A/V" branch --list 'sync/arbeit-*' --format='%(refname:short)')"
pruef "A: Exit 0" '[ "$rc" = 0 ]'
pruef "A: Arbeitszweig mit Merge-Commit (2 Eltern)" '[ -n "$ZW" ] && [ "$(git -C "$A/V" rev-list --parents -1 "$ZW" | wc -w)" = 3 ]'
pruef "A: neue upstream-Datei da" 'git -C "$A/V" cat-file -e "$ZW:neu-upstream.txt"'
pruef "A: build.yml mit upstream-Aenderung UND v2-Schritten" 'git -C "$A/V" show "$ZW:.woodpecker/build.yml" | grep -q "REPORT_KEEP: \"31\"" && git -C "$A/V" show "$ZW:.woodpecker/build.yml" | grep -q "^  lader-probe:"'
pruef "A: POM-Zeile neben groupId uebernommen, groupId v2" 'git -C "$A/V" show "$ZW:plaintext-root-common/pom.xml" | grep -A1 "<groupId>ch.plaintext.v2</groupId>" | grep -q "upstream-Zeile"'
pruef "A: CLAUDE.md = v2-Kopf + upstream-Zeile" 'git -C "$A/V" show "$ZW:CLAUDE.md" | sed -n "1p;5p" | tr "\n" "|" | grep -q "^> \*\*plaintext-root-v2.*|# plaintext-root — Claude orientation (neu)|$"'
pruef "A: sonar.yml weiter entfernt" '! git -C "$A/V" cat-file -e "$ZW:.woodpecker/sonar.yml" 2>/dev/null'
pruef "A: Merge-Meldung ohne Skip-Marker, Betreff uebernommen" 'm="$(git -C "$A/V" log -1 --format=%B "$ZW")"; ! grep -qiE "\[(skip[ -]ci|ci[ -]skip)\]" <<<"$m" && grep -q "Prepare next development iteration 1.999.0-SNAPSHOT" <<<"$m"'
pruef "A: master unveraendert (trocken)" '[ "$(git -C "$A/O" rev-parse master)" = "$(git -C "$A/V" rev-parse master)" ]'

# ── B ───────────────────────────────────────────────────────────────────────
B="$TMP/B"; aufbauen "$B"
echo "hand" >> "$B/V/README.md"; g -C "$B/V" commit -qam "Handabweichung"; g -C "$B/V" push -q origin master
echo x > "$B/U/neu-upstream.txt"; upstream_commit "$B" "upstream B"
sync "$B"; rc=$?
pruef "B: Exit 1" '[ "$rc" = 1 ]'
pruef "B: Meldung nennt README.md und die Vorpruefung" 'grep -q "Inhalt weicht ab: README.md" "$B/log" && grep -q "schon vor dem Sync" "$B/log"'

# ── C ───────────────────────────────────────────────────────────────────────
C="$TMP/C"; aufbauen "$C"
sed -i 's|^GRUPPE_PFAD="ch/plaintext"$|GRUPPE_PFAD="ch/plaintext"  # upstream|' "$C/U/scripts/release-vollstaendig.sh"
upstream_commit "$C" "upstream C"
sync "$C"; rc=$?
pruef "C: Exit 1" '[ "$rc" = 1 ]'
pruef "C: Meldung: Patch passt nicht" 'grep -q "transformation.patch passt nicht mehr" "$C/log"'
pruef "C: kein Arbeitszweig uebrig mit Commit" '[ -z "$(git -C "$C/V" branch --list "sync/arbeit-*")" ] || [ "$(git -C "$C/V" rev-parse "$(git -C "$C/V" branch --list "sync/arbeit-*" --format="%(refname:short)")")" = "$(git -C "$C/V" rev-parse master)" ]'

# ── D ───────────────────────────────────────────────────────────────────────
D="$TMP/D"; aufbauen "$D"
mkdir -p "$D/U/docs/v2"; echo "upstream" > "$D/U/docs/v2/README.md"
upstream_commit "$D" "upstream D"
sync "$D"; rc=$?
pruef "D: Exit 2" '[ "$rc" = 2 ]'
pruef "D: Konfliktzweig nennt docs/v2/README.md" 'grep -q "KONFLIKT" "$D/log" && grep -q "docs/v2/README.md" "$D/log" && [ -n "$(git -C "$D/V" branch --list "sync-konflikt/*")" ]'

echo "test-sync: $ok ok, $falsch falsch"
[ "$falsch" = 0 ] || { echo "── Logs ──"; for s in A B C D; do echo "## $s"; tail -15 "$TMP/$s/log"; done; exit 1; }
