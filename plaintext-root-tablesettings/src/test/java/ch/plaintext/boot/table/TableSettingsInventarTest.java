/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Karte 2086, Teil A: die Funktionen, die das Inventar fuer den Umstieg von seiner eigenen
 * {@code TableAnzeige} auf {@link TableSettings} braucht - je Funktion ein Test, ohne Faces-Kontext.
 *
 * @author info@plaintext.ch
 * @since 2026
 */
class TableSettingsInventarTest {

    private static final List<TableColumn> COLUMNS = List.of(
            TableColumn.knopfSpalte("aktionen", 60, "Bearbeiten", false),
            new TableColumn("nr", "Nr", 80),
            new TableColumn("name", "Name", 200),
            TableColumn.blockPlatzhalter("attribute", "Attribute"),
            TableColumn.ausBlock("attr.farbe", "Farbe", 100),
            TableColumn.knopfSpalte("auswahl", 40));

    private TableStateStore store;
    private TableSettings anzeige;

    @BeforeEach
    void setUp() {
        store = Mockito.mock(TableStateStore.class);
        when(store.load(any(), any())).thenReturn(new TableState());
        anzeige = new TableSettings("inventar", true);
        anzeige.init(store, COLUMNS);
    }

    // ── displayPriority ────────────────────────────────────────────────────

    @Test
    @DisplayName("displayPriority folgt der Reihenfolge ab 1; nicht verschiebbare und fremde Spalten stehen am Ende")
    void displayPriority() {
        assertThat(anzeige.displayPriority("aktionen")).isEqualTo(1);
        assertThat(anzeige.displayPriority("nr")).isEqualTo(2);
        assertThat(anzeige.displayPriority("attribute")).isEqualTo(4);
        assertThat(anzeige.getPrioAmEnde()).isEqualTo(COLUMNS.size() + 1);
        assertThat(anzeige.displayPriority("attr.farbe")).isEqualTo(anzeige.getPrioAmEnde());
        assertThat(anzeige.displayPriority("gibts-nicht")).isEqualTo(anzeige.getPrioAmEnde());

        anzeige.setSpaltenReihenfolge(List.of("name", "nr", "aktionen", "attribute", "auswahl"));
        assertThat(anzeige.displayPriority("name")).isEqualTo(1);
        assertThat(anzeige.displayPriority("aktionen")).isEqualTo(3);
    }

    // ── PagingVorgabe ──────────────────────────────────────────────────────

    @Test
    @DisplayName("PagingVorgabe: oben/unten/beides/aus gelten ohne Benutzerwert, ein Benutzerwert geht vor")
    void pagingVorgabe() {
        TableSettings oben = mitPaging(PagingVorgabe.oben(30));
        assertThat(oben.isPaginatorOben()).isTrue();
        assertThat(oben.isPaginatorUnten()).isFalse();
        assertThat(oben.getPaginatorPosition()).isEqualTo("top");
        assertThat(oben.getRows()).isEqualTo(30);

        TableSettings unten = mitPaging(PagingVorgabe.unten(25));
        assertThat(unten.getPaginatorPosition()).isEqualTo("bottom");

        TableSettings beides = mitPaging(PagingVorgabe.beides(100));
        assertThat(beides.getPaginatorPosition()).isEqualTo("both");

        TableSettings aus = mitPaging(PagingVorgabe.aus());
        assertThat(aus.isPaginator()).isFalse();
        assertThat(aus.getRows()).isZero();
        aus.setPaginatorUnten(true);
        assertThat(aus.isPaginator()).isTrue();
        assertThat(aus.getPaginatorPosition()).isEqualTo("bottom");
    }

    @Test
    @DisplayName("Bestehende Konstruktoren bleiben beim alten Verhalten: Paginator oben und unten")
    void altKonstruktorBeides() {
        assertThat(anzeige.getPaginatorPosition()).isEqualTo("both");
        assertThat(new TableSettings("x", false, 200).getRows()).isEqualTo(200);
    }

    private static TableSettings mitPaging(PagingVorgabe vorgabe) {
        TableSettings t = new TableSettings("p", true, vorgabe);
        t.init(null, COLUMNS);
        return t;
    }

