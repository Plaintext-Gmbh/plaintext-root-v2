#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# ══════════════════════════════════════════════════════════════════════════════
#  v2/umbenennen.sh — Maven-groupId der Reaktor-Module: ch.plaintext -> ch.plaintext.v2
#
#  Karte 1563. plaintext-root-v2 ist ein eigenstaendiges Repo mit der Historie von
#  plaintext-root (upstream). Beide veroeffentlichen in DIESELBEN Paket-Repos
#  (GitHub Packages plaintext-mvn, Reposilite maven.plaintext.ch). Damit ein v2-Release nie
#  ein v1-Artefakt ueberschreibt, tragen ALLE Reaktor-Artefakte von v2 die groupId
#  `ch.plaintext.v2`. Java-Pakete bleiben `ch.plaintext` — nur Maven-Koordinaten aendern sich.
#
#  WAS DAS SKRIPT AENDERT (nur in pom.xml-Dateien):
#    1. <groupId>ch.plaintext</groupId>, wenn die naechste <artifactId> ein Reaktor-Artefakt ist
#       (Parent-Block, eigene groupId der Wurzel, dependencies, dependencyManagement,
#       annotationProcessorPaths, Plugin-Abhaengigkeiten).
#    2. Koordinaten-Strings `ch.plaintext:<reaktor-artefakt>` (Surefire dependenciesToScan,
#       classpathDependencyExcludes, Systemproperties).
#    3. <includeGroupIds>/<excludeGroupIds>ch.plaintext</...>, wenn der zugehoerige
#       <includeArtifactIds>/<excludeArtifactIds> ausschliesslich Reaktor-Artefakte nennt.
#  WAS ES NICHT AENDERT: Abhaengigkeiten auf Artefakte, die NICHT im Reaktor liegen (z.B. ein
#  `ch.plaintext:plaintext-z-verrechnung` aus plaintext-app), Java-Pakete, Ressourcen,
#  AutoConfiguration.imports (dort stehen Klassennamen, keine Koordinaten — `--pruefen` belegt das).
#
#  Das Reaktor-Set wird aus der Wurzel-pom gelesen (<module>-Eintraege + deren artifactId +
#  die Wurzel selbst), nie aus einer gepflegten Liste: ein neues upstream-Modul wird beim naechsten
#  Sync automatisch mit umbenannt.
#
#  IDEMPOTENT: ein zweiter Lauf aendert nichts (`--selbsttest` beweist das, siehe unten).
#  Der Sync-Job (.github/workflows/v2-sync.yaml) ruft es nach jedem upstream-Merge auf.
#
#  AUFRUF
#    v2/umbenennen.sh              Umbenennung im Arbeitsbaum (Reaktorwurzel = Repo-Wurzel)
#    v2/umbenennen.sh --pruefen    nur pruefen: Exit 1, wenn noch eine Reaktor-Koordinate
#                                  `ch.plaintext` traegt (Grep-Test fuer CI und Sync)
#    v2/umbenennen.sh --selbsttest Fixture-Test (positiv, negativ, Fremd-Artefakt bleibt) und
#                                  Idempotenz-Test auf dem echten Arbeitsbaum
#    Umgebung: UMBENENNEN_WURZEL=<pfad> fuer eine andere Reaktorwurzel (Tests).
#
#  Nur bash, awk, sed, grep, find: das Skript laeuft im Maven-Step-Image (Woodpecker) genauso
#  wie auf ubuntu-latest (Sync-Job) und auf dem Jumphost.
# ══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

ALT="ch.plaintext"
NEU="ch.plaintext.v2"
SELBST="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"

fehler() { echo "umbenennen: FEHLER: $*" >&2; exit 1; }

