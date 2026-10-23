#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# ══════════════════════════════════════════════════════════════════════════════
#  v2/lader-probe.sh — Huelle + Lader + ein Modul-Jar gegen eine leere Postgres (Karte 1563)
#
#  Belegt, was Konzept 1503 (Abschnitt 8) als Wegwerf-Probe gemacht hat, wiederholbar und mit
#  dem gebauten Stand DIESES Repos:
#    B  Huelle startet ueber ch.plaintext.lader.ModulLader mit dem Modul in loader.path:
#       /actuator/health = UP, "[modul-lader] N Module bereit", die Flyway-Migrationen des Moduls
#       stehen in flyway_schema_history, die Zahl der MCP-Werkzeuge ("Registered tools") ist
#       hoeher als ohne das Modul.
#    A  Dieselbe Huelle ohne Module (PLAINTEXT_MODULE=aus) auf derselben DB: startet, health UP,
#       weniger Werkzeuge. (Konzept Abschnitt 4 "Notfall": Modultabellen stoeren die Huelle nicht.)
#    C  Negativ: ein Jar mit eigener application.yml wird vom Lader ABGEWIESEN, die Huelle
#       startet nicht (Schatten-Probe 1503).
#    D  Negativ: ein Jar mit einer Klasse, die es in der Huelle schon gibt, wird abgewiesen.
#  Die Pruefungen C/D sind dieselben, die ModulPruefungTest (plaintext-root-lader) als Unit-Test
#  haelt — hier laufen sie gegen das echte Exec-Jar.
#
#  AUFRUF
#    v2/lader-probe.sh --modul-gav ch.plaintext:plaintext-z-verrechnung:latest   (Woodpecker, LAN)
#    v2/lader-probe.sh --modul-jar pfad/zum/modul.jar --root-min 1.780.0           (lokal, Testjar)
#  OPTIONEN
#    --huelle PFAD   Exec-Jar der Huelle (Standard: plaintext-root-webapp/target/*-exec.jar)
#    --lader PFAD    Lader-Jar          (Standard: plaintext-root-lader/target/plaintext-root-lader-*.jar)
#    --port N        HTTP-Port der Huelle (Standard 18503)
#    --nur-b         nur Probe B (schneller Rauchtest)
#  UMGEBUNG
#    DB_URL, DB_USER, DB_PASSWORD   JDBC-Ziel, eine LEERE Datenbank
#                                   (Standard jdbc:postgresql://127.0.0.1:5432/plaintext_probe, plaintext/plaintext)
#    PROBE_PSQL                     optional, psql-Verbindung (z.B. "postgresql://plaintext:plaintext@127.0.0.1/plaintext_probe")
#                                   fuer den Auszug aus flyway_schema_history; ohne psql wird die
#                                   Abfrage nur angezeigt (in Woodpecker macht sie ein eigener Schritt)
#    REPOSILITE_BASIS               Standard https://maven.plaintext.ch/releases (fuer --modul-gav)
#    PROBE_ARBEIT                   Arbeitsverzeichnis (Standard: mktemp)
#  Exit 0 nur, wenn ALLE Erwartungen erfuellt sind. Die Belege stehen am Ende als Block.
# ══════════════════════════════════════════════════════════════════════════════
set -eu   # kein pipefail: head/grep-Ketten unten brechen sonst den Lauf ab

WURZEL="$(cd "$(dirname "$0")/.." && pwd)"
REPOSILITE_BASIS="${REPOSILITE_BASIS:-https://maven.plaintext.ch/releases}"
DB_URL="${DB_URL:-jdbc:postgresql://127.0.0.1:5432/plaintext_probe}"
DB_USER="${DB_USER:-plaintext}"
DB_PASSWORD="${DB_PASSWORD:-plaintext}"
PORT=18503
HUELLE=""; LADER=""; MODUL_JAR=""; MODUL_GAV=""; ROOT_MIN=""; NUR_B=0
while [ $# -gt 0 ]; do
    case "$1" in
        --huelle)    HUELLE="$2"; shift 2 ;;
        --lader)     LADER="$2"; shift 2 ;;
        --modul-jar) MODUL_JAR="$2"; shift 2 ;;
        --modul-gav) MODUL_GAV="$2"; shift 2 ;;
        --root-min)  ROOT_MIN="$2"; shift 2 ;;
        --port)      PORT="$2"; shift 2 ;;
        --nur-b)     NUR_B=1; shift ;;
        *) echo "lader-probe: unbekannte Option $1" >&2; exit 2 ;;
    esac
