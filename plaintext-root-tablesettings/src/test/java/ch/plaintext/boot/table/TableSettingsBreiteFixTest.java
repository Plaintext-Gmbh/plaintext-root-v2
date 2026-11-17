/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import jakarta.faces.component.UIData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.primefaces.component.api.UIColumn;
import org.primefaces.component.api.UITable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * Karte 2160/2333 (Daniel 17.11.): «Breite fix» ist ein Sitzungswert im Modus Resize. Leer:
 * der Doppelklick auf den Griff einer Spalte uebernimmt deren gerenderte Breite ins Feld.
 * Gefuellt: jeder Doppelklick setzt die jeweilige Spalte auf den Wert und speichert wie ein Zug.
 * Der Modus-Wechsel leert das Feld (TableSettingsTest#griffeJeModus).
 *
 * <p>Der Doppelklick meldet die Position des Kopfes; die Aufloesung auf den Schluessel steckt in
 * {@link TableKomponenten#kopfKey} und laeuft hier gegen eine gemockte Tabelle.</p>
 *
 * @author info@plaintext.ch
 * @since 2026
 */
class TableSettingsBreiteFixTest {

    private static final List<TableColumn> COLUMNS = List.of(
            new TableColumn("name", "Name", 160),
            new TableColumn("team", "TeamKey", 110));

    private TableStateStore store;

    private TableSettings anzeige;

    @BeforeEach
    void setUp() {
        store = Mockito.mock(TableStateStore.class);
        Mockito.when(store.load(any())).thenReturn(new TableState());
        anzeige = new TableSettings("testpage", true);
        anzeige.init(store, COLUMNS);
    }

    @Test
    @DisplayName("Leeres Feld: der Doppelklick uebernimmt die gerenderte Breite der Spalte, speichert nichts")
    void leerUebernimmtBreite() {
        Mockito.clearInvocations(store);   // init() legt das Standardprofil ab - nicht zaehlen
        anzeige.breiteFixDoppelklick(tabelleMitZweiSpalten(), 1, 137);

        assertThat(anzeige.getBreiteFixWert()).isEqualTo(137);
        assertThat(anzeige.getState().getColumnWidths()).isEmpty();
        Mockito.verify(store, Mockito.never()).save(any(), any());
    }

    @Test
    @DisplayName("Leeres Feld, Spalte schon gezogen: die gespeicherte Breite, nicht die gerenderte")
    void leerBevorzugtGespeicherteBreite() {
        anzeige.getState().getColumnWidths().put("team", "150px");

        anzeige.breiteFixDoppelklick(tabelleMitZweiSpalten(), 1, 137);

        assertThat(anzeige.getBreiteFixWert()).isEqualTo(150);
    }

    @Test
    @DisplayName("Leeres Feld ohne brauchbare gerenderte Breite: die Vorgabe der Spalte")
    void leerOhneGerenderteBreite() {
        anzeige.breiteFixDoppelklick(tabelleMitZweiSpalten(), 0, -1);

        assertThat(anzeige.getBreiteFixWert()).isEqualTo(Math.round(160 * 1.3f));
    }

    @Test
    @DisplayName("Gefuelltes Feld: der Doppelklick setzt die Spalte auf den Wert, Stand und Profil")
    void gefuelltStempeltSpalte() {
        anzeige.setBreiteFixWert(222);

        anzeige.breiteFixDoppelklick(tabelleMitZweiSpalten(), 1, 137);

        assertThat(anzeige.widthStyle("team")).isEqualTo("width:222px;");
        assertThat(anzeige.widthStyle("name")).isEqualTo("width:208px;");   // unberuehrt: Vorgabe
        assertThat(anzeige.getState().getProfiles().get(TableSettings.PROFILE_DEFAULT).getColumnWidths())
                .containsEntry("team", "222px");
        assertThat(anzeige.getBreiteFixWert()).as("der Stempel bleibt fuer die naechste Spalte").isEqualTo(222);
        Mockito.verify(store, Mockito.atLeastOnce()).save(Mockito.eq("testpage"), any(TableState.class));
    }

    @Test
    @DisplayName("Unbekannter Kopf oder Wert unter dem Minimum: nichts passiert")
    void ungueltigeEingaben() {
        anzeige.breiteFixDoppelklick(tabelleMitZweiSpalten(), 5, 137);
        assertThat(anzeige.getBreiteFixWert()).isNull();
        anzeige.breiteFixDoppelklick(null, 0, 137);
        assertThat(anzeige.getBreiteFixWert()).isNull();

        anzeige.setBreiteFixWert(10);
        anzeige.breiteFixDoppelklick(tabelleMitZweiSpalten(), 0, 137);
        assertThat(anzeige.getState().getColumnWidths()).isEmpty();
    }

    /** Eine gemockte Tabelle mit zwei gerenderten Spalten, wie TableSettingsTest sie aufbaut. */
    private static UIData tabelleMitZweiSpalten() {
        UIColumn col1 = Mockito.mock(UIColumn.class);
        UIColumn col2 = Mockito.mock(UIColumn.class);
        Mockito.when(col1.isRendered()).thenReturn(true);
        Mockito.when(col2.isRendered()).thenReturn(true);
        Mockito.when(col1.getHeaderText()).thenReturn("Name");
        Mockito.when(col2.getHeaderText()).thenReturn("TeamKey");
        UIData tabelle = Mockito.mock(UIData.class,
                Mockito.withSettings().extraInterfaces(UITable.class));
        Mockito.doReturn(List.of(col1, col2)).when((UITable<?>) tabelle).getColumns();
        return tabelle;
    }
}