# ── Reaktor-Set aus der Wurzel-pom ──────────────────────────────────────────────
# Gibt je Zeile eine artifactId aus: die Wurzel selbst und jedes <module>.
reaktor_artefakte() {
    local wurzel="$1" m aid
    [ -f "$wurzel/pom.xml" ] || fehler "$wurzel/pom.xml fehlt"
    # artifactId der Wurzel: die erste NACH </parent> (bzw. die erste ueberhaupt ohne Parent).
    awk '/<parent>/{p=1} /<\/parent>/{p=0; next} !p && /<artifactId>/{gsub(/.*<artifactId>|<\/artifactId>.*/,""); print; exit}' "$wurzel/pom.xml"
    for m in $(grep -oE '<module>[^<]+</module>' "$wurzel/pom.xml" | sed 's/<module>//;s/<\/module>//'); do
        [ -f "$wurzel/$m/pom.xml" ] || fehler "Modul '$m' aus pom.xml hat keine $m/pom.xml"
        aid="$(awk '/<parent>/{p=1} /<\/parent>/{p=0; next} !p && /<artifactId>/{gsub(/.*<artifactId>|<\/artifactId>.*/,""); print; exit}' "$wurzel/$m/pom.xml")"
        [ -n "$aid" ] || fehler "artifactId von Modul '$m' nicht lesbar"
        echo "$aid"
    done
}

# Alle pom.xml unterhalb der Wurzel, ohne Build-Ausgabe und ohne die Fixtures dieses Skripts.
pom_dateien() {
    local wurzel="$1"
    find "$wurzel" -name pom.xml -not -path '*/target/*' -not -path '*/.git/*' -not -path "$wurzel/v2/*" | sort
}

