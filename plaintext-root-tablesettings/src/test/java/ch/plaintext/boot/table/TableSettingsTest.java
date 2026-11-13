/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import jakarta.faces.component.UIData;
import org.primefaces.component.api.UIColumn;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * Die Anzeige-Steuerung ohne Faces-Kontext: Sichtbarkeiten, Breiten und Profile rechnen allein
 * auf dem Stand. Das ist kein Zufall, sondern die Bedingung dafuer, dass sie hier ueberhaupt
 * pruefbar ist — alles, was eine Seite von {@link TableSettings} sieht, laeuft ueber genau diese
 * Methoden.
 *
 * @author info@plaintext.ch
 * @since 2026
 */
class TableSettingsTest {

    private static final List<TableColumn> COLUMNS = List.of(
            new TableColumn("name", "Name", 160),
            new TableColumn("team", "TeamKey", 110),
            new TableColumn("extra", "Extra", 200, false));

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
    @DisplayName("Vorgaben greifen, solange nichts verstellt ist — und das Standardprofil entsteht sofort")
    void vorgabenUndStandardprofil() {
        assertThat(anzeige.isVisible("name")).isTrue();
        assertThat(anzeige.isVisible("extra")).isFalse();
        assertThat(anzeige.getVisibleColumns()).containsExactly("name", "team");
        assertThat(anzeige.getSelectedProfile()).isEqualTo(TableSettings.PROFILE_DEFAULT);
        assertThat(anzeige.getState().getProfiles()).containsKey(TableSettings.PROFILE_DEFAULT);
    }

    @Test
    @DisplayName("Ohne Ablage bleibt die Bedienung nutzbar, nur das Speichern setzt aus")
    void ohneAblageBedienbar() {
        TableSettings frei = new TableSettings("testpage", true);
        frei.init(null, COLUMNS);

        frei.alleSpaltenEin();

        assertThat(frei.getVisibleColumns()).containsExactly("name", "team", "extra");
    }

    @Test
    @DisplayName("Breitenstil kommt aus dem Stand, sonst aus der Vorgabe; die Summe folgt den sichtbaren Spalten")
    void breitenUndSumme() {
        assertThat(anzeige.widthStyle("name")).isEqualTo("width:208px;");   // 160 x 1.3 (Runde 6)
        assertThat(anzeige.spaltenSumme()).isEqualTo(351);   // 208 + 143, extra ist aus

        anzeige.getState().getColumnWidths().put("name", "300px");
        assertThat(anzeige.widthStyle("name")).isEqualTo("width:300px;");
        assertThat(anzeige.spaltenSumme()).isEqualTo(443);
        assertThat(anzeige.getTabellenBreite()).isEqualTo("width: 443px;");

        anzeige.setVisibleColumns(List.of("name", "team", "extra"));
        assertThat(anzeige.spaltenSumme()).isEqualTo(703);   // 300 + 143 + 260
    }

    @Test
    @DisplayName("Eine Seite ohne Breiten liefert leere Stile und keine Tabellenbreite")
    void ohneBreitenKeineStile() {
        TableSettings ohne = new TableSettings("andereseite", false);
        ohne.init(store, COLUMNS);
        assertThat(ohne.widthStyle("name")).isEmpty();
        assertThat(ohne.getTabellenBreite()).isEmpty();
    }

    @Test
    @DisplayName("Profil anlegen, umstellen, zurueckwechseln: der Stand folgt dem Profil")
    void profileWechseln() {
        // Stand einrichten und als "Breit" sichern
        anzeige.getState().getColumnWidths().put("name", "500px");
        anzeige.setNewProfileName("Breit");
        anzeige.createProfile();
        assertThat(anzeige.getProfileNames()).containsExactly("Breit", TableSettings.PROFILE_DEFAULT);

        // Anders einrichten — landet im aktiven Profil "Breit"
        anzeige.getState().getColumnWidths().put("name", "80px");
        anzeige.persist();

        // Das Standardprofil hat noch die Vorgabenbreite (kein name-Eintrag)
        anzeige.setSelectedProfile(TableSettings.PROFILE_DEFAULT);
        anzeige.onProfileSelected();
        assertThat(anzeige.getState().getColumnWidths()).doesNotContainKey("name");

        // Zurueck zu "Breit": die 80px von der letzten Aenderung stehen drin
        anzeige.setSelectedProfile("Breit");
        anzeige.onProfileSelected();
        assertThat(anzeige.getState().getColumnWidths()).containsEntry("name", "80px");
    }

