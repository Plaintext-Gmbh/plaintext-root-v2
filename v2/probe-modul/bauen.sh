#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
# Baut das synthetische Probe-Modul (siehe README.md) mit javac und jar — ohne Maven, damit es
# in keinem Reaktor auftaucht und nie veroeffentlicht wird.
set -euo pipefail
HIER="$(cd "$(dirname "$0")" && pwd)"
M2="${M2_REPO:-$HOME/.m2/repository}"
CP="$(find "$M2/org/springframework/ai/spring-ai-mcp-annotations" "$M2/org/springframework/spring-context" "$M2/org/springframework/spring-core" -name '*.jar' ! -name '*sources*' | sort | tail -n 3 | tr '\n' ':')"
[ -n "$CP" ] || { echo "bauen: spring-context/spring-ai-mcp-annotations nicht in $M2 — erst mvn install" >&2; exit 1; }
rm -rf "$HIER/target" && mkdir -p "$HIER/target/classes"
javac --release 25 -cp "$CP" -d "$HIER/target/classes" "$HIER"/src/ch/plaintext/probe1563/*.java
cp -r "$HIER/resources/." "$HIER/target/classes/"
jar cf "$HIER/target/probe-modul-1.0.0.jar" -C "$HIER/target/classes" .
echo "bauen: $HIER/target/probe-modul-1.0.0.jar"
jar tf "$HIER/target/probe-modul-1.0.0.jar" | grep -E 'class$|sql$'