    // ── pageState ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("pageState: Werte und Schalter landen im Stand und wandern mit dem Profil")
    void pageStateImStandUndProfil() {
        anzeige.setPageValue("team", "infra");
        anzeige.setPageValue("erledigte", "true");
        anzeige.persist();
        assertThat(anzeige.pageValue("team")).isEqualTo("infra");
        assertThat(anzeige.pageFlag("erledigte")).isTrue();
        assertThat(anzeige.pageValue("fehlt")).isEmpty();
        assertThat(anzeige.getState().getProfiles().get(TableSettings.PROFILE_DEFAULT).getPageState())
                .containsEntry("team", "infra");

        anzeige.setNewProfileName("Alles");
        anzeige.createProfile();
        anzeige.setPageValue("team", "dev");
        anzeige.setPageValue("erledigte", null);
        anzeige.persist();
        assertThat(anzeige.pageFlag("erledigte")).isFalse();

        anzeige.setSelectedProfile(TableSettings.PROFILE_DEFAULT);
        anzeige.onProfileSelected();
        assertThat(anzeige.pageValue("team")).isEqualTo("infra");
        assertThat(anzeige.pageFlag("erledigte")).isTrue();
    }

    // ── Spaltenbedingung ───────────────────────────────────────────────────

    @Test
    @DisplayName("Spaltenbedingung: eine nicht gerenderte Spalte zaehlt nicht in der Breitensumme")
    void spaltenBedingung() {
        int mit = anzeige.spaltenSumme();
        AtomicBoolean gerendert = new AtomicBoolean(true);
        anzeige.setSpaltenBedingung("nr", gerendert::get);
        assertThat(anzeige.spaltenSumme()).isEqualTo(mit);

        gerendert.set(false);
        assertThat(anzeige.istInDerTabelle("nr")).isFalse();
        assertThat(anzeige.isVisible("nr")).isTrue();
        assertThat(anzeige.spaltenSumme()).isEqualTo(mit - Math.round(80 * 1.3f));

        anzeige.setSpaltenBedingung("nr", null);
        assertThat(anzeige.spaltenSumme()).isEqualTo(mit);
    }

    // ── NachProfilwechsel ──────────────────────────────────────────────────

    @Test
    @DisplayName("NachProfilwechsel laeuft nach dem Anwenden - auch ohne gefundenes Profil")
    void nachProfilwechsel() {
        List<String> aufrufe = new ArrayList<>();
        anzeige.setNachProfilwechsel(() -> aufrufe.add(anzeige.getSelectedProfile()));

        anzeige.setSelectedProfile(TableSettings.PROFILE_DEFAULT);
        anzeige.onProfileSelected();
        anzeige.setSelectedProfile("weg");
        anzeige.onProfileSelected();

        assertThat(aufrufe).containsExactly(TableSettings.PROFILE_DEFAULT, "");
    }

    // ── neuAufsetzen ───────────────────────────────────────────────────────

    @Test
    @DisplayName("neuAufsetzen wechselt Seitenschluessel und Spalten und laedt den Stand dort")
    void neuAufsetzen() {
        TableState dort = new TableState();
        dort.getColumnVisible().put("wert", false);
        when(store.load(eq("konfig/b"), any())).thenReturn(dort);

        anzeige.neuAufsetzen("konfig/b", store, List.of(new TableColumn("schluessel", "Schluessel", 120),
                new TableColumn("wert", "Wert", 200)));

        assertThat(anzeige.getPage()).isEqualTo("konfig/b");
        assertThat(anzeige.getColumns()).extracting(TableColumn::getKey).containsExactly("schluessel", "wert");
        assertThat(anzeige.isVisible("wert")).isFalse();
        anzeige.persist();
        verify(store, atLeastOnce()).save(eq("konfig/b"), any());
    }

    // ── TableColumn listenLabel / waehlbar / verschiebbar ──────────────────

    @Test
    @DisplayName("listenLabel und laufende Nummer: der Name in der Spaltenliste")
    void listenLabel() {
        assertThat(anzeige.columnLabel("aktionen")).isEqualTo("Bearbeiten");
        assertThat(anzeige.columnLabel("name")).isEqualTo("Name");
        assertThat(anzeige.columnLabel("auswahl")).isEqualTo("-2");
        assertThat(anzeige.getColumnItems()).extracting(i -> i.getLabel()).containsExactly("Nr", "Name", "Farbe");
    }

    @Test
    @DisplayName("waehlbar=false: kein Schalter, kein Eintrag in der Auswahl, bleibt bei «Alle aus» sichtbar")
    void nichtWaehlbar() {
        assertThat(anzeige.waehlbar("aktionen")).isFalse();
        assertThat(anzeige.waehlbar("nr")).isTrue();

        anzeige.alleSpaltenAus();
        assertThat(anzeige.isVisible("aktionen")).isTrue();
        assertThat(anzeige.isVisible("attribute")).isTrue();
        assertThat(anzeige.isVisible("nr")).isFalse();
        assertThat(anzeige.getVisibleColumns()).isEmpty();

        anzeige.spalteSichtbarSetzen("aktionen", "false");
        assertThat(anzeige.isVisible("aktionen")).isTrue();

        anzeige.alleSpaltenEin();
        assertThat(anzeige.getVisibleColumns()).containsExactly("nr", "name", "attr.farbe");
    }