    @Test
    @DisplayName("Ein Profilname wird nicht zweimal vergeben")
    void profilnameNurEinmal() {
        anzeige.setNewProfileName("Breit");
        anzeige.createProfile();
        anzeige.setNewProfileName("Breit");
        anzeige.createProfile();

        assertThat(anzeige.getProfileNames()).containsExactly("Breit", TableSettings.PROFILE_DEFAULT);
    }

    @Test
    @DisplayName("Profil loeschen behaelt den Arbeitsstand und waehlt wieder ein Profil")
    void profilLoeschen() {
        anzeige.setNewProfileName("Weg");
        anzeige.createProfile();
        anzeige.deleteProfile();
        assertThat(anzeige.getProfileNames()).containsExactly(TableSettings.PROFILE_DEFAULT);
        assertThat(anzeige.getSelectedProfile()).isEqualTo(TableSettings.PROFILE_DEFAULT);
    }

    @Test
    @DisplayName("Kopftext wird auf den Spaltenschluessel zurueckgerechnet, unbekannte Koepfe verwerfen")
    void kopftextZuordnung() {
        assertThat(anzeige.keyFromHeader("Name")).isEqualTo("name");
        assertThat(anzeige.keyFromHeader("  teamkey ")).isEqualTo("team");
        assertThat(anzeige.keyFromHeader("Gibtsnicht")).isNull();
        assertThat(anzeige.keyFromHeader(null)).isNull();
    }

    @Test
    @DisplayName("Karte 1904: uebersetzte Kopftexte (EN) finden die Spalte ueber das uebersetzte Label")
    void uebersetzterKopftext() {
        // Ohne Faces und ohne Uebersetzer: nur das deutsche Label trifft (Verhalten wie bisher).
        assertThat(anzeige.keyFromHeader("Team key")).isNull();

        anzeige.setUebersetzer(label -> "TeamKey".equals(label) ? "Team key" : label);
        assertThat(anzeige.keyFromHeader("Team key")).isEqualTo("team");
        assertThat(anzeige.keyFromHeader("TeamKey")).isEqualTo("team");
        assertThat(anzeige.keyFromHeader("Gibtsnicht")).isNull();

        anzeige.setUebersetzer(label -> null); // keine Uebersetzung vorhanden
        assertThat(anzeige.keyFromHeader("Team key")).isNull();
    }

    @Test
    @DisplayName("Gesamtbreite verteilen: proportional, aber keine Spalte unter dem Minimum")
    void breiteVerteilen() {
        anzeige.setGesamtBreite(540);
        assertThat(anzeige.isBreiteAbweichend()).isTrue();
        anzeige.breitenKnopfGedrueckt();
        // name 160/270*540 = 320, team 110/270*540 = 220
        assertThat(anzeige.getState().getColumnWidths())
                .containsEntry("name", "320px")
                .containsEntry("team", "220px");
    }

    @Test
    @DisplayName("Zu schmale Gesamtbreite laesst die Tabelle wieder den Spalten folgen")
    void zuSchmaleGesamtbreiteWirdVerworfen() {
        anzeige.setGesamtBreite(50);

        assertThat(anzeige.getState().getTotalWidth()).isNull();
        assertThat(anzeige.isBreiteAbweichend()).isFalse();
        assertThat(anzeige.getTabellenBreite()).isEqualTo("width: 351px;");
    }

    @Test
    @DisplayName("Jede Aenderung landet in der Ablage — unter dem Seitenschluessel")
    void persistiertUnterSeite() {
        anzeige.persist();
        Mockito.verify(store, Mockito.atLeastOnce()).save(eq("testpage"), any(TableState.class));
    }

