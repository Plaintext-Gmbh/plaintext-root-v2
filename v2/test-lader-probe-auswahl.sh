#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# v2/test-lader-probe-auswahl.sh — Auswahl der Modulversion in v2/lader-probe.sh (Karte 1563).
# Attrappe: Reposilite als file://-Verzeichnis, Huelle als Jar mit nur einem Manifest.
# Braucht curl und jar, kein Netz, keine DB. Exit 0 = alle Faelle wie erwartet.
set -eu
PROBE="$(cd "$(dirname "$0")" && pwd)/lader-probe.sh"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
R="$T/repo"; M="$R/ch/plaintext/plaintext-z-test"; mkdir -p "$M"

pom() {   # $1 version, $2 Inhalt im <project> (eigene Zeile, wie in echten POMs)
    mkdir -p "$M/$1"
    printf '<project>\n%s\n<artifactId>plaintext-z-test</artifactId><version>%s</version>\n</project>\n' "$2" "$1" > "$M/$1/plaintext-z-test-$1.pom"
}
pom 2.1.0  '<properties><plaintext-root.version>1.780.0</plaintext-root.version></properties>'
pom 2.2.0  '<properties><plaintext-root.version>1.790.0</plaintext-root.version></properties>'
pom 2.10.0 '<parent><groupId>ch.plaintext</groupId><artifactId>plaintext-app-parent</artifactId><version>9.0.0</version></parent>'
mkdir -p "$R/ch/plaintext/plaintext-app-parent/9.0.0"
printf '<project><properties><plaintext-root.version>1.800.0</plaintext-root.version></properties></project>\n' \
    > "$R/ch/plaintext/plaintext-app-parent/9.0.0/plaintext-app-parent-9.0.0.pom"
# Reihenfolge absichtlich unsortiert; 2.10.0 > 2.2.0 nur bei Versions- statt Textsortierung
printf '<metadata><versioning><release>2.10.0</release><versions><version>2.2.0</version><version>2.10.0</version><version>2.1.0</version></versions></versioning></metadata>\n' \
    > "$M/maven-metadata.xml"

huelle() {   # $1 Version, $2 kompatibel-ab -> Pfad
    local d="$T/h-$1-$2"; mkdir -p "$d"
    printf 'Plaintext-Root-Version: %s-SNAPSHOT\nPlaintext-Root-Kompatibel-Ab: %s\n' "$1" "$2" > "$d/mf"
    (cd "$d" && jar cfm h.jar mf); echo "$d/h.jar"
}

OK=0; FALSCH=0
fall() {   # $1 Beschreibung, $2 erwarteter Exit, $3 erwartete Ausgabe (grep), $4 Huelle, $5 Version, [Umgebung...]
    local name="$1" rc_soll="$2" muster="$3" h="$4" v="$5"; shift 5
    local aus rc
    aus="$(cd "$T" && env "$@" REPOSILITE_BASIS="file://$R" PROBE_ARBEIT="$T/arbeit" bash "$PROBE" --huelle "${h#"$T"/}" --nur-auswahl \
        --modul-gav "ch.plaintext:plaintext-z-test:$v" 2>&1)" && rc=0 || rc=$?
    if [ "$rc" = "$rc_soll" ] && echo "$aus" | grep -q -- "$muster"; then
        OK=$((OK + 1)); echo "OK      $name"
    else
        FALSCH=$((FALSCH + 1)); echo "FALSCH  $name (Exit $rc, erwartet $rc_soll / '$muster')"; echo "$aus" | sed 's/^/        /'
    fi
}
mkdir -p "$T/arbeit"
fall "neueste passt (Elternkette)"          0 "AUSWAHL plaintext-z-test 2.10.0 root-min 1.800.0" "$(huelle 1.800.0 1.767.0)" latest
fall "neueste zu neu -> aeltere"            0 "AUSWAHL plaintext-z-test 2.2.0 root-min 1.790.0"  "$(huelle 1.799.0 1.767.0)" latest
fall "zwei zu neu -> drittneueste"          0 "AUSWAHL plaintext-z-test 2.1.0 root-min 1.780.0"  "$(huelle 1.785.0 1.767.0)" latest
fall "keine passt -> Exit 3"                3 "KEINE KOMPATIBLE VERSION"                          "$(huelle 1.770.0 1.767.0)" latest
fall "unter kompatibel-ab -> Exit 3"        3 "kompatibel-ab 1.795.0"                             "$(huelle 1.799.0 1.795.0)" latest
fall "Obergrenze 1 Versuch -> Exit 3"       3 "keine der 1 neuesten"                              "$(huelle 1.799.0 1.767.0)" latest PROBE_MAX_VERSUCHE=1
fall "explizite Version ohne Auswahl"       0 "AUSWAHL plaintext-z-test 2.10.0 root-min ?"        "$(huelle 1.770.0 1.767.0)" 2.10.0
echo "test-lader-probe-auswahl: $OK ok, $FALSCH falsch"
[ "$FALSCH" = "0" ]
