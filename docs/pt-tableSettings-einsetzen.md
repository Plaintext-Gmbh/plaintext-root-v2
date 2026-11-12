# `pt:tableSettings` einsetzen — Anleitung für KIs

Stand: Karte 1853, Fix-Runde 4 (11.11.2026). Repo: `Plaintext-Gmbh/plaintext-root-v2`
(Tag + CSS + JS in `plaintext-root-template`, Java in `plaintext-root-common`).

Diese Datei erklärt zwei Dinge: wie man den Bedienbereich einer Tabelle einbaut und wie
das Speichern pro Person funktioniert (und wo die bekannten Fallstricke liegen).

## 1. Minimaler Einsatz

```xhtml
<h:form id="fm">
    <pt:tableSettings anzeige="#{meinBackingBean.anzeige}" tabelle=":fm:tbl"
                      legend="Anzeige einrichten"/>
    <p:dataTable id="tbl" value="#{meinBackingBean.zeilen}" var="z"
                 draggableColumns="#{meinBackingBean.anzeige.verschiebenAktiv}"
                 resizableColumns="#{meinBackingBean.anzeige.groesseAendernAktiv}">
        <p:ajax event="colReorder" listener="#{meinBackingBean.anzeige.onColReorder(event, ':fm:tbl')}"
                update="fm:anz-bereich :fm:tbl"/>
        <p:column headerText="Name">...</p:column>
    </p:dataTable>
</h:form>
```

Pflicht-Attribute: `anzeige` (das `TableSettings`-Objekt der Seite), `tabelle` (Suchausdruck
für Ajax-Updates, **mit führendem Doppelpunkt**, also `:fm:tbl`). Optional: `settingsId`
(nur bei zwei Bedienbereichen auf einer Seite), `legend`, `tabelleDomId`.

Die drei Verdrahtungszeilen an der Tabelle sind Pflicht — ohne sie bleiben die Schalter
«Verschieben»/«Grösse ändern» im Panel wirkungslos (das Tag kann fremde Komponenten nur
anbieten). Breiten wirken, wenn die Seite zusätzlich bindet: `style`/`tableStyle` aus
`#{anzeige.spaltenBreiteStyle()}` (siehe JavaDoc `TableSettings`), Paginierung wie im
Tag-Kopfkommentar beschrieben.

## 2. Das TableSettings-Objekt

```java
@Inject /* oder Spring @Autowired */ TableStateStore store;

public TableSettings anzeige() {
    return new TableSettings("seite", true, 200); // Seitenname, mitBreiten, Vorgabe Zeilen/Seite
}
```

Der Konstruktor lädt den gespeicherten Stand über `TableStateStore`. root bringt
`UserPreferenceTableStateStore` mit (JSON je Benutzer und Mandant in `UserPreference`) —
eigene Apps können das `TableStateStore`-Interface implementieren und injizieren.

Pflichtspalten (nie ausblendbar, immer sichtbar) deklariert die Seite:

```java
anzeige.markPflicht("name"); // Spalten-Key, siehe TableColumn#getKey
```

Spalten-Keys entstehen aus dem Kopftext (`TableSettings#keyFromHeader`) — der `headerText`
einer Spalte ist damit Teil des gespeicherten Zustands. Kopftexte nicht leichtfertig ändern;
bei Änderungen migriert `gemergt`-Logik unbekannte Keys an den Schluss statt sie zu verlieren.

## 3. Wie das Speichern funktioniert (und warum es so kompliziert ist)

Stand lebt in `TableState` (columnOrder, sichtbare Spalten, Breiten, Paginator, Profil).
Persistiert wird über `TableSettings#persist()` → `TableStateStore#save`, aufgerufen von den
Listener-Methoden (`onVerschiebenChange`, `onColReorder`, `onSichtbarkeitGeaendert`, …).

Drei Mechanismen muss man kennen:

1. **Reihenfolge vor dem Rendern** (`<f:event preRenderComponent>` am Wrap): sortiert die
   `p:column`-Kinder der Tabelle an den gespeicherten Stand. PrimeFaces rendert statische
   Spalten in Komponenten-Reihenfolge — ohne diesen Schritt wäre der gespeicherte Stand nach
   jedem Neuladen wieder weg.

2. **Kopf-Zug (Spalten per Ziehen im Kopf)**: PrimeFaces schickt `colReorder` nur als
   from/to-Indizes. `TableSettings#reihenfolgeAnwenden` liest beim erneuten Rendern das
   `_columnOrder`-Feld des Requests und übernimmt es (Fix-Runde 2, Karte 1853).