done

sag() { echo "lader-probe: $*"; }
fehler() { echo "lader-probe: FEHLER: $*" >&2; exit 1; }
BELEGE=()
beleg() { BELEGE+=("$*"); echo "  BELEG  $*"; }

[ -n "$HUELLE" ] || HUELLE="$(ls "$WURZEL"/plaintext-root-webapp/target/plaintext-root-webapp-*-exec.jar 2>/dev/null | head -1 || true)"
[ -n "$LADER" ]  || LADER="$(ls "$WURZEL"/plaintext-root-lader/target/plaintext-root-lader-*.jar 2>/dev/null | grep -v -- '-sources\|-javadoc\|-tests' | head -1 || true)"
[ -f "$HUELLE" ] || fehler "Exec-Jar der Huelle fehlt (--huelle oder mvn package in plaintext-root-webapp)"
[ -f "$LADER" ]  || fehler "Lader-Jar fehlt (--lader oder mvn package in plaintext-root-lader)"
[ -n "$MODUL_JAR" ] || [ -n "$MODUL_GAV" ] || fehler "--modul-jar oder --modul-gav angeben"
command -v curl >/dev/null || fehler "curl fehlt"
command -v jar >/dev/null || fehler "jar (JDK) fehlt"

ARBEIT="${PROBE_ARBEIT:-$(mktemp -d)}"
MODULE="$ARBEIT/module"; mkdir -p "$MODULE/cache"
sag "Huelle $HUELLE"; sag "Lader  $LADER"; sag "Arbeit $ARBEIT"

sha256() { sha256sum "$1" | cut -d' ' -f1; }

# ── Modul beschaffen ────────────────────────────────────────────────────────
GRUPPE="ch.plaintext"
if [ -n "$MODUL_GAV" ]; then
    IFS=: read -r GRUPPE NAME VERSION <<< "$MODUL_GAV"
    G_PFAD="$(echo "$GRUPPE" | tr . /)"
    if [ "$VERSION" = "latest" ]; then
        META="$(curl -fsS --max-time 30 "$REPOSILITE_BASIS/$G_PFAD/$NAME/maven-metadata.xml")" || fehler "maven-metadata.xml von $NAME nicht erreichbar"
        VERSION="$(echo "$META" | grep -oE '<(release|latest)>[^<]+' | head -1 | sed 's/.*>//')"
        [ -n "$VERSION" ] || fehler "keine Release-Version in maven-metadata.xml von $NAME"
        sag "neueste Version von $NAME laut Reposilite: $VERSION"
    fi
    MODUL_JAR="$ARBEIT/$NAME-$VERSION.jar"
    curl -fsS --max-time 300 -o "$MODUL_JAR" "$REPOSILITE_BASIS/$G_PFAD/$NAME/$VERSION/$NAME-$VERSION.jar" || fehler "Download $NAME-$VERSION.jar fehlgeschlagen"
    # root-min aus der Elternkette: pom im Jar -> Parent-pom aus Reposilite -> <plaintext-root.version>
    # oder Parent plaintext-root-parent. Die Registry (plaintext-modules) traegt denselben Wert.
    if [ -z "$ROOT_MIN" ]; then
        POM="$(cd "$ARBEIT" && jar xf "$MODUL_JAR" "META-INF/maven/$GRUPPE/$NAME/pom.xml" 2>/dev/null && cat "META-INF/maven/$GRUPPE/$NAME/pom.xml" 2>/dev/null || true)"
        [ -n "$POM" ] || fehler "pom.xml im Jar nicht gefunden (META-INF/maven/$GRUPPE/$NAME/pom.xml)"
        for _ in 1 2 3; do
            RV="$(echo "$POM" | grep -oE '<plaintext-root\.version>[^<$]+' | head -1 | sed 's/.*>//' || true)"
            if [ -n "$RV" ]; then ROOT_MIN="$RV"; break; fi
            P_AID="$(echo "$POM" | sed -n '/<parent>/,/<\/parent>/p' | grep -oE '<artifactId>[^<]+' | head -1 | sed 's/.*>//')"
            P_VER="$(echo "$POM" | sed -n '/<parent>/,/<\/parent>/p' | grep -oE '<version>[^<]+' | head -1 | sed 's/.*>//')"
            P_GID="$(echo "$POM" | sed -n '/<parent>/,/<\/parent>/p' | grep -oE '<groupId>[^<]+' | head -1 | sed 's/.*>//')"
            [ -n "$P_AID" ] || break
            if [ "$P_AID" = "plaintext-root-parent" ]; then ROOT_MIN="$P_VER"; break; fi
            POM="$(curl -fsS --max-time 30 "$REPOSILITE_BASIS/$(echo "$P_GID" | tr . /)/$P_AID/$P_VER/$P_AID-$P_VER.pom")" || break
        done
        [ -n "$ROOT_MIN" ] || fehler "root-min aus der Elternkette nicht bestimmbar — --root-min angeben"
        sag "root-min aus der Elternkette: $ROOT_MIN"
    fi