    @Test
    @DisplayName("Ereignis-Differenzierung: beide Felder im Request — reorder nimmt _values, colReorder nimmt columnOrder, sonst nichts")
    void ereignisDifferenziertBeideFelder() {
        // Karte 1853: PF schickt _values der orderList UND columnOrder der Tabelle bei jedem
        // Submit mit (die orderList liegt im selben Formular). Nur das Behavior-Event sagt,
        // welche Reihenfolge gemeint ist — Feld-Reihenfolge entscheidet nicht mehr.
        UIData data = tabelleMitZweiSpalten();
        Map<String, String[]> values = Map.of("fm:anz:reihenfolge_values", new String[] {"team", "name"});
        Map<String, String> params = Map.of(
                "jakarta.faces.behavior.event", "reorder",
                "fm:tbl_columnOrder", "fm:tbl:j_idt1,fm:tbl:j_idt2");

        FacesContext faces1 = facesMit(params, values);
        try (MockedStatic<FacesContext> holder = mockStatic(FacesContext.class)) {
            holder.when(FacesContext::getCurrentInstance).thenReturn(faces1);

            anzeige.reihenfolgeAnwenden(data);
            assertThat(anzeige.getState().getColumnOrder()).containsExactly("team", "name", "extra");
        }

        // colReorder: columnOrder (Kopf, neu) gewinnt gegen _values (Panel, alt = Stand).
        TableSettings zweite = new TableSettings("testpage", true);
        zweite.init(store, COLUMNS);
        Map<String, String> params2 = Map.of(
                "jakarta.faces.behavior.event", "colReorder",
                "fm:tbl_columnOrder", "fm:tbl:j_idt2,fm:tbl:j_idt1");
        Map<String, String[]> values2 = Map.of("fm:anz:reihenfolge_values", new String[] {"name", "team"});

        FacesContext faces2 = facesMit(params2, values2);
        try (MockedStatic<FacesContext> holder = mockStatic(FacesContext.class)) {
            holder.when(FacesContext::getCurrentInstance).thenReturn(faces2);

            zweite.reihenfolgeAnwenden(data);
            assertThat(zweite.getState().getColumnOrder()).containsExactly("team", "name", "extra");
        }

        // Ohne Behavior-Event (normaler Formular-Submit): nichts uebernehmen, nichts speichern.
        TableStateStore store3 = Mockito.mock(TableStateStore.class);
        Mockito.when(store3.load(any())).thenReturn(new TableState());
        TableSettings dritte = new TableSettings("testpage", true);
        dritte.init(store3, COLUMNS);
        Map<String, String> params3 = Map.of("fm:tbl_columnOrder", "fm:tbl:j_idt2,fm:tbl:j_idt1");
        Map<String, String[]> values3 = Map.of("fm:anz:reihenfolge_values", new String[] {"team", "name"});

        FacesContext faces3 = facesMit(params3, values3);
        try (MockedStatic<FacesContext> holder = mockStatic(FacesContext.class)) {
            holder.when(FacesContext::getCurrentInstance).thenReturn(faces3);

            Mockito.clearInvocations(store3);   // init() legt das Standardprofil ab — nicht zaehlen
            dritte.reihenfolgeAnwenden(data);
            assertThat(dritte.getState().getColumnOrder()).isNull();
            Mockito.verify(store3, Mockito.never()).save(eq("testpage"), any(TableState.class));
        }
    }

    private UIData tabelleMitZweiSpalten() {
        UIData data = Mockito.mock(UIData.class);
        org.primefaces.component.column.Column col1 = Mockito.mock(org.primefaces.component.column.Column.class);
        org.primefaces.component.column.Column col2 = Mockito.mock(org.primefaces.component.column.Column.class);
        Mockito.when(col1.getHeaderText()).thenReturn("Name");
        Mockito.when(col2.getHeaderText()).thenReturn("TeamKey");
        List<jakarta.faces.component.UIComponent> kinder = new java.util.ArrayList<>(java.util.List.of(col1, col2));
        Mockito.when(data.getChildren()).thenReturn(kinder);
        Mockito.when(data.getClientId(any())).thenReturn("fm:tbl");
        Mockito.when(col1.getClientId(any())).thenReturn("fm:tbl:j_idt1");
        Mockito.when(col2.getClientId(any())).thenReturn("fm:tbl:j_idt2");
        return data;
    }

