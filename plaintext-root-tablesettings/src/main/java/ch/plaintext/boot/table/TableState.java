/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Was ein Benutzer sich an einer Tabelle eingerichtet hat — seitenneutral.
 *
 * <p>Reines Datenobjekt: kein Spring, kein JPA, keine Faces-Abhaengigkeit. Genau deshalb kann die
 * App es ablegen, wo sie will — als JSON-Spalte, als Datei, als Confluence-Sicherung. Die
 * Anbindung laeuft ueber {@link TableStateStore}.</p>
 *
 * <p><b>Neue Felder duerfen jederzeit dazukommen.</b> Wer den Stand als JSON ablegt, liest
 * aeltere Staende weiter: unbekannte Felder ignoriert ein Mapper, fehlende bleiben auf ihrem
 * Default. Ein Feld <i>umbenennen</i> ist dagegen ein Bruch — der alte Stand faellt dann still
 * auf die Vorgabe zurueck, und der Benutzer findet seine Spalten neu sortiert vor.</p>
 *
 * <p><b>Die Gegenrichtung ist mit abgesichert.</b> Der Mapper der Ablage
 * ({@code SimpleStorableConverter}) bricht bei <i>unbekannten</i> Feldern ab — und gibt dann
 * fuer den ganzen Datensatz {@code null} zurueck. Ohne {@code ignoreUnknown} liesse ein Rollback
 * auf eine aeltere root-Version jeden Benutzer, der schon ein neues Feld gespeichert hat, mit
 * leeren Einstellungen zurueck.</p>
 *
 * @author info@plaintext.ch
 * @since 2026
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TableState implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Sichtbarkeit der Spalten, Schluessel ist {@link TableColumn#getKey()}.
     * Nicht enthaltene Spalten starten mit ihrer Vorgabe.
     */
    private Map<String, Boolean> columnVisible = new LinkedHashMap<>();

    /**
     * Spaltenbreiten, Wert ist die CSS-Breite inkl. Einheit (z.B. {@code "180px"}).
     * Nicht enthaltene Spalten rendern mit ihrer Standardbreite.
     */
    private Map<String, String> columnWidths = new LinkedHashMap<>();

    /**
     * Gesamtbreite der Tabelle in Pixeln, oder {@code null} fuer "aus den Spalten rechnen".
     * Warum es diese Angabe ueberhaupt gibt, steht bei
     * {@link TableSettings#getTabellenBreite()}.
     */
    private Integer totalWidth;

    /** Breite, auf die der Knopf im Spaltenkopf eine einzelne Spalte setzt. */
    private Integer targetColumnWidth;

    /**
     * Steht der Bereich "Anzeige" offen? Das gehoert in den gespeicherten Stand und nicht in die
     * Bean: der Bereich wird bei jeder Spaltenaenderung mitgerendert, und ein Wert, der nur in der
     * Bean auf "zu" startet, klappte ihn dabei jedes Mal zu — mitten im Einrichten.
     */
    private boolean colsExpanded;

    /**
     * Benannte Spaltenprofile. Der Schluessel ist der vom Benutzer vergebene Name; er dient
     * zugleich als Anzeige im Auswahlfeld.
     */
    private Map<String, TableColumnProfile> profiles = new LinkedHashMap<>();

    /** Zuletzt angewendetes Profil — nur fuer die Vorauswahl im Feld. */
    private String activeProfile = "";

    /**
     * Paginator oberhalb der Tabelle (Karte 1336). {@code null} heisst "nie verstellt" und ergibt
     * das Verhalten vor diesem Feld: PrimeFaces zeigt den Paginator oben <i>und</i> unten.
     * Deshalb {@code Boolean} und nicht {@code boolean} — ein gespeicherter Altstand ohne das Feld
     * darf nicht auf "aus" fallen.
     */
    private Boolean paginatorTop;

    /** Paginator unterhalb der Tabelle; {@code null} wie bei {@link #paginatorTop}: an. */
    private Boolean paginatorBottom;

    /** Zeilen pro Seite; {@code null} = Vorgabe der Seite (Karte 1336). */
    private Integer rowsPerPage;

    /**
     * Sortierung (Karte 1346), in Prioritaetsreihenfolge. {@code null} heisst "nie sortiert" und
     * ergibt die Vorgabe der Seite; eine leere Liste heisst "ausdruecklich unsortiert".
     */
    private List<TableSort> sortBy;

    /**
     * Reihenfolge der Spalten, wie der Benutzer sie zuletzt per Ziehen im Kopf oder im
     * Spalten-Panel angeordnet hat (Karte 1853) — eine Permutation der Spaltenschluessel.
     * {@code null} heisst "nie verschoben" und ergibt die Markup-Reihenfolge der Seite.
     */
    private List<String> columnOrder;

    /**
     * Schalter «Verschieben» (draggableColumns): duerfen die Spaltenkoepfe per Ziehen
     * umgeordnet werden? {@code null} = aus — damit startet die Tabelle ohne versehentliches
     * Verschieben, wie der Schalter es will (Karte 1853).
     */
    private Boolean moveEnabled;

    /**
     * Schalter «Groesse aendern» (resizableColumns): duerfen die Spaltenbreiten gezogen werden?
     * {@code null} = an — so verhaelt sich der Bestand vor dem Schalter (Karte 1853).
     */
    private Boolean resizeEnabled;

    /**
     * Festgehaltener Spaltenstand (Karte 1853 Runde 7): im Ansicht-Reiter erfasst, per Doppelklick
     * auf den Modus-Knopf wiederhergestellt. {@code null} = keiner erfasst.
     */
    private FesterStand festerStand;

    /**
     * Was eine Seite ausser Spalten und Breiten noch merkt (Karte 2086): Filter, Schalter ueber der
     * Tabelle. Flache Zeichenkettenablage, Schluessel vergibt die Seite
     * ({@link TableSettings#pageValue}, {@link TableSettings#setPageValue}); wandert mit ins Profil.
     */
    private Map<String, String> pageState = new LinkedHashMap<>();

    /**
     * Obergrenze fuer {@link #expandedRows}: der Stand ist ein Datensatz je Benutzer und Seite;
     * beim Ueberlauf faellt der aelteste Eintrag weg (der Knoten startet dann wieder zugeklappt).
     */
    public static final int MAX_EXPANDED = 2000;

    /**
     * Aufgeklappte Zeilen einer Baum- oder Expansionstabelle, als Zeilenschluessel der Seite
     * (Karte 2086). Gehoert in den Stand, nicht ins Profil: ein Profil beschreibt eine Ansicht,
     * nicht den Punkt, an dem jemand im Baum steht.
     */
    private List<String> expandedRows = new ArrayList<>();

    /** Hat der Benutzer je selbst auf- oder zugeklappt? Unterscheidet "nie angefasst" von "alles zu". */
    private boolean expandedInitialized;

    /** Merkt eine aufgeklappte Zeile und haelt dabei {@link #MAX_EXPANDED} ein. */
    public void expand(String rowKey) {
        if (rowKey == null || rowKey.isBlank()) {
            return;
        }
        expandedInitialized = true;
        expandedRows.remove(rowKey);
        expandedRows.add(rowKey);
        while (expandedRows.size() > MAX_EXPANDED) {
            expandedRows.remove(0);
        }
    }

    /** Vergisst eine aufgeklappte Zeile. */
    public void collapse(String rowKey) {
        if (rowKey != null && !rowKey.isBlank()) {
            expandedInitialized = true;
            expandedRows.remove(rowKey);
        }
    }

    /** Ist die Zeile aufgeklappt? */
    public boolean isExpanded(String rowKey) {
        return rowKey != null && expandedRows.contains(rowKey);
    }
}
