# Analyse plaintext-root für die v2-Grundlage (Karte 1563)

Stand 22.10.2026, master = upstream `a529320` (plaintext-root 1.789.0 + Renovate-Commits).
Zweck: festhalten, was in root an Reaktor, Pipelines und Lader für v2 zählt — und woraus die
Entscheide in [README.md](README.md) folgen.

## 1. Reaktor: 32 Module + Parent

`pom.xml` (Parent `plaintext-root-parent`, groupId vor der Umbenennung `ch.plaintext`, Version
`1.789.0`, Java 25, Spring Boot 4.1.1) führt 32 `<module>`-Einträge. CLAUDE.md nennt noch 24 —
die Zahl ist veraltet, nicht das Prinzip.

| Gruppe | Module |
|---|---|
| Kern | katalog, interfaces, common, flyway, jpa |
| Menü/Zugriff | menu, menu-visibility, pageguard, role-assignment |
| Web | web, template, webapp (Exec-Jar = Hülle), freigabe, files, watch |
| Laufzeit-Module | **lader** (JDK-only, Karte 1513/1514/1515) |
| Qualität | archtests, testsupport |
| Admin | requirements, cron, sessions, settings, i18n, oidc, apitoken, secrets, sidecars, modules, mailtemplate, webhooks, notifications, claude |

Maven-Koordinaten mit `ch.plaintext` in den 33 POMs: **alle** zeigen auf Reaktor-Artefakte
(Parent-Blöcke, `dependencyManagement`, Abhängigkeiten, `annotationProcessorPaths` auf
`plaintext-root-katalog`, Surefire `dependenciesToScan`/`classpathDependencyExcludes`, die
Systemproperty `plaintext.abgewaehlte.module`, `includeGroupIds` des Dependency-Plugins). Kein
POM referenziert ein `ch.plaintext`-Artefakt ausserhalb des Reaktors. `META-INF/spring/*.imports`,
`spring.factories`, `application.yml`, `faces-config.xml` enthalten **Klassennamen** und Pakete
(`ch.plaintext.…`), keine Koordinaten — belegt durch `v2/umbenennen.sh --pruefen`, das genau
diese Dateien mit dem Reaktor-Set abgleicht.

Konsequenz: die groupId lässt sich mechanisch umbenennen (33 Dateien, 189 Zeilen), Java-Pakete
bleiben unverändert, und ein Modul-Jar aus plaintext-app (Pakete `ch.plaintext.…`, Koordinate
`ch.plaintext:plaintext-z-…`) passt weiterhin in die Hülle.

**Merge-Verhalten gemessen**: upstream bumpt bei jedem Release die `<version>`-Zeile in allen 33
POMs. Sie liegt zwei Zeilen unter der umbenannten `<groupId>`-Zeile (dazwischen `<artifactId>`).
`git merge` löst das sauber auf (Probe in der Sitzung: Release-Bump 1.789.0 → 1.790.0 gegen
umbenannte POMs, "MERGE SAUBER"). Eine v2-eigene Versionsänderung in den POMs würde dagegen
an jeder dieser 33 Zeilen kollidieren — daher committet v2 **keine** Versionen (siehe README,
Versionsschema).

## 2. Pipelines

### `.woodpecker/build.yml` (pull_request, manual, cron `nightly`)
Services: `postgres:18-alpine`. Schritte: `flyway-dubletten` (plaintext-scripts SHA-gepinnt),
`build-und-test` (`mvn clean install -DskipITs`, JaCoCo-Gate 40 % scharf, ArchUnit, zweiter
Surefire-Lauf `kontext-ohne-abwaehlbare-module`), `coverage-uebersicht`, `testbericht` (Allure,
Volume `woodpecker-reports`, Secret `github_status_token`). Kein `push: master` (M1 build once).

### `.woodpecker/deploy.yml` (push master, manual)
Ein Schritt `release`: Docker-CLI nachladen (nur damit `tui-build-logic.sh` beim Sourcen nicht
abbricht), `gh` nachladen, Git-Credential-Helper in `/tmp`, settings.xml mit **zwei** Server-IDs
(`plaintext` → GitHub Packages, `plaintext-nas` → Reposilite via Profil `dual-publish-nas` aus
`.mvn/maven.config`), **SSH-Schlüssel für den NAS** (wegen `.m3-jar-volume` → `stage_jar_to_nas`),
plaintext-scripts (SHA-gepinnt) + **plaintext-config (privat)** nach `$HOME/codeplain`, Prüfung
`basename $PWD == plaintext-root` (load_build_conf), Halbe-Release-Wächter
(`scripts/release-vollstaendig.sh --letzte 10`), dann `./build release` = `do_release`:
`versions:set` → Commit `Release version X [skip ci]` → Tag `X` → Push → `mvn clean deploy` →
Jar-Staging per SSH → `Prepare next development iteration X-SNAPSHOT [skip ci]`. Danach
`mirror_to_plaintext_mvn` (git-basierter Spiegel, nur root). Schritte `aufraeumen`,
`vollstaendigkeit` (beide Repos zählen).

Für v2 unbrauchbar in dieser Form: Versions-Commits (Sync-Konflikte), Tag ohne Präfix,
NAS-Staging (verboten), plaintext-config-Eintrag fehlt, Docker-Pfad ohne `.m3-jar-volume`.

### `.woodpecker/sonar.yml` (cron `wochenanalyse`, manual mit `analyse = voll`)
SonarQube (Schlüssel `groupId:artifactId`), OWASP-CVE mit NVD-Volume `github-runners_odc-cache`
(braucht `trusted.volumes`), SpotBugs, Quality-Gate mit Rückcommit `quality/quality-gate.properties
[skip ci]`, Pushover bei Ausfall. Secrets: `sonar_token`, `nvd_api_key`, `mvn_deploy_token`,
`pushover_app_token`, `pushover_user_key`.