# ── Das Herzstueck: eine pom.xml umschreiben ────────────────────────────────────
# $1 = pom, $2 = Datei mit dem Reaktor-Set (eine artifactId je Zeile), $3 = Zielmodus
#   schreiben -> Datei wird ersetzt, Exit 0; Ausgabe "geaendert"/"unveraendert"
#   pruefen   -> nichts schreiben; gibt die Zeilen aus, die noch umzubenennen waeren
umschreiben() {
    local pom="$1" setdatei="$2" modus="$3" tmp
    tmp="$(mktemp)"
    awk -v ALT="$ALT" -v NEU="$NEU" -v SETDATEI="$setdatei" -v MODUS="$modus" '
    function im_set(a) { return (a in SET) }
    function trim(s) { sub(/^[ \t]+/, "", s); sub(/[ \t\r]+$/, "", s); return s }
    # naechste <artifactId>…</artifactId> ab Zeile i+1 (max. 8 Zeilen, Kommentarzeilen uebersprungen)
    function naechste_artifactid(i,   j, s) {
        for (j = i + 1; j <= n && j <= i + 8; j++) {
            s = L[j]
            if (s ~ /<artifactId>/) { sub(/.*<artifactId>[ \t]*/, "", s); sub(/[ \t]*<\/artifactId>.*/, "", s); return s }
            if (s ~ /<groupId>/) return ""
        }
        return ""
    }
    # <includeArtifactIds>a,b</includeArtifactIds>: alle genannten Artefakte im Set?
    function filter_nur_reaktor(i, tag,   j, s, k, teile, anzahl) {
        for (j = i - 3; j <= i + 3; j++) {
            if (j < 1 || j > n || j == i) continue
            s = L[j]
            if (index(s, "<" tag ">") == 0) continue
            sub(".*<" tag ">", "", s); sub("</" tag ">.*", "", s)
            anzahl = split(s, teile, ",")
            if (anzahl == 0) return 0
            for (k = 1; k <= anzahl; k++) if (!im_set(trim(teile[k]))) return 0
            return 1
        }
        return 0
    }
    BEGIN {
        while ((getline a < SETDATEI) > 0) if (a != "") SET[a] = 1
        close(SETDATEI)
        n = 0
    }
    { L[++n] = $0 }
    END {
        geaendert = 0
        for (i = 1; i <= n; i++) {
            z = L[i]; vorher = z
            # 1. <groupId>ALT</groupId> mit Reaktor-artifactId (gleiche Zeile oder darunter)
            if (index(z, "<groupId>" ALT "</groupId>") > 0) {
                s = z
                if (s ~ ("<groupId>" ALT "</groupId>[ \t]*<artifactId>")) {
                    sub(".*<artifactId>[ \t]*", "", s); sub("[ \t]*</artifactId>.*", "", s); a = s
                } else {
                    a = naechste_artifactid(i)
                }
                if (a != "" && im_set(a)) {
                    sub("<groupId>" ALT "</groupId>", "<groupId>" NEU "</groupId>", z)
                }
            }
            # 2. Koordinaten-Strings ALT:<reaktor-artefakt>
            if (index(z, ALT ":") > 0) {
                neu = ""; rest = z
                while ((p = index(rest, ALT ":")) > 0) {
                    links = substr(rest, 1, p - 1); nach = substr(rest, p + length(ALT) + 1)
                    # Wortgrenze davor: kein [A-Za-z0-9_.-] unmittelbar vor ALT
                    davor = (p > 1) ? substr(rest, p - 1, 1) : ""
                    a = nach; sub(/[^A-Za-z0-9_.-].*$/, "", a)
                    if (davor !~ /[A-Za-z0-9_.-]/ && a != "" && im_set(a)) {
                        neu = neu links NEU ":"
                    } else {
                        neu = neu links ALT ":"
                    }
                    rest = nach
                }
                z = neu rest
            }
            # 3. include/excludeGroupIds, wenn der Artefakt-Filter nur Reaktor-Artefakte nennt
            if (index(z, "<includeGroupIds>" ALT "</includeGroupIds>") > 0 && filter_nur_reaktor(i, "includeArtifactIds"))
                sub("<includeGroupIds>" ALT "</includeGroupIds>", "<includeGroupIds>" NEU "</includeGroupIds>", z)
            if (index(z, "<excludeGroupIds>" ALT "</excludeGroupIds>") > 0 && filter_nur_reaktor(i, "excludeArtifactIds"))
                sub("<excludeGroupIds>" ALT "</excludeGroupIds>", "<excludeGroupIds>" NEU "</excludeGroupIds>", z)
            if (z != vorher) { geaendert++; if (MODUS == "pruefen") print FILENAME ":" i ": " trim(vorher) }
            if (MODUS == "schreiben") print z > (FILENAME ".umbenennen.tmp")
        }
        if (MODUS == "schreiben") { close(FILENAME ".umbenennen.tmp"); exit (geaendert > 0 ? 0 : 3) }
        exit (geaendert > 0 ? 1 : 0)
    }' "$pom" && rc=0 || rc=$?
    rm -f "$tmp"
    if [ "$modus" = "schreiben" ]; then
        case "$rc" in
            0) mv "$pom.umbenennen.tmp" "$pom"; echo "geaendert    $pom" ;;
            3) rm -f "$pom.umbenennen.tmp"; echo "unveraendert $pom" ;;
            *) rm -f "$pom.umbenennen.tmp"; fehler "awk scheiterte an $pom (rc=$rc)" ;;
        esac
        return 0
    fi
    return "$rc"
}

