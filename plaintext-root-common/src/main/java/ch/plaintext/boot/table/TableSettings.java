/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import ch.plaintext.boot.plugins.jsf.FacesMessages;
import jakarta.faces.component.UIComponent;
import jakarta.faces.component.UIData;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.AjaxBehaviorEvent;
import jakarta.faces.model.SelectItem;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.component.api.ColumnAware;
import org.primefaces.component.api.UIColumn;
import org.primefaces.component.api.UITable;
import org.primefaces.event.ColumnResizeEvent;
import org.primefaces.event.ReorderEvent;
import org.primefaces.event.ToggleEvent;
import org.primefaces.event.data.PageEvent;
import org.primefaces.event.data.SortEvent;
import org.primefaces.expression.SearchExpressionUtils;
import org.primefaces.model.SortMeta;
import org.primefaces.model.SortOrder;
import org.primefaces.model.Visibility;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Die Anzeige-Steuerung einer Tabelle: Spaltenwahl, Spaltenbreiten und benannte Profile —
 * je Benutzer gespeichert ueber einen {@link TableStateStore}.
 *
 * <p><b>Kein Spring-Bean, und das mit Absicht.</b> Jede Seite haelt sich ihr eigenes Exemplar in
 * ihrer Backing-Bean und reicht es dem Tag {@code pt:tableSettings} weiter. Ein gemeinsames Bean
 * muesste den Stand nach Seiten schluesseln und waere doch nur ein Umweg zum selben Ergebnis —
 * mit dem Zusatzrisiko, dass zwei Seiten sich gegenseitig die Spalten verstellen.</p>
 *
 * <p><b>Die Ablage kommt mit.</b> {@link UserPreferenceTableStateStore} ist ein Spring-Bean aus
 * diesem Modul und speichert je Benutzer und Mandant in {@code UserPreference}; die Backing-Bean
 * laesst sich einen {@link TableStateStore} injizieren ({@code transient}, wie ihre Services)
 * und reicht ihn an {@link #init} weiter. Mehr braucht eine Seite nicht.</p>
 *
 * <pre>{@code
 * private static final List<TableColumn> COLUMNS = List.of(
 *         new TableColumn("key",   "Schluessel", 160),
 *         new TableColumn("owner", "Owner",      180, false));
 *
 * @Getter
 * private final TableSettings anzeige = new TableSettings("projects", true);
 *
 * @Autowired
 * private transient TableStateStore tableStateStore;
 *
 * @PostConstruct
 * void init() {
 *     anzeige.init(tableStateStore, COLUMNS);
 * }
 * }</pre>
 *
 * <p>In der Seite steht dann einmal das Tag und an jeder Spalte zweimal die Abfrage:</p>
 *
 * <pre>{@code
 * <pt:tableSettings anzeige="#{projektBackingBean.anzeige}" tabelle=":fm:tbl"/>
 * ...
 * <p:column headerText="Owner"
 *           rendered="#{projektBackingBean.anzeige.isVisible('owner')}"
 *           style="#{projektBackingBean.anzeige.widthStyle('owner')}"> ... </p:column>
 * }</pre>
 *
 * <p><b>Seiten ohne feste Breiten</b> — etwa eine Uebersicht mit dreissig Auto-Spalten — schalten
 * den Breiten-Teil mit {@code mitBreiten=false} ab. Dann verschwinden die Breitenfelder aus dem
 * Bedienbereich, {@link #widthStyle(String)} liefert leer, und die Tabelle rendert im
 * Auto-Layout. Die Spaltenwahl und die Profile bleiben.</p>
 *
 * <p><b>Paginator und Seitengroesse (Karte 1336)</b> wirken auf demselben Weg wie Breite und
 * Sichtbarkeit: die Seite bindet vier Attribute ihrer Tabelle an dieses Objekt und meldet das
 * Blaettern zurueck.</p>
 *
 * <pre>{@code
 * <p:dataTable id="tbl" ...
 *              paginator="#{projektBackingBean.anzeige.paginator}"
 *              paginatorPosition="#{projektBackingBean.anzeige.paginatorPosition}"
 *              rows="#{projektBackingBean.anzeige.rows}"
 *              rowsPerPageTemplate="#{projektBackingBean.anzeige.rowsPerPageTemplate}">
 *     <p:ajax event="page" listener="#{projektBackingBean.anzeige.onPage}"/>
 * }</pre>
 *
 * <p>Die Vorgabe der Seite fuer die Zeilen pro Seite kommt ueber den Konstruktor
 * ({@link #TableSettings(String, boolean, int)}); ohne Angabe gilt {@value #VORGABE_ZEILEN}.
 * Eine Seite, die die Attribute nicht bindet, merkt von alledem nichts — nur die Checkboxen im
 * Bedienbereich bleiben dann ohne Wirkung.</p>
 *
 * <p><b>Sortierung (Karte 1346)</b> ebenso: die Tabelle bindet {@code sortBy} und meldet jedes
 * Sortieren zurueck. Mehrspaltig (Strg-Klick, PrimeFaces-Vorgabe {@code sortMode="multiple"})
 * geht mit.</p>
 *
 * <pre>{@code
 * <p:dataTable id="tbl" ...
 *              sortBy="#{projektBackingBean.anzeige.sortMeta}">
 *     <p:ajax event="sort" listener="#{projektBackingBean.anzeige.onSort}"/>
 * }</pre>
 *
 * <p><b>Herkunft der Breiten-Rechnerei.</b> Sie stammt aus einer einzelnen, sehr breiten
 * Projektuebersicht und ist dort teuer erkauft worden; die Begruendungen zu Gesamtbreite,
 * Zielbreite und proportionalem Umrechnen stehen bei den jeweiligen Methoden. Wer sie liest,
 * bevor er etwas vereinfacht, spart sich den Weg ein zweites Mal.</p>
 *
 * @author info@plaintext.ch
 * @since 2026
 */
@Slf4j
public class TableSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Name des Profils, das angelegt wird, wenn noch keines existiert. */
    public static final String PROFILE_DEFAULT = "Standard";

    /** Schmalste Spalte in Pixeln, damit nichts unlesbar zusammenfaellt. */
    private static final int MIN_SPALTE_PX = 44;

    /** Unter dieser Gesamtbreite ist die Tabelle nicht mehr bedienbar. */
    private static final int MIN_GESAMT_PX = 200;

    /** Zeilen pro Seite, wenn die Seite keine eigene Vorgabe nennt — dieselbe wie {@code pt:table}. */
    public static final int VORGABE_ZEILEN = 20;

    /**
     * Auswahl der Zeilen pro Seite (Karte 1336): die Werte, die member.xhtml bis dahin fest
     * anbot (20 bis 500), dazu 250, 1000, 2000 und 3000. Die Vorgabe der Seite und ein
     * gespeicherter Wert kommen immer dazu, auch wenn sie hier fehlen — sonst zeigte das
     * Auswahlfeld einen Wert, den es nicht anbietet.
     */
    public static final List<Integer> SEITENGROESSEN =
            List.of(20, 50, 100, 200, 250, 300, 500, 1000, 2000, 3000);

    /** Seitenschluessel, unter dem der Stand gespeichert wird. */
    @Getter
    private final String page;

    /** Bietet die Seite Spaltenbreiten an (resizableColumns + Breitenfelder)? */
    @Getter
    private final boolean mitBreiten;

    /** Zeilen pro Seite, solange der Benutzer nichts verstellt hat. */
    @Getter
    private final int vorgabeZeilen;

    /**
     * Transient wie die Services in den Backing-Beans: nach einer Session-Deserialisierung bleibt
     * der Stand bedienbar, nur das Speichern setzt aus, bis die Seite neu aufgebaut wird.
     */
    private transient TableStateStore store;

    /**
     * Der umschliessende Wrap des Tags, per {@code binding} gesetzt. Grund: die Reihenfolge-Liste
     * (p:orderList) ist ein NamingContainer — aus ihr heraus lassen sich weder der Wrap noch die
     * Meldung per relativer Id erreichen, und das Tag kennt die Form-Id nicht fuer absolute Ids.
     * Das reorder-Ajax braucht den Wrap aber als Update-Ziel, weil das preRenderComponent dort
     * die neue Ordnung aus dem Request liest und speichert (Karte 1853, Panel-Weg).
     */
    private transient UIComponent reihenfolgeZiel;

    public UIComponent getReihenfolgeZiel() {
        return reihenfolgeZiel;
    }

    public void setReihenfolgeZiel(UIComponent reihenfolgeZiel) {
        this.reihenfolgeZiel = reihenfolgeZiel;
    }

    /** ClientId des Wraps — Ziel des reorder-Updates; leer, solange das Tag nicht gerendert wurde. */
    public String getReihenfolgeZielClientId() {
        return reihenfolgeZiel != null ? reihenfolgeZiel.getClientId() : "";
    }

    /** Die Spalten der Seite, in Tabellenreihenfolge. Bei dynamischen Spalten via {@link #setColumns} nachgefuehrt. */
    @Getter
    private List<TableColumn> columns = new ArrayList<>();

    @Getter
    private TableState state = new TableState();

    /** Im Auswahlfeld gewaehltes Profil. */
    @Getter
    @Setter
    private String selectedProfile = "";

    /** Name fuer ein neues Profil, aus dem Dialog. */
    @Getter
    @Setter
    private String newProfileName = "";

    /** Letzte Rueckmeldung an den Benutzer (Profil angelegt, Breite verteilt, ...). */
    @Getter
    private String meldung = "";

    public TableSettings(String page, boolean mitBreiten) {
        this(page, mitBreiten, VORGABE_ZEILEN);
    }

    /**
     * @param vorgabeZeilen Zeilen pro Seite ohne Benutzereinstellung, z.B. {@code 200} fuer eine
     *                      Seite, die bisher {@code rows="200"} fest stehen hatte; Werte unter 1
     *                      ergeben {@value #VORGABE_ZEILEN}
     */
    public TableSettings(String page, boolean mitBreiten, int vorgabeZeilen) {
        this.page = page;
        this.mitBreiten = mitBreiten;
        this.vorgabeZeilen = vorgabeZeilen > 0 ? vorgabeZeilen : VORGABE_ZEILEN;
    }

    /**
     * Laedt den gespeicherten Stand und sorgt dafuer, dass immer ein Profil aktiv ist. Von der
     * Backing-Bean im {@code @PostConstruct} aufzurufen.
     *
     * @param store   Ablage des Benutzerstands; {@code null} ist erlaubt und ergibt einen
     *                frischen Stand ohne Speicherung (Unit-Test, Vorschau)
     * @param columns die Spalten der Seite, in Tabellenreihenfolge
     */
    public void init(TableStateStore store, List<TableColumn> columns) {
        this.store = store;
        this.columns = new ArrayList<>(columns);
        this.state = store == null
                ? new TableState()
                : store.load(page, this.columns.stream().map(TableColumn::getKey).toList());
        if (this.state == null) {
            // Der Vertrag sagt "nie null"; eine fremde Umsetzung, die ihn bricht, soll die Seite
            // trotzdem nicht in eine NPE laufen lassen.
            this.state = new TableState();
        }
        ensureProfile();
    }

    /** Fuehrt die Spaltenliste nach, wenn sie sich zur Laufzeit aendert (dynamische Spalten). */
    public void setColumns(List<TableColumn> columns) {
        this.columns = new ArrayList<>(columns);
    }

    // ── Sichtbarkeit ────────────────────────────────────────────────────────

    public boolean isVisible(String key) {
        Boolean stored = state.getColumnVisible().get(key);
        if (stored != null) {
            return stored;
        }
        return columns.stream()
                .filter(c -> c.getKey().equals(key))
                .findFirst()
                .map(TableColumn::isDefaultVisible)
                .orElse(true);
    }

    public void setVisible(String key, boolean visible) {
        state.getColumnVisible().put(key, visible);
    }

    /** Sichtbare Spalten als Schluesselliste — Wert des {@code p:selectManyCheckbox}. */
    public List<String> getVisibleColumns() {
        List<String> visible = new ArrayList<>();
        for (TableColumn column : columns) {
            if (isVisible(column.getKey())) {
                visible.add(column.getKey());
            }
        }
        return visible;
    }

    public void setVisibleColumns(List<String> visible) {
        for (TableColumn column : columns) {
            state.getColumnVisible().put(column.getKey(), visible != null && visible.contains(column.getKey()));
        }
    }

    /** Auswahl fuer das {@code p:selectManyCheckbox}: Wert = Schluessel, Anzeige = Kopftext. */
    public List<SelectItem> getColumnItems() {
        return columns.stream()
                .map(c -> new SelectItem(c.getKey(), c.getLabel()))
                .map(SelectItem.class::cast)
                .toList();
    }

    /** Blendet alle Spalten ein. */
    public void alleSpaltenEin() {
        for (TableColumn column : columns) {
            state.getColumnVisible().put(column.getKey(), true);
        }
        persist();
    }

    /**
     * Blendet alle abwählbaren Spalten aus (Karte 1853); {@link TableColumn#isRequired()
     * Pflichtspalten} bleiben sichtbar. Der Knopf «Alle aus» im Spalten-Panel.
     */
    public void alleSpaltenAus() {
        for (TableColumn column : columns) {
            state.getColumnVisible().put(column.getKey(), column.isRequired());
        }
        persist();
        meldung = "Alle abwählbaren Spalten sind ausgeblendet.";
    }

    /** Gehoert die Spalte zu den Pflichtspalten — nicht abschaltbar, bleibt bei «Alle aus» sichtbar? */
    public boolean isPflichtspalte(String key) {
        return columns.stream().anyMatch(c -> c.getKey().equals(key) && c.isRequired());
    }

    /** EL-Methode fuer das Tag ({@code anzeige.pflichtspalte(key)}): Alias von {@link #isPflichtspalte}. */
    public boolean pflichtspalte(String key) {
        return isPflichtspalte(key);
    }

    /** Nach dem Kippen eines Sichtbarkeits-Schalters: sichern, Pflichtspalten absichern. */
    public void onSichtbarkeitGeaendert() {
        for (TableColumn column : columns) {
            if (column.isRequired()) {
                state.getColumnVisible().put(column.getKey(), true);
            }
        }
        persist();
    }

    /**
     * Schalter einer Spalte im Spalten-Panel (Karte 1853/1904). Die Liste ist eine
     * {@code p:orderList}, keine Iterationskomponente: ein Eingabefeld darin bekommt keine Id je
     * Zeile, und beim Dekodieren fehlt die Zeilenvariable. Deshalb meldet eine schlichte Checkbox
     * Schluessel und Zustand per {@code p:remoteCommand} ({@code spalte}, {@code sichtbar}).
     */
    public void onSpalteSichtbarGemeldet() {
        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap();
        spalteSichtbarSetzen(params.get("spalte"), params.get("sichtbar"));
    }

    /** Kern von {@link #onSpalteSichtbarGemeldet()}, ohne Faces testbar. Unbekannte Schluessel werden verworfen. */
    void spalteSichtbarSetzen(String key, String sichtbar) {
        if (key == null || columns.stream().noneMatch(c -> c.getKey().equals(key))) {
            log.debug("[TableSettings:{}] onSpalteSichtbarGemeldet | verworfen | spalte={}", page, key);
            return;
        }
        state.getColumnVisible().put(key, Boolean.parseBoolean(sichtbar));
        onSichtbarkeitGeaendert();
    }

    /** Kopftext einer Spalte — fuer die Reihenfolge-Liste im Spalten-Panel. */
    public String columnLabel(String key) {
        return columns.stream()
                .filter(c -> c.getKey().equals(key))
                .map(TableColumn::getLabel)
                .findFirst()
                .orElse(key);
    }

    /** Zurueck auf die Grundeinstellung der Seite. */
    public void standardSpalten() {
        for (TableColumn column : columns) {
            state.getColumnVisible().put(column.getKey(), column.isDefaultVisible());
        }
        persist();
    }

    // ── Reihenfolge (Karte 1853) ────────────────────────────────────────────

    /**
     * Die Spalten in der vom Benutzer zuletzt gespeicherten Reihenfolge. Unbekannte Eintraege
     * (neue Spalten der Seite) bleiben in ihrer Markup-Reihenfolge, ganz hinten.
     */
    public List<TableColumn> getOrderedColumns() {
        List<String> order = state.getColumnOrder();
        if (order == null || order.isEmpty()) {
            return columns;
        }
        Map<String, TableColumn> byKey = new LinkedHashMap<>();
        for (TableColumn column : columns) {
            byKey.put(column.getKey(), column);
        }
        List<TableColumn> result = new ArrayList<>();
        for (String key : order) {
            TableColumn column = byKey.remove(key);
            if (column != null) {
                result.add(column);
            }
        }
        result.addAll(byKey.values());
        return result;
    }

    /** Wert der Reihenfolge-Liste im Spalten-Panel: Schluessel in Anzeigereihenfolge. */
    public List<String> getSpaltenReihenfolge() {
        return getOrderedColumns().stream().map(TableColumn::getKey).toList();
    }

    /** Die Liste schreibt die vom Benutzer im Panel gezogene Reihenfolge zurueck. */
    public void setSpaltenReihenfolge(List<String> reihenfolge) {
        if (reihenfolge == null || reihenfolge.isEmpty()) {
            return;
        }
        state.setColumnOrder(new ArrayList<>(reihenfolge));
    }

    /**
     * Nach dem Ziehen in der Reihenfolge-Liste des Panels: sichern und die Tabelle gleich
     * umsortieren — sonst zeigte ein folgendes Teilupdate der Tabelle noch die alte Reihenfolge
     * (das Auf-ziehen-im-Vollrender passiert erst beim naechsten Seitenaufruf).
     *
     * @param tabelle Suchausdruck der Tabelle, wie ihn das Tag bekommt (z.B. {@code ":fm:tbl"})
     */
    public void onReihenfolgeGeaendert(String tabelle) {
        persist();
        reihenfolgeAnwenden(tabelle);
    }

    /**
     * Spalte im Tabellenkopf per Ziehen verschoben (colReorder): PrimeFaces meldet nur von/to —
     * als Indizes ueber die gerenderten, also sichtbaren Spalten. Zusätzliche rendered-Bedingungen
     * der Seite (z.B. «✓» bei abgeschaltetem Jahresbeitrags-Versand) entziehen dem Client Spalten,
     * ohne dass der Server das sieht — dann landet die neue Reihenfolge einen Platz daneben.
     * Genauigkeitsgrenze, bewusst dokumentiert statt mit Mess-JS nachgebessert (Karte 1853).
     *
     * @param tabelle Suchausdruck der Tabelle
     */
    public void onColReorder(AjaxBehaviorEvent event, String tabelle) {
        if (event == null) {
            return;
        }
        // PF 16 sendet die neue Reihenfolge im versteckten Feld <clientId>_columnOrder
        // (kommagetrennte Client-Ids der Spaltenköpfe) — from/to bleiben dabei leer.
        // Der Feldweg ist der Hauptweg; from/to bleibt als Fallback für Wege, die sie mitsenden.
        log.debug("[TableSettings:{}] onColReorder EINTRITT | tabelle={}", page, tabelle);
        UIData data = tabelleFinden(tabelle);
        log.debug("[TableSettings:{}] onColReorder | tabelleGefunden={}", page, data != null);
        List<String> feldReihenfolge = data != null ? reihenfolgeAusColumnOrderFeld(data) : List.of();
        log.debug("[TableSettings:{}] onColReorder | feldReihenfolge={}", page, feldReihenfolge);
        if (!feldReihenfolge.isEmpty()) {
            state.setColumnOrder(mergeSichtbar(feldReihenfolge));
            persist();
            reihenfolgeAnwenden(tabelle);
            log.debug("[TableSettings:{}] onColReorder | ueber columnOrder-Feld | {}", page, feldReihenfolge);
            return;
        }
        List<String> sichtbar = new ArrayList<>();
        for (TableColumn column : getOrderedColumns()) {
            if (isVisible(column.getKey())) {
                sichtbar.add(column.getKey());
            }
        }
        int from = event instanceof ReorderEvent re ? re.getFromIndex() : -1;
        int to = event instanceof ReorderEvent re2 ? re2.getToIndex() : -1;
        if (from < 0 || to < 0 || from >= sichtbar.size() || to >= sichtbar.size() || from == to) {
            return;
        }
        String gezogen = sichtbar.remove(from);
        sichtbar.add(to, gezogen);
        state.setColumnOrder(mergeSichtbar(sichtbar));
        persist();
        growl("Spaltenreihenfolge wurde geändert.");
        reihenfolgeAnwenden(tabelle);
        log.debug("[TableSettings:{}] onColReorder | from={} | to={}", page, from, to);
    }

    /** Persistiert die im Request mitgelieferte neue Reihenfolge, falls sie vom Stand abweicht. */
    private void neueReihenfolgeAusRequestUebernehmen(UIData data) {
        try {
            // Nach dem Ereignis unterscheiden, nicht nach Feld-Reihenfolge (Karte 1853):
            // PF schickt BEIDE Felder mit jedem Submit mit — die orderList liegt im selben
            // Formular, also ist auch ihr _values bei jedem POST dabei (alter Stand), und
            // das columnOrder der Tabelle ebenso. Nur das Behavior-Event sagt, was gemeint
            // ist (gemessen: claude1, Inventar-Pilot 3.19/3.20).
            String event = eventAusRequest();
            List<String> keys = switch (event) {
                case "reorder" -> reihenfolgeAusOrderListValuesFeld();
                case "colReorder" -> data != null ? reihenfolgeAusColumnOrderFeld(data) : List.of();
                default -> List.of();
            };
            if (keys.isEmpty()) {
                return;
            }
            List<String> gemerged = mergeSichtbar(keys);
            if (!gemerged.equals(state.getColumnOrder())) {
                state.setColumnOrder(gemerged);
                persist();
                log.debug("[TableSettings:{}] Reihenfolge aus Request uebernommen | {}", page, gemerged);
            }
        } catch (RuntimeException e) { // NOSONAR - ein Kippen der Reihenfolge darf das Rendern nicht abbrechen
            log.debug("[TableSettings:{}] Reihenfolge-Request-Verarbeitung fehlgeschlagen | {}", page, e.getMessage());
        }
    }

    /** Das Behavior-Event des Requests ({@code jakarta.faces.behavior.event}), leer wenn keins. */
    private String eventAusRequest() {
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces == null || faces.getExternalContext() == null) {
            return "";
        }
        String event = faces.getExternalContext().getRequestParameterMap()
                .get("jakarta.faces.behavior.event");
        return event != null ? event : "";
    }

    /**
     * Liest die Reihenfolge aus dem versteckten {@code <id>_values}-Select der Reihenfolge-Liste
     * (p:orderList im Panel). Der Browser sendet alle Eintraege in ihrer neuen Ordnung als
     * Mehrfach-Parameter. Erkannte wird das Feld daran, dass alle Werte bekannte Spalten-Keys sind.
     */
    private List<String> reihenfolgeAusOrderListValuesFeld() {
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces == null || faces.getExternalContext() == null) {
            return List.of();
        }
        java.util.Set<String> bekannteKeys = new java.util.HashSet<>();
        for (TableColumn column : getOrderedColumns()) {
            bekannteKeys.add(column.getKey());
        }
        for (Map.Entry<String, String[]> eintrag
                : faces.getExternalContext().getRequestParameterValuesMap().entrySet()) {
            if (!eintrag.getKey().endsWith("_values")) {
                continue;
            }
            String[] werte = eintrag.getValue();
            if (werte == null || werte.length == 0 || !bekannteKeys.containsAll(java.util.Arrays.asList(werte))) {
                continue;
            }
            List<String> keys = new ArrayList<>();
            for (String wert : werte) {
                keys.add(wert);
            }
            return keys;
        }
        return List.of();
    }

    /**
     * Liest die neue Spaltenreihenfolge aus dem versteckten {@code <clientId>_columnOrder}-Feld
     * des DataTable-Requests (kommagetrennte Client-Ids der gerenderten Spaltenköpfe). Die Ids
     * sind die Client-Ids der UIColumn-Kinder — bei Spalten ohne explizite Id generische
     * j_idt-Ids, die sich NICHT über SearchExpressions auflösen lassen. Der Abgleich laeuft
     * deshalb direkt ueber die Kinder der Tabelle.
     */
    private List<String> reihenfolgeAusColumnOrderFeld(UIData data) {
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces == null || faces.getExternalContext() == null) {
            return List.of();
        }
        String wert = faces.getExternalContext().getRequestParameterMap()
                .get(data.getClientId(faces) + "_columnOrder");
        if (wert == null || wert.isBlank()) {
            return List.of();
        }
        Map<String, String> clientIdZuKey = new HashMap<>();
        for (UIComponent kind : data.getChildren()) {
            if (kind instanceof UIColumn col) {
                String key = keyFromHeader(col.getHeaderText());
                if (key != null && !key.isBlank()) {
                    clientIdZuKey.put(col.getClientId(faces), key);
                }
            }
        }
        List<String> keys = new ArrayList<>();
        for (String thId : wert.split(",")) {
            String key = clientIdZuKey.get(thId.trim());
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    /**
     * Setzt die sichtbaren Schluessel in ihre Positionen in der bisherigen Gesamtreihenfolge
     * ein — unsichtbare Spalten behalten ihren Platz, damit sie nach «Alle ein» wieder dort
     * stehen, wo die Konfiguration sie vorsieht.
     */
    private List<String> mergeSichtbar(List<String> sichtbarNeu) {
        Set<String> sichtbarSet = new HashSet<>(sichtbarNeu);
        List<String> result = new ArrayList<>();
        int naechste = 0;
        for (String key : getSpaltenReihenfolge()) {
            if (sichtbarSet.contains(key) && naechste < sichtbarNeu.size()) {
                result.add(sichtbarNeu.get(naechste++));
            } else {
                result.add(key);
            }
        }
        return result;
    }

    /**
     * Ordnet die Spalten-Kinder der Tabelle nach dem gespeicherten Stand an — aufgerufen vom Tag
     * kurz vor dem Rendern. PrimeFaces rendert statische {@code p:column} in
     * Komponenten-Reihenfolge; wer die Reihenfolge nach dem Neuladen halten will, muss die Kinder
     * hier bewegen. Idempotent, Schluessel-basiert; Kinder ohne Kopftext bleiben unangetastet.
     *
     * @param tabelle Suchausdruck der Tabelle
     */
    public void reihenfolgeAnwenden(String tabelle) {
        UIData data = tabelleFinden(tabelle);
        if (data != null) {
            reihenfolgeAnwenden(data);
        }
    }

    /** Der Kern von {@link #reihenfolgeAnwenden(String)}, mit der Tabelle von aussen — testbar ohne Faces. */
    void reihenfolgeAnwenden(UIData data) {
        // PF 16 decodiert den colReorder-Request selbst; das Behavior-Event erreicht unseren
        // Listener nicht zuverlässig. Die neue Reihenfolge steht im _columnOrder-Feld des
        // Requests — beim erneuten Rendern der Tabelle (update am colReorder-Ajax) wird sie
        // hier uebernommen und gespeichert.
        neueReihenfolgeAusRequestUebernehmen(data);
        List<String> order = state.getColumnOrder();
        if (order == null || order.isEmpty()) {
            return;
        }
        List<UIComponent> kinder = data.getChildren();
        Map<String, UIComponent> spalten = new HashMap<>();
        Set<String> zielKeys = new LinkedHashSet<>(order);
        for (UIComponent kind : kinder) {
            if (kind instanceof UIColumn) {
                String key = keyFromHeader(((UIColumn) kind).getHeaderText());
                if (key != null && zielKeys.contains(key)) {
                    spalten.put(key, kind);
                }
            }
        }
        if (spalten.size() < 2) {
            return;
        }
        List<String> ziel = order.stream().filter(spalten::containsKey).toList();
        List<UIComponent> gemischt = new ArrayList<>(kinder);
        java.util.Iterator<String> it = ziel.iterator();
        for (int i = 0; i < gemischt.size() && it.hasNext(); i++) {
            UIComponent kind = gemischt.get(i);
            if (kind instanceof UIColumn) {
                String key = keyFromHeader(((UIColumn) kind).getHeaderText());
                if (key != null && zielKeys.contains(key)) {
                    gemischt.set(i, spalten.get(it.next()));
                }
            }
        }
        kinder.clear();
        kinder.addAll(gemischt);
    }

    // ── Schalter «Verschieben» / «Grösse aendern» (Karte 1853) ─────────────

    /** Schalter «Verschieben»: duerfen die Spaltenkoepfe per Ziehen umgeordnet werden? Vorgabe: aus. */
    public boolean isVerschiebenAktiv() {
        return Boolean.TRUE.equals(state.getMoveEnabled());
    }

    public void setVerschiebenAktiv(boolean aktiv) {
        state.setMoveEnabled(aktiv);
    }

    public void onVerschiebenChange() {
        persist();
    }

    /** Der «Fertig»-Knopf im Panel (Runde 4, Karte 1853): beendet den Zieh-Modus nach dem Verschieben. */
    public void verschiebenBeenden() {
        state.setMoveEnabled(false);
        meldung = "Verschieben beendet — die Reihenfolge ist gespeichert.";
        persist();
    }

    /** Schalter «Grösse aendern»: duerfen Spaltenbreiten gezogen werden? Vorgabe: an (bisheriges Verhalten). */
    public boolean isGroesseAendernAktiv() {
        return !Boolean.FALSE.equals(state.getResizeEnabled());
    }

    public void setGroesseAendernAktiv(boolean aktiv) {
        state.setResizeEnabled(aktiv);
    }

    public void onGroesseAendernChange() {
        persist();
    }

    // ── Breiten ─────────────────────────────────────────────────────────────

    /** Karte 1904: nur der Wert ({@code "120px"}) fuers {@code width}-Attribut; null ohne Breiten/Vorgabe. */
    public String breite(String key) {
        String stored = mitBreiten ? state.getColumnWidths().get(key) : null;
        int fallback = mitBreiten ? defaultWidth(key) : 0;
        return stored != null && !stored.isBlank() ? stored : fallback > 0 ? fallback + "px" : null;
    }

    /**
     * Breite einer Spalte fuer das {@code style}-Attribut, z.B. {@code "width:180px;"}.
     * Leer, wenn die Seite ohne Breiten arbeitet oder die Spalte keine Vorgabe hat.
     */
    public String widthStyle(String key) {
        if (!mitBreiten) {
            return "";
        }
        String stored = state.getColumnWidths().get(key);
        if (stored != null && !stored.isBlank()) {
            return "width:" + stored + ";";
        }
        int fallback = defaultWidth(key);
        return fallback > 0 ? "width:" + fallback + "px;" : "";
    }

    /**
     * Gesamtbreite der Tabelle als Summe der sichtbaren Spalten.
     *
     * <p>Ohne diese Angabe springt eine gezogene Spalte zurueck: bei {@code table-layout: fixed}
     * verteilt der Browser die Spalten anteilig auf die Tabellenbreite neu, sobald ihre Summe
     * nicht mehr passt. Die gesetzten Pixelwerte werden damit zu blossen Verhaeltnissen. Erst eine
     * Tabellenbreite, die der Summe entspricht, macht sie wieder verbindlich.</p>
     */
    public String getTabellenBreite() {
        if (!mitBreiten) {
            return "";
        }
        Integer gesetzt = state.getTotalWidth();
        if (gesetzt != null && gesetzt >= MIN_GESAMT_PX) {
            return "width: " + gesetzt + "px;";
        }
        return "width: " + spaltenSumme() + "px;";
    }

    /** Summe der sichtbaren Spalten, ohne eine von Hand gesetzte Gesamtbreite. */
    public int spaltenSumme() {
        int summe = 0;
        for (TableColumn column : columns) {
            if (isVisible(column.getKey())) {
                summe += breiteVon(column);
            }
        }
        return summe;
    }

    /** Uebernimmt eine gezogene Spaltenbreite; die Zuordnung laeuft ueber den Kopftext. */
    public void onColumnResize(ColumnResizeEvent event) {
        UIColumn column = event.getColumn();
        if (column == null) {
            return;
        }
        String key = keyFromHeader(column.getHeaderText());
        if (key == null) {
            log.debug("[TableSettings:{}] onColumnResize | unbekannte Spalte | header={}", page, column.getHeaderText());
            return;
        }
        state.getColumnWidths().put(key, event.getWidth() + "px");
        persist();
        growl("Spaltenbreite wurde geändert.");
        log.debug("[TableSettings:{}] onColumnResize | column={} | width={}px", page, key, event.getWidth());
    }

    /**
     * Breite einer Spalte, die der Setzen-Knopf im Spaltenkopf meldet
     * (Request-Parameter {@code sp} = Kopftext, {@code px} = Breite).
     */
    public void onSpaltenbreiteGemeldet() {
        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap();
        String key = keyFromHeader(params.get("sp"));
        Integer breite = zuBreite(params.get("px"));
        if (key == null || breite == null) {
            log.debug("[TableSettings:{}] onSpaltenbreiteGemeldet | verworfen | sp={} | px={}",
                    page, params.get("sp"), params.get("px"));
            return;
        }
        state.getColumnWidths().put(key, breite + "px");
        // Eine gesetzte Gesamtbreite waere jetzt falsch — die Tabelle folgt
        // wieder der Summe ihrer Spalten.
        state.setTotalWidth(null);
        persist();
        growl("Spaltenbreite wurde gespeichert.");
    }

    /** Kurzes Growl-Overlay fuer gelungene Aenderungen (Runde 4, Karte 1853) — globale
     *  FacesMessage, die das globalGrowl des Templates mit autoUpdate uebernimmt. Laeuft
     *  zentral ueber FacesMessages (Null-Sicherheit ausserhalb eines JSF-Requests,
     *  Architekturregel meldungenNurUeberFacesMessages). */
    private void growl(String text) {
        FacesMessages.info(text);
    }

    /** Gesamtbreite fuer das Eingabefeld; ohne eigene Angabe die aktuelle Summe als Vorschlag. */
    public Integer getGesamtBreite() {
        Integer gesetzt = state.getTotalWidth();
        if (gesetzt != null && gesetzt >= MIN_GESAMT_PX) {
            return gesetzt;
        }
        return spaltenSumme();
    }

    /**
     * Zu schmale Werte werden verworfen statt beanstandet: eine Fehlermeldung an einem Feld, das
     * nur die Anzeige einstellt, haelt das ganze Formular auf — und der Benutzer wollte hier
     * nichts abschicken, sondern nur schieben.
     */
    public void setGesamtBreite(Integer breite) {
        if (breite == null || breite < MIN_GESAMT_PX) {
            state.setTotalWidth(null);
            return;
        }
        state.setTotalWidth(breite);
    }

    public void onGesamtBreiteChange() {
        persist();
    }

    /** Zielbreite fuer den Knopf im Spaltenkopf; ohne Angabe die schmalste sichtbare Spalte. */
    public Integer getZielSpaltenBreite() {
        Integer gesetzt = state.getTargetColumnWidth();
        if (gesetzt != null && gesetzt >= MIN_SPALTE_PX) {
            return gesetzt;
        }
        int schmalste = Integer.MAX_VALUE;
        for (TableColumn column : columns) {
            if (isVisible(column.getKey())) {
                schmalste = Math.min(schmalste, breiteVon(column));
            }
        }
        return schmalste == Integer.MAX_VALUE ? MIN_SPALTE_PX : schmalste;
    }

    public void setZielSpaltenBreite(Integer breite) {
        state.setTargetColumnWidth(breite == null || breite < MIN_SPALTE_PX ? null : breite);
    }

    public void onZielSpaltenBreiteChange() {
        persist();
    }

    /**
     * Weicht die eingetippte Gesamtbreite von dem ab, was die Spalten ergeben?
     * Danach richtet sich, was der Knopf daneben tut.
     */
    public boolean isBreiteAbweichend() {
        Integer gesetzt = state.getTotalWidth();
        return gesetzt != null && gesetzt >= MIN_GESAMT_PX && gesetzt != spaltenSumme();
    }

    public String getBreitenKnopfText() {
        return isBreiteAbweichend() ? "Setzen" : "Aus Spalten rechnen";
    }

    /** Ein Knopf, zwei Aufgaben — je nachdem, ob eine abweichende Breite dasteht. */
    public void breitenKnopfGedrueckt() {
        if (isBreiteAbweichend()) {
            breiteAufSpaltenVerteilen();
        } else {
            state.setTotalWidth(null);
            persist();
            meldung = "Gesamtbreite folgt wieder den Spalten.";
        }
    }

    /**
     * Rechnet alle sichtbaren Spalten proportional auf die eingetragene Gesamtbreite um; keine
     * faellt dabei unter {@value #MIN_SPALTE_PX} px.
     */
    void breiteAufSpaltenVerteilen() {
        Integer ziel = state.getTotalWidth();
        Map<String, Integer> sichtbar = new LinkedHashMap<>();
        for (TableColumn column : columns) {
            if (isVisible(column.getKey())) {
                sichtbar.put(column.getKey(), breiteVon(column));
            }
        }
        if (ziel == null || sichtbar.isEmpty()) {
            return;
        }
        int alt = sichtbar.values().stream().mapToInt(Integer::intValue).sum();
        if (alt <= 0) {
            return;
        }

        int gesetzt = 0;
        int amMinimum = 0;
        for (Map.Entry<String, Integer> e : sichtbar.entrySet()) {
            int neu = Math.round((float) e.getValue() * ziel / alt);
            if (neu < MIN_SPALTE_PX) {
                neu = MIN_SPALTE_PX;
                amMinimum++;
            }
            state.getColumnWidths().put(e.getKey(), neu + "px");
            gesetzt += neu;
        }

        if (gesetzt > ziel) {
            // Die Untergrenze hat die Rechnung gesprengt: der Wert im Feld
            // waere gelogen, also folgt die Breite wieder den Spalten.
            state.setTotalWidth(null);
            meldung = "Bei " + ziel + " px waeren " + amMinimum + " Spalten zu schmal — "
                    + "kleinstmoegliche Breite ist " + gesetzt + " px.";
        } else {
            meldung = "Spalten auf " + ziel + " px verteilt.";
        }
        persist();
    }

    /** Alle Spaltenbreiten auf die Vorgaben der Seite zuruecksetzen. */
    public void resetColumnWidths() {
        state.getColumnWidths().clear();
        state.setTotalWidth(null);
        persist();
        meldung = "Spaltenbreiten zurueckgesetzt.";
    }

    // ── Paginator und Seitengroesse (Karte 1336) ───────────────────────────

    /** Paginator oberhalb der Tabelle; ohne Einstellung an (PrimeFaces-Vorgabe "both"). */
    public boolean isPaginatorOben() {
        return !Boolean.FALSE.equals(state.getPaginatorTop());
    }

    public void setPaginatorOben(boolean oben) {
        state.setPaginatorTop(oben);
    }

    /** Paginator unterhalb der Tabelle; ohne Einstellung an. */
    public boolean isPaginatorUnten() {
        return !Boolean.FALSE.equals(state.getPaginatorBottom());
    }

    public void setPaginatorUnten(boolean unten) {
        state.setPaginatorBottom(unten);
    }

    /** Wert fuer {@code paginator} der Tabelle: aus nur, wenn oben UND unten abgewaehlt sind. */
    public boolean isPaginator() {
        return isPaginatorOben() || isPaginatorUnten();
    }

    /** Wert fuer {@code paginatorPosition} der Tabelle: {@code both}, {@code top} oder {@code bottom}. */
    public String getPaginatorPosition() {
        if (isPaginatorOben() && isPaginatorUnten()) {
            return "both";
        }
        return isPaginatorOben() ? "top" : "bottom";
    }

    /** Die eingestellten Zeilen pro Seite — Wert des Auswahlfelds im Bedienbereich. */
    public Integer getSeitengroesse() {
        Integer gespeichert = state.getRowsPerPage();
        return gespeichert != null && gespeichert > 0 ? gespeichert : vorgabeZeilen;
    }

    /** Unbrauchbare Werte fallen auf die Vorgabe der Seite zurueck, statt das Formular aufzuhalten. */
    public void setSeitengroesse(Integer zeilen) {
        state.setRowsPerPage(zeilen != null && zeilen > 0 ? zeilen : null);
    }

    /**
     * Wert fuer {@code rows} der Tabelle. Ohne Paginator {@code 0} = alle Zeilen: eine Tabelle
     * mit {@code paginator="false"} und {@code rows="200"} zeigte sonst stumm nur die ersten 200
     * Zeilen — ohne ein Bedienelement, mit dem man an die restlichen kaeme.
     */
    public int getRows() {
        return isPaginator() ? getSeitengroesse() : 0;
    }

    /** Auswahl fuer das Feld im Bedienbereich, aufsteigend und ohne Doppelte. */
    public List<Integer> getSeitengroessen() {
        TreeSet<Integer> werte = new TreeSet<>(SEITENGROESSEN);
        werte.add(vorgabeZeilen);
        werte.add(getSeitengroesse());
        return new ArrayList<>(werte);
    }

    /** Wert fuer {@code rowsPerPageTemplate} der Tabelle, z.B. {@code "20,50,100,..."}. */
    public String getRowsPerPageTemplate() {
        return getSeitengroessen().stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * Blaettern in der Tabelle ({@code <p:ajax event="page">}): eine im Paginator gewaehlte
     * Seitengroesse wird gespeichert wie jede andere Einstellung. Das reine Umblaettern
     * aendert nichts und schreibt deshalb auch nichts.
     */
    public void onPage(PageEvent event) {
        Integer zeilen = event == null ? null : event.getRowsPerPage();
        if (zeilen == null || zeilen <= 0 || zeilen.equals(getSeitengroesse())) {
            return;
        }
        state.setRowsPerPage(zeilen);
        persist();
        log.debug("[TableSettings:{}] onPage | rowsPerPage={}", page, zeilen);
    }

    /** Checkbox oben/unten geaendert: speichern und die Tabelle angleichen. */
    public void onPaginatorChange(String tabelle) {
        speichernUndAngleichen(tabelle);
    }

    /** Seitengroesse im Bedienbereich geaendert: speichern und die Tabelle angleichen. */
    public void onSeitengroesseChange(String tabelle) {
        speichernUndAngleichen(tabelle);
    }

    /**
     * Gemeinsamer Rumpf der beiden Listener (Karte 1410, Sonar java:S4144). Zwei Listener bleiben es
     * trotzdem: Sie haengen an verschiedenen Bedienelementen in {@code tableSettings.xhtml}, und
     * wer einen davon spaeter anders behandeln muss, aendert nur seinen.
     */
    private void speichernUndAngleichen(String tabelle) {
        persist();
        tabelleAngleichen(tabelle);
    }

    /**
     * Schreibt Seitengroesse und Startzeile direkt in die Tabellenkomponente.
     *
     * <p><b>Warum die Bindung {@code rows="#{anzeige.rows}"} allein nicht reicht.</b> Sobald der
     * Benutzer im Paginator selbst eine Seitengroesse waehlt, setzt PrimeFaces sie als lokalen
     * Wert an der Komponente ({@code DataTable.updatePaginationData}). Ein lokaler Wert geht vor
     * dem Ausdruck — jede spaetere Aenderung ueber den Bedienbereich liefe ins Leere, bis die
     * Seite neu geladen wird. Und ohne Paginator muss die Tabelle bei Zeile 0 anfangen, sonst
     * fehlen die Zeilen der bisher aufgeschlagenen Seiten.</p>
     *
     * @param tabelle Suchausdruck der Tabelle; unbekannt oder ohne Faces-Kontext passiert nichts
     */
    void tabelleAngleichen(String tabelle) {
        UIData data = tabelleFinden(tabelle);
        if (data != null) {
            angleichen(data);
        }
    }

    /** Die Tabelle zum Suchausdruck; {@code null}, wenn unbekannt oder ohne Faces-Kontext. */
    private UIData tabelleFinden(String tabelle) {
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces == null || faces.getViewRoot() == null || tabelle == null || tabelle.isBlank()) {
            return null;
        }
        UIComponent gefunden;
        try {
            gefunden = SearchExpressionUtils.contextlessOptionalResolveComponent(faces, faces.getViewRoot(), tabelle);
        } catch (RuntimeException e) { // NOSONAR - eine Einstellung darf den Ajax-Aufruf nicht abbrechen
            log.debug("[TableSettings:{}] tabelleAngleichen | nicht aufloesbar | tabelle={} | {}", page, tabelle, e.getMessage());
            return null;
        }
        if (gefunden instanceof UIData data) {
            return data;
        }
        log.debug("[TableSettings:{}] tabelleAngleichen | keine Tabelle | tabelle={}", page, tabelle);
        return null;
    }

    /** Der Kern von {@link #tabelleAngleichen(String)}, ohne die Suche — testbar ohne Faces. */
    void angleichen(UIData data) {
        data.setFirst(0);
        data.setRows(getRows());
    }

    // ── Sortierung (Karte 1346) ────────────────────────────────────────────

    /**
     * Wert fuer {@code sortBy} der Tabelle: die gespeicherte Sortierung als {@link SortMeta}, in
     * Prioritaetsreihenfolge; {@code null}, solange der Benutzer nie sortiert hat (dann gilt,
     * was die Seite selbst vorgibt).
     *
     * <p><b>Warum hier gefiltert wird.</b> PrimeFaces sucht zu jedem Eintrag die Spalte ueber
     * das Feld und wirft eine {@code FacesException}, wenn es keine findet — die Seite antwortet
     * dann mit HTTP 500, bei jedem Aufruf, bis jemand den Stand in der Datenbank loescht. Eine
     * Spalte mit {@code rendered="false"} findet es nicht. Deshalb gehen nur Eintraege hinaus,
     * deren Spalte bekannt und sichtbar ist, und — wenn die Tabelle beim Auswerten greifbar ist,
     * wie beim ersten Aufbau in {@code DataTableRenderer.preRender} — deren Feld eine gerenderte,
     * sortierbare Spalte der Tabelle wirklich hat. Ein Stand von einer Seite, die ihre Spalten
     * seither umgebaut hat, sortiert dann eben nicht, statt die Seite zu sperren.</p>
     *
     * <p>PrimeFaces liest den Wert nur beim Aufbau der Tabelle, also bei jedem Seitenaufruf,
     * nicht bei Ajax-Aufrufen. Genau dort wird die gespeicherte Sortierung gebraucht.</p>
     */
    public List<SortMeta> getSortMeta() {
        if (state.getSortBy() == null) {
            return null; // NOSONAR - null heisst fuer PrimeFaces "keine Vorgabe"
        }
        return sortMetaFuer(sortierbareFelderDerTabelle());
    }

    /**
     * Der Kern von {@link #getSortMeta()}, mit den Feldern der Tabelle von aussen — testbar ohne Faces.
     *
     * @param felder Sortierfelder der gerenderten Spalten, {@code null} = nicht pruefbar
     */
    List<SortMeta> sortMetaFuer(Set<String> felder) {
        List<TableSort> gespeichert = state.getSortBy();
        if (gespeichert == null) {
            return null; // NOSONAR - siehe getSortMeta
        }
        List<SortMeta> metas = new ArrayList<>();
        for (TableSort sort : gespeichert) {
            if (!anwendbar(sort, felder)) {
                continue;
            }
            metas.add(SortMeta.builder()
                    .field(sort.getField())
                    .order(sort.isDescending() ? SortOrder.DESCENDING : SortOrder.ASCENDING)
                    .priority(metas.size())
                    .build());
        }
        return metas;
    }

    private boolean anwendbar(TableSort sort, Set<String> felder) {
        if (sort == null || leer(sort.getField()) || leer(sort.getColumn())) {
            return false;
        }
        boolean bekannt = columns.stream().anyMatch(c -> c.getKey().equals(sort.getColumn()));
        if (!bekannt || !isVisible(sort.getColumn())) {
            return false;
        }
        return felder == null || felder.contains(sort.getField());
    }

    /**
     * Die Sortierfelder der gerenderten Spalten der Tabelle, die gerade ausgewertet wird; {@code null},
     * wenn keine Tabelle greifbar ist (Unit-Test, Aufruf ausserhalb des Aufbaus). Dieselbe
     * Rechnung wie in {@code UITable.initSortBy}, damit die Filterung genau das durchlaesst, was
     * PrimeFaces danach auch findet.
     */
    private Set<String> sortierbareFelderDerTabelle() {
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces == null) {
            return null; // NOSONAR - null = "nicht pruefbar", nicht "keine Felder"
        }
        try {
            if (!(UIComponent.getCurrentComponent(faces) instanceof UITable<?> tabelle)) {
                return null; // NOSONAR - siehe oben
            }
            Set<String> felder = new HashSet<>();
            tabelle.forEachColumn(spalte -> {
                SortMeta meta = SortMeta.of(faces, tabelle.getVar(), spalte);
                if (meta != null && meta.getField() != null) {
                    felder.add(meta.getField());
                }
                return true;
            });
            return felder;
        } catch (RuntimeException e) { // NOSONAR - eine Einstellung darf den Seitenaufbau nicht abbrechen
            log.debug("[TableSettings:{}] sortierbareFelderDerTabelle | nicht ermittelbar | {}", page, e.getMessage());
            return null; // NOSONAR - siehe oben
        }
    }

    /**
     * Sortieren in der Tabelle ({@code <p:ajax event="sort">}): die aktiven Spalten werden in
     * ihrer Prioritaet gespeichert. Eine Spalte, die sich keinem Schluessel zuordnen laesst,
     * faellt weg — sie liesse sich beim naechsten Aufruf nicht auf Sichtbarkeit pruefen.
     */
    public void onSort(SortEvent event) {
        if (event == null || event.getSortBy() == null) {
            return;
        }
        UIComponent quelle = event.getComponent();
        List<SortMeta> aktiv = event.getSortBy().values().stream()
                .filter(Objects::nonNull)
                .filter(m -> m.isActive() && !m.isHeaderRow())
                .sorted()
                .toList();
        List<TableSort> neu = new ArrayList<>();
        for (SortMeta meta : aktiv) {
            String key = spalteZu(quelle, meta);
            if (key == null || leer(meta.getField())) {
                log.debug("[TableSettings:{}] onSort | nicht zuordenbar | columnKey={} | field={}",
                        page, meta.getColumnKey(), meta.getField());
                continue;
            }
            neu.add(new TableSort(key, meta.getField(), meta.getOrder() == SortOrder.DESCENDING));
        }
        if (neu.equals(state.getSortBy())) {
            return;
        }
        state.setSortBy(neu);
        persist();
        log.debug("[TableSettings:{}] onSort | sortBy={}", page, neu);
    }

    /**
     * Spaltenschluessel zu einer sortierten Spalte: ueber den Kopftext wie beim Resize, sonst
     * ueber das Feld, wenn es selbst ein Schluessel der Seite ist (useradmin: {@code username}
     * mit uebersetztem Kopftext).
     */
    String spalteZu(UIComponent quelle, SortMeta meta) {
        if (quelle instanceof ColumnAware tabelle && meta.getColumnKey() != null) {
            try {
                UIColumn spalte = tabelle.findColumn(meta.getColumnKey());
                String key = spalte == null ? null : keyFromHeader(spalte.getHeaderText());
                if (key != null) {
                    return key;
                }
            } catch (RuntimeException e) { // NOSONAR - findColumn wirft bei unbekanntem Schluessel
                log.debug("[TableSettings:{}] spalteZu | {}", page, e.getMessage());
            }
        }
        String field = meta.getField();
        if (field != null && columns.stream().anyMatch(c -> c.getKey().equals(field))) {
            return field;
        }
        return null;
    }

    /**
     * Verwirft die Sortierzuordnung der Tabelle, damit PrimeFaces sie beim naechsten Rendern
     * neu aus {@link #getSortMeta()} aufbaut — nach einem Profilwechsel, der eine andere
     * Sortierung mitbringt.
     */
    void sortierungAngleichen(UIData data) {
        if (data instanceof UITable<?> tabelle) {
            tabelle.setSortByAsMap(null);
        }
    }

    private static List<TableSort> kopie(List<TableSort> sortBy) {
        if (sortBy == null) {
            return null; // NOSONAR - null bleibt "nie sortiert"
        }
        List<TableSort> kopie = new ArrayList<>();
        for (TableSort sort : sortBy) {
            if (sort != null) {
                kopie.add(new TableSort(sort.getColumn(), sort.getField(), sort.isDescending()));
            }
        }
        return kopie;
    }

    private static boolean leer(String wert) {
        return wert == null || wert.isBlank();
    }

    // ── Profile ─────────────────────────────────────────────────────────────

    public List<String> getProfileNames() {
        return state.getProfiles().keySet().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public boolean isProfileActive() {
        return selectedProfile != null && !selectedProfile.isBlank();
    }

    /** Auswahl im Feld: ein Profil wird sofort angewendet. */
    public void onProfileSelected() {
        if (!isProfileActive()) {
            return;
        }
        TableColumnProfile profile = state.getProfiles().get(selectedProfile);
        if (profile == null) {
            meldung = profilMeldung(selectedProfile, "gibt es nicht mehr.");
            selectedProfile = "";
            return;
        }
        state.setColumnWidths(new LinkedHashMap<>(profile.getColumnWidths()));
        state.setColumnVisible(new LinkedHashMap<>(profile.getColumnVisible()));
        state.setTotalWidth(profile.getTotalWidth());
        state.setTargetColumnWidth(profile.getTargetColumnWidth());
        state.setPaginatorTop(profile.getPaginatorTop());
        state.setPaginatorBottom(profile.getPaginatorBottom());
        state.setRowsPerPage(profile.getRowsPerPage());
        state.setSortBy(kopie(profile.getSortBy()));
        state.setActiveProfile(selectedProfile);
        save();
        meldung = profilMeldung(selectedProfile, "angewendet.");
        log.info("[TableSettings:{}] applyProfile | name={}", page, selectedProfile);
    }

    /**
     * Wie {@link #onProfileSelected()}, gleicht danach aber die Tabelle an: ein Profil kann eine
     * andere Seitengroesse oder einen abgeschalteten Paginator mitbringen, siehe
     * {@link #tabelleAngleichen(String)}.
     *
     * @param tabelle Suchausdruck der Tabelle, wie ihn das Tag bekommt (z.B. {@code ":fm:tbl"})
     */
    public void profilAnwenden(String tabelle) {
        onProfileSelected();
        UIData data = tabelleFinden(tabelle);
        if (data != null) {
            angleichen(data);
            sortierungAngleichen(data);
        }
    }

    /** Rueckmeldung zu einem Profil, z. B. {@code Profil 'Kurz' angelegt.} */
    private static String profilMeldung(String name, String zustand) {
        return "Profil '" + name + "' " + zustand;
    }

    /** Legt ein Profil unter dem im Dialog eingegebenen Namen an. */
    public void createProfile() {
        if (newProfileName == null || newProfileName.isBlank()) {
            meldung = "Bitte einen Profilnamen eingeben.";
            return;
        }
        String name = newProfileName.trim();
        if (state.getProfiles().containsKey(name)) {
            meldung = profilMeldung(name, "gibt es schon.");
            return;
        }
        selectedProfile = name;
        state.setActiveProfile(name);
        writeActiveProfile();
        save();
        newProfileName = "";
        meldung = profilMeldung(name, "angelegt.");
        log.info("[TableSettings:{}] createProfile | name={}", page, name);
    }

    /** Entfernt das gewaehlte Profil; der laufende Stand bleibt, wie er ist. */
    public void deleteProfile() {
        if (!isProfileActive()) {
            meldung = "Kein Profil gewaehlt.";
            return;
        }
        String name = selectedProfile;
        if (state.getProfiles().remove(name) == null) {
            meldung = profilMeldung(name, "gibt es nicht mehr.");
            return;
        }
        if (name.equals(state.getActiveProfile())) {
            state.setActiveProfile("");
        }
        save();
        ensureProfile();
        meldung = profilMeldung(name, "geloescht.");
        log.info("[TableSettings:{}] deleteProfile | name={}", page, name);
    }

    /**
     * Sorgt dafuer, dass immer ein Profil aktiv ist — beim ersten Aufruf entsteht aus dem
     * vorhandenen Stand ein Profil {@value #PROFILE_DEFAULT}.
     */
    private void ensureProfile() {
        if (!state.getProfiles().isEmpty()) {
            String aktiv = state.getActiveProfile();
            selectedProfile = aktiv != null && state.getProfiles().containsKey(aktiv)
                    ? aktiv
                    : getProfileNames().get(0);
            state.setActiveProfile(selectedProfile);
            return;
        }
        selectedProfile = PROFILE_DEFAULT;
        state.setActiveProfile(PROFILE_DEFAULT);
        writeActiveProfile();
        save();
    }

    /**
     * Schreibt Breiten und Sichtbarkeiten in das aktive Profil — nach jeder Aenderung, ein eigener
     * Sichern-Knopf entfaellt damit.
     */
    private void writeActiveProfile() {
        String name = state.getActiveProfile();
        if (name == null || name.isBlank()) {
            return;
        }
        TableColumnProfile profile = new TableColumnProfile();
        profile.setColumnWidths(new LinkedHashMap<>(state.getColumnWidths()));
        profile.setColumnVisible(new LinkedHashMap<>(state.getColumnVisible()));
        profile.setTotalWidth(state.getTotalWidth());
        profile.setTargetColumnWidth(state.getTargetColumnWidth());
        profile.setPaginatorTop(state.getPaginatorTop());
        profile.setPaginatorBottom(state.getPaginatorBottom());
        profile.setRowsPerPage(state.getRowsPerPage());
        profile.setSortBy(kopie(state.getSortBy()));
        state.getProfiles().put(name, profile);
    }

    // ── Auf- und Zuklappen des Bereichs ─────────────────────────────────────

    public boolean isColsCollapsed() {
        return !state.isColsExpanded();
    }

    public void onColsToggle(ToggleEvent event) {
        state.setColsExpanded(event.getVisibility() == Visibility.VISIBLE);
        save();
    }

    // ── Persistenz und Zuordnung ────────────────────────────────────────────

    /** Schreibt den Stand samt aktivem Profil weg — nach jeder Spaltenaenderung aufzurufen. */
    public void persist() {
        writeActiveProfile();
        save();
    }

    private void save() {
        if (store == null) {
            // Nach einer Session-Deserialisierung fehlt die Ablage; der Stand
            // bleibt bedienbar, nur das Speichern setzt bis zum Neuaufbau aus.
            return;
        }
        store.save(page, state);
    }

    /**
     * Rechnet den Kopftext einer Spalte auf ihren Schluessel zurueck — das Resize-Ereignis liefert
     * keine eigene Spaltenkennung mit.
     */
    public String keyFromHeader(String headerText) {
        if (headerText == null) {
            return null;
        }
        String h = headerText.trim().toLowerCase(Locale.ROOT);
        for (TableColumn column : columns) {
            if (column.getLabel() != null && column.getLabel().trim().toLowerCase(Locale.ROOT).equals(h)) {
                return column.getKey();
            }
        }
        // Karte 1904: Kopftexte kommen uebersetzt (#{i18n.t('Benutzername')} -> "Username"), das Label ist deutsch.
        for (TableColumn column : columns) {
            String u = column.getLabel() == null ? null
                    : uebersetzer != null ? uebersetzer.apply(column.getLabel()) : perI18nBean(column.getLabel());
            if (u != null && u.trim().toLowerCase(Locale.ROOT).equals(h)) {
                return column.getKey();
            }
        }
        return null;
    }

    /** Uebersetzer fuer Labels (Tests setzen ihn); null = die i18n-Bean der App per EL. */
    private transient java.util.function.UnaryOperator<String> uebersetzer;

    void setUebersetzer(java.util.function.UnaryOperator<String> uebersetzer) {
        this.uebersetzer = uebersetzer;
    }

    /** Label per {@code #{i18n.t(...)}}; ohne Kompilier-Abhaengigkeit, v1-Apps ohne die Bean liefern null. */
    private static String perI18nBean(String label) {
        FacesContext faces = FacesContext.getCurrentInstance();
        try {
            String literal = label.replace("\\", "\\\\").replace("'", "\\'");
            return faces == null ? null
                    : faces.getApplication().evaluateExpressionGet(faces, "#{i18n.t('" + literal + "')}", String.class);
        } catch (RuntimeException e) { // NOSONAR - keine i18n-Bean oder kein Eintrag: Label bleibt unuebersetzt
            return null;
        }
    }

    private int defaultWidth(String key) {
        return columns.stream()
                .filter(c -> c.getKey().equals(key))
                .findFirst()
                .map(TableColumn::getDefaultWidth)
                .orElse(0);
    }

    /** Gespeicherte Pixelbreite einer Spalte, sonst die Vorgabe. */
    private int breiteVon(TableColumn column) {
        String gespeichert = state.getColumnWidths().get(column.getKey());
        if (gespeichert == null || gespeichert.isBlank()) {
            return column.getDefaultWidth();
        }
        try {
            return Math.round(Float.parseFloat(gespeichert.replace("px", "").trim()));
        } catch (NumberFormatException e) {
            return column.getDefaultWidth();
        }
    }

    /** Zahl aus einem Meldeparameter, oder {@code null} wenn unbrauchbar. */
    private static Integer zuBreite(String roh) {
        if (roh == null || roh.isBlank()) {
            return null;
        }
        try {
            int wert = Math.round(Float.parseFloat(roh.trim()));
            return wert >= MIN_SPALTE_PX ? wert : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
