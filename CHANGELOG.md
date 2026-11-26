# Changelog

All notable changes to this project will be documented here. The format
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). The version
number is **not** SemVer: the minor number counts releases (every merge to
`master` is one), and whether a release breaks a consumer is stated in the
entry text, not in the number.

Entries are written by hand per release; nothing is generated from commit
messages. Earlier minor versions are summarised from `git log` and may not be
exhaustive.

## [Unreleased]

### Deprecated

- **`plaintext-root-files` ist veraltet, Nachfolger `ch.plaintext:plaintext-modul-files`** (Karte 2377, ROOT-11).
  Umstieg: die Abhängigkeit `plaintext-root-files` durch `plaintext-modul-files` ersetzen (bzw. das Modul über die
  Laufzeit-Module laden). Dateien unter `plaintext.files.verzeichnis` (Layout `files/<benutzer>/…`) und die
  Freigabe-Links in `freigabe_link` (Art `files`, `files-inline`, Adresse `/nosec/files/{token}`) liest der Nachfolger
  ohne Migration weiter; die Rolle heisst in beiden `files`. Sind beide Module geladen, bricht der Start jetzt mit
  einer Meldung ab (gleiche Bean-Namen, Adressen und MCP-Werkzeuge); allein geladen meldet root-files beim Start
  «veraltet». Das Modul fällt in einem späteren Release weg.

## [3.83.0] - 2026-11-26

### Fixed

- **`my_user_entity.roles` auf `VARCHAR(4000)`** (Karte 2517): Das Rollen-Set wird mit vielen Modulrollen
  länger als 255 Zeichen; Speichern unter Rollenzuteilung scheiterte mit «Value too long». Migration
  `V1795639075__benutzer_rollen_spalte_breiter.sql`, nur Verbreitern.

## [3.82.0] - 2026-11-26

### Changed

- **`plaintext-root-lader`: Framework-Module von root dürfen sich in den Spring-Start einhängen** (Karte 2514,
  Entscheid Daniel 25.11.2026). Ein nachgeladenes `plaintext-root-*`/`plaintext-admin-*` darf
  `META-INF/spring/*.imports` bzw. `spring.factories` mitbringen: In der Hülle wirkt es so wie heute, weil die Hülle
  `ch.plaintext` scannt und Überschreiben erlaubt. Für alle anderen Module bleibt die Regel.

## [3.80.0] - 2026-11-26

## [3.79.0] - 2026-11-25

### Changed
- **Mehr Abstand in Formularen** (Karte 2425, Test Daniel im Träger): `plaintext-layout/css/layout.css`
  gibt dem Seiteninhalt mehr Abstand zum oberen Rand (2.25rem statt 1.5rem), Fieldsets und Dialoginhalten
  mehr Luft oben, der Suche in Dropdowns (`selectOneMenu`/`selectCheckboxMenu`) und Autocomplete Polster sowie
  Tabellen-Kopf und Spaltenfiltern Abstand. Wirkt in allen Apps, keine Seite muss angepasst werden.

## [3.78.0] - 2026-11-23

## [3.77.0] - 2026-11-22

## [3.75.0] - 2026-11-22

### Added
- **Modul-Zugriff: MCP-Schreibweg auf ROOT im globalen Mandanten** (Karte 2390, Übergabe chef
  417): `set_setting`/`delete_setting` verweigern `*.zugriff.*`-Schlüssel im Mandanten `global`,
  wenn der Aufrufer nicht ROOT ist. Bisher konnte ein globaler Admin-Token den Modul-Zugriff ändern;
  UI-Wege (settings-Seite, Modulverwaltung) prüften ROOT bereits. Schreiben im eigenen Mandanten
  bleibt Admin-Sache und wirkt auf den Modul-Zugriff nicht.
- **Übersetzungen: Tabelle mit Tabellen-Einstellungen** (Karte 2464): `i18n-translations.html`
  nutzt jetzt denselben Bedienbereich wie die übrigen Tabellen (Vorbild useradmin, Karte 1904):
  Spalten wählen und ordnen, Breiten ziehen, Sortierung und Seitengrosse speichern je Benutzer
  und Mandant. Neue Bean-Felder: `I18nBackingBean.anzeige` (TableSettings) mit init über den
  optionalen `TableStateStore`; die Aktionen-Spalte ist weder ausblendbar noch verschiebbar.

## [3.73.0] - 2026-11-22

## [3.72.0] - 2026-11-22

### Added
- **Modul-Zugriff, Teil 2: Sichtbarkeit und Bearbeiten je Modul** (Karte 2390, Entscheid 3):
  - `<key>.zugriff.sichtbar=false` blendet Menüpunkt und Dashboard-Kachel des Moduls aus — auch
    für ROOT/ADMIN, denn es ist keine Rollenprüfung: Die Seite bleibt per direktem Link erreichbar,
    `isOn()` und PageAccessGuard sind unberührt (wie `plaintext.menu.platzierung.<menuId>.sichtbar`,
    aber je Modul über die Modulverwaltung in settings).
  - `<key>.zugriff.bearbeiten=false` macht das Modul für alle schreibgeschützt, die es nutzen
    dürfen — auch für ROOT/ADMIN. Auswertung über `@modulRolle.darfBearbeiten('<key>')`
    (root-common); zuerst nutzt das files-Modul ihn (eigener PR).
  - Beide Schalter lesen und anwenden `ModulZugriff` (admin-settings) wie Teil 1: kein yml-Pendant,
    Standard ist sichtbar/bearbeitbar, nur exakt «true» öffnet, jeder andere Wert schliesst
    (fail-closed); eine Änderung wirkt nach dem Commit ohne Neustart. Module ohne Rollen-Eintrag
    können die Schalter ebenfalls bekommen; die Pflege-Oberfläche folgt in Teil 3.
  - Neu: `ModuleRoleProperties.nichtSichtbar`/`ohneBearbeitung` (canonical),
    `ModuleRoleService.isModuleSichtbarForLink` (Kacheln), `MenuItemImpl.moduleRoleSichtbar`
    (Menü-Filter im MenuModelBuilder, bewusst nicht in `isOn()`).
- **Modul-Zugriff, Teil 3: Pflege-Oberfläche in der Modulverwaltung** (Karte 2390): in
  `module.xhtml` je Modul ein «Zugriff»-Dialog (Schloss-Knopf), der dieselben fünf
  `<key>.zugriff.*`-Schlüssel in settings pflegt (Mandant `global`). Dialog nur, wenn
  `plaintext-admin-settings` läuft (optionale Abhängigkeit von `plaintext-admin-modules`); ohne
  ihn bleibt alles wie bisher. Seitenhilfe nennt die Semantik inkl. Root-trägt-meist-ADMIN und
  «Rolle leer öffnet wieder».

## [3.70.0] - 2026-11-22

### Added
- **Die Hülle liefert `plaintext-admin-requirements` mit** (Karte 2450): `plaintext-root-webapp`
  hängt compile an `ch.plaintext.v2:plaintext-admin-requirements` (optional, eigene Version). Das
  Artefakt steckt damit im Hüllen-Exec-Jar; Apps und Module können es künftig `provided` einbinden
  statt es als eigenes Modul mitzubringen. Das löst bei app den registry.sh-Classifier-Fund
  (Karte 2459): `registry.sh` überspringt es als Hüllen-Modul, die Zeile
  `plaintext-root-tablesettings:jar:ohne-taglib` wird nicht mehr geparst. `spring-ai-mcp`
  (spring-ai-starter-mcp-server-webmvc samt Transitiven) liefert die Hülle schon seit 3.0.0 mit
  (zuletzt gehoben auf 2.0.2); Module mit MCP (z. B. z-schuetu) können ebenfalls auf `provided`
  umstellen. Folgeschritte je App
  (provided-Umstellen) sind eigene Karten. Wegen der neuen Abhängigkeit baut Maven
  `plaintext-admin-requirements` vor `plaintext-root-webapp`; der ArchUnit-Lauf der webapp prüft
  seine Klassen nun mit (Regelkommentar angepasst), der eigene Lauf des Moduls bleibt.
- **Modul-Zugriff je App in settings** (Karte 2390, Teil 1, Entscheide Daniel 21.11.2026):
  - Wer ein Modul mit Modulrolle nutzen darf, steht in settings (Mandant `global`): `<key>.zugriff.rolle` (leer = frei), `<key>.zugriff.root` und `<key>.zugriff.admin` (`true` = darf ohne die Rolle). Pflege über die settings-Seite (Gruppe des Moduls); die Oberfläche in der Modulverwaltung folgt in Teil 3.
  - Beim ersten Start schreibt `ModulZugriff` (plaintext-admin-settings) aus `plaintext.menu.module-roles`/`strenge-module-roles` die fehlenden Schlüssel, nie überschreibend: root/admin `true`, bei strengen Modulen `false`. Module ohne Eintrag bekommen nichts (frei bleibt frei). Danach gilt settings; eine Änderung wirkt sofort (Ereignis `EinstellungGeaendert` aus `SettingsServiceImpl`), ohne Neustart, und steht im Log mit Benutzer.
  - Fehlt ein yml-Modul in settings (Vorgabe nicht geschrieben), gilt für dieses Modul weiter die yml: der Zugriff öffnet sich nie aus Versehen.
  - Root- und Admin-Umgehung getrennt: `ModuleRoleProperties` kennt neu `ohne-root-umgehung`/`ohne-admin-umgehung` (strenge Module = beides), `ModuleRoleService.holdsAny(..., ohneRoot, ohneAdmin)`, `MenuItemImpl.moduleRoleOhneRoot/OhneAdmin`, Kacheln und `@modulRolle.darf` (REST/MCP) nach derselben Regel. `ModuleRoleService.neuAufloesen()` löst nach einer Änderung neu auf. Hinweis: Ein Root-Benutzer trägt meist auch ADMIN; «root nein, admin ja» lässt ihn über die Admin-Umgehung trotzdem hinein.
  - Ohne plaintext-admin-settings in einer App bleibt alles wie bisher aus der yml. Keine Migration.

## [3.69.0] - 2026-11-22

### Changed
- **Seite «Laufzeit-Module» übersichtlicher** (Karte 2456, Test Daniel im Träger):
  - Im Dropdown steht jede Version einmal. Die Registry nennt es nur, wenn sich die Kopien einer Version in den Registries unterscheiden (sha256, Signatur oder Passung zur Hülle). Gleiche Kopien lädt der Lader ohne Registry aus der ersten, mit demselben Ergebnis. Eine gespeicherte Registry bei gleichen Kopien zeigt die Seite als diese eine Version.
  - «Verfügbar» zählt nicht mehr jede Kopie auf, sondern zeigt «n Versionen (Registries)» und dahinter nur, was nicht geht (Signatur ungültig, passt nicht).
  - Die Registry-Liste zählt Module und Versionen («1 Modul, 4 Versionen») statt Index-Zeilen als «4 Modul(e)».

### Fixed
- **Kein «Registry-Fehler» beim Start für eine noch nicht vorgeladene Quelle aus der Oberfläche** (Karte 2456): Eine mit «Quelle übernehmen» angelegte Quelle hat `vorbereitet/<name>/index.txt` erst, wenn «Auswahl speichern» ein Modul daraus gewählt hat. Bis dahin meldete jeder Start «Registry-Fehler (Auswahl trotzdem aufgelöst): {kunde=…/vorbereitet/kunde/index.txt}». Jetzt übergeht der Lader die Quelle mit einer Logzeile. Nennt die Auswahl sie trotzdem, scheitert das Auflösen wie bisher, und es gilt der letzte gute Stand.

## [3.68.0] - 2026-11-21

### Fixed
- **Verbrauchter Bestätigungs- oder Reset-Link zeigt sofort «Link ungültig»** (Karte 2410): `GET /register/verify` und `GET /password-reset/confirm` zeigten bisher immer das Passwortformular; erst das Absenden meldete «Link ungültig». Jetzt prüfen beide den Link beim Aufruf (`tokenGueltig`: vorhanden, nicht verbraucht, `expiresAt > now`, wie das atomare `consumeToken`, ohne ihn zu verbrauchen) und zeigen sonst gleich die bekannte Seite «Link ungültig».

## [3.67.0] - 2026-11-21

### Added
- **Speicher-Konfiguration in settings** (Karte 2425, PR C, Entscheid Daniel (284) 1):
  - Die Ablagen aus `plaintext.speicher.<name>` stehen auch in settings, Mandant `global`, unter `speicher.<name>.<einstellung>` (z. B. `speicher.files.art`, `speicher.files.pfad`). Beim Start schreibt root die yml-Werte als **fehlende** Vorgaben dorthin und überschreibt nie.
  - Danach gilt je Einstellung der Wert aus settings, bei jedem Öffnen gelesen, also ohne Neustart. ROOT kann in settings auch einen neuen Speicher anlegen (`speicher.<name>.art` und die Einstellungen der Art).
  - Geheimnisse nie in settings: Ein Schlüssel `passwort`, `token`, `secret` … wird nicht nach settings geschrieben, und ein Speicher mit einem solchen Schlüssel (aus yml oder settings) wird abgewiesen. Der Verweis heisst `passwort-secret` (Name im Secret-Store).
  - **Secret-Verweise nur im eigenen Namensraum** (Fund claude2, Entscheid chef (299)): Ein `…-secret` muss auf `speicher.<name>.<feld>` zeigen, sonst bleibt der Speicher aus. So kann niemand über settings ein beliebiges vorhandenes Secret an einen fremden Host schicken.
  - Settings-Seite: «Geheimnis eines Speichers setzen (nur schreiben)» legt den Wert über `SecretWriter` unter `speicher.<name>.<feld>` ab und setzt den Verweis `speicher.<name>.<feld>-secret`. Der Wert wird nie angezeigt.
  - Ohne settings in der Anwendung gilt nur die yml.

## [3.66.0] - 2026-11-21

### Added
- **Vorgaben der Module in settings, Gruppen je Modul** (Karte 2425, PR B, Auftrag Daniel):
  - `ModuleDescriptor.einstellungen()` (neu, Vorgabe leer) meldet die Vorgaben eines Moduls als `Vorgabe(schluessel, wert, typ, beschreibung)`. Beim Start schreibt admin-settings (`ModulVorgaben`) jede **fehlende** nach Mandant `global`; ein vorhandener Wert wird nie überschrieben.
  - Ein Schlüssel muss mit `<moduleId>.` beginnen; sonst, oder wenn er wie ein Geheimnis heisst (`…passwort`, `…token`, `…secret`), wird er mit einer Log-Zeile übergangen.
  - Settings-Seite: Auswahl «Gruppe» über der Tabelle. Die Gruppe ist der Teil des Schlüssels vor dem ersten Punkt, wenn er eine `moduleId` ist, sonst «root». Vorgaben eines Moduls sind als «Vorgabe des Moduls» markiert; andere Schlüssel derselben Gruppe (z. B. persönliche Werte) nicht.

## [3.65.0] - 2026-11-21