else
    [ -f "$MODUL_JAR" ] || fehler "$MODUL_JAR fehlt"
    [ -n "$ROOT_MIN" ] || fehler "--root-min ist bei --modul-jar Pflicht"
    NAME="$(basename "$MODUL_JAR" .jar | sed -E 's/-[0-9][A-Za-z0-9._-]*$//')"
    VERSION="$(basename "$MODUL_JAR" .jar | sed -E "s/^$NAME-//")"
fi
cp "$MODUL_JAR" "$MODULE/cache/$NAME-$VERSION.jar"
MODUL_SHA="$(sha256 "$MODUL_JAR")"
MODUL_MIGRATIONEN="$(jar tf "$MODUL_JAR" | grep -E '^db/migration/V' | sed 's|db/migration/||' || true)"
sag "Modul $GRUPPE:$NAME:$VERSION sha256 $MODUL_SHA, Migrationen: $(echo "$MODUL_MIGRATIONEN" | tr '\n' ' ')"
# Fuer einen Folgeschritt mit psql (Woodpecker: lader-probe-flyway): welche Skripte muessen in
# flyway_schema_history stehen.
printf '%s\n' $MODUL_MIGRATIONEN > "$ARBEIT/modul-migrationen.txt"

konfig_schreiben() {   # $1 name $2 version $3 sha $4 root-min
    cat > "$MODULE/module.yaml" <<EOF
module:
  - name: $1
    version: $2
    sha256: $3
    root-min: $4
EOF
}