3. **Panel-Griff (`p:orderList` im Spalten-Reiter)**: PF 16 **consumt das `reorder`-Event
   intern** (`OrderList.queueEvent` legt es in `customEvents`, überschreibt `validate`) —
   weder `listener` noch `oncomplete`/`onstart` am `<p:ajax>` laufen jemals. Der Browser
   sendet die neue Ordnung aber mit dem reorder-Request im versteckten `_values`-Feld der
   Liste. `TableSettings#neueReihenfolgeAusRequestUebernehmen` liest beim erneuten Rendern
   (derselbe `preRenderComponent`-Weg) alle Request-Parameter, deren Werte ausschließlich
   bekannte Spalten-Keys sind, und persistiert bei Abweichung. **Der Wrap muss dazu im
   `update` des reorder-Ajax stehen** — sonst rendert er beim reorder-Request gar nicht
   und der Feldweg läuft nie (live auf PROD gemessen, 12.11.2026). Das Ziel kommt per
   `binding="#{anzeige.reihenfolgeZiel}"` in das Update: aus der Liste heraus ist keine
   relative Id möglich, weil `p:orderList` ein NamingContainer ist, und die Form-Id kennt
   das Tag nicht. Beweis: `SpaltenReihenfolgeDragPlaywrightIT` in plaintext-guild (Drag →
   Reload → Reihenfolge steht).

**Für KIs: was NICHT funktioniert** (alle messbar erprobt, Karte 1853 Runde 3):
- `listener` am `<p:ajax event="reorder">` — feuert nie.
- `oncomplete` + `p:remoteCommand` — das remoteCommand-Skript kommt im PF-16-Lebenszyklus
  innerhalb von OverlayPanel/Tabs nicht zuverlässig ins DOM (gemessen: `typeof fn ===
  "undefined"`); und PF ruft das Behavior-Callback beim OrderList-Sortable nicht auf.
- `p:toggleSwitch` pro Zeile in `p:orderList` — orderList ist keine Iterationskomponente,
  alle Schalter bekommen dieselbe Widget-Id. Seit Runde 3/4 ist es eine schlichte Checkbox
  (`data-spalte`, `data-pt-remote`), Listener in `js/table-settings.js`.

## 4. Tests, die den Vertrag sichern

- `TableSettingsSpaltenauswahlTest` (root-common): Sichtbarkeit/Pflichtspalten.
- `SpaltenReihenfolgeDragPlaywrightIT` (plaintext-guild-webapp): Drag-Regression, beide
  Wege (Kopf-Zug + Panel-Griff). Läuft gegen die lokal installierte root-Version:
  `mvn verify -pl plaintext-guild-webapp
  -Dit.test=SpaltenReihenfolgeDragPlaywrightIT -Dplaintext-root.version=3.9.0-SNAPSHOT`
  (die Version, die lokal installiert ist — sonst misst der Test einen fremden Stand).
- `PlaintextRootTemplateDriftTest` (root-archtests): sha256 über Tag/CSS/JS. **Jede Änderung
  an einer der drei Dateien erfordert den Hash-Nachzug** (`sha256sum <datei>`, in der Map
  ersetzen). Die Kopie im `plaintext-oblique-theme`-Repo (Karte 1088) muss Änderungen
  separat übernehmen, sonst wird dessen Build beim nächsten root-Bump rot.

## 5. CSP

Kein Inline-JS/kein `on…`-Attribut im Tag (Welle 4, `script-src` ohne `'unsafe-inline'`).
JS-Verdrahtung läuft über `data-pt-*`-Attribute und delegierte Listener in
`js/table-settings.js`. Beim Erweitern des Tags gilt: neues Verhalten = neues `data-`
Attribut + Listener in dieser Datei.

## 6. Growl-Feedback (Runde 4)

Gelungene Änderungen meldet das Tag kurz oben rechts: Spaltenbreite geändert (freies Ziehen
und Setzen-Knopf) sowie Spaltenreihenfolge geändert (Kopf-Zug). `TableSettings#growl` legt
eine globale FacesMessage ab; zeigt tut sie das eine `globalGrowl` des Templates (Karte 1547,
aktualisiert sich per `p:autoUpdate` bei jedem Ajax-Response) — das Tag trägt bewusst KEIN
eigenes `p:growl` (Regel: ein Growl pro Seite, `EinGrowlProSeiteTest`). Seiten verdrahten
nichts. Der Panel-Griff und die Sichtbarkeit melden bewusst nur die Textzeile: Ihre Requests
rendern oft, ein Growl pro Render waere Laerm.