    private FacesContext facesMit(Map<String, String> params, Map<String, String[]> values) {
        FacesContext faces = Mockito.mock(FacesContext.class);
        ExternalContext ext = Mockito.mock(ExternalContext.class);
        Mockito.when(faces.getExternalContext()).thenReturn(ext);
        Mockito.when(ext.getRequestParameterMap()).thenReturn(params);
        Mockito.when(ext.getRequestParameterValuesMap()).thenReturn(values);
        return faces;
    }

    @Test
    @DisplayName("Karte 1904: breite(key) liefert gespeicherte Breite, sonst skalierte Vorgabe, ohne Breiten null")
    void breiteFuerDasWidthAttribut() {
        TableSettings mit = new TableSettings("t1904", true);
        mit.init(null, java.util.List.of(new TableColumn("a", "A", 120), new TableColumn("b", "B", 0)));
        // breite() und widthStyle liefern bei einer Vorgabe dieselbe Breite
        String ausWidthStyle = mit.widthStyle("a").replace("width:", "").replace(";", "");
        org.junit.jupiter.api.Assertions.assertEquals(ausWidthStyle, mit.breite("a"));
        org.junit.jupiter.api.Assertions.assertNull(mit.breite("b"));
        // eine gespeicherte Breite kommt unskaliert zurueck
        mit.getState().getColumnWidths().put("a", "200px");
        org.junit.jupiter.api.Assertions.assertEquals("200px", mit.breite("a"));
        TableSettings ohne = new TableSettings("t1904b", false);
        ohne.init(null, java.util.List.of(new TableColumn("a", "A", 120)));
        org.junit.jupiter.api.Assertions.assertNull(ohne.breite("a"));
    }

    @Test
    @DisplayName("Dreifach-Schalter: anpassen -> Verschieben -> Vergroessern -> anpassen, gespeichert")
    void modusRotiertUndPersistiert() {
        assertThat(anzeige.getModus()).isEqualTo(2);   // Vorgabe: Groesse an (bisheriges Verhalten)

        anzeige.modusWechseln();
        assertThat(anzeige.getModus()).isEqualTo(0);
        assertThat(anzeige.isVerschiebenAktiv()).isFalse();
        assertThat(anzeige.isGroesseAendernAktiv()).isFalse();

        anzeige.modusWechseln();
        assertThat(anzeige.getModus()).isEqualTo(1);
        assertThat(anzeige.isVerschiebenAktiv()).isTrue();
        assertThat(anzeige.isGroesseAendernAktiv()).isFalse();

        anzeige.modusWechseln();
        assertThat(anzeige.getModus()).isEqualTo(2);
        assertThat(anzeige.isVerschiebenAktiv()).isFalse();
        assertThat(anzeige.isGroesseAendernAktiv()).isTrue();

        Mockito.verify(store, Mockito.atLeastOnce()).save(eq("testpage"), any(TableState.class));
    }

    @Test
    @DisplayName("Modus-Label und -Icon folgen dem Zustand")
    void modusLabelUndIcon() {
        assertThat(anzeige.getModusLabel()).isEqualTo(anzeige.text("modus.vergroessern"));
        assertThat(anzeige.getModusIcon()).isEqualTo("pi pi-arrows-h");
        anzeige.modusWechseln();
        assertThat(anzeige.getModusLabel()).isEqualTo(anzeige.text("modus.anpassen"));
        assertThat(anzeige.getModusIcon()).isEqualTo("pi pi-sliders-h");
    }