# ── Huelle starten/stoppen ──────────────────────────────────────────────────
APP_PID=""
starten() {   # $1 = Kennung, $2.. = zusaetzliche Umgebungsvariablen (K=V)
    local kennung="$1"; shift
    local log="$ARBEIT/$kennung.log"
    sag "Start $kennung (Log $log)"
    env "$@" \
        SPRING_DATASOURCE_URL="$DB_URL" SPRING_DATASOURCE_USERNAME="$DB_USER" SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
        SERVER_PORT="$PORT" PLAINTEXT_MODULE_VERZEICHNIS="$MODULE" PLAINTEXT_COOKIE_SECURE=false \
        LOGGING_LEVEL_ORG_FLYWAYDB=INFO LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_AI=INFO LOGGING_LEVEL_IO_MODELCONTEXTPROTOCOL=INFO \
        java -XX:MaxRAMPercentage=50 -cp "$HUELLE:$LADER" ch.plaintext.lader.ModulLader > "$log" 2>&1 &
    APP_PID=$!
}
warten_auf_up() {   # $1 = Kennung; Exit 1 wenn Prozess endet oder 300 s um sind
    local log="$ARBEIT/$1.log" i
    for i in $(seq 1 150); do
        if ! kill -0 "$APP_PID" 2>/dev/null; then return 1; fi
        if curl -fsS --max-time 3 "http://127.0.0.1:$PORT/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
            return 0
        fi
        sleep 2
    done
    echo "lader-probe: health nach 300 s nicht UP — letzte Logzeilen:" >&2; tail -30 "$log" >&2
    return 1
}
stoppen() {
    [ -n "$APP_PID" ] || return 0
    kill "$APP_PID" 2>/dev/null || true
    for _ in $(seq 1 30); do kill -0 "$APP_PID" 2>/dev/null || break; sleep 1; done
    kill -9 "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
    APP_PID=""
}
trap stoppen EXIT
werkzeuge() { grep -oE 'Registered tools: [0-9]+' "$ARBEIT/$1.log" | tail -1 | grep -oE '[0-9]+' || echo "?"; }
FEHLER=0

# ── Probe B: Huelle + Modul auf leerer DB ───────────────────────────────────
konfig_schreiben "$NAME" "$VERSION" "$MODUL_SHA" "$ROOT_MIN"
starten B
if warten_auf_up B; then
    HEALTH="$(curl -fsS "http://127.0.0.1:$PORT/actuator/health")"
    beleg "B health: $HEALTH"
    grep -E '^\[modul-lader\]' "$ARBEIT/B.log" | while read -r z; do beleg "B $z"; done
    grep -qE "^\[modul-lader\] 1 Module bereit: $NAME:$VERSION" "$ARBEIT/B.log" || { echo "  FEHLT: Zeile '[modul-lader] 1 Module bereit: $NAME:$VERSION'"; FEHLER=1; }
    N1="$(werkzeuge B)"; beleg "B Registered tools: $N1"
    grep -iE 'flyway|migrat' "$ARBEIT/B.log" | grep -ivE 'DEBUG' | head -8 | while read -r z; do beleg "B $(echo "$z" | cut -c1-200)"; done
    for m in $MODUL_MIGRATIONEN; do
        v="$(echo "$m" | sed -E 's/^V([0-9]+)__.*/\1/')"
        if grep -qE "version \"?$v\"?|to version \"$v|$m" "$ARBEIT/B.log"; then beleg "B Modul-Migration $m im Flyway-Log"; else echo "  FEHLT: Modul-Migration $m im Flyway-Log"; FEHLER=1; fi
    done
else
    echo "  FEHLT: Probe B — Huelle mit Modul nicht UP"; FEHLER=1; N1="?"
fi
stoppen

# ── flyway_schema_history ───────────────────────────────────────────────────
SQL="select installed_rank, version, description, script, success from flyway_schema_history order by installed_rank desc limit 12;"
if [ -n "${PROBE_PSQL:-}" ] && command -v psql >/dev/null; then
    sag "flyway_schema_history (juengste 12):"
    psql "$PROBE_PSQL" -c "$SQL" | sed 's/^/    /'
    for m in $MODUL_MIGRATIONEN; do
        if psql -tA "$PROBE_PSQL" -c "select count(*) from flyway_schema_history where script='$m' and success" | grep -qx 1; then
            beleg "flyway_schema_history enthaelt $m (success=true)"
        else
            echo "  FEHLT: $m nicht in flyway_schema_history"; FEHLER=1
        fi
    done
else
    sag "kein PROBE_PSQL/psql — Abfrage fuer den Beleg aus flyway_schema_history: $SQL"
    sag "  erwartet darin: $(echo "$MODUL_MIGRATIONEN" | tr '\n' ' ')"
fi

