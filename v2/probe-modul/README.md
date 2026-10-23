# Synthetisches Probe-Modul für `v2/lader-probe.sh` (Karte 1563)

Ohne Zugang zum Reposilite (ausserhalb des LAN) gibt es kein released `plaintext-z-verrechnung`-Jar.
Dieses Verzeichnis baut ein kleines Modul-Jar, das dieselben Mechanismen anspricht:

- `ch/plaintext/probe1563/ProbeMcpTools.java` — `@Component` mit einem `@McpTool` (`probe_1563_echo`),
  gefunden vom Component-Scan `ch.plaintext` der Hülle und vom MCP-Scanner (`Registered tools` +1).
- `db/migration/V1800000000__probe_1563.sql` — eine Flyway-Migration, die nur das Modul mitbringt.

Bauen (JDK 25, die Jars aus `~/.m2` nach einem `mvn install`):

```
v2/probe-modul/bauen.sh            # schreibt target/probe-modul-1.0.0.jar
```

Probe lokal (leere Datenbank `plaintext_probe` auf 127.0.0.1:5432, Benutzer plaintext/plaintext):

```
DB_URL=jdbc:postgresql://127.0.0.1:5432/plaintext_probe \
PROBE_PSQL=postgresql://plaintext:plaintext@127.0.0.1/plaintext_probe \
v2/lader-probe.sh --modul-jar v2/probe-modul/target/probe-modul-1.0.0.jar --root-min 1.780.0
```

`--root-min` ist hier eine Angabe von Hand, weil das synthetische Jar keine Elternkette hat. Beim
echten Modul (`--modul-gav`) leitet das Skript den Wert aus dem Jar ab.