    @Test
    @DisplayName("Runde 7: Modus Verschieben hat ein eigenes Symbol, Resize kein erfundenes")
    void modusIconsExistieren() {
        anzeige.modusWechseln();   // 2 -> 0
        anzeige.modusWechseln();   // 0 -> 1 Verschieben
        assertThat(anzeige.getModusIcon()).isEqualTo("pi pi-arrow-right-arrow-left");
        assertThat(anzeige.text("modus.anpassen")).isEqualTo("Anpassen");
    }

    @Test
    @DisplayName("Runde 7: fester Stand - festhalten, verstellen, genau zurueck; wandert mit ins Profil")
    void festerStandFesthaltenUndHerstellen() {
        anzeige.getState().getColumnWidths().put("name", "180px");
        anzeige.getState().setColumnOrder(java.util.List.of("team", "name", "extra"));
        anzeige.getState().setTotalWidth(900);
        assertThat(anzeige.isFesterStandErfasst()).isFalse();

        anzeige.festerStandErfassen();
        assertThat(anzeige.isFesterStandErfasst()).isTrue();
        assertThat(anzeige.getState().getProfiles().get(TableSettings.PROFILE_DEFAULT).getFesterStand())
                .isEqualTo(anzeige.getState().getFesterStand());

        // verstellen: andere Breite, neue Spalte mit Breite, andere Reihenfolge, keine Gesamtbreite
        anzeige.getState().getColumnWidths().put("name", "300px");
        anzeige.getState().getColumnWidths().put("team", "77px");
        anzeige.getState().setColumnOrder(java.util.List.of("extra", "name", "team"));
        anzeige.getState().setTotalWidth(null);

        anzeige.festerStandHerstellen(":fm:tbl");

        assertThat(anzeige.getState().getColumnWidths()).containsExactly(java.util.Map.entry("name", "180px"));
        assertThat(anzeige.getState().getColumnOrder()).containsExactly("team", "name", "extra");
        assertThat(anzeige.getState().getTotalWidth()).isEqualTo(900);

        // Der feste Stand bleibt unberuehrt, wenn danach weiter gezogen wird.
        anzeige.getState().getColumnWidths().put("name", "222px");
        assertThat(anzeige.getState().getFesterStand().columnWidths()).containsEntry("name", "180px");
    }

    @Test
    @DisplayName("Runde 7: nie verschoben - der feste Stand haelt die Markup-Reihenfolge fest, nicht null")
    void festerStandOhneVerschiebenHaeltReihenfolgeFest() {
        anzeige.festerStandErfassen();
        anzeige.getState().setColumnOrder(java.util.List.of("extra", "team", "name"));

        anzeige.festerStandHerstellen(":fm:tbl");

        assertThat(anzeige.getState().getColumnOrder()).containsExactly("name", "team", "extra");
    }

    @Test
    @DisplayName("Runde 7: Restore vergisst PFs Kopf-Zug-Ordnung (displayPriority), Breite/Sichtbarkeit bleiben")
    void pfReihenfolgeVergessen() {
        org.primefaces.component.datatable.DataTable tabelle = Mockito.mock(org.primefaces.component.datatable.DataTable.class);
        org.primefaces.model.ColumnMeta meta = new org.primefaces.model.ColumnMeta("fm:tbl:c1");
        meta.setDisplayPriority(3);
        meta.setWidth("120");
        meta.setVisible(Boolean.FALSE);
        Mockito.when(tabelle.getColumnMeta()).thenReturn(new java.util.HashMap<>(java.util.Map.of("fm:tbl:c1", meta)));

        TableKomponenten.pfReihenfolgeVergessen(tabelle);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.Map<String, org.primefaces.model.ColumnMeta>> neu =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        Mockito.verify(tabelle).setColumnMeta(neu.capture());
        org.primefaces.model.ColumnMeta kopie = neu.getValue().get("fm:tbl:c1");
        assertThat(kopie.getDisplayPriority()).isNull();
        assertThat(kopie.getWidth()).isEqualTo("120");
        assertThat(kopie.getVisible()).isFalse();
        Mockito.verify(tabelle).setColumns(null);
    }

