/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import jakarta.faces.component.UIComponent;
import jakarta.faces.component.UIData;
import jakarta.faces.component.behavior.Behavior;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.primefaces.component.column.Column;
import org.primefaces.event.ReorderEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Karte 1853: «Alle aus» mit Pflichtspalten, die Schalter «Verschieben»/«Grösse ändern» und die
 * gespeicherte Spaltenreihenfolge — alles ohne Faces-Kontext, wie die Bestandstests in diesem
 * Verzeichnis.
 *
 * @author info@plaintext.ch
 * @since 2026
 */
class TableSettingsSpaltenauswahlTest {

    private static final List<TableColumn> COLUMNS = List.of(
            new TableColumn("nr", "Nr", 80, true, true),
            new TableColumn("name", "Name", 220, true, true),
            new TableColumn("typ", "Typ", 140),
            new TableColumn("aktionen", "Aktionen", 120));

    private TableStateStore store;
    private TableSettings anzeige;

    @BeforeEach
    void setUp() {
        store = Mockito.mock(TableStateStore.class);
        when(store.load(any(), any())).thenReturn(new TableState());
        anzeige = new TableSettings("testpage", true);
        anzeige.init(store, COLUMNS);
    }

    @Test
    @DisplayName("«Alle aus» blendet alle abwählbaren Spalten aus — Pflichtspalten bleiben")
    void alleAusLaesstPflichtspalten() {
        anzeige.setVisibleColumns(List.of("nr", "name", "typ", "aktionen"));
        anzeige.alleSpaltenAus();

        assertThat(anzeige.isVisible("nr")).isTrue();
        assertThat(anzeige.isVisible("name")).isTrue();
        assertThat(anzeige.isVisible("typ")).isFalse();
        assertThat(anzeige.isVisible("aktionen")).isFalse();
    }

    @Test
    @DisplayName("Der Schalter-Guard hebt eine ausgeschaltete Pflichtspalte wieder an")
    void guardHebtPflichtspalteWiederAn() {
        anzeige.getSpaltenSichtbarkeit().put("name", false);
        anzeige.onSichtbarkeitGeaendert();

        assertThat(anzeige.isVisible("name")).isTrue();
    }

    @Test
    @DisplayName("Sichtbarkeits-Map liest mit Vorgabe-Fallback und schreibt in den Stand")
    void sichtbarkeitsMapMitVorgabe() {
        Map<String, Boolean> schalter = anzeige.getSpaltenSichtbarkeit();

        assertThat(schalter.get("typ")).isTrue();          // defaultVisible
        assertThat(schalter.get("unbekannt")).isTrue();    // ohne Vorgabe gilt isVisible: sichtbar

        schalter.put("typ", false);
        assertThat(anzeige.isVisible("typ")).isFalse();
    }

    @Test
    @DisplayName("Schalter starten ohne versehentliches Verschieben — Grösse ändern wie bisher an")
    void schalterDefaults() {
        assertThat(anzeige.isVerschiebenAktiv()).isFalse();
        assertThat(anzeige.isGroesseAendernAktiv()).isTrue();

        anzeige.setVerschiebenAktiv(true);
        anzeige.setGroesseAendernAktiv(false);
        assertThat(anzeige.isVerschiebenAktiv()).isTrue();
        assertThat(anzeige.isGroesseAendernAktiv()).isFalse();
    }

    @Test
    @DisplayName("Reihenfolge: ohne Stand gilt die Markup-Reihenfolge, mit Stand die gespeicherte")
    void reihenfolgeLesen() {
        assertThat(anzeige.getSpaltenReihenfolge())
                .containsExactly("nr", "name", "typ", "aktionen");

        anzeige.setSpaltenReihenfolge(List.of("typ", "name", "aktionen", "nr"));
        assertThat(anzeige.getSpaltenReihenfolge())
                .containsExactly("typ", "name", "aktionen", "nr");
        assertThat(anzeige.getOrderedColumns().stream().map(TableColumn::getLabel))
                .containsExactly("Typ", "Name", "Aktionen", "Nr");
    }

    @Test
    @DisplayName("colReorder: eine sichtbare Spalte wandert, unsichtbare behalten ihre Position")
    void colReorderVerschiebtSichtbare() {
        anzeige.setVisibleColumns(List.of("nr", "name", "aktionen")); // typ ist unsichtbar
        anzeige.setSpaltenReihenfolge(List.of("nr", "name", "typ", "aktionen"));

        // nr(0), name(1), aktionen(2): aktionen von 2 nach 0
        anzeige.onColReorder(reorder(2, 0), ":fm:tbl");

        // sichtbar neu: aktionen, nr, name — unsichtbare typ bleibt an ihrem Gesamtindex (2)
        assertThat(anzeige.getSpaltenReihenfolge())
                .containsExactly("aktionen", "nr", "typ", "name");
    }

    @Test
    @DisplayName("Reihenfolge anwenden: die Spalten-Kinder der Tabelle werden umgeordnet")
    void reihenfolgeAnwendenSortiertKinder() {
        anzeige.setSpaltenReihenfolge(List.of("aktionen", "name", "typ", "nr"));

        UIData tabelle = mock(UIData.class);
        List<UIComponent> kinder = new ArrayList<UIComponent>(List.of(
                spalte("nr"), spalte("name"), spalte("typ"), spalte("aktionen")));
        when(tabelle.getChildren()).thenReturn(kinder);

        anzeige.reihenfolgeAnwenden(tabelle);

        assertThat(kinder.stream().map(k -> ((Column) k).getHeaderText()))
                .containsExactly("Aktionen", "Name", "Typ", "Nr");
    }

    @Test
    @DisplayName("Reihenfolge anwenden ohne gespeicherten Stand lässt die Kinder unangetastet")
    void reihenfolgeAnwendenOhneStand() {
        UIData tabelle = mock(UIData.class);
        List<UIComponent> kinder = new ArrayList<UIComponent>(List.of(spalte("nr"), spalte("name")));
        when(tabelle.getChildren()).thenReturn(kinder);

        anzeige.reihenfolgeAnwenden(tabelle);

        assertThat(kinder.stream().map(k -> ((Column) k).getHeaderText()))
                .containsExactly("Nr", "Name");
    }

    private static UIComponent spalte(String key) {
        TableColumn definition = COLUMNS.stream()
                .filter(c -> c.getKey().equals(key)).findFirst().orElseThrow();
        Column spalte = mock(Column.class);
        when(spalte.getHeaderText()).thenReturn(definition.getLabel());
        return spalte;
    }

    private static ReorderEvent reorder(int from, int to) {
        return new ReorderEvent(mock(UIComponent.class), mock(Behavior.class), from, to);
    }
}
