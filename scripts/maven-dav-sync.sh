#!/usr/bin/env bash
# scripts/maven-dav-sync.sh — Karte 2302: Release zusaetzlich maven-konform auf Nextcloud ablegen.
#
# Statisches Maven-Repository-Layout (groupId/artifactId/version + maven-metadata.xml), kein Server.
# Quelle ist das lokale Maven-Repo (dort landet das Release vorher via mvn deploy) — dieselbe Basis
# wie die plaintext-mvn-Spiegelung (Karte 1653). Das maven-metadata.xml pro Artefakt erzeugt und
# mergt das Maven-Deploy-Plugin in einem file://-Staging; der Upload nach WebDAV laeuft per curl,
# jede maven-metadata.xml ZULETZT (erst dann sieht ein Consumer die neue Version).
#
# Aufruf: maven-dav-sync.sh <version>
#   Ohne  Geheimnis/Schreib-URL (MAVEN_NEXTCLOUD bzw. MAVEN_NEXTCLOUD_DAV): Dry-Run — Staging wird
#         gebaut und geprueft, nichts hochgeladen (exit 0).
#   Env:  MAVEN_NEXTCLOUD_DAV   Ziel-Basis, z.B. https://host/remote.php/dav/files/<user>/maven
#         MAVEN_NEXTCLOUD       <user>:<app-passwort> fuer den Schreib-Zugriff (Woodpecker-Secret)
#         MAVEN_LOCAL_REPO      Quelle, Default ~/.m2/repository
set -euo pipefail
cd "$(dirname "$0")/.."
V="${1:?Aufruf: maven-dav-sync.sh <version>}"
[[ "$V" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "maven-dav-sync: FEHLER: '$V' ist keine Version X.Y.Z" >&2; exit 1; }

GRUPPE_PFAD="ch/plaintext/v2"
GRUPPE_MAVEN="ch.plaintext.v2"
QUELLE="${MAVEN_LOCAL_REPO:-$HOME/.m2/repository}/$GRUPPE_PFAD"
DAV="${MAVEN_NEXTCLOUD_DAV:-}"
AUTH="${MAVEN_NEXTCLOUD:-}"
sag() { echo "maven-dav-sync: $*"; }
fehler() { echo "maven-dav-sync: FEHLER: $*" >&2; exit 1; }
[ -d "$QUELLE" ] || fehler "Quelle $QUELLE fehlt"
ls -d "$QUELLE"/*/"$V" >/dev/null 2>&1 || fehler "$GRUPPE_MAVEN:$V liegt nicht in $QUELLE"

STAGE="$(mktemp -d /tmp/maven-dav.XXXXXX)"; chmod 700 "$STAGE"
trap 'rm -rf "$STAGE"' EXIT
NETRC="$STAGE/netrc"; : > "$NETRC"

# ── 1. Staging: je Artefakt ein deploy:deploy-file ins file://-Repo (Plugin baut Layout + Metadata)
ARTEFAKTE=0
for art in "$QUELLE"/*/"$V"; do
    a="$(basename "$(dirname "$art")")"
    POM="$art/$a-$V.pom"
    [ -f "$POM" ] || fehler "$a: POM fehlt ($POM)"
    ARGS=(-Durl="file://$STAGE" -DcreateChecksum=true -DpomFile="$POM")
    if [ -f "$art/$a-$V.jar" ]; then
        ARGS+=(-Dfile="$art/$a-$V.jar")
        [ -f "$art/$a-$V-sources.jar" ] && ARGS+=(-Dsources="$art/$a-$V-sources.jar")
    else
        ARGS+=(-Dfile="$POM" -Dpackaging=pom)   # reines POM-Artefakt (z.B. plaintext-root-parent)
    fi
    mvn -q -B org.apache.maven.plugins:maven-deploy-plugin:3.1.4:deploy-file "${ARGS[@]}" \
        || fehler "$a: deploy-file ins Staging schlug fehl"
    ARTEFAKTE=$((ARTEFAKTE + 1))
done
[ "$ARTEFAKTE" -ge 10 ] || fehler "nur $ARTEFAKTE Artefakte in $QUELLE/*/$V — Aufloesung kaputt?"
sag "Staging: $ARTEFAKTE Artefakte unter $STAGE/$GRUPPE_PFAD."

# ── 2. Upload nach WebDAV — Artefakte zuerst, maven-metadata.xml je Artefakt ZULETZT
if [ -z "$DAV" ] || [ -z "$AUTH" ]; then
    sag "Dry-Run: MAVEN_NEXTCLOUD_DAV/MAVEN_NEXTCLOUD nicht gesetzt — nichts hochgeladen."
    find "$STAGE" -type f | sort | sed "s#^$STAGE/##" | head -20 | sed 's/^/  /'
    sag "Dry-Run: $(find "$STAGE" -type f | wc -l) Dateien waeren es."
    exit 0
fi
[[ "$AUTH" == *:* ]] || fehler "MAVEN_NEXTCLOUD ohne ':' (erwartet <user>:<passwort>)"
( umask 077
  printf 'machine %s login %s password %s\n' "$(echo "$DAV" | awk -F/ '{print $3}')" \
      "${AUTH%%:*}" "${AUTH#*:}" > "$NETRC" )
dav() { curl -sS --fail --max-time 300 --netrc-file "$NETRC" "$@"; }

# Zielverzeichnisse zuerst anlegen (MKCOL ist idempotent; 405 = existiert bereits),
# dann PUTs ohne MKCOL — so bleibt der Upload ~1 Request pro Datei.
sag "Upload nach $DAV/$GRUPPE_PFAD ..."
while IFS= read -r d; do
    dav -o /dev/null -X MKCOL "$DAV/${d#"$STAGE"/}" 2>/dev/null || true
done < <(find "$STAGE" -type d | sort)
hochladen() { dav -o /dev/null -T "$1" "$DAV/${1#"$STAGE"/}"; }
while IFS= read -r f; do hochladen "$f"; done < <(find "$STAGE" -type f ! -name 'maven-metadata.xml*' | sort)
while IFS= read -r f; do hochladen "$f"; done < <(find "$STAGE" -type f -name 'maven-metadata.xml*' | sort)
sag "$ARTEFAKTE Artefakte hochgeladen (Metadata zuletzt)."
