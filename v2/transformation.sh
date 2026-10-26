#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# ══════════════════════════════════════════════════════════════════════════════
#  v2/transformation.sh — v1 (plaintext-root) -> v2 (plaintext-root-v2), Karte 1595
#
#  GRUNDSATZ: Ausserhalb von v2/ ist plaintext-root-v2 = T(upstream), bis auf die handgepflegten
#  (c)-Dateien unten. T ist deterministisch und idempotent:
#    1. v2/umbenennen.sh               groupId ch.plaintext -> ch.plaintext.v2 in allen Reaktor-POMs
#    2. Dateien entfernen              .woodpecker/sonar.yml (keine Sonar/OWASP-Laeufe in v2),
#                                      .github/workflows/repo-lint.yaml (self-hosted Runner, Cron)
#    3. renovate.json                  "enabled": false als erste Eigenschaft (Renovate aus)
#    4. CLAUDE.md                      v2-Hinweis als Kopf
#    5. v2/transformation.patch        .woodpecker/build.yml (kein Cron, v2-Schritte, Lader-Probe),
#                                      scripts/release-vollstaendig.sh (GRUPPE_PFAD, TAG_PRAEFIX)
#  Passt ein Schritt nicht mehr auf den upstream-Stand (Patch-Kontext geaendert, renovate.json
#  ohne `{` in Zeile 1, ...), bricht T mit Exit 1 ab — der Sync wird rot, statt still zu raten.
#
#  AUFRUF
#    v2/transformation.sh                  T auf den Arbeitsbaum anwenden (Wurzel = Repo-Wurzel)
#    v2/transformation.sh --pruefen REF    T(REF) gegen HEAD vergleichen (ohne v2/); Exit 1, wenn
#                                          etwas ausserhalb der (c)-Liste abweicht. REF = v1-Commit,
#                                          in CI und Sync: git merge-base HEAD upstream/master
#    v2/transformation.sh --patch-erneuern REF
#                                          v2/transformation.patch aus `git diff REF` der
#                                          Patch-Dateien neu schreiben (nach einer Handaenderung
#                                          an build.yml oder release-vollstaendig.sh)
#    v2/transformation.sh --selbsttest REF Beweis, Idempotenz und Negativproben (siehe unten)
#    Umgebung: TRANSFORM_WURZEL=<pfad> fuer einen anderen Baum (Beweis, Tests).
# ══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

V2="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$V2/.." && pwd)"
PATCH="$V2/transformation.patch"
PATCH_DATEIEN=(.woodpecker/build.yml scripts/release-vollstaendig.sh)
ENTFERNEN=(.woodpecker/sonar.yml .github/workflows/repo-lint.yaml)
# (c): handgepflegt, weder angleichbar noch per T erzeugbar. Begruendung je Datei: docs/v2/README.md.
# Was davon auch in upstream existiert, steht zusaetzlich in .gitattributes auf merge=ours.
HANDGEPFLEGT=(.gitattributes .github/workflows/v2-sync.yaml .woodpecker/deploy.yml docs/v2/README.md docs/v2/analyse.md)
KOPF='> **plaintext-root-v2 (Karte 1563):** dieses Repo ist die v2-Abspaltung von plaintext-root mit'

fehler() { echo "transformation: FEHLER: $*" >&2; exit 1; }