    @Test
    @DisplayName("Runde 7: ohne festen Stand aendert der Doppelklick nichts")
    void festerStandFehlt() {
        anzeige.getState().getColumnWidths().put("name", "300px");
        anzeige.festerStandHerstellen(":fm:tbl");
        assertThat(anzeige.getState().getColumnWidths()).containsEntry("name", "300px");
    }

    @Test
    @DisplayName("text(): Bundle-Aufloesung mit Locale, Fallback ohne Faces-Kontext")
    void textAusBundle() {
        assertThat(anzeige.text("meldung.reihenfolgeGeaendert")).isEqualTo("Spaltenreihenfolge wurde geändert.");
        assertThat(anzeige.text("meldung.verteilt", 540)).isEqualTo("Spalten auf 540 px verteilt.");
        assertThat(anzeige.text("gibts.nicht")).isEqualTo("gibts.nicht");   // Fallback: der Schluessel
    }

    @Test
    @DisplayName("bundle(): liefert das Bundle in der Benutzer-Sprache (en), nicht der ViewRoot-Locale (de)")
    void bundleBenutzerSprache() {
        FacesContext faces = Mockito.mock(FacesContext.class);
        jakarta.faces.application.Application app = Mockito.mock(jakarta.faces.application.Application.class);
        jakarta.faces.component.UIViewRoot viewRoot = Mockito.mock(jakarta.faces.component.UIViewRoot.class);
        Mockito.when(faces.getApplication()).thenReturn(app);
        Mockito.when(faces.getViewRoot()).thenReturn(viewRoot);
        Mockito.when(viewRoot.getLocale()).thenReturn(Locale.GERMAN);
        Mockito.when(app.evaluateExpressionGet(eq(faces), eq("#{userPreferencesBackingBean.language}"), eq(String.class)))
                .thenReturn("en");

        try (MockedStatic<FacesContext> holder = mockStatic(FacesContext.class)) {
            holder.when(FacesContext::getCurrentInstance).thenReturn(faces);
            assertThat(TableSettingsTexte.bundle().getString("modus.anpassen")).isEqualTo("Adjust");
        }
    }

    @Test
    @DisplayName("text(): Benutzer-Sprache (userPreferences.language=en) hat Vorrang vor ViewRoot de")
    void textBenutzerSpracheVorViewRoot() {
        FacesContext faces = Mockito.mock(FacesContext.class);
        jakarta.faces.application.Application app = Mockito.mock(jakarta.faces.application.Application.class);
        jakarta.faces.component.UIViewRoot viewRoot = Mockito.mock(jakarta.faces.component.UIViewRoot.class);
        Mockito.when(faces.getApplication()).thenReturn(app);
        Mockito.when(faces.getViewRoot()).thenReturn(viewRoot);
        Mockito.when(viewRoot.getLocale()).thenReturn(Locale.GERMAN);
        Mockito.when(app.evaluateExpressionGet(eq(faces), eq("#{userPreferencesBackingBean.language}"), eq(String.class)))
                .thenReturn("en");

        try (MockedStatic<FacesContext> holder = mockStatic(FacesContext.class)) {
            holder.when(FacesContext::getCurrentInstance).thenReturn(faces);
            assertThat(TableSettingsTexte.text("modus.anpassen")).isEqualTo("Adjust");
            assertThat(TableSettingsTexte.text("meldung.reihenfolgeGeaendert")).isEqualTo("Column order changed.");
        }
    }

    // ── Karte 2097: Wrap-Binding request-gebunden ───────────────────────────

    private static FacesContext request() {
        FacesContext faces = Mockito.mock(FacesContext.class);
        Mockito.when(faces.getAttributes()).thenReturn(new java.util.HashMap<>());
        return faces;
    }

