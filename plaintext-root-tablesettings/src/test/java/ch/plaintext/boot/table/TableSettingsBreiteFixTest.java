/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.primefaces.component.api.UIColumn;
import org.primefaces.event.ColumnResizeEvent;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * Karte 2160, Punkt 2: exakte Breite per Doppelklick auf den Modus-Knopf im Modus
 * «Vergroessern» — das Feld im Ansicht-Reiter gilt der zuletzt gezogenen Spalte und der Wert
 * wandert ueber {@code persist()} ins aktive Profil.
 *
 * <p>Der Zug im Tabellenkopf kommt als {@link ColumnResizeEvent}; wie im Bestand (TableSettingsTest)
 * als Mockito-Mock, statt Faces-Komponenten aufzubauen.</p>
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
    @DisplayName("Doppelklick im Resize-Modus: Feld oeffnet sich mit der Breite der zuletzt gezogenen Spalte")
    void oeffnenNachResize() {
        resizeGemeldet("Name", 123);

        assertThat(anzeige.isBreiteFixOffen()).isFalse();
        anzeige.breiteFixOeffnen();

        assertThat(anzeige.isBreiteFixOffen()).isTrue();
        assertThat(anzeige.getBreiteFixSpalte()).isEqualTo("name");
        assertThat(anzeige.getBreiteFixWert()).isEqualTo(123);
    }

    @Test
    @DisplayName("Uebernehmen schreibt die Breite in den Stand und ins aktive Profil, das Feld klappt zu")
    void uebernehmenLandetImProfil() {
        resizeGemeldet("Name", 123);
        anzeige.breiteFixOeffnen();
        anzeige.setBreiteFixWert(222);

        anzeige.breiteFixUebernehmen(null);

        assertThat(anzeige.isBreiteFixOffen()).isFalse();
        assertThat(anzeige.widthStyle("name")).isEqualTo("width:222px;");
        assertThat(anzeige.getState().getProfiles().get(TableSettings.PROFILE_DEFAULT).getColumnWidths())
                .containsEntry("name", "222px");
        Mockito.verify(store, Mockito.atLeastOnce()).save(Mockito.eq("testpage"), any(TableState.class));
    }

    @Test
    @DisplayName("Ohne gezogene Spalte oder unter dem Minimum passiert nichts")
    void ungueltigeEingaben() {
        anzeige.breiteFixOeffnen();
        assertThat(anzeige.getBreiteFixWert())
                .as("keine Spalte gezogen -> kein Vorschlag").isNull();
        anzeige.setBreiteFixWert(222);
        anzeige.breiteFixUebernehmen(null);
        assertThat(anzeige.getState().getColumnWidths()).doesNotContainKey("name");

        resizeGemeldet("Name", 123);
        anzeige.breiteFixOeffnen();
        anzeige.setBreiteFixWert(10);
        anzeige.breiteFixUebernehmen(null);

        assertThat(anzeige.getState().getColumnWidths())
                .containsEntry("name", "123px");
        assertThat(anzeige.isBreiteFixOffen())
                .as("verworfener Wert laesst das Feld offen").isTrue();
    }

    private void resizeGemeldet(String kopfText, int px) {
        UIColumn column = Mockito.mock(UIColumn.class);
        Mockito.when(column.getHeaderText()).thenReturn(kopfText);
        ColumnResizeEvent event = Mockito.mock(ColumnResizeEvent.class);
        Mockito.when(event.getColumn()).thenReturn(column);
        Mockito.when(event.getWidth()).thenReturn(px);
        anzeige.onColumnResize(event);
    }
}