anwenden() {
    local w="$1" f
    UMBENENNEN_WURZEL="$w" bash "$V2/umbenennen.sh" >/dev/null || fehler "v2/umbenennen.sh scheiterte"
    for f in "${ENTFERNEN[@]}"; do rm -f "$w/$f"; done
    f="$w/renovate.json"
    [ -f "$f" ] || fehler "renovate.json fehlt"
    [ "$(sed -n 1p "$f")" = "{" ] || fehler "renovate.json beginnt nicht mit '{' in Zeile 1"
    [ "$(sed -n 2p "$f")" = '  "enabled": false,' ] || sed -i '1a\  "enabled": false,' "$f"
    f="$w/CLAUDE.md"
    [ -f "$f" ] || fehler "CLAUDE.md fehlt"
    if [ "$(head -1 "$f")" != "$KOPF" ]; then
        { printf '%s\n' "$KOPF" \
            '> groupId `ch.plaintext.v2`, eigenem Sync, Release (`v2/release.sh`, Tags `v2-X`) und Purge.' \
            '> Alles v2-Spezifische: `docs/v2/README.md`. Der Rest dieser Datei ist upstream-Stand.' ''
          cat "$f"; } > "$f.tmp" && mv "$f.tmp" "$f"
    fi
    if git -C "$w" apply --reverse --check "$PATCH" 2>/dev/null; then
        :   # schon angewandt
    elif ! git -C "$w" apply "$PATCH" 2>"$w/.transformation.err"; then
        cat "$w/.transformation.err" >&2; rm -f "$w/.transformation.err"
        fehler "v2/transformation.patch passt nicht mehr auf den upstream-Stand (${PATCH_DATEIEN[*]}).
  Datei von Hand angleichen, dann: v2/transformation.sh --patch-erneuern <v1-commit>"
    fi
    rm -f "$w/.transformation.err"
}

ist_handgepflegt() { local h; for h in "${HANDGEPFLEGT[@]}"; do [ "$1" = "$h" ] && return 0; done; return 1; }