    @Test
    @DisplayName("Karte 2097: in Request 1 gesetzt - Request 2 sieht die Komponente nicht (JSF1104)")
    void reihenfolgeZielUeberlebtKeinenRequest() {
        jakarta.faces.component.UIComponent wrap = Mockito.mock(jakarta.faces.component.UIComponent.class);
        FacesContext request1 = request();
        FacesContext request2 = request();
        try (MockedStatic<FacesContext> holder = mockStatic(FacesContext.class)) {
            holder.when(FacesContext::getCurrentInstance).thenReturn(request1);
            anzeige.setReihenfolgeZiel(wrap);

            holder.when(FacesContext::getCurrentInstance).thenReturn(request2);
            assertThat(anzeige.getReihenfolgeZiel()).isNull();
            assertThat(anzeige.getReihenfolgeZielClientId()).isEmpty();
        }
        assertThat(anzeige.getReihenfolgeZiel()).isNull();   // ohne FacesContext
    }

    @Test
    @DisplayName("Karte 2097: im selben Request liefert der Getter die Komponente und ihre ClientId")
    void reihenfolgeZielImSelbenRequest() {
        jakarta.faces.component.UIComponent wrap = Mockito.mock(jakarta.faces.component.UIComponent.class);
        Mockito.when(wrap.getClientId()).thenReturn("fm:anz-bereich");
        FacesContext request = request();
        try (MockedStatic<FacesContext> holder = mockStatic(FacesContext.class)) {
            holder.when(FacesContext::getCurrentInstance).thenReturn(request);
            anzeige.setReihenfolgeZiel(wrap);

            assertThat(anzeige.getReihenfolgeZiel()).isSameAs(wrap);
            assertThat(anzeige.getReihenfolgeZielClientId()).isEqualTo("fm:anz-bereich");
        }
    }

    @Test
    @DisplayName("Karte 2097: zwei TableSettings im selben Request kommen sich nicht in die Quere")
    void zweiBereicheImSelbenRequest() {
        TableSettings zweite = new TableSettings("testpage", true);
        zweite.init(store, COLUMNS);
        jakarta.faces.component.UIComponent wrapA = Mockito.mock(jakarta.faces.component.UIComponent.class);
        jakarta.faces.component.UIComponent wrapB = Mockito.mock(jakarta.faces.component.UIComponent.class);
        FacesContext request = request();
        try (MockedStatic<FacesContext> holder = mockStatic(FacesContext.class)) {
            holder.when(FacesContext::getCurrentInstance).thenReturn(request);
            anzeige.setReihenfolgeZiel(wrapA);
            zweite.setReihenfolgeZiel(wrapB);

            assertThat(anzeige.getReihenfolgeZiel()).isSameAs(wrapA);
            assertThat(zweite.getReihenfolgeZiel()).isSameAs(wrapB);
        }
    }

    @Test
    @DisplayName("Ausgelagerte Rechnungen (Runde 7): Verteilen hebt aufs Minimum, from/to verschiebt")
    void ausgelagerteRechnungen() {
        java.util.Map<String, Integer> sichtbar = new java.util.LinkedHashMap<>();
        sichtbar.put("a", 100);
        sichtbar.put("b", 300);
        TableKomponenten.Verteilung v = TableKomponenten.verteilen(sichtbar, 120, 44);
        assertThat(v.breiten()).containsExactly(java.util.Map.entry("a", 44), java.util.Map.entry("b", 90));
        assertThat(v.amMinimum()).isEqualTo(1);
        assertThat(v.summe()).isEqualTo(134);
        assertThat(TableKomponenten.verteilen(new java.util.LinkedHashMap<>(), 500, 44)).isNull();

        org.primefaces.event.ReorderEvent event = Mockito.mock(org.primefaces.event.ReorderEvent.class);
        Mockito.when(event.getFromIndex()).thenReturn(2);
        Mockito.when(event.getToIndex()).thenReturn(0);
        assertThat(TableKomponenten.verschoben(java.util.List.of("x", "y", "z"), event)).containsExactly("z", "x", "y");
        Mockito.when(event.getToIndex()).thenReturn(2);
        assertThat(TableKomponenten.verschoben(java.util.List.of("x", "y", "z"), event)).isNull();
    }
}
