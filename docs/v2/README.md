# plaintext-root-v2 — Betrieb der v2-Grundlage (Karte 1563)

plaintext-root-v2 ist ein eigenständiges Repository mit der vollen Historie von
[plaintext-root](https://github.com/Plaintext-Gmbh/plaintext-root) (upstream). Es wird laufend
mit upstream synchronisiert, veröffentlicht aber unter **eigenen Maven-Koordinaten**
(`ch.plaintext.v2`) in dieselben Paket-Repos. Alles, was v2 von upstream unterscheidet, liegt in
`v2/`, `docs/v2/`, `.gitattributes`, `.github/workflows/v2-sync.yaml` und den drei
`.woodpecker/*.yml` mit `merge=ours`. Die Analyse, aus der die Entscheide folgen:
[analyse.md](analyse.md).

| Thema | Datei |
|---|---|
| groupId umbenennen (idempotent, Selbsttest) | `v2/umbenennen.sh` |
| Sync von upstream | `v2/sync.sh`, `.github/workflows/v2-sync.yaml`, `.gitattributes` |
| Release | `v2/release.sh`, `.woodpecker/deploy.yml`, `scripts/release-vollstaendig.sh` (TAG_PRAEFIX, GRUPPE_PFAD) |
| PR-Pipeline mit Lader-Probe | `.woodpecker/build.yml`, `v2/lader-probe.sh`, `v2/probe-modul/` |
| Purge alter v2-Releases | `v2/purge.py`, `v2/test_purge.py` |
| Wochenanalyse | keine in v2 — `.woodpecker/sonar.yml` ist ein Stub, der nie läuft (Entscheid Daniel 22.10.2026) |

## 1. Koordinaten

- groupId aller 33 Reaktor-Artefakte: `ch.plaintext.v2` (Parent `ch.plaintext.v2:plaintext-root-parent`).
- Java-Pakete unverändert `ch.plaintext.…`. Modul-Jars aus plaintext-app (`ch.plaintext:plaintext-z-…`)
  laden weiter in die Hülle; der Lader holt sie unter `ch/plaintext/` vom Reposilite.
- `v2/umbenennen.sh` erledigt die Umbenennung; nie von Hand. `--pruefen` ist der Grep-Test (CI und
  Release-Skript rufen ihn auf), `--selbsttest` der Fixture- und Idempotenz-Test.

## 2. Versionen und Tags

**POM-Version bleibt die von upstream** (`1.789.0` bzw. `1.789.0-SNAPSHOT`). v2 committet nie eine
Versionsänderung — sonst kollidierte jeder Sync an 33 `<version>`-Zeilen (Messung in analyse.md §1).

**v2-Version = (M+1).m.n** aus der upstream-Basis M.m.0:

| upstream-POM | v2-Releases |
|---|---|
| 1.789.0 | 2.789.1, 2.789.2, … (n = laufende Nummer, auch für v2-eigene Merges) |
| 1.790.0 | 2.790.1, … |

**Tag = `v2-<version>`**, z. B. `v2-2.789.1`. Die Nummer n ist MAX der vorhandenen Tags
`v2-(M+1).m.*` + 1 (v2-Fassung von `versionsbasis()`), plus Kollisionsprüfung gegen das
Reposilite. `versionsbasis()`/`do_release` aus plaintext-scripts sehen `v2-`-Tags nicht und
werden in v2 **nicht benutzt** — wer `./build release` in einem v2-Checkout startet, erzeugt einen
v1-artigen Tag ohne Präfix und Versions-Commits; nicht tun. upstream-Patch ≠ 0 bricht ab (kommt in
root nicht vor; alle 1.x-Tags enden auf .0).

## 3. Sync von upstream

`v2/sync.sh` (lokal und im Job identisch):

1. `upstream/master` holen; schon enthalten → nichts zu tun.
2. Arbeitszweig, `git merge --no-ff upstream/master`. `.gitattributes` `merge=ours` hält die
   v2-Fassung der Pipelines, von `repo-lint.yaml`, `renovate.json`, `quality-gate.properties`;
   upstream-Änderungen daran werden als HINWEIS gemeldet. Treiber: `git config merge.ours.driver true`
   (setzt das Skript; lokal einmal selbst setzen).
3. `v2/umbenennen.sh` → eigener Commit `v2: groupId nach Sync … erneut umbenannt`; `--pruefen` muss
   grün sein, sonst Abbruch ohne Push.
4. Push nach `master` **ohne** `[skip ci]` → Woodpecker released (deploy.yml).
5. Konflikt: Zweig `sync-konflikt/<datum>-<sha>` mit Konfliktmarkern, PR mit Label
   `sync-konflikt`, `master` unverändert. Steht so ein PR offen, tut der nächste Lauf nichts.

**Auslöser** (`.github/workflows/v2-sync.yaml`, `ubuntu-latest`, kostenlos für ein öffentliches Repo,
kein self-hosted Runner): **ein geplanter Lauf pro Nacht** (`schedule` 04:41 UTC) und
`workflow_dispatch` für Hand-Läufe (mit Trockenlauf-Option). Entscheid Daniel 22.10.2026: kein
Haken in root (v1), kein stündlicher Lauf. Ein upstream-Release kommt also spätestens in der
folgenden Nacht nach v2 und löst dort das v2-Release aus.

**Keine Schleife**: der Sync läuft nur auf schedule/dispatch; ein Push mit `GITHUB_TOKEN` löst keine
weiteren Actions aus; das v2-Release pusht nur einen Tag (Woodpecker filtert auf `push` + `master`,
ein Tag ist Ereignis `tag`).

## 4. Release (`.woodpecker/deploy.yml` → `v2/release.sh`)

Auf jeden Push nach `master` (Sync oder gemergter PR): Wurzel-groupId muss `ch.plaintext.v2` sein,
`--pruefen` grün; Version rechnen; Tag `v2-X` anlegen und **zuerst pushen** (Karte 518);
`mvn versions:set` nur im Arbeitsbaum; `mvn clean deploy -DskipITs -DexcludedGroups=quality-gate
-Dmaven.build.cache.enabled=false` → GitHub Packages (`<distributionManagement>`, Server-ID
`plaintext`) **und** Reposilite (Profil `dual-publish-nas` aus `.mvn/maven.config`, Server-ID
`plaintext-nas`); POMs zurücksetzen; GitHub-Release mit Notes (best effort); Schritt
`vollstaendigkeit` zählt beide Repos (`scripts/release-vollstaendig.sh`, `TAG_PRAEFIX=v2-`,
Gruppenpfad aus der POM). Kein NAS-Staging, kein SSH, kein Docker, kein git-Spiegel nach
plaintext-mvn. `[fast]` im Betreff überspringt die Tests, `[halbe-release-ok]` die Vorprüfung.

Manuell: "Run pipeline" mit `deploy_target=release-only`; ohne Variable tut der Lauf nichts.

## 5. Woodpecker-Aktivierung (Jumphost)

Secrets am Repo plaintext-root-v2 (Namen wie root, Werte je nach Vault):

| Secret | gebraucht von | Pflicht |
|---|---|---|
| `mvn_deploy_token` | deploy.yml (GitHub Packages, Tag-Push, GitHub-Release) | ja |
| `maven_nas_token` | deploy.yml (Reposilite) | ja |
| `github_status_token` | build.yml `testbericht` (Ereignis `pull_request` am Secret!) | ja (sonst startet build.yml nicht) |
| `sonar_token`, `nvd_api_key`, `pushover_app_token`, `pushover_user_key` | — (Wochenanalyse nur in root) | nein, liegen ungenutzt am Repo |
| `ssh_private_key` | — | **nein** (v2 referenziert es nicht) |

Repo-Einstellungen: `trusted.volumes = true` (Allure-Volume `woodpecker-reports`),
`cancel_previous_pipeline_events` ohne `push` (Karte 1281). **Keine Crons** in v2 (build.yml hat
kein `event: cron`, sonar.yml läuft nie). **Ein `from_secret` auf ein fehlendes Secret startet die Pipeline gar nicht und
meldet nichts** — deshalb die Liste.

**Stand 22.10.2026 (Jumphost):** Woodpecker für plaintext-root-v2 aktiv (Repo-Id 18),
`trusted.volumes=true`, `cancel_previous_pipeline_events=[pull_request]`, Timeout 120 min. Secrets
angelegt: `github_status_token` und `maven_nas_token` (Ereignisse cron/deployment/manual/push/
pull_request), `mvn_deploy_token`, `sonar_token`, `nvd_api_key`, `pushover_app_token`,
`pushover_user_key` (cron/deployment/manual/push). Kein `ssh_private_key`. Keine Crons (Entscheid
Daniel). Erster PR-Lauf (Pipeline 1, Commit `ed11299f`): build und playwright grün.

**Versionsbasis nach dem halben root-Release 1.789.0 (22.10.2026):** der v2-master stammt aus
dem Zustand mit POM fest auf `1.789.0` (ohne Prepare-Commit). Ein v2-Release daraus heisst
`2.789.1`, nie `1.789.0` — das Schema `(M+1).m.n` erzeugt keine 1.x-Nummer, und v2 schreibt nur
unter `ch/plaintext/v2/`. Nach der Reparatur in root (1.790.0) bringt der nächtliche Sync die
POM `1.790.0` und die Basis wird `2.790.n`.

## 6. Purge (`v2/purge.py`)

```
v2/purge.py                        # Trockenlauf, beide Repos, behält 5 je Artefakt
v2/purge.py --behalten 3 --nur reposilite
v2/purge.py --scharf               # löscht; nur auf Anweisung
```
Umgebung: `REPOSILITE_TOKEN` (= `maven_nas_token`, Benutzer `ci`), `GITHUB_TOKEN` mit
`read:packages` und für `--scharf` `delete:packages` (das Deploy-PAT hat das laut Vault-Notiz
bewusst nicht → eigenes Token). Ausgabe: je Artefakt behalten/löschen, v1-Zählung vorher/nachher
(muss gleich sein, sonst Exit 1). Sperre: Reposilite nur `releases/ch/plaintext/v2/`, GitHub
Packages nur `ch.plaintext.v2.*`; ein anderer Kandidat → Abbruch (Exit 3) vor dem ersten DELETE.
Fehlende Rechte → Exit 4 mit Klartext. Nach dem Löschen schreibt das Skript die
`maven-metadata.xml` des Artefakts im Reposilite neu (ohne die gelöschten Versionen). Tests:
`python3 -I v2/test_purge.py` (16, davon 5 Negativtests zur Sperre).

Läuft auf dem Jumphost (LAN) oder als manueller Woodpecker-Schritt in einem `python:3.13-alpine`-Image.

## 7. Lader-Probe (`v2/lader-probe.sh`)

In `.woodpecker/build.yml` nach dem Build: Hülle (`plaintext-root-webapp-*-exec.jar`) + Lader
starten mit dem jüngsten released `ch.plaintext:plaintext-z-verrechnung` aus dem Reposilite über
`loader.path` gegen den leeren Service `postgres-probe`. Belege im Schrittlog: `health` UP,
`[modul-lader] 1 Module bereit`, `Registered tools` mit/ohne Modul, Modul-Migration im Flyway-Log;
Schritt `lader-probe-flyway` liest `flyway_schema_history` per psql. Negativproben: Jar mit
`application.yml` und Jar mit Hüllen-Klasse → Lader bricht ab (Exit ≠ 0, Meldung).
`root-min` kommt aus der Elternkette des Jars; Module gegen root < 1.767.0 lehnt die Hülle ab.

Lokal ohne Reposilite: `v2/probe-modul/` (synthetisches Modul mit `@McpTool` und Migration), siehe
dort.

## 8. Nicht geprüft / offen

- `mvn clean deploy` in die echten Repos, der Tag-Push durch Woodpecker, der GitHub-Release-Schritt:
  erst nach der Aktivierung (Reposilite und Woodpecker sind aus der Cloud-Sitzung unerreichbar).
- `purge.py` gegen die echten APIs (nur Fake-Transport). Reposilite-API-Form (`/api/maven/details`,
  DELETE auf Verzeichnis, PUT der Metadatei) nach Dokumentation von Reposilite 3, nicht gemessen.
- Lader-Probe mit dem echten Verrechnungs-Jar (nur synthetisches Modul lokal).
- Branch-Schutz auf `master`: der Sync pusht mit `GITHUB_TOKEN`; eine Schutzregel ohne Ausnahme für
  Actions blockiert das. Dann: Regel anpassen oder den Sync auf PR-Modus umstellen.
- Die Woodpecker-Repo-Einstellungen (trusted.volumes) macht der Jumphost.
