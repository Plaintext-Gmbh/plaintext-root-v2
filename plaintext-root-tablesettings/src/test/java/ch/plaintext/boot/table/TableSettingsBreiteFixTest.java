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
 * Karte 2160, Daniel 15.11.: exakte Breite per Doppelklick auf den Verbreitern-Griff der Spalte —
 * das Feld im Ansicht-Reiter gilt der gewaehlten (sonst der zuletzt gezogenen) Spalte und speichert
 * beim change-Event sofort, ohne Uebernehmen-Knopf.
 *
 * <p>Der Zug im Tabellenkopf kommt als {@link ColumnResizeEvent}; wie im Bestand (TableSettingsTest)
 * als Mockito-Mock, statt Faces-Komponenten aufzubauen. Der Doppelklick selbst meldet die Position
 * des Kopfes ({@code kopf}-Parameter) — die Aufloesung auf den Schluessel steckt in
 * {@link TableKomponenten#kopfKey} und wird mit einer gemockten Tabelle geprueft.</p>
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
    @DisplayName("Doppelklick waehlt die Spalte ueber die Kopf-Position und schlaegt ihre Breite vor")
    void oeffnenMitKopfPosition() {
        FacesContext faces = facesMit(Map.of("kopf", "0"));
        try (MockedStatic<FacesContext> holder = mockStatic(FacesContext.class)) {
            holder.when(FacesContext::getCurrentInstance).thenReturn(faces);

            anzeige.breiteFixOeffnen(null);   // tabelle null: keine Aufloesung, Ziel bleibt die gezogene Spalte
            assertThat(anzeige.getBreiteFixSpalte()).isNull();
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
    @DisplayName("Ziehen vor dem Doppelklick: das Feld schlaegt die Breite der zuletzt gezogenen Spalte vor")
    void oeffnenNachResize() {
        resizeGemeldet("Name", 123);
        oeffneOhneKopf();

        assertThat(anzeige.getBreiteFixSpalte()).isEqualTo("name");
        assertThat(anzeige.getBreiteFixWert()).isEqualTo(123);
    }

    @Test
    @DisplayName("change speichert die Breite in den Stand und ins aktive Profil")
    void uebernehmenLandetImProfil() {
        resizeGemeldet("Name", 123);
        oeffneOhneKopf();
        anzeige.setBreiteFixWert(222);

        anzeige.breiteFixUebernehmen(null);

        assertThat(anzeige.widthStyle("name")).isEqualTo("width:222px;");
        assertThat(anzeige.getState().getProfiles().get(TableSettings.PROFILE_DEFAULT).getColumnWidths())
                .containsEntry("name", "222px");
        Mockito.verify(store, Mockito.atLeastOnce()).save(Mockito.eq("testpage"), any(TableState.class));
    }

    @Test
    @DisplayName("Ohne gewaehlte Spalte oder unter dem Minimum passiert nichts")
    void ungueltigeEingaben() {
        oeffneOhneKopf();
        assertThat(anzeige.getBreiteFixWert()).isNull();
        anzeige.setBreiteFixWert(222);
        anzeige.breiteFixUebernehmen(null);
        assertThat(anzeige.getState().getColumnWidths()).isEmpty();

        resizeGemeldet("Name", 123);
        anzeige.setBreiteFixWert(10);
        anzeige.breiteFixUebernehmen(null);
        // Der Zug selbst steht im Stand (123px); der zu kleine Eintrag wurde verworfen.
        assertThat(anzeige.getState().getColumnWidths()).containsExactly(
                org.assertj.core.api.Assertions.entry("name", "123px"));
    }

    /** Der Doppelklick-Pfad ohne gueltige Kopf-Position: leere Parameter, keine Tabelle. */
    private void oeffneOhneKopf() {
        FacesContext faces = facesMit(Map.of());
        try (MockedStatic<FacesContext> holder = mockStatic(FacesContext.class)) {
            holder.when(FacesContext::getCurrentInstance).thenReturn(faces);
            anzeige.breiteFixOeffnen(null);
        }
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