# ── Lauf ueber eine Reaktorwurzel ───────────────────────────────────────────────
# $1 = Wurzel, $2 = schreiben|pruefen. Im Modus pruefen Exit 1 bei offenen Stellen.
laufen() {
    local wurzel="$1" modus="$2" setdatei pom offen=0 anzahl=0
    setdatei="$(mktemp)"
    reaktor_artefakte "$wurzel" | sort -u > "$setdatei"
    anzahl="$(wc -l < "$setdatei" | tr -d ' ')"
    [ "$anzahl" -ge 1 ] || fehler "Reaktor-Set leer"
    echo "umbenennen: $anzahl Reaktor-Artefakte, Wurzel $wurzel, Modus $modus"
    while IFS= read -r pom; do
        if [ "$modus" = "schreiben" ]; then
            umschreiben "$pom" "$setdatei" schreiben
        else
            umschreiben "$pom" "$setdatei" pruefen || offen=$((offen + 1))
        fi
    done < <(pom_dateien "$wurzel")
    if [ "$modus" = "pruefen" ]; then
        # Zweiter Blick, unabhaengig vom awk oben: Koordinaten-Strings in Ressourcen und Imports.
        # AutoConfiguration.imports und spring.factories enthalten Klassennamen (ch.plaintext.x.Y),
        # keine Maven-Koordinaten (ch.plaintext:artefakt) — das wird hier belegt, nicht angenommen.
        # Nur src/-Verzeichnisse: quality/quality-gate.properties traegt den Sonar-Projektschluessel
        # (ch.plaintext:plaintext-root-parent) — das ist kein Maven-Bezug, und v2 meldet an Sonar
        # ohnehin unter einem eigenen Schluessel (.woodpecker/sonar.yml).
        local treffer srcdirs
        srcdirs="$(find "$wurzel" -type d -name src -not -path '*/target/*' -not -path '*/.git/*' -not -path "$wurzel/v2/*")"
        treffer=""
        if [ -n "$srcdirs" ]; then
            # shellcheck disable=SC2086
            treffer="$(grep -rn --include='*.imports' --include='spring.factories' --include='*.yml' \
                --include='*.yaml' --include='*.properties' --include='*.xml' --exclude='pom.xml' \
                -E "(^|[^A-Za-z0-9_.-])${ALT}:($(paste -sd'|' "$setdatei"))([^A-Za-z0-9_.-]|$)" $srcdirs || true)"
        fi
        if [ -n "$treffer" ]; then
            echo "$treffer"
            echo "umbenennen: Reaktor-Koordinaten mit $ALT in Ressourcen gefunden (oben)." >&2
            offen=$((offen + 1))
        fi
        rm -f "$setdatei"
        if [ "$offen" -gt 0 ]; then
            echo "umbenennen: PRUEFUNG ROT — $offen Datei(en) tragen noch Reaktor-Koordinaten mit $ALT." >&2
            return 1
        fi
        echo "umbenennen: Pruefung gruen — keine Reaktor-Koordinate traegt mehr $ALT (Ressourcen und Imports eingeschlossen)."
        return 0
    fi
    rm -f "$setdatei"
}

# ── Selbsttest ──────────────────────────────────────────────────────────────────
selbsttest() {
    local fx erwartet ist
    fx="$(mktemp -d)"
    mkdir -p "$fx/modul-a" "$fx/modul-b" "$fx/modul-a/src/main/resources/META-INF/spring"
    cat > "$fx/pom.xml" <<'EOF'
<project>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
    </parent>
    <groupId>ch.plaintext</groupId>
    <artifactId>fixture-parent</artifactId>
    <version>1.0.0</version>
    <modules>
        <module>modul-a</module>
        <module>modul-b</module>
    </modules>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>ch.plaintext</groupId>
                <artifactId>modul-b</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>ch.plaintext</groupId>
                <!-- Kommentar zwischen groupId und artifactId -->
                <artifactId>modul-a</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>ch.plaintext</groupId>
                <artifactId>plaintext-z-fremd</artifactId>
                <version>2.0.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
EOF
    cat > "$fx/modul-a/pom.xml" <<'EOF'
<project>
    <parent>
        <groupId>ch.plaintext</groupId>
        <artifactId>fixture-parent</artifactId>
        <version>1.0.0</version>
    </parent>
    <artifactId>modul-a</artifactId>
    <dependencies>
        <dependency><groupId>ch.plaintext</groupId><artifactId>modul-b</artifactId></dependency>
        <dependency><groupId>ch.plaintext</groupId><artifactId>plaintext-z-fremd</artifactId></dependency>
        <dependency><groupId>org.jsoup</groupId><artifactId>jsoup</artifactId></dependency>
    </dependencies>
    <build><plugins><plugin>
        <configuration>
            <dependenciesToScan><dependency>ch.plaintext:modul-b</dependency></dependenciesToScan>
            <liste>ch.plaintext:modul-b,ch.plaintext:plaintext-z-fremd,ch.plaintext:modul-a</liste>
            <includeGroupIds>ch.plaintext</includeGroupIds>
            <includeArtifactIds>modul-b</includeArtifactIds>
        </configuration>
    </plugin><plugin>
        <configuration>
            <includeGroupIds>ch.plaintext</includeGroupIds>
            <includeArtifactIds>modul-b,plaintext-z-fremd</includeArtifactIds>
        </configuration>
    </plugin></plugins></build>
</project>
EOF
    cat > "$fx/modul-b/pom.xml" <<'EOF'
<project>
    <parent>
        <groupId>ch.plaintext</groupId>
        <artifactId>fixture-parent</artifactId>
        <version>1.0.0</version>
    </parent>
    <artifactId>modul-b</artifactId>
    <name>ch.plaintext:modul-b Beschreibung</name>
</project>
EOF
    echo "ch.plaintext.a.AConfig" > "$fx/modul-a/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"

    echo "── Selbsttest 1: Umbenennung der Fixture ──"
    UMBENENNEN_WURZEL="$fx" "$SELBST" >/dev/null
    erwartet="$(cat <<'EOF'
<project>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
    </parent>
    <groupId>ch.plaintext.v2</groupId>
    <artifactId>fixture-parent</artifactId>
    <version>1.0.0</version>
    <modules>
        <module>modul-a</module>
        <module>modul-b</module>
    </modules>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>ch.plaintext.v2</groupId>
                <artifactId>modul-b</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>ch.plaintext.v2</groupId>
                <!-- Kommentar zwischen groupId und artifactId -->
                <artifactId>modul-a</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>ch.plaintext</groupId>
                <artifactId>plaintext-z-fremd</artifactId>
                <version>2.0.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
EOF
)"
    ist="$(cat "$fx/pom.xml")"
    [ "$ist" = "$erwartet" ] || { diff <(echo "$erwartet") <(echo "$ist") || true; fehler "Selbsttest 1: Wurzel-pom weicht ab"; }
    erwartet="$(cat <<'EOF'
<project>
    <parent>
        <groupId>ch.plaintext.v2</groupId>
        <artifactId>fixture-parent</artifactId>
        <version>1.0.0</version>
    </parent>
    <artifactId>modul-a</artifactId>
    <dependencies>
        <dependency><groupId>ch.plaintext.v2</groupId><artifactId>modul-b</artifactId></dependency>
        <dependency><groupId>ch.plaintext</groupId><artifactId>plaintext-z-fremd</artifactId></dependency>
        <dependency><groupId>org.jsoup</groupId><artifactId>jsoup</artifactId></dependency>
    </dependencies>
    <build><plugins><plugin>
        <configuration>
            <dependenciesToScan><dependency>ch.plaintext.v2:modul-b</dependency></dependenciesToScan>
            <liste>ch.plaintext.v2:modul-b,ch.plaintext:plaintext-z-fremd,ch.plaintext.v2:modul-a</liste>
            <includeGroupIds>ch.plaintext.v2</includeGroupIds>
            <includeArtifactIds>modul-b</includeArtifactIds>
        </configuration>
    </plugin><plugin>
        <configuration>
            <includeGroupIds>ch.plaintext</includeGroupIds>
            <includeArtifactIds>modul-b,plaintext-z-fremd</includeArtifactIds>
        </configuration>
    </plugin></plugins></build>
</project>
EOF
)"
    ist="$(cat "$fx/modul-a/pom.xml")"
    [ "$ist" = "$erwartet" ] || { diff <(echo "$erwartet") <(echo "$ist") || true; fehler "Selbsttest 1: modul-a/pom.xml weicht ab"; }
    grep -q '<groupId>ch.plaintext.v2</groupId>' "$fx/modul-b/pom.xml" || fehler "Selbsttest 1: Parent in modul-b nicht umbenannt"
    grep -q '<name>ch.plaintext.v2:modul-b Beschreibung</name>' "$fx/modul-b/pom.xml" || fehler "Selbsttest 1: Koordinate in <name> nicht umbenannt"
    grep -q '^ch.plaintext.a.AConfig$' "$fx/modul-a/src/main/resources/META-INF/spring/"*.imports || fehler "Selbsttest 1: Imports-Datei wurde angefasst"
    echo "   gruen: Reaktor-Koordinaten umbenannt, Fremd-Artefakt plaintext-z-fremd und Imports unveraendert."

    echo "── Selbsttest 2: Idempotenz auf der Fixture ──"
    local sum1 sum2
    sum1="$(cat "$fx/pom.xml" "$fx/modul-a/pom.xml" "$fx/modul-b/pom.xml" | cksum)"
    UMBENENNEN_WURZEL="$fx" "$SELBST" | grep -q 'geaendert ' && fehler "Selbsttest 2: zweiter Lauf meldet Aenderungen"
    sum2="$(cat "$fx/pom.xml" "$fx/modul-a/pom.xml" "$fx/modul-b/pom.xml" | cksum)"
    [ "$sum1" = "$sum2" ] || fehler "Selbsttest 2: zweiter Lauf hat die Fixture veraendert"
    echo "   gruen: zweiter Lauf = keine Aenderung."

    echo "── Selbsttest 3: --pruefen gruen nach Umbenennung, rot bei Rueckfall ──"
    UMBENENNEN_WURZEL="$fx" "$SELBST" --pruefen >/dev/null || fehler "Selbsttest 3: --pruefen rot auf sauberer Fixture"
    sed -i 's|<groupId>ch.plaintext.v2</groupId>|<groupId>ch.plaintext</groupId>|' "$fx/modul-b/pom.xml"
    if UMBENENNEN_WURZEL="$fx" "$SELBST" --pruefen >/dev/null 2>&1; then
        fehler "Selbsttest 3: --pruefen bleibt gruen, obwohl modul-b wieder ch.plaintext traegt"
    fi
    echo "   gruen: Rueckfall wird erkannt."
    rm -rf "$fx"

    echo "── Selbsttest 4: Idempotenz auf dem echten Arbeitsbaum ──"
    local wurzel="${UMBENENNEN_WURZEL:-$(cd "$(dirname "$SELBST")/.." && pwd)}"
    sum1="$(pom_dateien "$wurzel" | xargs cat | cksum)"
    "$SELBST" >/dev/null
    sum2="$(pom_dateien "$wurzel" | xargs cat | cksum)"
    "$SELBST" | grep -q 'geaendert ' && fehler "Selbsttest 4: zweiter Lauf im Arbeitsbaum meldet Aenderungen"
    [ "$(pom_dateien "$wurzel" | xargs cat | cksum)" = "$sum2" ] || fehler "Selbsttest 4: zweiter Lauf hat den Arbeitsbaum veraendert"
    if [ "$sum1" = "$sum2" ]; then
        echo "   gruen: Arbeitsbaum war schon umbenannt, zwei Laeufe aendern nichts."
    else
        echo "   gruen: erster Lauf hat umbenannt, zweiter Lauf aendert nichts."
    fi
    "$SELBST" --pruefen
    echo "umbenennen: SELBSTTEST GRUEN"
}

wurzel="${UMBENENNEN_WURZEL:-$(cd "$(dirname "$SELBST")/.." && pwd)}"
case "${1:-}" in
    "")            laufen "$wurzel" schreiben ;;
    --pruefen)     laufen "$wurzel" pruefen ;;
    --selbsttest)  selbsttest ;;
    *)             fehler "unbekannte Option '$1' (erlaubt: --pruefen, --selbsttest)" ;;
esac