### `.woodpecker/playwright.yml` (pull_request, manual)
Browser-Smoke-Tests im Playwright-Image; keine Secrets, kein Volume. Für v2 unverändert
übernommen (kein `merge=ours`).

### `.github/workflows/repo-lint.yaml`
Einzige GitHub-Actions-Datei: `runs-on: [self-hosted, nas]`, liest mit `MVN_DEPLOY_TOKEN` die
Flyway-Nummern der vier privaten Repos. Für v2 nicht lauffähig (Runner gehört den bestehenden
Repos, Secret fehlt) → Stub.

### `.ci-engine`, `waechter.sh`
`woodpecker`; jeder Schritt sourcet den Wächter. Bleibt in v2 so.

## 3. Lader (`plaintext-root-lader`)

JDK-only, keine Laufzeit-Abhängigkeit. `ModulLader.main`: Verzeichnis `PLAINTEXT_MODULE_VERZEICHNIS`
(Standard `/data/module`), Notfall `PLAINTEXT_MODULE=aus` oder Datei `NOTFALL-AUS`, `module.yaml`
(`ModulKonfig`: `name`, `version`, `sha256`, `root-min`, optional `gruppe`, `bibliotheken`),
Kompatibilität gegen Manifest-Attribute `Plaintext-Root-Version`/`Plaintext-Root-Kompatibel-Ab`
des Exec-Jars (webapp-pom: `plaintext.root.kompatibel-ab = 1.767.0`), Download nur von
`maven.plaintext.ch/releases` (Gruppe `ch.plaintext*`) oder Maven Central, sha256-Prüfung,
Cache-Aufräumen, dann `ModulPruefung` (Karte 1514): **weist Jars mit `application*.yml/properties`,
`bootstrap*` ab; Module mit `META-INF/spring/*.imports`/`spring.factories`; Klassen, die doppelt
oder in der Hülle (`BOOT-INF/classes`, `BOOT-INF/lib`) schon vorhanden sind.** Danach
`loader.path` + `PropertiesLauncher`; ohne Module `JarLauncher`.

Die in Karte 1563 (e) verlangte Registry-Prüfung existiert also bereits im Lader, mit
Unit-Tests positiv/negativ (`ModulPruefungTest`: sauberes Modul geladen, application.yml/
properties/bootstrap abgewiesen, Bibliothek mit application.properties abgewiesen,
Klassen-Duplikate zwischen Modulen und gegen die Hülle abgewiesen). v2 ergänzt dieselben Fälle als
**Lauf gegen das echte Exec-Jar** (`v2/lader-probe.sh`, Proben C und D).

Folgen für die Probe: `root-min` des Verrechnungs-Jars muss `>= 1.767.0` sein, sonst verweigert
der Lader (`ist gegen root 1.766.0 gebaut, die Huelle ist erst ab 1.767.0 kompatibel`). Das in
Konzept 1503 verwendete `2.1954.0` (gegen root 1.766.0 gebaut) ist dafür **zu alt**; die Probe
nimmt deshalb standardmässig das jüngste Release aus `maven-metadata.xml` und leitet `root-min`
aus der Elternkette des Jars ab (`plaintext-app-parent` → `plaintext-root.version`).

Die Hülle: `plaintext-root-webapp` baut das Exec-Jar mit `includeOptional=true`, der MCP-Server
(`spring-ai-starter-mcp-server-webmvc`, Karte 1512) ist fest drin; `HuelleKontextTest` prüft das.
Instanz-Konfig: `spring.config.import` aus `/data/config` bzw. `PLAINTEXT_KONFIG_VERZEICHNIS`.
Flyway: `out-of-order: true`, `validate-on-migrate: false`, `baseline-on-migrate: true`.

## 4. Versionsbasis in plaintext-scripts

`tui-build-logic.sh`: `hoechster_release_tag()` listet nur Tags `^[0-9]+\.[0-9]+\.[0-9]+$`,
`versionsbasis()` = MAX(davon, POM), `compute_release_versions` erhöht die Minor (Standard),
`do_release` taggt `${NEW_VERSION}` ohne Präfix und committet zweimal. Ein Tag `v2-…` ist für
diese Funktionen unsichtbar — gewollt, siehe README: v2 benutzt `do_release` nicht.

## 5. Was sonst noch auffiel

- `.build-trigger`, `cp.txt`, `start`, `build`: lokale Hilfsdateien von upstream, in v2 unangetastet.
- `renovate.json` ist aktiv (Renovate-PRs auf upstream). In v2 abgeschaltet, sonst doppelte
  Abhängigkeits-PRs, die bei jedem Sync kollidieren.
- `quality/quality-gate.properties` trägt den Sonar-Schlüssel `ch.plaintext:plaintext-root-parent`
  in einer URL — kein Maven-Bezug; v2 schreibt dort seinen eigenen Stand zurück (`merge=ours`).
- Lokaler Build mit JDK 25, `mvn clean install -DskipITs -DexcludedGroups=quality-gate
  -Dmaven.build.cache.enabled=false` nach der Umbenennung: 33 Module grün bis auf zwei
  `HuelleKontextTest`-Fehler durch `FATAL: sorry, too many clients already` der lokalen Postgres
  (max_connections 100) — Umgebung, nicht Umbenennung; mit 400 Verbindungen grün (Zahlen in der
  PR-Beschreibung).