# $1 = T(v1)-Baum, $2 = v2-Baum. Gibt je abweichende Datei eine Zeile aus; Exit 1 bei Abweichung.
vergleichen() {
    local a="$1" b="$2" f rot=0
    while IFS= read -r f; do
        case "$f" in v2/*) continue ;; esac
        ist_handgepflegt "$f" && continue
        if [ ! -e "$a/$f" ]; then echo "  nur in v2:      $f"; rot=1
        elif [ ! -e "$b/$f" ]; then echo "  nur in T(v1):   $f"; rot=1
        elif ! cmp -s "$a/$f" "$b/$f"; then echo "  Inhalt weicht ab: $f"; rot=1
        elif [ "$(test -x "$a/$f" && echo x)" != "$(test -x "$b/$f" && echo x)" ]; then echo "  Modus weicht ab: $f"; rot=1
        fi
    done < <({ (cd "$a" && find . -type f -o -type l); (cd "$b" && find . -type f -o -type l); } | sed 's|^\./||' | sort -u)
    for f in "${HANDGEPFLEGT[@]}"; do
        [ -e "$b/$f" ] || { echo "  (c)-Datei fehlt in v2: $f"; rot=1; }
        if [ -e "$a/$f" ] && cmp -s "$a/$f" "$b/$f"; then echo "  HINWEIS: (c)-Datei $f weicht nicht mehr ab — aus HANDGEPFLEGT streichen"; fi
    done
    return "$rot"
}

baum() { mkdir -p "$2"; git -C "$REPO" archive "$1" | tar -x -C "$2"; }

pruefen() {
    local ref="$1" ziel="${2:-HEAD}" tmp rc=0
    tmp="$(mktemp -d)"
    baum "$ref" "$tmp/t" && (anwenden "$tmp/t") || { rm -rf "$tmp"; fehler "T(${ref:0:10}) laesst sich nicht bilden"; }
    baum "$ziel" "$tmp/v2"
    echo "transformation: vergleiche T(${ref:0:10}) mit $ziel (ohne v2/ und ${#HANDGEPFLEGT[@]} (c)-Dateien)"
    vergleichen "$tmp/t" "$tmp/v2" || rc=$?
    rm -rf "$tmp"
    if [ "$rc" -ne 0 ]; then
        echo "transformation: PRUEFUNG ROT — Abweichung ausserhalb von v2/, die weder T erzeugt noch die (c)-Liste deckt." >&2
        return 1
    fi
    echo "transformation: Pruefung gruen — v1 + T = v2 bis auf (c)."
}

selbsttest() {
    local ref="$1" tmp s1 s2
    tmp="$(mktemp -d)"
    echo "── Selbsttest 1: v1 + T = HEAD bis auf (c) ──"
    pruefen "$ref" HEAD
    echo "── Selbsttest 2: Idempotenz (T zweimal) ──"
    baum "$ref" "$tmp/t"; anwenden "$tmp/t"
    s1="$(cd "$tmp/t" && find . -type f -print0 | sort -z | xargs -0 cksum | cksum)"
    anwenden "$tmp/t"
    s2="$(cd "$tmp/t" && find . -type f -print0 | sort -z | xargs -0 cksum | cksum)"
    [ "$s1" = "$s2" ] || fehler "Selbsttest 2: zweiter Lauf hat den Baum veraendert"
    echo "   gruen: zweiter Lauf aendert nichts."
    echo "── Selbsttest 3: Handaenderung ausserhalb von v2/ wird erkannt ──"
    baum HEAD "$tmp/v2"
    echo "# hand" >> "$tmp/v2/README.md"
    if vergleichen "$tmp/t" "$tmp/v2" >/dev/null; then fehler "Selbsttest 3: Handaenderung an README.md nicht erkannt"; fi
    rm -f "$tmp/v2/.m3-jar-volume"
    s1="$(vergleichen "$tmp/t" "$tmp/v2" || true)"
    grep -q 'nur in T(v1):   .m3-jar-volume' <<<"$s1" || fehler "Selbsttest 3: fehlende Datei nicht erkannt"
    echo "   gruen: geaenderte und fehlende Datei melden rot."
    echo "── Selbsttest 4: upstream aendert eine Patch-Stelle -> T bricht ab ──"
    rm -rf "$tmp/t"; baum "$ref" "$tmp/t"
    sed -i 's|^GRUPPE_PFAD="ch/plaintext"$|GRUPPE_PFAD="ch/plaintext/anders"|' "$tmp/t/scripts/release-vollstaendig.sh"
    if (anwenden "$tmp/t") 2>/dev/null; then fehler "Selbsttest 4: T lief trotz geaenderter Patch-Stelle durch"; fi
    echo "   gruen: T bricht ab (Exit != 0)."
    echo "── Selbsttest 5: renovate.json ohne '{' in Zeile 1 -> T bricht ab ──"
    rm -rf "$tmp/t"; baum "$ref" "$tmp/t"
    sed -i '1s/^{$/{ "x": 1,/' "$tmp/t/renovate.json"
    if (anwenden "$tmp/t") 2>/dev/null; then fehler "Selbsttest 5: T lief trotz unbekannter renovate.json durch"; fi
    echo "   gruen: T bricht ab."
    rm -rf "$tmp"
    echo "transformation: SELBSTTEST GRUEN"
}

patch_erneuern() {
    local ref="$1"
    git -C "$REPO" diff --no-color --no-ext-diff "$ref" -- "${PATCH_DATEIEN[@]}" > "$PATCH"
    echo "transformation: $PATCH neu geschrieben ($(grep -c '^@@' "$PATCH") Hunks)"
}

case "${1:-}" in
    "")               anwenden "${TRANSFORM_WURZEL:-$REPO}"; echo "transformation: angewandt auf ${TRANSFORM_WURZEL:-$REPO}" ;;
    --pruefen)        [ -n "${2:-}" ] || fehler "--pruefen braucht REF"; pruefen "$2" "${3:-HEAD}" ;;
    --patch-erneuern) [ -n "${2:-}" ] || fehler "--patch-erneuern braucht REF"; patch_erneuern "$2" ;;
    --selbsttest)     [ -n "${2:-}" ] || fehler "--selbsttest braucht REF"; selbsttest "$2" ;;
    *)                fehler "unbekannte Option '$1'" ;;
esac