### Added
- **Benannte Speicher** (Karte 2425, PR A, Auftrag Daniel):
  - Neue Schnittstelle `DateiAblageArt` (root-interfaces): eine Speicher-Art mit Namen (`art()`) öffnet eine `DateiAblage` aus Einstellungen. Jede Anwendung kann eigene Arten als Bean beitragen (z. B. das Inventar eine Art für seinen Code-Service).
  - Arten in root: `lokal` (root-common, Unterordner `pfad` unter `plaintext.ablagen.lokal.basis`), `nextcloud` und `git` (sidecars, dieselben Klassen wie unter Root > Sidecars).
  - Ablagen per Name in der `application.yml`: `plaintext.speicher.<name>.art` plus die Einstellungen der Art. Geheimnisse stehen dort nie: `passwort-secret` nennt den Namen im Secret-Store.
  - Das Register, das Module bekommen (`DateiAblagenRegister`, jetzt `KonfigurierteAblagenRegister` in root-common, `@Primary`), führt die yml-Ablagen mit Root > Sidecars zusammen; bei gleichem Namen gilt die yml (WARN). Eine unbekannte Art oder eine nicht nutzbare Ablage ergibt ein leeres Ergebnis mit Log-Zeile, kein Startabbruch.
  - Damit hat auch eine Anwendung ohne sidecars (z. B. das Inventar) ein Register.
  - **Achtung für Anwendungen:** Ein eigenes `DateiAblagenRegister` (z. B. `DemoAblagenRegister` oder ein Test-Register) bleibt eine Quelle und wird mit zusammengeführt. Es darf aber **nicht** `@Primary` sein, sonst gibt es zwei primäre Beans (`NoUniqueBeanDefinitionException`).

## [3.64.0] - 2026-11-21

### Added
- **Laufzeit-Module: Quelle per JSON aus modules** (Karte 2408, Teil 2):
  - Auf «Laufzeit-Module» kann ROOT das JSON `plaintext-modul-quelle/1` (Seite «Meine Zugänge» auf modules) einfügen. Geprüft wird streng: unbekannte Felder werden abgewiesen, `index` nur https, Ed25519-Schlüssel, und der Index muss mit dem Zugang abrufbar sein und mindestens ein Modul mit gültiger Signatur enthalten.
  - Der Zugang geht über die neue Schnittstelle `SecretWriter` (root-interfaces, umgesetzt von `SecretService`) in das **aktive Backend des Secret-Stores** unter `modul-quelle.<name>`. In `quellen-ui.txt` steht nur Name, Index und Schlüssel.
  - Die Seite zeigt Datei- und UI-Quellen zusammen.
  - «Auswahl speichern» lädt die gewählten Module einer UI-Quelle mit `ModulAuswahl.vorladen` nach `vorbereitet/<name>/` vor (Signatur, sha256; erst vollständig, dann umbenannt). Scheitert das, wird nichts gespeichert.
  - «Entfernen» nimmt Quelle, Vorgeladenes und Zugang weg.
  - Hinweis: Ohne eingerichtetes Backend ist das aktive Backend des Secret-Stores VAULTWARDEN. Wer LOCAL_DB will, stellt es unter Root > Secrets ein.

## [3.63.0] - 2026-11-21

### Added
- **Lader: Quellen aus der Oberflaeche** (Karte 2408, Teil 1, Variante A nach chef (227)):
  - Neben `auswahl.yaml` darf `quellen-ui.txt` stehen. Erlaubt ist dieselbe Syntax wie in `module-quellen.txt`, aber nur `https` und ohne `anmeldung-env` und `eingebettet`. Die Datei enthält nie ein Passwort.
  - Der Zugang liegt im Secret-Store der App. Die App lädt beim Speichern Index und Jars nach `vorbereitet/<name>/` (`index.txt` mit signierten Zeilen samt Jars).
  - Der Lader liest dieses Verzeichnis als `file:`-Registry, also ohne Netz und ohne Passwort. Signatur und sha256 prüft er wie bei jeder Registry, je Slot in den eigenen Cache.
  - Bei gleichem Namen gilt `module-quellen.txt`.
  - **Erster Start ohne Vorgeladenes:** kein Absturz. `status.txt` nennt die Quelle, dann gilt der letzte gute Stand, ohne ihn `module.yaml`, ohne beide ein Start ohne Module.
  - Eine unlesbare `quellen-ui.txt` wird mit einer Log-Zeile übergangen.
  - Die Oberfläche (JSON einfügen, Secret, Vorladen) folgt in Teil 2 (admin-modules).

## [3.62.0] - 2026-11-21

### Fixed
- **pt:-Tags fehlten in root-v2-Apps** (Karte 2411): `plaintext-root-tablesettings` bringt eine eigene Taglib im
  Namensraum `http://plaintext.ch/ui` mit (nur `tableSettings`). Mojarra fuehrt Taglibs mit gleichem Namensraum nicht
  zusammen, sondern behaelt die erste; im Exec-Jar lag sie vor `plaintext.taglib.xml` des Templates und verdeckte alle
  uebrigen pt:-Tags (`dateiAblage`, `freigabeLinks`, `table`, `chip`, `filter`, …), z. B. 500 auf «Ablage ausprobieren».
  `plaintext-root-common` haengt jetzt am Jar mit Classifier `ohne-taglib`; Apps ohne Template (Inventar) nehmen weiter
  das Haupt-Jar. Neuer Test `TaglibNamensraumEindeutigTest` (webapp): jeder Taglib-Namensraum genau einmal.

## [3.61.0] - 2026-11-20

### Added
- **Laufzeit-Module: Registry je Wahl, Registries mit Status** (Karte 2377, Backport ROOT-9). `auswahl.yaml` kennt je
  Eintrag optional `registry: <name>` (Name aus `module-quellen.txt`); der Lader laedt dann genau aus dieser Registry,
  ohne Angabe wie bisher aus der ersten mit passender Version. Die Seite «Laufzeit-Module» zeigt die Registries der
  Quellen mit Status (Fehler, leer, Anzahl Module, ungueltige Signaturen) und bietet eine Version, die in mehreren
  Registries liegt, je Registry an («Version (Registry)»); `auswahl.yaml` erhaelt `registry` nur, wenn so gewaehlt.
  Hinweis: eine `auswahl.yaml` mit `registry` ist fuer aeltere Huellen ein Fehler («optional 'registry'» kennt erst
  diese Version); root-min des Huellen-Release beachten.

## [3.60.0] - 2026-11-20

### Added
- **ArchUnit-Regel `menuIdPflichtBeiModuleId`** (Karte 2377, Backport ROOT-4): eine Klasse mit
  `@MenuAnnotation(moduleId=…)` braucht eine nicht-leere `menuId`; sie ist der stabile Schluessel fuer die
  Platzierung von aussen und die Auto-Rolle. Verstoesse in Konsumenten-Repos ueber `plaintext-arch-allowlist.txt`
  (`menu-id <Klasse>  # <Karte, Grund>`), Uebergang ein Release. Beschrieben in `docs/MENU_SYSTEM.md`.
  Schalter `plaintext.arch.menu-id` wie `plaintext.arch.inline-js`: Vorgabe **melden** (Konsumenten-Repos werden beim
  root-Bump nicht rot, die Verstoesse stehen im Testlog), `enforce` = rot; root selbst laeuft mit `enforce`.

### Changed
- **menuIds der root-eigenen Menueeintraege** (Karte 2377, ROOT-4): `claude-api`, `i18n`, `mailtemplate`,
  `notifications`, `secrets`, `settings`, `setup`, `ablage-ausprobieren`, `sidecars`, `webhooks`, `misc`, `deeplinks`,
  `cron`.
  Nur unter `plaintext.menu.access-policy=STRICT` sichtbar: die Auto-Rollen heissen jetzt `ROLE_MENU_<MENUID>`
  (z. B. `ROLE_MENU_SECRETS` statt `ROLE_MENU_ROOT_SECRETS`, `ROLE_MENU_I18N` statt `ROLE_MENU_ROOT_UEBERSETZUNGEN`);
  unter der Vorgabe PERMISSIVE aendert sich nichts. Platzierungen (`plaintext.menu.platzierung.<menuId>`) nutzen die
  neuen Ids.

## [3.59.0] - 2026-11-19

### Added
- **Menueplatzierung von aussen: Modell und Konfiguration** (Karte 2377, Backport ROOT-1). Neu in plaintext-root-interfaces
  `MenuPlatzierung` (parent, order, title, icon, sichtbar; alle optional), `MenuGruppe` (order, icon),
  `MenuPlatzierungProvider` (`@ModulApi` SCHNITTSTELLE, NEU) und `MenuPlatzierungProperties`
  (`plaintext.menu.platzierung.<menuId>`, `plaintext.menu.gruppen.<Titel>`). plaintext-root-menu registriert den
  Standard-Provider aus den Properties (`@ConditionalOnMissingBean`, eine eigene Bean gewinnt). Noch keine
  Verhaltensaenderung: angewendet wird die Platzierung erst mit ROOT-2.
- **Menueplatzierung von aussen: anwenden** (Karte 2377, Backport ROOT-2). `MenuRegistryPostProcessor` wendet
  `plaintext.menu.platzierung` (oder eine eigene `MenuPlatzierungProvider`-Bean) vor der Registrierung an:
  parent/order/title/icon werden gesetzt, die wirksame `menuId` wird vorher eingefroren (Auto-Rolle
  `ROLE_MENU_<menuId>` und `moduleId` bleiben). Fehlende Eltern aus `plaintext.menu.gruppen` entstehen als Gruppe ohne
  Link (`MenuItemImpl.gruppe`, keine eigene Rollenpruefung, faellt ohne sichtbares Kind aus dem Menue).
  `sichtbar: false` nimmt den Eintrag aus dem Menue, PageGuard und Suche sehen ihn weiter. Unbekannte menuId, Elter
  weder vorhanden noch Gruppe und Zyklus ueber parent: WARN, Eintrag bleibt auf der Vorgabe, kein Startabbruch.
  Startlog `MenuPlatzierung : n Eintraege umgehaengt, m Gruppen angelegt - erkannte menuIds: [...]`.
  `/debug/menu-scan` und die Menue-Diagnose zeigen die Vorgabe neben dem wirksamen Platz (`MenuDiagnoseZeile.platzVorgabe`
  neu). Ohne Konfiguration unveraendert. **Achtung beim Umhaengen:** der volle Titel (`Elter | Titel`) aendert sich;
  Mandanten-Menuelisten und Kachel-`menuTitle`, die den alten Titel nennen, greifen dann nicht mehr. Umhaengen unter
  «Root» unterstellt den Eintrag der Root-Zweig-Regel (nur ROOT).
- **Strenge Modulrollen ohne ADMIN/ROOT-Umgehung** (Karte 2377, Backport ROOT-3). Neu
  `plaintext.menu.strenge-module-roles` (Liste von Modul-Keys): fuer diese Module brauchen auch ADMIN und ROOT die
  Modulrolle, in Menue, Kacheln, PageGuard (`isOn`) und `@modulRolle.darf(...)`. Ein strenges Modul ohne Eintrag in
  `module-roles` verlangt die gleichnamige Rolle (geschlossen statt offen). `ModuleRoleService.holdsAny(..., streng)`
  neu, `MenuItemImpl.moduleRoleStreng` neu; Startlog nennt die strengen Module, die Menue-Diagnose sagt «streng».
  Nicht gelistete Module unveraendert.

## [3.58.0] - 2026-11-19

### Added
- **Systemlauf: eine Maschinen-Identitaet** (Karte 1863, Etappe 1). `ch.plaintext.security.Systemlauf` (root-common)
  baut den Kontext fuer Laeufe ohne Benutzer an EINER Stelle: Principal `SYSTEM`, `ROLE_SYSTEM`,
  `PROPERTY_MANDAT_<m>`, danach der vorherige Kontext zurueck. SuperCron und PlaintextBusDispatcher nutzen ihn
  (verhaltensgleich). Neu: `TaskDecorator` (AutoConfiguration in root-common) -> `@Scheduled`/`@Async` ohne
  Kontext laufen als Systemlauf im Mandat `global`, ein vorhandener Kontext bleibt. Die Benutzerrolle `system`
  wird in keinem Anmeldeweg (Formular, OIDC, Mandatswechsel, MCP-Token, Watch-Link) zur Authority, ADMIN kann sie
  nicht vergeben, ROOT auch nicht (UI, Rollenzuteilung). Auditing ohne Benutzer schreibt `SYSTEM` statt `system`.
  Keine neuen Gates (Etappe 2). **Verhaltensaenderung fuer Konsumenten:** rohe `@Scheduled`-/`@Async`-Threads hatten
  bisher keinen Kontext (`getMandat()` = `NO_AUTH`); jetzt `ROLE_SYSTEM` + `PROPERTY_MANDAT_global`, neue Entitaeten
  ohne gesetztes Mandat bekommen also `global` statt `NO_AUTH` (`SuperModel` @PrePersist). Pruefpunkt beim
  schuetu-Bump: KorrekturWrapper, RevokedTokenService, ResultateVerarbeiter persistieren ohne eigenen Kontext.
- **Flyway-Vorpruefung mit Modul-Hinweis** (Karte 2343, Folge des app-Vorfalls 17.11.). Neue
  `FlywayMigrationStrategy` in plaintext-root-webapp (`ch.plaintext.boot.flyway`): bei `validate-on-migrate: true`
  validiert sie vor dem Migrate genau wie `Flyway.migrate()` (konfigurierte Muster plus `*:pending`) und bricht bei
  Fehlschlag mit Versionen und dem Hinweis auf `module/gemeinsam/auswahl.yaml` ab, statt mit Flyways nackter
  Versionsnummer. Bei Erfolg eine INFO-Zeile «Flyway-Vorpruefung ok». Wirkt in der Huelle fuer alle Apps; eine
  App mit eigener `FlywayMigrationStrategy` muss diese beim Bump entfernen (sonst zwei Beans). Der ModulLader
  meldet Module aus `module.yaml`, die die Auswahl nicht nennt (`[modul-lader] ACHTUNG: … NICHT in der Auswahl`).

### Changed
- **Release benennt den CHANGELOG-Block selbst um** (Karte 2366): `scripts/release.sh` macht aus
  `[Unreleased]` die Sektion `[<version>] - <datum>` und legt ein neues leeres `[Unreleased]` an, im
  selben `[skip ci]`-Commit wie der SNAPSHOT-Schritt. Die Sektionen 3.1.0 bis 3.57.0 werden nicht
  nachgetragen.
