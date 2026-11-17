/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import jakarta.faces.component.UIData;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import org.primefaces.component.api.UIColumn;
import org.primefaces.component.api.UITable;
import org.primefaces.event.ColumnResizeEvent;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Feld «Feste Breite» (Karte 2160, neu geregelt mit Karte 2333, Daniel 17.11.): der Wert lebt nur
 * in der Sitzung. Ein Doppelklick auf den Verbreitern-Griff fuellt das leere Feld mit der Breite
 * der Spalte; steht ein Wert im Feld, bekommt die Spalte genau diese Breite.
 *
 * <p>Der Doppelklick meldet die Position des Kopfes ({@code kopf}-Parameter) — die Aufloesung auf
 * den Schluessel steckt in {@link TableKomponenten#kopfKey} und wird mit einer gemockten Tabelle
 * geprueft; der Kern {@code breiteFixAufSpalte} laeuft ohne Faces.</p>
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
    @DisplayName("Kopf-Position wird auf den Spaltenschluessel aufgeloest; ohne Tabelle passiert nichts")
    void kopfPosition() {
        FacesContext faces = facesMit(Map.of("kopf", "0"));
        try (MockedStatic<FacesContext> holder = mockStatic(FacesContext.class)) {
            holder.when(FacesContext::getCurrentInstance).thenReturn(faces);
            anzeige.breiteFixDoppelklick(null);   // keine Tabelle: keine Spalte, nichts geschieht
            assertThat(anzeige.getBreiteFixWert()).isNull();
        }
        assertThat(TableKomponenten.kopfKey(tabelleMitZweiSpalten(), anzeige::keyFromHeader, 0))
                .isEqualTo("name");
        assertThat(TableKomponenten.kopfKey(tabelleMitZweiSpalten(), anzeige::keyFromHeader, 1))
                .isEqualTo("team");
        assertThat(TableKomponenten.kopfKey(tabelleMitZweiSpalten(), anzeige::keyFromHeader, 5))
                .isNull();
        assertThat(TableKomponenten.kopfKey(Mockito.mock(UIData.class),
                anzeige::keyFromHeader, 0)).isNull();
    }

    @Test
    @DisplayName("Leeres Feld: der erste Doppelklick uebernimmt die Breite der Spalte und aendert nichts")
    void ersterDoppelklickFuelltDasFeld() {
        resizeGemeldet("Name", 123);
        Mockito.clearInvocations(store);

        anzeige.breiteFixAufSpalte("name", null);

        assertThat(anzeige.getBreiteFixWert()).isEqualTo(123);
        assertThat(anzeige.getState().getColumnWidths()).doesNotContainKey("team");
        Mockito.verifyNoInteractions(store);
    }

    @Test
    @DisplayName("Wert im Feld: jeder weitere Doppelklick setzt ihn auf die jeweilige Spalte und speichert die Breite")
    void weitereDoppelklicksSetzenDenWert() {
        anzeige.setBreiteFixWert(222);

        anzeige.breiteFixAufSpalte("team", null);
        anzeige.breiteFixAufSpalte("name", null);

        assertThat(anzeige.widthStyle("team")).isEqualTo("width:222px;");
        assertThat(anzeige.widthStyle("name")).isEqualTo("width:222px;");
        assertThat(anzeige.getBreiteFixWert()).isEqualTo(222);
        assertThat(anzeige.getState().getProfiles().get(TableSettings.PROFILE_DEFAULT).getColumnWidths())
                .containsEntry("name", "222px");
        Mockito.verify(store, Mockito.atLeastOnce()).save(Mockito.eq("testpage"), any(TableState.class));
    }

    @Test
    @DisplayName("Unbekannte Spalte, zu kleiner Wert, geleertes Feld")
    void ungueltigeEingaben() {
        anzeige.setBreiteFixWert(10);
        anzeige.breiteFixAufSpalte("name", null);
        anzeige.breiteFixAufSpalte("gibtEsNicht", null);
        anzeige.breiteFixAufSpalte(null, null);
        assertThat(anzeige.getState().getColumnWidths()).isEmpty();

        anzeige.setBreiteFixWert(0);   // geleertes Feld: der naechste Doppelklick fuellt es wieder
        assertThat(anzeige.getBreiteFixWert()).isNull();
    }

    /** Meldet einen Zug per {@link ColumnResizeEvent}-Mock — wie im Bestand (TableSettingsTest). */
    private void resizeGemeldet(String header, int breite) {
        ColumnResizeEvent event = Mockito.mock(ColumnResizeEvent.class);
        UIColumn column = Mockito.mock(UIColumn.class);
        Mockito.when(column.getHeaderText()).thenReturn(header);
        Mockito.when(event.getColumn()).thenReturn(column);
        Mockito.when(event.getWidth()).thenReturn(breite);
        anzeige.onColumnResize(event);
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

    private static FacesContext facesMit(Map<String, String> params) {
        ExternalContext external = Mockito.mock(ExternalContext.class);
        Mockito.when(external.getRequestParameterMap()).thenReturn(params);
        FacesContext faces = Mockito.mock(FacesContext.class);
        Mockito.when(faces.getExternalContext()).thenReturn(external);
        return faces;
    }
}