if [ "$NUR_B" = "0" ]; then
    # ── Probe A: ohne Module auf derselben DB (Notfall-Schalter) ───────────
    starten A PLAINTEXT_MODULE=aus
    if warten_auf_up A; then
        beleg "A health ohne Module: $(curl -fsS "http://127.0.0.1:$PORT/actuator/health")"
        grep -E '^\[modul-lader\]' "$ARBEIT/A.log" | while read -r z; do beleg "A $z"; done
        N0="$(werkzeuge A)"; beleg "A Registered tools: $N0"
        if [ "$N0" != "?" ] && [ "$N1" != "?" ] && [ "$N1" -gt "$N0" ]; then
            beleg "MCP: $N0 Werkzeuge ohne, $N1 mit Modul ($NAME bringt $((N1 - N0)))"
        else
            echo "  FEHLT: Werkzeugzahl mit Modul ($N1) nicht groesser als ohne ($N0)"; FEHLER=1
        fi
    else
        echo "  FEHLT: Probe A — Huelle ohne Module nicht UP"; FEHLER=1
    fi
    stoppen

    # ── Probe C: Jar mit application.yml wird abgewiesen ───────────────────
    C="$ARBEIT/c"; mkdir -p "$C"; printf 'spring:\n  application:\n    name: modul-gewinnt\n' > "$C/application.yml"
    (cd "$C" && rm -f probe-schatten-1.0.0.jar && jar cf probe-schatten-1.0.0.jar application.yml)
    cp "$C/probe-schatten-1.0.0.jar" "$MODULE/cache/"
    konfig_schreiben probe-schatten 1.0.0 "$(sha256 "$C/probe-schatten-1.0.0.jar")" "$ROOT_MIN"
    starten C; wait "$APP_PID" && RC=0 || RC=$?; APP_PID=""
    if [ "$RC" != "0" ] && grep -q 'enthaelt application.yml' "$ARBEIT/C.log" && grep -q 'verdraengen' "$ARBEIT/C.log"; then
        beleg "C abgewiesen (Exit $RC): $(grep -m1 'START ABGEBROCHEN' "$ARBEIT/C.log" | cut -c1-200)"
    else
        echo "  FEHLT: Probe C — Jar mit application.yml wurde nicht abgewiesen (Exit $RC)"; tail -5 "$ARBEIT/C.log"; FEHLER=1
    fi

    # ── Probe D: Klasse der Huelle im Modul wird abgewiesen ────────────────
    D="$ARBEIT/d"; mkdir -p "$D/ch"; printf 'kein Bytecode' > "$D/ch/RootBootApplication.class"
    (cd "$D" && rm -f probe-doppel-1.0.0.jar && jar cf probe-doppel-1.0.0.jar ch/RootBootApplication.class)
    cp "$D/probe-doppel-1.0.0.jar" "$MODULE/cache/"
    konfig_schreiben probe-doppel 1.0.0 "$(sha256 "$D/probe-doppel-1.0.0.jar")" "$ROOT_MIN"
    starten D; wait "$APP_PID" && RC=0 || RC=$?; APP_PID=""
    if [ "$RC" != "0" ] && grep -q 'ch.RootBootApplication aus probe-doppel-1.0.0.jar gibt es schon in der Huelle' "$ARBEIT/D.log"; then
        beleg "D abgewiesen (Exit $RC): $(grep -m1 'START ABGEBROCHEN' "$ARBEIT/D.log" | cut -c1-200)"
    else
        echo "  FEHLT: Probe D — Jar mit Huellen-Klasse wurde nicht abgewiesen (Exit $RC)"; tail -5 "$ARBEIT/D.log"; FEHLER=1
    fi
fi

echo "────────────────────────────────────────────────────────────────"
echo "LADER-PROBE  Huelle $(basename "$HUELLE")  Modul $GRUPPE:$NAME:$VERSION  root-min $ROOT_MIN"
for b in "${BELEGE[@]}"; do echo "  $b"; done
if [ "$FEHLER" = "0" ]; then echo "ERGEBNIS: alle Erwartungen erfuellt."; else echo "ERGEBNIS: FEHLER — siehe 'FEHLT'-Zeilen oben und Logs in $ARBEIT"; fi
echo "────────────────────────────────────────────────────────────────"
exit "$FEHLER"