    @Test
    @DisplayName("verschiebbar=false: nicht in der Reihenfolge-Liste, behaelt beim Ziehen seinen Platz")
    void nichtVerschiebbar() {
        assertThat(anzeige.getSpaltenReihenfolge()).containsExactly("aktionen", "nr", "name", "attribute", "auswahl");

        anzeige.setSpaltenReihenfolge(List.of("name", "nr", "aktionen", "attribute", "auswahl"));

        assertThat(anzeige.getState().getColumnOrder())
                .containsExactly("name", "nr", "aktionen", "attribute", "attr.farbe", "auswahl");
        assertThat(anzeige.getOrderedColumns()).extracting(TableColumn::getKey)
                .containsExactly("name", "nr", "aktionen", "attribute", "attr.farbe", "auswahl");
    }

    @Test
    @DisplayName("Fuenfer-Konstruktor bleibt Pflichtspalte, Sechser-Konstruktor ist waehlbar/verschiebbar")
    void konstruktoren() {
        TableColumn pflicht = new TableColumn("nr", "Nr", 80, true, true);
        assertThat(pflicht.isRequired()).isTrue();
        assertThat(pflicht.isWaehlbar()).isTrue();
        assertThat(pflicht.isVerschiebbar()).isTrue();

        TableColumn inventar = new TableColumn("x", "X", 80, true, false, false);
        assertThat(inventar.isRequired()).isFalse();
        assertThat(inventar.isWaehlbar()).isFalse();
        assertThat(inventar.isVerschiebbar()).isFalse();
    }

    // ── expandedRows ───────────────────────────────────────────────────────

    @Test
    @DisplayName("expandedRows: auf-/zuklappen, Obergrenze, 'nie angefasst' unterscheidbar")
    void expandedRows() {
        TableState state = anzeige.getState();
        assertThat(state.isExpandedInitialized()).isFalse();

        state.expand("a");
        state.expand("b");
        state.expand("a");
        assertThat(state.getExpandedRows()).containsExactly("b", "a");
        assertThat(state.isExpanded("a")).isTrue();
        state.collapse("a");
        assertThat(state.isExpanded("a")).isFalse();
        assertThat(state.isExpandedInitialized()).isTrue();
        state.expand(" ");
        assertThat(state.getExpandedRows()).containsExactly("b");

        for (int i = 0; i < TableState.MAX_EXPANDED + 5; i++) {
            state.expand("z" + i);
        }
        assertThat(state.getExpandedRows()).hasSize(TableState.MAX_EXPANDED);
        assertThat(state.isExpanded("b")).isFalse();
    }

    // ── Serialisierung der Hooks ───────────────────────────────────────────

    @Test
    @DisplayName("Hooks als Lambdas: TableSettings bleibt serialisierbar (Session), Hooks wirken danach")
    void hooksSerialisierbar() throws Exception {
        anzeige.setSpaltenBedingung("nr", () -> false);
        anzeige.setNachProfilwechsel(() -> { });
        anzeige.setPageValue("team", "infra");

        TableSettings zurueck = rundreise(anzeige);

        assertThat(zurueck.istInDerTabelle("nr")).isFalse();
        assertThat(zurueck.pageValue("team")).isEqualTo("infra");
        zurueck.setSelectedProfile(TableSettings.PROFILE_DEFAULT);
        zurueck.onProfileSelected();
    }

    private static TableSettings rundreise(TableSettings t) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(t);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (TableSettings) in.readObject();
        }
    }

    @Test
    @DisplayName("Profil traegt pageState auch beim Speichern ueber die Ablage")
    void profilMitPageStateWirdGespeichert() {
        anzeige.setPageValue("filter", "offen");
        anzeige.persist();
        ArgumentCaptor<TableState> gespeichert = ArgumentCaptor.forClass(TableState.class);
        verify(store, atLeastOnce()).save(anyString(), gespeichert.capture());
        assertThat(gespeichert.getValue().getProfiles().get(TableSettings.PROFILE_DEFAULT).getPageState())
                .containsEntry("filter", "offen");
    }
}