- **`plaintext-root-files`: unter «Misc», mit Rolle `files`, Admin/Root sehen alle, Links zum Anzeigen**
  (Karte 2354). **Verhaltensaenderung fuer Konsumenten:** die Ablage hatte bisher jeder Angemeldete (`USER`);
  jetzt braucht sie die Rolle `files` - auf der Seite, an `/files/api/*` und an den MCP-Werkzeugen (dort wird die
  Rolle neu geprueft, vorher genuegte das Token). `admin` und `root` kommen weiter hinein. Beim Bump die Rolle
  vergeben (`FilesRoleProvider` meldet sie der Benutzerverwaltung), sonst ist die Ablage fuer alle anderen zu;
  die Dateien bleiben liegen und laufen nach `plaintext.files.frist` ab wie immer. Ohne Seitenschutz
  (`page-guard.enabled=false`) galt «angemeldet genuegt», jetzt gelten dort dieselben drei Rollen.
  Der Menueeintrag haengt neu unter «Misc»; das Modul bringt dieses Elternmenue mit (`order` 60). Eine Anwendung
  mit eigenem «Misc» setzt `plaintext.files.misc-menue=false` (sonst doppelter Titel, WARN bei jedem Menueaufbau)
  und oeffnet ihr «Misc» fuer die Rolle. `menuId = files` ist fest, `ROLE_MENU_FILES` bleibt also gueltig.
  Admin und Root sehen auf der Seite die Dateien aller Benutzer mit Besitzer (Schalter «Nur meine Dateien»),
  laden sie herunter und loeschen sie; teilen kann nur der Besitzer. REST: `dateien?alle=true` (Eintraege mit
  `besitzer`), `datei?benutzer=…`, `loeschen {benutzer}`; wer nicht Admin/Root ist und einen fremden Ordner
  nennt, bekommt 403. Fremdzugriffe stehen im Log (`[FilesApi] … fremde Datei`). `Datei` hat neu die Komponente
  `besitzer` an erster Stelle - wer den Record selbst baut, muss nachziehen.
  Freigabe-Links: Haken «Direkt im Browser anzeigen statt Download» (Vorgabe aus). Ein solcher Link liefert
  Bilder, PDF und Textdateien mit `Content-Disposition: inline` und ihrem Typ (Text immer `text/plain`, SVG mit
  `Content-Security-Policy: sandbox`); alles andere - HTML zuerst - laedt er weiter herunter, weil es unter der
  Adresse der Anwendung mit der Sitzung des Oeffnenden liefe. Gespeichert ist die Wahl als zweite Link-Art
  `files-inline` im `LinkTokenStore`, ohne neue Spalte und ohne Aenderung am Vertrag; eine Token-Uebersicht, die
  nach Art `files` filtert, zeigt diese Links erst, wenn sie auch `files-inline` liest. MCP `create_file_link`
  hat den optionalen Parameter `imBrowser`. Bestehende Links bleiben Download-Links.
- **`system` an zwei weiteren Schreibwegen nie vergebbar, `Systemlauf` ist `@StabileApi`** (Karte 2352, Nits aus
  dem Review zu 1863). Der MCP-Import (`BenutzerMcpTools`) verwirft `system` ausdruecklich, nicht nur ueber
  `isPrivileged`; das per OIDC automatisch angelegte Konto uebernimmt `system` nicht aus den Default-Rollen der
  OIDC-Konfiguration (bisher gespeichert, beim Anmelden gefiltert). `ch.plaintext.security.Systemlauf` ist als
  stabile API deklariert. **Fuer Konsumenten:** ein `root-common-api`-Eintrag `-> ch.plaintext.security.Systemlauf`
  in `plaintext-arch-allowlist.txt` ist beim Bump auf diese Version verwaist und muss raus (guild PR 561).
