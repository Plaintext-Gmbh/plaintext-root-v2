# plaintext-root-v2 — die Root-Entwicklungslinie (Karte 1621)

`plaintext-root-v2` ist seit dem 08.11.2026 **das** Root-Entwicklungsrepo. Die v1
(`Plaintext-Gmbh/plaintext-root`) ist archiviert (lesend). Früher war dieses Repo eine
Synchronisations-Kopie von v1 plus `v2/transformation.sh` — Sync, Transformation und
Abgleich-Agent sind mit Karte 1621 abgebaut. Die Analyse aus der Umbauzeit bleibt als
Historie: [analyse.md](analyse.md) (Karten 1563/1595).

## Koordinaten

- groupId aller 33 Reaktor-Artefakte: `ch.plaintext.v2` (Parent `ch.plaintext.v2:plaintext-root-parent`).
  Java-Pakete bleiben `ch.plaintext.…`. Ein Reaktor-POM mit `<groupId>ch.plaintext</groupId>`
  ist ein Fehler — der CI-Schritt `koordinaten` (`.woodpecker/build.yml`) und `scripts/release.sh`
  weigern sich.
- Veröffentlicht wird in beide Paket-Repos: GitHub Packages
  (`https://maven.pkg.github.com/Plaintext-Gmbh/plaintext-mvn`) und Reposilite
  (`https://maven.plaintext.ch/releases/ch/plaintext/v2/`). Der öffentliche git-Spiegel
  `Plaintext-Gmbh/plaintext-mvn` wird vom Release aus dem lokalen Maven-Repo gefüttert
  (Karte 1653).

## Versionen und Tags

- Die POM-Version auf master ist eine SNAPSHOT-Version und trägt bewusst die **gerade
  veröffentlichte** Nummer (Hauskonvention, siehe `compute_release_versions` in
  `plaintext-scripts/tui-build-logic.sh`).
- `scripts/release.sh` rechnet: `BASIS = MAX(POM ohne -SNAPSHOT, höchster v2-Tag)`; ist die
  Major der POM größer als die Tag-Major (**Versionsschnitt**, z. B. POM `3.0.0-SNAPSHOT` über
  Tag `2.821.1`), wird die POM-Version exakt released, sonst `BASIS + 1 Minor`.
- Tag-Präfix `v2-` (z. B. `v2-3.0.0`). Der Schnitt auf **3.0.0** war der Entscheid Daniels
  zur Umstellung (Karte 1621); alle Pins der Apps springen einmal auf 3.x.
- Nach dem Release committet der Lauf `Prepare next development iteration <Version>-SNAPSHOT`
  mit `[skip ci]` — Versions-Commits sind ohne Sync wieder unproblematisch.

## Release (`.woodpecker/deploy.yml` → `scripts/release.sh`)

Läuft auf jeden Push nach `master` (`release-only`). Reihenfolge (Karte 518): Tag zuerst
pushen, dann `mvn clean deploy` in beide Paket-Repos; bei einem halben Release liefert
`scripts/release-nachliefern.sh --runden` nach (Karte 1596); die Vollständigkeitsprüfung
(`scripts/release-vollstaendig.sh`, `TAG_PRAEFIX=v2-`, Gruppenpfad aus der POM) zählt beide
Repos. Danach Spiegelung nach `plaintext-mvn` und GitHub-Release mit Notes (best effort),
zuletzt der SNAPSHOT-Commit.

Manuell: "Run pipeline" mit `deploy_target=release-only`; ohne Variable tut der Lauf nichts
(ci-only). `[fast]` im Betreff überspringt die Tests.

## Weitere Skripte (alle unter `scripts/`)

- `purge.py` — alte Releases beider Paket-Repos aufräumen (Trockenlauf ist der Standard,
  `--scharf` löscht; harte Sperre gegen v1-Pfade, Pin-Schutz Karte 1635). Tests: `test_purge.py`
  (CI-Schritt `purge-tests`).
- `lader-probe.sh` + `test-lader-probe-auswahl.sh` + `probe-modul/` — Hülle + Lader +
  ein released Modul-Jar gegen eine leere Postgres (CI-Schritte `lader-probe`,
  `lader-probe-flyway`).
- `release-vollstaendig.sh`, `release-nachliefern.sh` — wie in root (Karten 1281/1596),
  `TAG_PRAEFIX=v2-` setzen.

## Was von der v2-Umbauzeit übrig ist

Nichts im Tagesgeschäft: `v2/sync.sh`, `v2/transformation.sh`, `v2/umbenennen.sh`,
`v2/transformation.patch`, `v2/test-sync.sh`, der Agent `~/scripts/v2-abgleich-agent`
(Cron Sa 03:30) und der GitHub-Action-Schedule sind entfernt bzw. deaktiviert
(Cron-Zeile auskommentiert mit Vermerk Karte 1621; `v2-sync.yaml` bleibt als
`workflow_dispatch`--only-Datei im Git, greift aber ins Leere — das Skript fehlt).
Der Rückweg zu v1 besteht über die v1-Artefakte in Reposilite/GitHub Packages und das
archivierte Repo.