- **Java-21-Ziel seit 3.46.0** (afb1525 + #57, Karte 2302/2347): der Parent setzt `release 21` fest
  (Class-Version 65 fuer die BIT-Laufzeit); ein eigenes `java.version` in der App greift nicht. Konsumenten
  kompilieren damit ebenfalls fuer 21: unbenannte Variablen (`catch (X _)`, `_ ->`) und Java-22+-APIs
  brechen den Build — vor dem Bump `grep -rnE 'catch \([A-Za-z.| ]+ _\)|\b_ ->' --include=*.java`
  und umbenennen (app 1acf4da6 als Muster).
- **Tomcat 11.0.27 → 11.0.29** und **PrimeFaces 16.0.1 → 16.0.2** (Renovate #51/#53 gebuendelt, Karte 2029):
  Bugfix-Releases ohne CVE-Druck. PrimeFaces setzt die Defaults von `p:commandButton` `escape` und
  `renderDisabledClick` wieder auf true; bekannte HTML-Werte in Buttons pruefen (app zeiterfassung.xhtml
  ist korrekt).
- **MY_OBJECT auf TEXT** (Karte 2347, Folge 2333): Migration in plaintext-root-webapp hebt
  `simple_storable_entity.my_object` von VARCHAR(30000) auf TEXT (`SET DATA TYPE`, laeuft auch auf dem
  demo-H2-Pfad). Kommt in jeder App mit dem naechsten root-Bump an; die Entity bleibt unveraendert
  (`ddl-auto: none`). Dazu `white-space: nowrap` fuer die Beschriftung «Fixed width:» der
  Tabelleneinstellungen.
- **pt:tableSettings nach Daniels Test** (Karte 2333, 17.11.). Die Griffe im Tabellenkopf folgen wieder dem Modus:
  «Anpassen» zeigt keine, «Verschieben» nur die Verschiebe-Griffe, «Vergrössern» nur den Verbreitern-Griff
  (`isGroesseAendernAktiv()` = `mitBreiten` und Modus 2; gespeicherte Breiten gelten in jedem Modus). Das Feld
  «Feste Breite» steht nur im Modus «Vergrössern» neben dem Modus-Knopf, ohne Spaltenname; sein Wert lebt nur in
  der Sitzung. Doppelklick auf einen Verbreitern-Griff: leeres Feld übernimmt die Breite dieser Spalte und das
  Panel öffnet sich am Regler-Symbol (nie an der Spalte); steht ein Wert im Feld, bekommt die Spalte genau diese
  Breite, die wie jede Spaltenbreite sofort gespeichert wird. Das Panel bleibt beim Anlegen eines Profils offen.
  Die Spaltenliste im Reiter «Spalten» nutzt die ganze Breite und merkt sich ihre von Hand gezogene Höhe
  (`TableState.listenHoehe`). Scheitert das Speichern des Stands, werden die Profile des Altbestands verworfen
  und der laufende Stand neu gespeichert; ein unlesbarer Stand beim Laden ergibt einen frischen.
  Apps müssen nichts ändern. Playwright-validiert auf `useradmin.xhtml`.

### Fixed
- **`plaintext-root-files`: `FilesAufraeumen` ist `@Scope("prototype")`** (Karte 2354). Die Angabe fehlte; mit
  `plaintext-admin-cron` am Klassenpfad brach der `CronBeanPostProcessor` den Start der Anwendung ab
  («PlaintextCron bean 'filesAufraeumen' must be @Scope("prototype")»). Aufgefallen bei der Pruefung der Seite in
  der root-Anwendung; ein Test haelt die Angabe fest.
- **`plaintext-root-freigabe`: `JpaLinkTokenStore.anlegen` schreibt das Recht als `r`/`rw`** (Karte 2354). Die
  Spalte `freigabe_link.recht` ist `VARCHAR(2)`; der Store schrieb `lesen`/`schreiben`, und jedes Anlegen eines
  Token-Links (Files, Draw.io, Diashows) scheiterte auf PostgreSQL mit «value too long for type character
  varying(2)». Der Test lief gegen einen Repository-Mock und sah es nie. Gelesen wird wie bisher ueber
  `LinkRecht.von()`, das `rw` schon als Schreibrecht kennt; keine Migration, bestehende Zeilen gibt es keine.
- **`plaintext-root-files`: der Dialog «Link teilen» bleibt nach «Link erzeugen» offen** (Karte 2354). Erzeugen
  und Zurueckziehen renderten den ganzen `p:dialog` neu, der damit wieder zu war - die Adresse, die nur in diesem
  Moment angezeigt wird, sah niemand. Erneuert wird jetzt nur der Inhalt.

### Removed
- **pt:tableSettings: fester Stand** (Karte 2333). «Aktuellen Stand festhalten» und der Doppelklick auf den
  Modus-Knopf sind weg, ebenso `TableSettings.festerStandErfassen()`, `festerStandHerstellen(String)`,
  `isFesterStandErfasst()`, `getBreiteFixSpalte()`, `breiteFixOeffnen(String)` und `breiteFixUebernehmen(String)`
  (das Tag war der einzige Aufrufer). `TableState.festerStand` bleibt als Feld, damit gespeicherte Stände lesbar
  bleiben.

### Added
- **pt:tableSettings: exakte Breite per Doppelklick auf den Verbreitern-Griff** (Karte 2160). Ein Doppelklick auf
  das ↔-Viereck im Kopf einer Spalte (Daniel 15.11. — ersetzt die Zwischenlösung über den Modus-Knopf vom 14.11.)
  wählt diese Spalte und öffnet das Panel am Griff: das Eingabefeld im Ansicht-Reiter (dauerhaft sichtbar beim
  «Fester Stand»-Block, Vorschlag = aktuelle Breite) speichert beim Verlassen ohne Übernehmen-Knopf — der Wert
  (unter 44 px wird verworfen) wandert über `persist()` ins aktive Profil und wirkt am Tabellenkopf auch nach dem
  Reload. Der Doppelklick auf den Modus-Knopf stellt durchgehend den festen Stand her (Runde 7).
  Playwright-validiert auf `useradmin.xhtml`.
- **File store type «Lokale Datei» (`LOKAL`)** (Karte 2096): `ch.plaintext.boot.ablage.LokaleDateiAblage` implements
  `DateiAblage` on a folder of the server's file system, confined to a base the operator sets with
  `plaintext.ablagen.lokal.basis` (no `..`, no symlink leading outside, atomic writes). Under *Root → Sidecars →
  Speicher-Ablagen* the new type only asks for the sub-folder below that base - never an absolute path, so an admin
  cannot read the server's file system through it. Without the property the type is off (greyed out in the form,
  rejected by `set_speicher_ablage`). Non-breaking: existing NEXTCLOUD/GIT stores are unchanged; apps without
  `plaintext-admin-sidecars` can use `LokaleDateiAblage` directly behind their own `DateiAblagenRegister`.

### Removed
- **pt:tableSettings: Gesamtbreite und Zielspaltenbreite samt Java** (Karte 2160). Der Reiter «Breiten» war seit
  Runde 7 (3.31.0) ohne Bedienelemente; jetzt sind auch die Felder `TableState.totalWidth`/`targetColumnWidth`,
  `TableColumnProfile.totalWidth`/`targetColumnWidth` und die `FesterStand`-Komponente `totalWidth` weg, ebenso
  `getGesamtBreite`/`getZielSpaltenBreite` (jeweils mit Setter und Change-Listener), `isBreiteAbweichend`,
  `getBreitenKnopfText`, `breitenKnopfGedrueckt`, `breiteAufSpaltenVerteilen`, `resetColumnWidths`,
  `onSpaltenbreiteGemeldet`, `TableKomponenten.verteilen` und `zuBreite`. **Nicht** entfernt: `getTabellenBreite`,
  `spaltenSumme`, `widthStyle`, `breite` — die Summen-basierte Tabellenbreite bleibt (gebunden u.a. von
  `useradmin.xhtml` und guild). Breaking für Aufrufer der gelöschten Methoden (guild nutzt nur
  `resetColumnWidths` in einem Test; Inventar in eigenen Tests — Bumps passen mit an). Daten bleiben kompatibel:
  alte Stände mit den Feldern überliest die Ablage dank `ignoreUnknown` auf allen drei Typen (Test mit echten
  Alt-Feldern auf Stand-, Profil- und FesterStand-Ebene).

### Changed
- **pt:tableSettings: Modus wird nie gespeichert, Verbreitern-Griff haengt immer** (Karte 2160, Daniel 15.11.).
  Der Dreifach-Modus-Knopf startet je Anmeldung neutral mit «Anpassen» und rotiert nur noch sitzungsintern —
  `TableState.moveEnabled`/`resizeEnabled` sind entfernt (alte Stände/Profile mit den Feldern laden weiter über
  `ignoreUnknown`). `isGroesseAendernAktiv()` liefert jetzt `mitBreiten`: das Resize-Viereck im Spaltenkopf ist auf
  jeder Seite mit Breiten dauerhaft sichtbar (Daniel: «wird aktuell gar nicht angezeigt — fixen») und das Ziehen
  jederzeit möglich; nur das Verschieben der Köpfe hängt noch am Modus «Verschieben». Im Panel steht der Modus-Knopf
  zuoberst über die volle Breite, mit einem Trennstrich vor der Profilzeile, und `modusWechseln` schreibt nichts
  in den Stand (Playwright prüft den Neustart mit «Anpassen»).

### Fixed
- **pt:tableSettings: Wrap-Binding request-gebunden (JSF1104, session-scoped Beans)** (Karte 2097). `TableSettings`
  kept the wrap component bound via `binding="#{anzeige.reihenfolgeZiel}"` in a field; in a session-scoped backing
  bean the getter returned the component of the PREVIOUS request and JSF re-attached it with its children (JSF1104
  in development, «create profile» only on the second click, buttons/grips missing after a mode switch). The
  component now lives in `FacesContext.getAttributes()` under a per-instance key; without a FacesContext the getter
  returns `null`. Tag unchanged.

### Changed
- **`pt:tableSettings` Runde 7** (Karte 1853): the «Breiten» tab is gone (widths are dragged in Resize mode;
  the click-to-target-width on the header square went with it). New in the View tab: «save current layout» keeps
  widths, total width and column order (`TableState.festerStand`, also stored in the profile); a double-click on
  the mode button restores exactly that layout. Mode label «Adjust»/«Anpassen» (outline in that mode, filled in
  Reorder/Resize), real icons for Reorder/Resize, and header grips per mode again - `table-settings.js` looked
  for the mode row inside the wrap, where it has not been since Runde 5. Script and stylesheet now load even
  without `mitBreiten`. The wrap attribute `data-pt-remote` is replaced by `data-pt-breiten`.
- **`ch.plaintext.boot.table` lives only in `plaintext-root-tablesettings`** (Karte 2086). Until now the package was
  compiled twice (into `plaintext-root-common` and into the standalone `plaintext-root-tablesettings`, which also
  picked up `FacesMessages` and `StabileApi` through the source path). `plaintext-root-common` now depends on
  `plaintext-root-tablesettings`; class names and packages are unchanged. The arch rule `modulgrenze` counts
  `plaintext-root-tablesettings` as a base module like root-common. **Consumers with `root-common-api` allowlist
  entries pointing at `ch.plaintext.boot.table.*`** (guild: 9 lines) must delete them on the bump - the rule no
  longer sees those edges as root-common, and the orphan check fails otherwise.
- **The shipped table store is an AutoConfiguration** (Karte 2086): `UserPreferenceTableStateStore` moved to
  `ch.plaintext.boot.tablestore` and is registered by `TableStateStoreAutoConfiguration` only when the app has no
  `TableStateStore` bean of its own (`@ConditionalOnMissingBean`) - an own bean replaces it without property,
  exclude or `@Primary`. User and tenant come from the new `TableStateOwner` bean (backed by `PlaintextSecurity`).

### Added
- **`TableSettings` covers what the inventory app needs** (Karte 2086): `PagingVorgabe` (paginator top/bottom/
  both/off as page default), `displayPriority`/`prioAmEnde`, `pageValue`/`pageFlag`/`setPageValue` (stored in
  `TableState.pageState` and in profiles), `setSpaltenBedingung` (`SpalteGerendert`), `setNachProfilwechsel`
  (`NachProfilwechsel`), `neuAufsetzen`, `TableColumn.listenLabel`/`waehlbar`/`verschiebbar` with the factories
  `ausBlock`, `blockPlatzhalter`, `knopfSpalte`, and `TableState.expandedRows` (`expand`/`collapse`/`isExpanded`).
  All additive; existing constructors and pages behave as before.

## [3.0.0] - 2026-11-08

### Changed
- **Version cut to 3.0.0 with the end of v1** (Karte 1621, Daniel's decision). `plaintext-root`
  (v1) is archived read-only; this repository is now the one and only root development line.
  Releases continue as `3.x` under groupId `ch.plaintext.v2` (GitHub Packages + Reposilite
  `ch/plaintext/v2/` + the `plaintext-mvn` git mirror). The version arithmetic in
  `scripts/release.sh` releases the POM version exactly when its major is ahead of the highest
  `v2-` tag (the cut), otherwise highest-tag-plus-one-minor; after every release the next
  SNAPSHOT version is committed (`[skip ci]`).
- **The v1→v2 sync machinery is dismantled** (Karte 1621): `v2/sync.sh`, `v2/transformation.sh`,
  `v2/umbenennen.sh`, `v2/transformation.patch`, the nightly sync schedule and the
  `v2-abgleich-agent` cron are gone. `release.sh`, `purge.py` and the loader probe live in
  `scripts/` now and are maintained directly. Renovate is re-enabled; the `koordinaten` CI step
  guards the reactor against stray `ch.plaintext` groupIds. Details: `docs/v2/README.md`.

### Removed
- **`PlaintextSecWrapper` and `PlaintextAuthentication` (`plaintext-root-common`) are gone** (Karte 1615). root
  never had an implementation of `PlaintextAuthentication`, so the wrapper returned `null` from `getUser()` (the
  fwtool adfliste NPE, Karte 1614) and `true` from `hasRole()` for every caller. **Breaking** for code that still
  references either type; the only consumer, plaintext-fwtool, moved off it in fwtool#131. Use
  `PlaintextSecurityHolder` (mandate, user, authentication) or an injected `PlaintextSecurity` (`ifGranted`) instead.
  No new arch rule: the existing `root-common-api` rule already forbids non-`@StabileApi` types for other repos.
- **The "Laufzeit-Module (Konfig im PV)" section of `module.html` is gone** (Karte 1612, from Karte 1515), with
  `LaufzeitModuleBackingBean` and `LaufzeitModuleService`. It wrote `module/module.yaml`, which the `huelle/start.sh`
  of app, guild, schuetu and iot rewrites from the app jar on every start, and which the loader ignores once
  `module/auswahl.yaml` exists (demo); the audit log of all four PROD instances has no `LAUFZEIT_MODULE_*` entry.
  Its replacement is `modulauswahl.html` (Karte 1608, behind `plaintext.module.auswahl=true`). The rest of
  `module.html` (switching modules, interfaces, clearing data) and the loader's `module.yaml` path are unchanged.
- **`modulauswahl.html` moved to the Root menu and is ROOT-only** (Karte 1612, Daniel). It was under Admin with a
  read-only view for ADMIN; now menu `parent = "Root"`, `roles = {"ROOT"}`, and `ModulAuswahlService.ansicht()` refuses
  ADMIN. The saved version of a module stays selectable when no registry offers it (before, the page preselected the
  first option, so "Auswahl speichern" during a registry outage silently switched to `latest`).

### Changed
- **`plaintext-admin-requirements` no longer ships `AutoConfiguration.imports`** (Karte 1520). The module is not
  part of `plaintext-root-webapp`; in the shell it is loaded by the module loader, which refuses any module jar
  with an imports file ("Module haengen sich nicht in den Spring-Start"), so plaintext-app could not move to the
  shell. `AnforderungenModuleConfiguration` stays (still `@AutoConfiguration`) and is picked up by the
  `ch.plaintext` component scan of the shell and of plaintext-app; an application that does not scan
  `ch.plaintext` enables it with `@ImportAutoConfiguration`. Same pattern as plaintext-app#1136 (drawio,
  praesentation). No consumer other than plaintext-app declares the module.
- **`scripts/release-vollstaendig.sh` counts again in CI and fails closed** (Karte 1603). The Woodpecker step
  `vollstaendigkeit` had never counted a file in v1 or v2: `printf | grep -q '</parent>'` under `pipefail` lost to
  SIGPIPE in the CI image (200 of 200 runs, 0 of 200 locally), the root artifactId fell back to
  `spring-boot-starter-parent`, the probe got 404 and the script exited 0 with "kein Befund". The check is now a bash
  pattern match (as in `release-nachliefern.sh`), and "not assessable" (probe not 200, unclear answers, no version,
  GitHub requested but not countable) is exit 3 instead of 0; `--letzte` counts it as a failure. Self-test cases 5-9.
- **`scripts/release-vollstaendig.sh` keeps `GITHUB_TOKEN` off the command line** (Karte 1602). With
  `PRUEFE_GITHUB=1` the Authorization header reaches curl only through a `--config` file in `/dev/shm`
  (directory 0700, file 0600, shredded on exit) instead of `-H`, so it no longer shows up in `ps`. Output and exit
  codes are unchanged. New self-test `scripts/test-release-vollstaendig.sh` (mock server and curl).
- **`scripts/release-nachliefern.sh` works for plaintext-root-v2 too** (Karte 1596). groupId and path come from the
  tagged `pom.xml` instead of a fixed `ch/plaintext`, `TAG_PRAEFIX` selects prefixed tags (`v2-<version>`), and the
  rounds "measure, upload, build" plus the check against both repositories moved from `./build nachliefern` into
  `release-nachliefern.sh --runden` (no behaviour change for root). Tokens no longer appear in any curl command
  line (`ps`): they are passed via `--config` files in `/dev/shm` that are shredded on exit. New self-test
  `scripts/test-release-nachliefern.sh` (mock server, curl and mvn).
- **Release upload hardened against hung and aborted uploads** (Karte 1563). Runs 634 and 641 (22 October 2026)
  each stalled 46 minutes on one `PUT` to GitHub Packages and died with `Read timed out`, leaving 1.789.0 and
  1.790.0 half published in both repositories and `master` without the next SNAPSHOT commit. The release step
  now sets `aether.transport.http.requestTimeout=300000` and explicit `retryHandler.count=3`/`interval=5000`
  (Resolver 2.0.24 names, as shipped with Maven 3.10.0; the 1.9.x `aether.connector.*` keys are no longer read),
  and a red `./build release` is followed by `./build nachliefern`: `scripts/release-nachliefern.sh` probes every
  file of every built module in both repositories and uploads only what is missing (a 409 on a file the probe did
  not see is an error, not swallowed), `mvn clean deploy -pl` builds the modules the reactor never reached,
  `release-vollstaendig.sh` verifies both repositories, then plaintext-mvn is mirrored and the SNAPSHOT commit is
  made as `do_release` would have. Neither `deployAtEnd` nor `retryFailedDeploymentCount` is used: both re-upload
  files that are already there and run into the same 409. The half releases 1.789.0 and 1.790.0 are not reused;
  1.791.0 went out complete on 23 October 2026 (run 645) still without this fix, so the first release carrying it
  is 1.792.0.

### Fixed
- **`plaintext-root-files`: link expiry on the same day as the store** (Karte 1559). `FilesService` computed the
  last valid day of a share link in `Europe/Zurich`, the `LinkTokenStore` checks it against the JVM default zone.
  Between midnight in Zurich and midnight UTC the two disagreed by a day, so `FilesServiceTest` failed in the
  release build of 1.786.0 (22:49 UTC) and left that release without `plaintext-root-files` and
  `plaintext-admin-requirements`. Both now count days in the JVM default zone, as Draw.io and Diashow links do.

### Added
- **`plaintext-root-archtests` run in a module repository** (Karte 1604). A repository of a few modules without a
  webapp (template `plaintext-modul-vorlage`) sets `-Dplaintext.archtests.modul-repo=true` in Surefire
  `<systemPropertyVariables>`. Then the lower bounds measured on the six app reactors become "at least one", the two
  empty-should ArchUnit rules (raw `@Scheduled`, cron prototype) accept an empty set, and the four classes that need
  a whole application (`MethodSecurityEnabledTest`, `SecurityAllowlistGuardTest`, `PageGuardZuordnungTest`,
  `PlaintextRootTemplateDriftTest`, now `@Tag("nur-app")`) skip themselves with a reason. Measured in the template:
  before 22 of 35 classes red, after 0 red, 4 skipped. Without the switch nothing changes; in a reactor that ships a
  `src/main/resources/application.yml` the switch is red instead of active, so an app cannot lower its own bounds.
  `McpToolParamVertragTest` no longer asks for `@McpToolParam` when no tool has a parameter at all.
- **UI 2 foundation** (Karte 1548, the five UI principles decided on 15.10.2026). All additive; existing pages look
  the same after the bump. New file `plaintext-layout/css/layout.css`, loaded by the template after
  `layout-light/dark.css`: tokens for four colour roles (`--pt-flaeche*`, `--pt-text*`, `--pt-akzent*`,
  `--pt-status-{ok,warnung,fehler,info}*`), four spacing steps (`--pt-abstand-1..4`), `--pt-radius` and one font
  in three sizes (`--pt-schrift-{klein,normal,gross}`); dark as a second variable set in the same file. New tags:
  `pt:seitenkopf` (title left, one main action right, rare actions in a «⋯» menu), `pt:tabelle` (= `pt:table`
  with paginator only from page 2, German empty state with a way out via `leerText`/`leerWeg`, rows as cards
  below 640px; columns the card does not need get `colClass="pt-mobil-aus"`), `pt:chip` (status as a quiet chip)
  and `pt:filter` (filters behind a button on the phone, behaviour in `js/ui-grundlage.js`). `pt:table` gets the
  optional attributes `paginatorAlwaysVisible`, `leerText`, `leerWeg`, `leerIcon` (unset = unchanged).
  Living reference: `/styleguide.html` (Admin → Styleguide, ADMIN/ROOT). Screenshots in `docs/ui/1548/`.
- **`PlaintextUiGrundlageLinterTest`** in `plaintext-root-archtests` (Karte 1548): XHTML without `style="`,
  without its own `p:growl`, every `h:form` with `_csrf`; the existing state is frozen per file in
  `plaintext-ui-bestand.txt` at the reactor root and may only shrink. Enforced in root; in consumers it only
  reports (default `report`) until they write their own baseline with
  `-Dplaintext.arch.ui-grundlage=bestand-schreiben` and set `enforce`.
- **Personal file store `plaintext-root-files`** (Karte 1559, ported from the Inventar): every user uploads, lists,
  downloads and deletes their own files under `files/<user>/<name>`; a file lives `plaintext.files.frist` (default
  21 days) after its last change, uploading the same name again replaces it and restarts the period. Share links
  without login at `GET /nosec/files/{token}` (address as in the Inventar) through the `LinkTokenStore` (art
  `files`), with expiry, purpose and call counter; they die with the file. Page `files/ablage.html` (menu
  `moduleId = files`), REST `/files/api/*` in the format of the Draw.io store widget, MCP tools `list_files`,
  `get_file`, `save_file`, `create_file_link` (optional identity question `plaintext.files.mcp-identitaet`). New port
  `ch.plaintext.files.DateiSpeicher` in `plaintext-root-interfaces` (`upload`, `download`, `exists`, `list`,
  `delete`, flag `raeumtSelbstAuf`); default file system under `plaintext.files.verzeichnis`, replaced by any own
  bean. Without `raeumtSelbstAuf` the cron `FilesAufraeumen` deletes expired files. **Not** in the
  `plaintext-root-webapp` aggregate: an application adds the dependency, and on that bump raises
  `mindestens.werkzeuge` in its `mcp-scope-vertrag.properties` by 4. New module, additive.
- **One token-link store for Draw.io, slide shows and Files** (Karte 1559): port `ch.plaintext.linktoken.LinkTokenStore`
  in `plaintext-root-interfaces` (`anlegen`, `finde`, `liste`, `widerrufe`, `widerrufeFuer`, `zieheUm`, `aufgerufen`)
  with `LinkToken`, `LinkTokenAntrag`, `LinkRecht` (`lesen`/`schreiben`). Default `JpaLinkTokenStore` in
  `plaintext-root-freigabe`, registered `@ConditionalOnMissingBean`, so an application with its own store (Inventar:
  config list) replaces it with one bean. It shares the table `freigabe_link` (token only as SHA-256, same expiry and
  call counter): Flyway `V1792612327` makes `objekt_id` optional and adds `schluessel` and `besitzer`.
  `FreigabeLinkService` ignores rows with a `schluessel`, so a store token never opens a `FreigabeQuelle`. Additive;
  consumers only run the migration.
- **Claude API for every module** (Karte 1560): contract `ch.plaintext.claude.ClaudeClient` in
  `plaintext-root-interfaces` (`frage(ClaudeAnfrage)`, `frage(quelle, prompt)`, `isKonfiguriert()`, errors as
  `ClaudeException` with `Art` and `retryAfter`), implemented by the new module `plaintext-admin-claude` (comes with
  `plaintext-root-webapp`, can be opted out). JDK `HttpClient`, no SDK; retries 429/529 twice. Properties
  `plaintext.claude.api-key` (e.g. `vault:anthropic.api-key`), `admin-key` (optional, for costs), `default-model`
  (`claude-sonnet-5-5`), `base-url`. Without a key the bean exists and reports "not configured". Consumption per
  day, calling module and model in the new table `claude_nutzung` (no tenant: the key is per installation). ROOT
  page *Root → Claude API* (`claude-api.html`, in `ROOT_ONLY_PAGES`): connection check, models, organisation,
  rate-limit headroom, own consumption, test question, costs via the Admin API cost report. The remaining credit
  cannot be read through the API — the page says so.
- **Share links from module code** (Karte 1498): contract `ch.plaintext.freigabe.FreigabeLinks` in
  `plaintext-root-interfaces` (`erzeugeAdresse` with days, `erzeugeAdresseBis` with a fixed last day set by the
  module, `widerrufeAlle`), implemented by `FreigabeLinkService` with the same tenant and `darfFreigeben` checks.
  Modules no longer need a compile dependency on `plaintext-root-freigabe` (app: drop the allowlist exception for
  `WikiFreigabeLinks`). Additive, nothing breaks.
- **`MailSender` is a contract of the shell** (Karte 1504): `ch.plaintext.mailversand.MailSender`, `MailAccountRef`,
  `MailAttachment` in `plaintext-root-interfaces`, `AutomatischerVersand` in `plaintext-root-common`. New package on
  purpose: app still has `ch.plaintext.mail.*` and switches in its own PR. Helper code from app-interfaces now in
  `plaintext-root-common` under the same names (`@StabileApi`): `Auswertungszeitraum`, `AbstractHoehenService`,
  `ILatLon`, `McpLiterale`, `AbstractGcmPasswordCipher`, `Telefonnummern` — app deletes its copies on the bump.
- **Share links without login, for every module** (Karte 1476): new module `plaintext-root-freigabe` (comes with
  `plaintext-root-webapp`). A module registers a `FreigabeQuelle` (`plaintext-root-interfaces`, package
  `ch.plaintext.freigabe`: `typ`, `darfFreigeben`, `zeige`, optional `schreibe`); root handles the token (24 bytes
  `SecureRandom`, only the SHA-256 is stored), right `r` or `rw`, expiry, revocation, call counter and an optional
  part (stored with the link, not in the address). `GET /nosec/freigabe/{token}` only renders, `POST` only with an
  `rw` link (`r` → 403); unknown, expired and revoked all answer 404. The module runs with an empty
  SecurityContext, so the token is the only credential (no CSRF surface under `/nosec`). Default CSP
  `FreigabeInhalt.STRENG` with `sandbox`, `no-store`, `no-referrer`, `noindex`. Management through the tag
  `pt:freigabeLinks typ objektId teil`, the page *Admin → Freigabe-Links* and the MCP tools
  `erstelle_freigabe_link`, `list_freigabe_links`, `widerrufe_freigabe_link` (consumers: raise
  `mindestens.werkzeuge` in `mcp-scope-vertrag.properties` by 3 on the bump). Flyway `V1791641837` (table
  `freigabe_link`). Without a registered `FreigabeQuelle` every address answers 404.
- **Folder management in `DateiAblage` and `pt:dateiAblage`** (Karte 1475). The interface gets `legeOrdnerAn`,
  `verschiebe` (move or rename a file or folder; target must not exist or lie inside the source) and
  `loescheOrdner(pfad, rekursiv)` (non-empty without `rekursiv` → `DirectoryNotEmptyException`). Nextcloud uses
  MKCOL/MOVE (`Overwrite: F`)/DELETE; Git makes one commit per operation and keeps an otherwise empty folder with a
  hidden `.gitkeep` (moving or deleting the last file of a folder without it removes the folder, Git has no empty
  folders). **Breaking for own implementations of `DateiAblage`** (none outside root). `AblageAuswahl` offers
  `legeOrdnerAn`, `verschiebe`, `loesche` with server-side write check; the tag shows them only with
  `ordnerVerwaltung="true"` (default off) and write right; a non-empty folder is deleted only with the
  explicit tick «Mit gesamtem Inhalt löschen».
- `AblageAuswahl` and `AblageEinsatz` are now `@StabileApi` (Karte 1475): the draw.io module in app uses both;
  the constructor `AblageAuswahl(AblageEinsatz)` is unchanged, folder management stays off unless the tag enables it.
- **`pt:dateiAblage`, a reusable UI for the file stores** (Karte 1440), tag file in `plaintext-root-template`,
  Java side `ch.plaintext.boot.ablage.AblageAuswahl` in `plaintext-root-common`: choose a store (only those the
  user may read), browse folders below a fixed root, open, download, upload and save. Each place of use passes an
  `AblageEinsatz`: stores with read and write roles (`*` = every configured store), root folder, allowed file
  types (required as soon as writing is allowed) and a size limit. Rights, names, types and size are checked
  server side on every access; names are single segments, so nothing leaves the root; downloads are always
  `attachment` with `application/octet-stream`. Try it under Root → Ablage ausprobieren (ROOT only, folder
  `ablage-demo/<mandat>`).
- **`@ModulApi` and `@ModulApiUmsetzung`** (Karte 1422, Daniel 04.10.2026), in
  `plaintext-root-interfaces` (`ch.plaintext.modules`). `@ModulApi(art = SCHNITTSTELLE | DTO,
  stabilitaet = STABIL | NEU | VERALTET, seit, ersatz)` marks a contract other modules may use; a DTO's
  name must start with `I`. `@ModulApiUmsetzung(beschreibung, seiteneffekte = KEINE | INTERN | AUSSEN,
  hinweise, beispiele)` describes how an implementation behaves, on the class and optionally on methods.
- **The interface catalog runs in every module.** The parent now puts `plaintext-root-katalog` in front
  of Lombok in `annotationProcessorPaths` and passes `-Aplaintext.katalog.modul=${project.artifactId}`.
  Outside `*-interfaces` it records only `@ModulApi` interfaces and `@ModulApiUmsetzung` descriptions and
  writes no file when there are none; in `*-interfaces` it still records every public interface.
  `SchnittstellenKatalog` and the MCP tools show art, stability, origin and the implementation's
  description next to each implementer. **Consumer impact:** a module that declares its own
  `annotationProcessorPaths` must use `combine.self="override"` with the catalog before Lombok, or it
  loses the catalog (Maven merges these lists by position). Catalogs written by older processors stay
  readable. Test sources get no catalog (`default-testCompile` without the module option).
- **Module page shows the interfaces** (Karte 1422): search, filter by kind, expandable rows with
  methods, "Genutzt von" (required/optional) and the implementers with their `@ModulApiUmsetzung`.
- **MCP `analysiere_module` and `bauplan`** (Karte 1422): who provides, implements and uses which
  interface in the running version (from the beans' injection points), what breaks when a module is
  left out, and for a question the matching interfaces, MCP tools, sidecar capabilities and gaps.
  `get_modul_schnittstelle` adds `genutztVon`. ADMIN/ROOT only, like the existing catalog tools.
- **`PlaintextModulApiVertragTest`** (shared rule, Karte 1422): a DTO interface is named `I…`, every
  implementation of a `SCHNITTSTELLE` carries `@ModulApiUmsetzung`, and its texts contain no secrets
  or internal addresses. Records may be DTOs and keep their name; `ERWEITERUNG` marks framework
  extension points whose implementations need no description.
- **root's own contracts are marked** (Karte 1422, Etappe B): in `plaintext-root-interfaces` 21 services
  carry `@ModulApi(art = SCHNITTSTELLE)`, 8 extension points `ERWEITERUNG` (`PlaintextCron`,
  `ModuleDescriptor`, `DashboardTileDataProvider`, `SearchProvider`, `DeepLinkTarget`,
  `MenuVisibilityProvider`, `PlaintextBusSubscriber`, `StoreBacked`) and 2 DTOs; the 19 root
  implementations carry `@ModulApiUmsetzung`. `MenuItem`, `TileItem` and `SearchHit` stay unmarked
  until they are renamed with their consumers. **Consumer impact:** a consumer class that implements one
  of the 21 services (e.g. `PlaintextSecurity`, `SystemMailSender`, `DateiAblage`) fails
  `PlaintextModulApiVertragTest` until it carries `@ModulApiUmsetzung` — add it with the bump.

### Changed
- **PrimeFaces 15.0.18 → 16.0.1** (Karte 1511, analysis Karte 1507; first cut of the family). **Breaking for
  consumers** — on the bump:
  1. **Drop `<classifier>jakarta</classifier>`** from every `org.primefaces:primefaces` (and
     `primefaces-extensions`) dependency. PF 16 is Jakarta-only and publishes no `-jakarta` jar; a leftover
     classifier fails the build with `Could not find artifact org.primefaces:primefaces:jar:jakarta:16.0.1`.
     The root parent now manages `primefaces`, `primefaces-extensions` and `primefaces-themes` without classifier
     and excludes the `jakarta` jars that JoinFaces 6.1.x's `primefaces-spring-boot-starter` (6.1.2: BOM on PF 15.0.19) still pulls. A
     consumer that declares the starter **with its own `<exclusions>`** does not inherit those exclusions (Maven
     applies managed exclusions only when a dependency has none) and must repeat the two
     (`org.primefaces:primefaces`, `org.primefaces.extensions:primefaces-extensions`). A consumer page using
     `pe:` needs an explicit `primefaces-extensions` dependency unless it gets it through
     `plaintext-admin-apitoken`.
  2. **Replace `p:separator` with `p:divider`** — the tag is gone in PF 16 and Facelets fails the page. A
     vertical separator in a toolbar becomes `<p:divider layout="vertical"/>`.
  3. **Dialog CSS on `.ui-dialog-box`**: PF 16 renders `.ui-dialog` as the full-viewport scroll layer and
     `.ui-dialog-box` as the visible box. Own CSS that sets width, height, shadow or flex on `.ui-dialog` must
     move to `.ui-dialog .ui-dialog-box`. Root's `mobile-responsive.css` and the `primefaces-plaintext` theme
     are converted (dialog rules taken over from saga-blue 16.0.1); the `[aria-hidden="false"]` display
     workaround is gone.
  4. `p:selectOneButton` renders its text as `label.ui-button-text` instead of `span`; selectors on
     `span.ui-button-text` need adjusting (none in root).
  5. `p:accordionPanel activeIndex` is deprecated in PF 16, use `active` (none in root).
  6. **The EL functions `p:ifGranted`, `p:ifAllGranted`, `p:ifAnyGranted`, `p:ifNoneGranted`, `p:remoteUser`,
     `p:userPrincipal` are gone** — a page using one fails with `Function [p:ifGranted] not found` (HTTP 500).
     Root's `topbar.xhtml` now calls `facesContext.externalContext.isUserInRole('ROLE_…')`, which is what
     `p:ifGranted` did internally.
  7. Attributes removed in PF 16 (none used in root; grep in consumers): `p:dataExporter exporter`,
     `p:autoComplete effect/effectDuration`, `p:button onchange/readonly`, `p:calendar showLongMonthNames`,
     `p:dataTable initMode`, `p:fileUpload validateContentType/virusScan`, `p:graphicImage url`,
     `collectionType` on `p:selectCheckboxMenu`/`p:selectManyCheckbox`/`p:selectManyMenu`,
     `p:selectOneMenu inputmode`, `p:timeline timeChangeable`, `p:tree sourceFilterPlaceholder`,
     `p:treeTable touchable`, `p:chip removeIconClass`, `p:confirmPopup showEffect/hideEffect`.
  Renovate keeps blocking PrimeFaces majors until the last application is on 16; that rule goes with the last
  app PR.
- **Three colour variants instead of ten** (Karte 1548): green (default for new users), blue (fallback for unknown
  names) and orange (the warm side). Users with a removed variant get the nearest one by hue — turquoise/purple →
  blue, avocado/lime → green, red/crimson/yellow → orange — written back to their preferences on the next load
  (`ThemeColorProvider.VARIANTEN`/`RUECKFALL`, also for the colour cookie, the REST endpoint and the login page).
  Custom colours stay. Visible for those users: their accent colour changes once.
- `pt:colAmount` cells carry `.pt-zahl` (`font-variant-numeric: tabular-nums`) — digits equally wide, alignment
  unchanged.
- **Contracts live with their provider** (Karte 1504, decisions in Karte 1502). New ArchUnit rule `vertragsort`
  (`PlaintextVertragsortTest`): an interface implemented only in another module and not used in its own belongs in
  that provider's `<modul>-api` jar; root's shell (`plaintext-root-interfaces` + platform layer) is exempt.
  `modulgrenze` no longer frees every `-interfaces` module: allowed targets are `-api` modules, root-interfaces and
  the frozen `plaintext-app-interfaces`. **Consumers on the bump:** freeze the current `vertragsort` hits in
  `plaintext-arch-allowlist.txt` (the failure message prints the lines).
- **Removed dead interfaces** `TileRegistry` (with `TileItem`), `apitoken.IApiErrorResponse`,
  `sessions.ISessionAuditService` — no consumer in any repository; `TileRegistryImpl` is gone with them.
- **All watch pages of root on the mobile framework** (Karte 1387, Daniel 01.10.2026: «bitte alles
  umstellen»). `WatchHomePage` (tiles from `WatchWidget`) and `WatchTestPage` (page switches plus a
  gallery of the building blocks) implement `MobilWatchPage`; `home.xhtml`, `elemente.xhtml`,
  `WatchHomeBean`, `WatchUebersichtBean` and `WatchElementeBean` are gone. New blocks in
  `MobilSeite`, each once: `Kacheln`, `Aktion` (large button with a running time that keeps counting
  in the browser and small side buttons), `Schalter`, chosen state of a `Knopf`, `Eintrag` with
  leading column, line below and editable fields (`Aenderung`/`Feld`, saved on change, handed to
  `MobilWatchPage.handle(aktion, wert, felder)` as `f-<name>` only). `MobilAltadressenFilter`
  redirects the old `/watch/<id>.html|.xhtml` of every migrated page to `/watch/m/<id>`.
  `mobil.js` stays under 5 KB. **Consumer impact:** the root pages move to `/watch/m/home` and
  `/watch/m/elemente` (old addresses redirect); the page-guard aliases `watch/home` and
  `watch/elemente` are removed. The Facelet frame (`frame.xhtml`, `WatchFrameBean`, `w:aktion`)
  stays for modules that still ship a Facelet watch page (guild with an older
  `plaintext-z-kalenderhost`). A module page without editable fields needs no change.
- **Watch tiles at phone width** (Karte 1387): three tiles per row only from 481 px; below, two.
  Measured at 390 px, three columns of ~105 px could not hold a four-character figure at 2.8rem
  and the home screen scrolled sideways (row 382 px in a 332 px card). Same rule as the Facelet
  home had; the font size is unchanged.

### Added
- **Mobile framework for watch/phone pages** (Karte 1355, prototype). A module describes a page
  (`MobilWatchPage.beschreibe()`: figure, buttons, list, note) and handles its actions
  (`handle(aktion, wert)`); `MobilSeitenController` renders plain HTML under `/watch/m/{id}` with
  `watch.css` and one script (`mobil.js`, under 5 KB), and runs actions as `fetch` without a page
  reload (no-JavaScript fallback: POST/303). Same registry, rotation, visibility, phone-link
  confinement and CSRF as the Facelet pages; no inline script. `WatchPageRegistry.uebersicht()`
  now holds the overview rule that `WatchFrameBean` had. **Consumer impact:** none until a page
  implements `MobilWatchPage`.

### Security
- **Security audit part B hardenings** (Karte 1360, 30 September 2026).
  - Requirements module: every by-id path is tenant-scoped (`anforderungdetail`/`claudesummary`/
    `howtodetail` `?id=`, REST `/nosec/api/claude/howtos`, `/anforderung/{id}/howtos`, `ack`,
    `ack/{nr}/status`, `summary`, howto and constraint-template lookups in the prompt context). A
    foreign id reads as "not found".
  - MCP `set_secret` with backend `VAULTWARDEN` and `set_secret_backend` now require the role
    **ROOT** (the vault belongs to the whole instance; the HashiCorp URL is called from the
    container). LOCAL_DB/HASHICORP secrets stay available to ADMIN. **Consumer impact:** none for
    instances whose admins also hold `root`; an ADMIN-only token gets `FEHLER: … erfordert die Rolle ROOT`.
  - Faces pages: `plaintext-defaults.yml` sets `joinfaces.primefaces.csp-policy` to
    `base-uri 'self'; object-src 'none'; frame-ancestors 'self'; script-src 'self'` (PrimeFaces
    appends the nonce). Deliberately without `default-src`/`form-action` — see the comment there.
  - Claude summary: rendered markdown goes through DOMPurify 3.4.16 (local) before `innerHTML`;
    without DOMPurify the page shows plain text.
  - `plaintext.mcp.bearer-filter.validation` defaults to `DATABASE` (was `JWT`). API JWTs carry
    `iss` (`plaintext.jwt.issuer`, else `plaintext.baseurl`); a token with a foreign `iss` is
    rejected, tokens without `iss` stay valid.
  - Removed the unused `CardDavHttpFirewall` (a `DefaultHttpFirewall`), the entity editor no longer
    logs field values at DEBUG, ADR 0002 corrected (there is no Hibernate tenant filter).

### Fixed
- **User preferences belonged to `anonymousUser` instead of the person** (Karte 1336, 29 September
  2026). `UserPreferencesBackingBean` is session-scoped and was created on the login page already
  (`#{i18n.t(...)}` asks it for the language), i.e. for `anonymousUser`; the session survives the
  login (`ChangeSessionIdAuthenticationStrategy`), so every user who came through the login page
  wrote theme, language and table states into the one shared record `anonymousUser`. The bean
  now checks the signed-in name on every access and loads that person's record when it changes;
  without a sign-in it keeps an in-memory state that is never saved. Found by the new
  `TabellenstandZweiBenutzerPlaywrightIT` (two users, useradmin column selection).
  **Consumer impact:** settings stored so far under `anonymousUser` are no longer read; every
  user starts from their own record (or the defaults). The old record stays in the table.

### Added
- **Start page edit mode** (Karte 1351). A button "Anpassen" on `index.xhtml` opens an edit mode:
  hide and show tiles, order them by drag and drop (HTML5, in `js/dashboard.js`, no inline
  handlers) or with arrow buttons (touch, keyboard), switch each tile between half and full row,
  "Speichern", "Abbrechen", "Standard wiederherstellen". Stored per person and tenant in the new
  `UserPreference.startseiten` (JSON, same store as the table states — no migration).
  Rights are unchanged: the arrangement only orders what `TileItemImpl.isOn()` permits; a stored
  or submitted id without the right is dropped. Hidden tiles are not enriched
  (`DashboardTileModelBuilder.buildTiles(Predicate)`). New `DashboardTileData.hidden/halfWidth`.
  **Consumer impact:** the grid is now two columns (half tile = one column) instead of
  `auto-fill minmax(300px)`, i.e. two tiles per row by default instead of three or four; below
  768 px one per row. Every tile starts half-width and visible; new tiles are appended.
- **Table sorting stored per person** (Karte 1346). `TableState` and every profile carry a new
  field `sortBy` (list of `TableSort{column, field, descending}` in priority order; `null` = the
  page default, i.e. the behaviour so far). Multi-column sorting (`sortMode="multiple"`, the
  PrimeFaces 15 default) is kept with its priority. It takes effect when the page binds
  `sortBy="#{…anzeige.sortMeta}"` and `<p:ajax event="sort" listener="#{…anzeige.onSort}"/>` on
  its `p:dataTable` — like widths, visibility and paginator. Columns are mapped by header text
  (like resize), falling back to the sort field when it is itself a column key. Only entries whose
  column is visible and whose field the rendered table really has are handed to PrimeFaces, which
  otherwise throws `FacesException: No column with field …` (HTTP 500) on every page load.
  Applying a profile resets the table's sort map so the profile's sorting shows at once.
  `useradmin.xhtml` binds it (new `TabellenSortierungZweiBenutzerPlaywrightIT`).
  **Consumer impact:** none without the two bindings; older roots ignore the new field.

- **`pt:tableSettings`: paginator top/bottom, rows per page, one handle per column header**
  (Karte 1336). Two checkboxes switch the paginator above and below the table; "Zeilen pro Seite"
  offers 20 to 3000 (new: 250, 1000, 2000, 3000 plus the page default). Both are stored in
  `TableState` and in every profile (`paginatorTop`, `paginatorBottom`, `rowsPerPage`; `null` =
  previous behaviour). They take effect when the page binds `paginator`, `paginatorPosition`,
  `rows`, `rowsPerPageTemplate` and `<p:ajax event="page">` to the `TableSettings` object — the
  same way as widths and visibility; the page default comes from the new constructor
  `TableSettings(page, mitBreiten, vorgabeZeilen)`. In the column header the drag handle and the
  separate ↔ button are merged into one square: drag = free width, click = set width. Header
  cells get 32 px right padding, so title and filter field no longer touch the handle.

- **Seven guards lifted from the consumers into `plaintext-root-archtests`, plus a new module
  `plaintext-root-testsupport`** (Karte 1298, 23 September 2026). `QualityGateTest`,
  `WurzelVersionVertragTest`, `XhtmlWohlgeformtVertragTest`, `AjaxZielAufloesbarTest`,
  `MethodSecurityEnabledTest`, `McpToolParamVertragTest` and `PageGuardZuordnungTest` existed as
  3 to 6 copies across root/app/guild/schuetu/iot/fwtool and had measurably drifted. The shared
  version is the strongest copy in each case; per-repository lower bounds stay per repository
  (keyed on the reactor's root `artifactId`, `ReactorLayout.mindestensFuerDiesenReactor`).
  `@Tag("quality-gate")` on `QualityGateTest` is unchanged. `EmbeddedPg` and `Seitenliste` (five
  identical copies) move to `plaintext-root-testsupport` (package `ch.plaintext.testsupport`),
  which consumers pull in with `<scope>test</scope>` instead of `io.zonky.test:embedded-postgres`.
  **Consumer impact:** after bumping to this release, consumers delete their copies (otherwise the
  test runs twice); fwtool now gets `MethodSecurityEnabledTest` and needs `@EnableMethodSecurity`.
- **Drift guard widened from 3 to 35 files** (Karte 1088, 7 September 2026).
  `PlaintextTableSettingsDriftTest` is renamed to `PlaintextRootTemplateDriftTest` and now pins
  the SHA-256 of 35 of the 42 `plaintext-root-template` resource files that
  `plaintext-oblique-theme` carries as its own copy — not just the three `pt:tableSettings`
  files. A full comparison against `coreit-arch-inventar-design` (7 September 2026) found 17 of
  42 files had drifted since Karte 1077, all but the three known ones unnoticed; 14 were pure
  translation drift (fixed by taking root's file verbatim), one was a stale Java-class reference
  in a comment (fixed), and two were the already-known mobile-width rule missing from
  `table-settings.css`/`.js` (pulled back in). The other 7 shared paths carry a documented,
  permanent Oblique-specific addition on top of root's content and are deliberately NOT in this
  guard's list — see the class Javadoc. **Known limitation:** `coreit-arch-inventar-design` does
  not include `plaintext-root-archtests` (needs BIT-Nexus access for `ch.plaintext` coordinates,
  Karte 1089), so this guard does not run there automatically; the assertion message names the
  known consumer explicitly as a manual follow-up reminder, and that repo carries its own
  self-contained `ObliqueTemplateDriftTest` instead.
- **`pt:tableSettings` has its storage and is ready for consumers** (Karte 1077, 6 September
  2026). The building block from 1.657.0 shipped `TableStateStore` as an interface only — every
  app would have had to write its own storage, and none did. root now ships
  `UserPreferenceTableStateStore` (`plaintext-root-common`): the table state is stored per
  **user and tenant** (key `mandat + "/" + page`) as JSON in `UserPreference.tabellenStaende`,
  through the session bean that already owns the record — no entity, no repository, no Flyway.
  A page needs one injected `TableStateStore`, one `TableSettings` field and a `@PostConstruct`.
  The previous storage `UserPreference.tabellenSpalten` (column visibility only, no tenant) is
  read as a fallback and taken over on first save, so the `useradmin` selection of 25.08.2026
  survives; `MyUserBackingBean` now runs on the shared building block. New drift guard
  `PlaintextTableSettingsDriftTest` (runs in every consumer): every copy of
  `tableSettings.xhtml`, `table-settings.js` and `table-settings.css` — root's own or the one in
  `plaintext-oblique-theme` — must hash to the released root version. Below 768px the tag no
  longer applies stored column widths (visibility and profiles still apply).
  **Consumers:** the classes moved from `ch.plaintext.boot.web.table` (`plaintext-root-web`)
  to `ch.plaintext.boot.table` (`plaintext-root-common`) — no consumer imported them yet, and
  `plaintext-root-common` is on every module's class path already, so `pt:tableSettings` needs
  no new POM entry. `UserPreference`, `TableState` and `TableColumnProfile` carry
  `@JsonIgnoreProperties(ignoreUnknown = true)`: from this version on, a rollback no longer
  discards a user's whole preference record because of a field the older version does not know.

### Security
- **Reset and verification links no longer take their host from the request** (Karte 1068,
  5 September 2026). `SelfServiceController` used `request.getServerName()` as the fallback
  for `plaintext.selfservice.public-base-url`; behind the `ForwardedHeaderFilter` that is the
  value of `X-Forwarded-Host`, which the reverse proxy did not overwrite until Karte 1054 —
  a forged header made a genuine password-reset mail carry a genuine token that pointed to a
  foreign host. The base URL now comes from `EigeneAdresse` (setting `app.ownhost`, then
  `plaintext.app.ownhost`, then `plaintext.baseurl`) and is empty rather than request-derived
  when nothing is configured. `PlaintextAuthenticationSuccessHandler` no longer reads
  `X-Forwarded-Proto/-Host/-Port` for the login event either. New ArchUnit rule
  `PlaintextHostAbleitungTest` (runs in every consumer) forbids `getServerName()` and
  `ServletUriComponentsBuilder` outside a frozen list of legacy classes (Karte 1069 A-01).
  Consumers: set `plaintext.baseurl` (already the case in PROD) or `app.ownhost`.

### Removed
- **`ch.plaintext.upload.IUploadTarget` deleted** (Karte 1204, 13 September 2026). The contract
  and its `UploadResultTest` are gone from `plaintext-root-interfaces`; the successor has lived
  under **the same** fully qualified name in `plaintext-app-interfaces` since plaintext-app
  2.1792.0 (PR #745, merged 13 September 2026). This completes the expand/contract move announced
  under *Deprecated* in [1.643.0]. The mandatory order was kept: app first published its own
  contract, root deletes only now, and app pins the root version without this copy afterwards.
  Consumers change no import. Measured before the deletion: no module in root uses the type
  except the module's own test, and `plaintext-guild`, `plaintext-iot` and `plaintext-schuetu`
  do not mention it at all (fresh clones, 0 hits each; the same search finds 8 files in
  plaintext-app).

### Changed
- **Documentation overhauled and switched to English** (30 August 2026). All code
  comments and Javadoc across the 24 modules, the Woodpecker pipeline comments and
  every document are now English; identifiers, UI strings, `@DisplayName` values and
  log messages stay as they are, because four consuming repositories depend on them.
  The documentation list at the bottom of the [README](README.md#documentation) is
  the entry point, grouped by what you are trying to do.
- `docs/ARCHITECTURE.md` rewritten: six diagrams read off the `pom.xml` files
  instead of eight that named three modules which do not exist and left out eight
  that do.
- `docs/CI-UMSCHALTEN.md` (594 lines about a one-time migration) replaced by
  [docs/CI.md](docs/CI.md) — what actually runs, on Woodpecker.
- Corrected against the code: the Flyway timestamp is **Unix epoch seconds**, not
  seconds since 2000 (a number computed the old way would sort below every
  migration of the last year); the module role is the **second** menu filter, not
  the fourth; the TOTP migration is PostgreSQL, and `dev.samstevens.totp` is not a
  Spring library; `plaintext-root-webapp` bundles **22** internal modules, not 20.

### Added
- [docs/ci/WOODPECKER_SETUP.md](docs/ci/WOODPECKER_SETUP.md) — runbook for wiring a
  repository to `ci.plaintext.ch`, written to be executed by an AI agent: what to
  configure, how to verify it really ran, and the pitfalls that cost us a day
  (`${...}` in a YAML comment, a `.woodpecker/` subdirectory, the shared Maven
  build cache, a pipeline `error` producing no GitHub status).
- `docs/MENU_SYSTEM.md` — moved out of `plaintext-root-menu/README.md`, with the
  `.html` link invariant corrected in the examples.

### Removed
- GitHub Actions workflows (`ci-cd.yaml`, `playwright.yaml`, `housekeeping.yml`).
  The repository runs on Woodpecker; `.ci-engine` decides, and it says `woodpecker`.
- Nine documents and ten `INFO.md` files, about 2 700 lines that described classes
  which do not exist (`ConfigEncryptionService`, `XStreamSerializer`, `Chat*`),
  plans never carried out, and a test report written on a different machine.

### Added
- `docs/OPTIONAL_MODULES.md` and [ADR 0007](docs/adr/0007-admin-module-abwaehlbar-per-exclusions.md):
  an application can throw admin modules out of `plaintext-root-webapp` via Maven `<exclusions>`
  (status report of 29 August 2026, §3 „Aggregator ohne Opt-out"). Opting out is possible for
  `plaintext-admin-webhooks`, `-notifications`, `-secrets` and `-modules`; for every module the table
  states whether it can be dropped and, where it cannot, why. **Nothing changes for the existing applications** —
  the dependencies stay non-`optional`, the default stays "everything on". `<optional>true</optional>`
  would have silently taken modules away from all four applications at the next bump; a BOM of its
  own would be a second copy of the list that `plaintext-root-parent` already keeps; and
  `@ConditionalOnProperty` on the module auto-configurations would have no effect, because in root
  as in all four applications the beans hang off the
  `@ComponentScan("ch.plaintext")` and not off their auto-configuration.
- `SchlankerKontextTest` (plaintext-root-webapp) with the second Surefire execution
  `kontext-ohne-abwaehlbare-module`: it takes the place on the test classpath away from the four
  module jars via `classpathDependencyExcludes` — the absence is real, not simulated — and starts the
  Spring context against an embedded PostgreSQL. That proves the opting out and locks it down at the
  same time: whoever imports a class from one of these packages in the core, or hard-injects one of
  their beans, brings this run down instead of only the PROD start of a slim application.
- `AbwaehlbareModuleXhtmlTest` (plaintext-root-webapp): the context start renders no page, so a
  core XHTML with `#{webhookBean.x}` does not catch its eye. The test collects the bean names of
  the optional modules and requires, for every mention in an XHTML from
  `plaintext-root-webapp` or `plaintext-root-template`, a null guard in the same document (the
  notification bell in `includes/topbar.xhtml` has one; that is precisely why
  `plaintext-admin-notifications` can be opted out of).
- **Wave 4 (CSP without `'unsafe-inline'`) — groundwork.** `plaintext.security.csp.script-unsafe-inline`
  (default `true` = previous behaviour) controls whether `script-src` carries the token
  `'unsafe-inline'`. As long as it is in there, the browser executes EVERY `<script>` that stands in
  the document — an injected one as well; at this point the CSP is not XSS protection. The policy is
  now built in `PlaintextSecurityConfig.cspPolicy(boolean)`, `PlaintextCspPolicyTest` checks both
  positions and that no other directive changes in the process. **The switch is thrown per
  application and not in this PR** — precondition per application: `joinfaces.primefaces.csp=true`
  AND no more inline JavaScript of its own in the markup.
  **Finding for the switching step (measured on 30 August 2026 against the running root application,
  not derived):** with `joinfaces.primefaces.csp=true` PrimeFaces writes its own
  `Content-Security-Policy` header on EVERY Faces page and replaces the one built by Spring
  Security — what remains is `script-src 'self' 'nonce-…';`, so neither `default-src` nor
  `frame-ancestors`, `form-action`, `img-src` or `connect-src`. On non-Faces paths (REST, Actuator,
  static files) the Spring header stays in force. That affects app, guild, iot and schuetu **today
  already**, because `csp: true` has been set there for a long time. Whoever switches has to hand
  PrimeFaces the complete policy (`joinfaces.primefaces.csp-policy`), and there `script-src` must be
  the LAST directive — PrimeFaces appends the nonce to the end of the string and it otherwise ends up
  in the wrong directive (measured: `… form-action 'self' 'nonce-…'`).
- Shared test `PlaintextInlineJsVertragTest` (plaintext-root-archtests) with two rules:
  **(1)** `<script>`/`<h:outputScript>` WITH a body instead of an extracted `.js` file;
  **(2)** an `on…` attribute on a non-`p:` tag. `on…` on `p:` components stays unchallenged:
  `oncomplete`/`onstart`/`onerror` are PrimeFaces events of the Ajax lifecycle and not HTML
  attributes at all, and where PrimeFaces really does generate an HTML handler (`onclick` on
  `p:commandButton`), the CSP mode pulls it out itself. Exceptions: `<!-- inline-js-ok -->` on the
  same line or allowlist rule `inline-js`.
  **Two positions:** enforcing only with `-Dplaintext.arch.inline-js=enforce` — set that way in the
  Surefire block of `plaintext-root-webapp`. Without the property the test only reports on
  `System.err` and passes. That way a root release does not knock over a consumer build while app,
  guild, iot and schuetu are still clearing up their existing stock; whoever is through sets the same
  two lines in their webapp `pom.xml`.
- `InlineJsLinter` (plaintext-root-common): the scanner for it. Before the evaluation it masks out
  XML comments as well as the bodies of `<script>`/`<style>` — otherwise an
  `<img src=x onerror=…>` from an explanatory comment and a JS property such as
  `xhr.onreadystatechange = …` count as a violation.
- i18n seed `plaintext-root.csv`: **French and Italian** for all 287 keys, plus
  25 **menu titles** from the `@MenuAnnotation` classes (en/fr/it) — 312 keys × 3 target languages
  = 936 lines. So far only `en` was in the seed; whoever switched to FR or IT in the topbar read
  `X_Speichern`, because `I18nService.translate()` silently creates an `X_` placeholder for a
  missing language. `getAvailableLanguages()` already offered `de, en, fr, it` before, the content
  was missing. The menu titles already run through the i18n layer
  (`PrimefacesMenuItem.getValue()` / `PrimefacesSubmenu.getLabel()` call
  `I18nProvider.translate(title, lang)`) but had no pre-population — **no change to the
  menu classes needed**. Translated for a Swiss club and business application:
  „Mandant/Mandat" is the application's tenant term (fr `mandant`, it `mandante`, not
  `mandat` in the sense of an assignment). Proper names stay: `Flyway`, `Swagger`, `Cron`,
  `Howtos`, `Secrets`, `Magic-Link`, `Deep-Links`, `Root`, `Admin`, `OIDC`, `TOTP`.
- Test `I18nSeedSprachabdeckungTest` (plaintext-admin-i18n): for every seed key there is a
  line for every target language from `getAvailableLanguages()` without `de`; no `de` or foreign
  language codes; no `(key, language)` twice; every `@MenuAnnotation` title of the reactor
  is in the seed. Justified gaps go into `BEGRUENDETE_LUECKEN` (empty today). Complements
  `PlaintextI18nSeedTest`, which only checks the other axis (every `i18n.t('…')` has an
  `en` line), but not whether the same key is also present in fr/it.
- ADR-0008 `docs/adr/0008-vertragsbesitz-und-split-packages.md`: measurement of contract ownership in
  `plaintext-root-interfaces` and of the split packages of the whole family (status report of
  29 August 2026, §3 — wave 3). Result: 41 of the 42 contracts are framework and stay; the
  domain terms in the module appear exclusively in Javadoc examples, not in signatures. 29
  packages are filled by more than one module, most of them the intended pattern
  "contract in the interface module, implementation in the domain module"; they stay. Family-wide
  there is exactly one name collision (`ForwardedHeaderConfig`, three times identical in app-, guild-
  and iot-webapp), and it is inconsequential, because the three copies never share a classpath.
- i18n seed `plaintext-admin-i18n/src/main/resources/i18n/plaintext-root.csv`: 287 English
  pre-populations for every `i18n.t('…')` of the root facelets (status report of 29 August 2026, §4 —
  the seed importer had been running empty at every start, family-wide there was no seed CSV; all
  texts are derived from the German default text, PROD had only `X_` placeholders). The importer
  only creates missing entries or `X_` placeholders; texts maintained in the database survive
  every deploy (`I18nServiceSeedImportTest` proves it).
- Shared test `PlaintextI18nSeedTest` (plaintext-root-archtests): every `i18n.t('…')` in a
  facelet of the reactor needs a seed line for the lead language `en`; seed lines without use
  are reported as a warning. Runs in root and, via `dependenciesToScan`, in every consumer —
  seeds from jars (root) count as delivered there. Exceptions: `<!-- i18n-seed-ok -->` or
  allowlist rule `i18n-seed`.
- `I18nSeedLinter` (plaintext-root-common): shared CSV parser and facelet scanner for
  importer and test; resolves the protective apostrophe of the export, so that an export can be
  checked in unchanged as a seed.
- Shared test `PlaintextLayeringTest` (plaintext-root-archtests, status report of 29 August 2026,
  measure 12) with two rules: **L1** — a class in a `service`/`services` package does not hang off
  `jakarta.faces..` or `org.primefaces..` (allowlist rule `layering-jsf-in-service`);
  **L2** — a `*BackingBean` does not lie in a package with the segment `service`, `services`,
  `repository`, `repositories` or `jpa`, unless the same package also names the web layer
  (`web`, `view(s)`, `ui`, `gui`, `jsf`, `bean(s)`; allowlist rule `layering-backingbean-paket`).
  `jakarta.servlet..` is deliberately **not** in L1: the finding of the report is the JSF coupling,
  and a session registry or a servlet filter rightly needs the Servlet API. The web exception in L2
  prevents the module namespace (`ch.plaintext.jpa.web`) from counting against the class. Unlike the
  other ArchUnit rules this test imports only the `target/classes` of its own reactor — a consumer
  should judge its own code and not root classes from jars that it cannot change. root is green on
  both rules **without exception** and still keeps no `plaintext-arch-allowlist.txt`.
- Shared test `PlaintextGroessenLeitplankeTest` (plaintext-root-archtests): no `.java` file
  in `src/main/java` or `src/test/java` of the reactor over 1500 lines (allowlist rule
  `groesse-max-loc`). The threshold is deliberately not a property — a justified exception is
  traceable, a dialled-up limit is not. root keeps to it without exception (largest
  file: `ClaudeAutomationServiceTest`, 1128 lines).
- **Not** implemented: the planned third rule "no cycles between the `ch.plaintext.*` packages".
  The measurement produced 31 cycle groups over practically all top-level packages (hub
  `ch.plaintext.boot`, e.g. `boot -> settings -> modules -> jpa -> boot`). A rule with thirty
  exceptions checks nothing; the finding is recorded as a Javadoc section in `PlaintextLayeringTest`
  and the disentangling is a stage of its own.
- **Woodpecker pipeline and CI switch** (`.ci-engine`, `.woodpecker/`,
  [docs/CI.md](docs/CI.md) (then `docs/CI-UMSCHALTEN.md`)). root is the last repository of the family to get the
  switchover; app, guild, iot and schuetu already run over `.ci-engine=woodpecker`.
  **`.ci-engine` is set to `github` — nothing is armed**, the Woodpecker files bail out again
  immediately on every run. Three workflows instead of the four of the apps: `verify-dev.yml` and
  `verify-prod.yml` are deliberately missing, because the shared GitHub pipeline skips both jobs for
  `deploy-target: release-only` anyway (`!= 'release-only'` respectively `== 'release-all'`)
  and the root container has been shut down since 12 August 2026. The release call is taken character
  for character from the GitHub deploy job (`./build release`), including `MVN_TEST_FLAG` and the
  `[fast]` special handling; nothing has been changed about the order of the three publication targets
  (GitHub Packages, Reposilite, git mirror `plaintext-mvn`) — `deployAtEnd` stays out of the game
  (409 from Reposilite, 29 August 2026). Deliberately added compared with the iot template: `gh` is
  installed afterwards, otherwise the GitHub release with notes would be dropped silently, and a
  manual run accepts only `ci-only`/`release-only` — the replacement for the
  `release-all` option deleted in the GitHub UI. **Weaker than today:** Woodpecker knows no
  concurrency groups; two simultaneous release runs can compute the same version and only fall apart
  at the rejected `git push` respectively at the Reposilite collision check (details in section 7.1 of
  the documentation).

### Changed
- `RepoMaster.repos` is `@Autowired(required = false)`: a context without a single
  `PlaintextRepository` bean now starts. Previously the standard injection of an empty list held the
  start up — and the only implementations in the framework lie in
  `plaintext-admin-modules` and `plaintext-admin-secrets`; whoever opted out of both got a
  startup error at a place that has nothing to do with either module. Nothing changes about the
  behaviour of the applications: as soon as a bean is there, Spring injects the list as before,
  without one the already initialised `new ArrayList<>()` remains.
- **root is free of inline JavaScript** (wave 4): 15 inline blocks (13 `<script>` with a body,
  2 `<h:outputScript>` with a body) and 16 real HTML event attributes have been extracted. New under
  `plaintext-layout/js/`: `menu.js`, `topbar.js`, `global-search.js`, `primefaces-fixes.js`,
  `pushstate.js`; new under the library `plaintext-root`: `login.js`, `login-totp.js`,
  `myuser.js`, `dashboard.js` (root-webapp), `cron.js`, `claudesummary.js`, `secrets.js`.
  Server-side values arrive throughout as a `data-` attribute on an element instead of as EL in the
  script body (`data-pt-pushstate`, `data-pt-redirect`, `data-pt-copy-target`,
  `data-pt-delete-color`, `data-pt-config-aktion`, `data-pt-dropdown`, `data-pt-logout-form`).
  The 69 `on…` attributes on `p:` components stay unchanged — they are not in the way of the
  enforcing header.
- `I18nService.importSeedTranslations()` and `I18nExportController` read CSV lines via
  `I18nSeedLinter` instead of via a private copy of the parser each.
- `I18nServiceSeedImportTest` and `I18nServiceTest` now distinguish stored entries by
  `(label, language)` instead of only by label: with fr/it in the seed the same label carries three
  texts, and the rule that a maintained entry is never overwritten
  (`I18nServiceSeedImportTest.gepflegterTextWirdNichtUeberschrieben`) applies only to the
  maintained pair —
  the missing languages of the same label are indeed pre-populated.
- `plaintext-root-jpa` no longer hangs off `plaintext-admin-sessions` (status report of 29 August 2026,
  measure 12): that was a layering inversion — a base module hung off an admin module
  that conversely builds on the substructure. No source of the module names `ch.plaintext.sessions`;
  the dependency was dead and has been removed without replacement. Nothing changes for consumers,
  `plaintext-root-webapp` declares `plaintext-admin-sessions` itself.

### Fixed
- Topbar: the notification dropdown menu stopped reacting after 60 seconds. Its
  `<p:poll interval="60" update="@form"/>` replaces bell and list with new elements, but the
  inline block had attached its listeners once to the original nodes. The
  extracted `topbar.js` binds by delegation on the `document` and survives every Ajax update.
- Session beans in `plaintext-admin-requirements` are serialisable again:
  `AnforderungSettingsBackingBean.apiSettingsRepository`/`.claudeAutomationService` and
  `HowtoBackingBean.howtoRepository` are `transient` (rule `PlaintextSessionBeanSerialisierbarTest`,
  card 915). root had not seen the violations, because the shared ArchUnit rules run only in
  `plaintext-root-webapp` and that does not hang off `plaintext-admin-requirements` — the base
  package was already `ch.plaintext`, the classpath was not. Now the module runs the
  class-based rules (`PlaintextSessionBeanSerialisierbarTest`, `PlaintextMcpScopeVertragTest`)
  itself via Surefire `dependenciesToScan`, and the new `SessionBeanRegelDeckungTest` in the webapp
  reports every session-scoped bean in the reactor that neither the webapp sees nor its own module checks.
- Duplicate Flyway migration `V1774038471__create_branding_logo_table.sql` (identical in
  `plaintext-root-flyway` and `plaintext-admin-settings`): the copy in the Flyway module has been
  removed, the table belongs to `BrandingLogo` in settings. Flyway had not reported this only
  because the classpath scanner collapses resources of the same name; a diverging line in one
  copy would have produced a checksum conflict at startup. Nothing changes for consumers
  (same resource name, same checksum, no entry in `flyway_schema_history` affected).

### Changed
- **JaCoCo gate enforcing (status report of 29 August 2026, measure 13).** The `coverage-check` execution
  in the reactor parent breaks the build off if a module falls below 40 % line coverage
  (`jacoco.halt-on-failure=true`, `jacoco.coverage.minimum=0.40`, both properties). All root modules
  keep to the threshold without staggering; `plaintext-root-archtests` does not measure (its main sources are
  the test rules), `plaintext-root-template` has no Java. **Consumer note:** app, guild, iot and
  schuetu inherit the execution and, when bumping to this version, have to put
  `<jacoco.halt-on-failure>false</jacoco.halt-on-failure>` into their pom until they are clean
  themselves — otherwise `mvn verify` breaks off there.
- Coverage raised (lines, before → after): `plaintext-root-web` 3.5 % → 59.3 % (the tests for
  `UrlRewriteConfig`, `PathParameterConfig`, `SessionTrackingConfig`, `MenuBean`, `MenuDebugController`,
  `XhtmlDebugController`, `SpringSecurityProvider`, `DashboardController` lay in the webapp and did
  not count there — now in the module), `plaintext-admin-oidc` 15.4 % → 91.1 %
  (`OidcConfigServiceTest`, `OidcConfigBackingBeanFlowTest`), `plaintext-admin-secrets` 19.3 % → 48.5 %
  (`PasswordGeneratorTest`, `SecretServiceVerwaltungTest`), `plaintext-admin-i18n` 28.1 % → 57.7 %
  (`I18nServiceTest` incl. CSV seed import), `plaintext-admin-notifications` 38.0 % → 82.3 %
  (`NotificationCleanupCronTest`, `NotificationBeansTest`), `plaintext-admin-mailtemplate`
  39.6 % → 91.2 % (`MailTemplateBackingBeanTest`), `plaintext-root-flyway` without a test → 98.3 %
  (`FlywayServiceTest`, `FlywayMigrationenTest`: file name, version unique across the reactor,
  PostgreSQL syntax of all migrations).
- `plaintext-root-webapp` measures honestly: excluded are only `RootBootApplication`, the empty
  `@MenuAnnotation` declarations and five pure `@Bean` wirings without branching (each group
  with a reason in the pom); `PlaintextSecurityConfig` & co. stay in.

### Deprecated
- `ch.plaintext.upload.IUploadTarget` (plaintext-root-interfaces) is `@Deprecated(forRemoval =
  true)`: the only contract of the module that is application domain logic. No module in root uses it,
  both implementations (`PostkontoUploadTarget`, `RunningUploadTarget`) and the driving
  `RootUploadController` lie in plaintext-app, and guild/iot/schuetu do not know it — the endpoint
  name `/nosec/root/upload` had feigned a root responsibility that never existed.
  The successor lies under **the same** fully qualified name in `plaintext-app-interfaces`
  (plaintext-app PR „IUploadTarget nach app-interfaces"), so consumers change no imports.
  **Not yet removed:** app is pinned to a published root version; it will be deleted
  only once app has released its own contract and is pinned to a root version without this copy.
  The order is mandatory: app first, then the deletion here.

## [1.635.0] — 2026-08-29

### Fixed
- Language switching (topbar) can be turned off in the setup again: `I18nService.isI18nEnabled()`
  only read the old global key `i18n.enabled`, but the setup writes
  `branding.i18n.enabled` per tenant — now the tenant switch applies first, then the old
  key, otherwise on. Affects the topbar icon, translated menu titles and `i18n.t()`.
- The setup page is cleanly aligned on the phone: the six rigid two-column tables
  are flex grids (side by side from `md`, on mobile the label above the field), fixed widths are fluid.
- Menü-Diagnose (`menudiagnose.html`) renders again: `MenuDiagnoseZeile` is a record, and
  the RecordELResolver resolves `#{z.modulKeysText}` only via a method `modulKeysText()`
  — the previous bean getters `getModulKeysText()`/`getErsterGrund()` left the page
  empty with a `PropertyNotFoundException`. The new `MenuDiagnoseZeileElTest` checks every
  `#{z.…}` reference of the page against the record methods.
- Datenverwaltung (`rootentities.html`, `adminentities.html`): the type selection did not trigger
  a request via `onchange="submit()"`, no table appeared. Now a `p:ajax` event
  with listener `entityTypeChanged()`, which reloads list and messages and discards the selection
  of the old type.
- The menu item „Swagger" only appears when `springdoc.swagger-ui.enabled=true`.
  Previously in PROD (springdoc off) it led to a 404 and from there — via
  `PlaintextErrorViewResolver` — to the dashboard. For that the `MenuAnnotationScanner` evaluates
  `@Conditional` annotations on `@MenuAnnotation` classes against the application environment
  (`mitUmgebung(Environment)`); `SwaggerMenu` carries `@ConditionalOnProperty`.

### Changed
- Menuesteuerung guide: no menu item of its own any more (`MenuesteuerungAnleitungMenu`
  removed). It opens via an info button on `mandatemenu`, `mandatemenudetail`
  and `menudiagnose`; in the `PageAccessGuardService` it stands on the framework allowlist,
  ROOT-only is still enforced by `PlaintextSecurityConfig`.
- Mailtexte (`mailtemplates.html`) now hang in the **Admin** submenu and can be operated by ADMIN and
  ROOT (previously root-only). The overrides are tenant-specific; `ADMIN_PAGES` instead of
  `ROOT_ONLY_PAGES` in `PlaintextSecurityConfig`.

### Added
- Configurable module roles: `plaintext.menu.module-roles.<modulKey>=<rolle>` switches a
  whole module on or off per application — menu item, dashboard tile and the direct call of the
  pages (`PageAccessGuard`) additionally require the configured role, `admin`/`root`
  bypass it. The module key is the `moduleId` (admin panel „Root | Module"), alternatively the
  menu root id; modules without an entry behave unchanged. The configured roles
  appear automatically in the `PlaintextRoleRegistry` without the application writing a
  `PlaintextRoleProvider` of its own. New: `ModuleRoleProperties` (`plaintext-root-interfaces`),
  `ModuleRoleService` (`plaintext-root-menu`), `ModuleRoleDeclarationProvider`
  (`plaintext-root-common`). Documentation: `docs/ROLE_REGISTRY.md`.
- `plaintext-root-web`: reusable JSF/web infrastructure carved out of
  `plaintext-root-webapp` — `UrlRewriteConfig` (the `.html`/`.htm` → `.xhtml` rewrite
  filter), `SpringSecurityProvider`, `MenuBean` and the two debug controllers.
  Applications on `plaintext-root-webapp` are unaffected; the module is pulled in
  transitively. The point of the split is that `plaintext-root-webapp` is an
  *application* module — it carries its own `application.yml`, its own
  `SecurityFilterChain`, its own JPA entities and pulls eighteen `ch.plaintext`
  modules plus Flyway and PostgreSQL. An application that only wants the URL
  rewriting cannot take all of that.
- `WebAutoConfiguration` registers `SpringSecurityProvider` and `UrlRewriteConfig`
  through `AutoConfiguration.imports`, guarded by `@ConditionalOnWebApplication` and
  `@ConditionalOnMissingBean`. As with the page guard, these were previously reachable
  only by component-scanning `ch.plaintext`; an application that did not scan it
  started cleanly and silently had no URL rewriting, so every `.html` link 404'd.
  `MenuBean` deliberately keeps its `@Component`: it runs in the JSF `view` scope,
  which only an application with JoinFaces knows.
- `plaintext-root-pageguard`: the page access guard is now its own module instead of
  living inside `plaintext-root-webapp`. It carries `PageAccessGuardFilter`, the
  service, the startup report, the `preRenderView` backing bean and `PageGuardMode`.
  Applications on `plaintext-root-webapp` are unaffected — the module is pulled in
  transitively and the filter is still registered by `PlaintextSecurityConfig`, where
  its position in the Spring Security chain is documented. The point of the split is
  that an application consuming single modules can now have page-level authorization
  without taking the whole web stack.
- `PageGuardAutoConfiguration` registers the guard through `AutoConfiguration.imports`
  rather than through `@Service`/`@Component`, and `MenuAutoConfiguration` now
  registers `MenuRegistryImpl` the same way. Both were previously reachable only by
  component-scanning `ch.plaintext`; an application that did not scan it started
  cleanly and silently had no page guard at all.
- `plaintext.security.page-guard.startup-report` (default `true`) switches off the
  boot-time scan that lists views without an access rule.
- `plaintext-root-pageguard` publishes a `test-jar`. `PageAccessGuardTestFactory`
  builds a guard over a mocked menu registry, so a consuming application can assert
  its own views against its own menus — see `MenuLinkInvariantTest` in
  `plaintext-root-webapp` for the pattern.
- Menu access policy `STRICT` (`plaintext.menu.access-policy`): a menu item stays
  hidden unless a rule admits the user — `ROLE_ROOT`, `ROLE_ADMIN` outside the Root
  menu, the derived `ROLE_MENU_<menuId>`, one of the declared `roles`, or a prefix
  from the new `roleStartsWith`. The default stays `PERMISSIVE`, so existing
  applications are unaffected, and an unknown value falls back to it rather than
  failing startup. New `menuId` and `roleStartsWith` attributes on `MenuAnnotation`,
  `getAutoRole()`/`getEffectiveMenuId()`/`isUnderRootMenu()` on `MenuItemImpl`,
  `hasAnyRoleStartingWith()` as a default method on `SecurityProvider`.
- `MenuRoleService`: lists every assignable menu role and resolves a request path
  back to its menu item, ignoring the current user's permissions — a screen that
  grants roles must show the ones its operator does not hold. Registered through
  `MenuAutoConfiguration` so consumers need not component-scan `ch.plaintext`.
- `CronConfigStore` as the seam for cron configuration, with `JpaCronConfigStore`
  as the unchanged default (`plaintext.cron.store`, default `jpa`). Applications
  that keep their cron configuration outside the database — a wiki page, a config
  service — contribute their own bean.
- `plaintext.cron.default-enabled` / `default-startup` (both `true`, unchanged
  behaviour) plus `isEnabledByDefault()` / `isStartupByDefault()` on
  `PlaintextCron`, so a deployment need not fire every newly registered job at once.
- `CronModuleConfiguration` registers the cron beans explicitly (guarded by
  `@ConditionalOnMissingBean`) and is announced through `AutoConfiguration.imports`
  — the module now works in applications that do not component-scan `ch.plaintext`.

### Fixed
- `WebAutoConfiguration` is now ordered `@AutoConfigureBefore(MenuAutoConfiguration.class)`.
  Both offer a `SecurityProvider` bean guarded by `@ConditionalOnMissingBean`: the menu module
  contributes a *permissive* default so that an application without security still gets a menu,
  the web module contributes the real, Spring-Security-backed one. Without the ordering the
  permissive default won — every menu item visible to everyone, and since the page guard derives
  its rules from menu visibility, every page reachable. Fail-open, with no error message.
  `SecurityProviderReihenfolgeTest` pins it down.

### Changed
- `PlaintextSecurityProperties.PageGuardProperties` is now the top-level class
  `ch.plaintext.boot.security.PageGuardProperties`, and `getPageGuard()` is gone from
  `PlaintextSecurityProperties`. **The configuration prefix is unchanged** —
  `plaintext.security.page-guard.*` binds exactly as before, so no `application.yml`
  and no environment variable needs touching; `PageGuardAutoConfigurationTest` pins
  this down. Only code that referenced the nested Java class has to adjust its import.
- `PageAccessGuardService` takes `PageGuardProperties` rather than
  `PlaintextSecurityProperties` in its constructor.

### Fixed
- Cron key derivation resolves CGLIB proxies via `ClassUtils.getUserClass`. A job
  annotated `@Transactional` or `@Async` was filed as `MyCron$$SpringCGLIB$$0`,
  never found its stored configuration again and silently created a fresh one.
- `SessionAttribute` formats sizes with `Locale.ROOT`; `SessionAttributeTest`
  failed on every machine whose locale uses a decimal comma.

### Security
- Validate CSRF tokens on JSF pages: `/**/*.xhtml` and `/**/*.html` removed
  from `DEFAULT_CSRF_IGNORE` — the JSF ViewState is state management, not a
  CSRF protection. Every `h:form` must embed the
  `<input type="hidden" name="_csrf" value="#{_csrf.token}"/>` convention;
  the new repo-wide `CsrfFormInvariantTest` enforces this and the missing
  inputs in `menu.xhtml`, `debug.xhtml`, `adminentities.xhtml` and
  `rootentities.xhtml` were added. (Spec 22)

### Added
- App-wide mobile-responsive forms: new central
  `plaintext-layout/css/mobile-responsive.css` (loaded last from the shared
  `includes/template.xhtml`, so it wins the cascade) caps every `p:dialog`
  at `96vw`/`92vh` with a scrolling content area, and on `max-width: 768px`
  makes inputs/textareas/selects `max-width: 100%`. Fixes `p:dialog width="NNN"`
  (fixed px) dialogs running off the right edge on phones. Adds a
  `.mobile-safe` helper class; all root dialogs with fixed px widths were
  converted from `width="NNN"` to `styleClass="mobile-safe"`.
- `MobileFormLinter` (in `plaintext-root-common`) + `MobileFormLinterTest`
  fail the build on mobile anti-patterns (`p:dialog` with fixed px width
  without a `mobile-exempt`/`<!-- mobile-ok -->` opt-out). The utility is
  reusable by dependent apps (app, iot, fwtool, schuetu) after the next root
  release. `scripts/mobile-form-lint.sh` reports fixed-width dialogs / inputs
  and `panelGrid`s per file:line and offers an idempotent `--fix`.
- `ConfigEncryptionService` now uses authenticated AES/GCM with a versioned
  `ENCv2[…]` envelope; `ConfigEncryptionMigrator` re-encrypts legacy
  `ENC[…]` (CBC) values. (#117 / closes #116)
- `docs/CRYPTO.md` documents the wire format and migration procedure.
- `plaintext.security.remember-me-key` makes the persistent-token signing
  key configurable; an ephemeral random key is generated when not set, with
  a startup WARN. (#120)
- `docs/IMPROVEMENT_PROPOSALS.md` and `docs/DOCS_OVERHAUL_PLAN.md` capture
  prioritised follow-ups and the docs-site rewrite plan. (#119)

### Changed
- README, `docs/index.md`, and `SECURITY.md` corrected to use the
  Plaintext-Gmbh organisation URLs and current Spring Boot 4 / framework
  v1.167 versions. (#118)
- `plaintext-root-menu` README no longer pins a stale SNAPSHOT version in
  the dependency snippet. (#118)

### Removed
- The `email_config` legacy table and `EmailConfigMigration` (start-up data
  backfill marked `@Deprecated(forRemoval = true, since = "1.94.0")`). (#121)

## [1.166.0] - 2026-04-29

### Added
- SonarQube integration: build option `s` plus a weekly CI analysis
  workflow. (#89)

### Security
- Escape error messages instead of concatenating them into `innerHTML`
  in admin pages. (#115)
- Stop leaking exception messages in REST error responses. (#114)
- Mark theme cookies `Secure` + `SameSite=Lax`. (#113)

### Changed
- Bump base image from `eclipse-temurin:25.0.2_10-jre-alpine` to
  `26_35-jre-alpine`. (#109)

### Fixed
- Align redirect-path expectations with handler logic in tests. (#111)

## [1.164.0] - 2026-04 (approximate)

### Added
- Multi-OIDC provider configuration in `plaintext-admin-oidc`.

### Changed
- Spring Boot 4.0.6 baseline; PrimeFaces 15.x; JoinFaces 5.x.

## [1.162.0] - 2026-04 (approximate)

### Added
- API token module (`plaintext-admin-apitoken`).
- Custom branding (per-mandate logo and colour palette).

### Fixed
- Logout flow on dynamic theme; integration-test stability.

## [1.158.x] - 2026-03

### Added
- Release workflow scaffolding.
- Dynamic theming (light/dark + per-tenant accent colour).

### Changed
- Build configuration extracted into `plaintext-build.cfg`.

---

For older history, run `git log` against the relevant tags. Release tags
follow the `<major>.<minor>.<patch>` pattern (no `v` prefix); the
`maven-release-plugin` is the source of truth.
