/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.plugins.jsf.userprofile;

import ch.plaintext.boot.plugins.objstore.SimpleStorable;
import ch.plaintext.boot.plugins.objstore.SimpleStorableConverter;
import ch.plaintext.boot.table.TableColumnProfile;
import ch.plaintext.boot.table.TableState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Karte 1077: {@code tabellenStaende} ueberlebt den Weg durch die Spalte — und ein Rollback.
 *
 * <p>Der Konverter ist absichtlich der echte ({@link SimpleStorableConverter}), nicht ein
 * Mapper aus dem Test: entscheidend ist, was <i>seine</i> Einstellungen (Default-Typing,
 * Typ-Allowlist, Fehlerverhalten) mit einem verschachtelten {@link TableState} machen.
 *
 * @author info@plaintext.ch
 * @since 2026
 */
class UserPreferenceTabellenStaendeTest {

    private final SimpleStorableConverter converter = new SimpleStorableConverter();

    @Test
    @DisplayName("Der Getter liefert nie null — auch bei Altbestand ohne das Feld")
    void getterNieNull() throws Exception {
        UserPreference pref = new UserPreference();
        var feld = UserPreference.class.getDeclaredField("tabellenStaende");
        feld.setAccessible(true);
        feld.set(pref, null); // XStream-/Alt-JSON-Bestand nachgestellt

        assertThat(pref.getTabellenStaende()).isNotNull().isEmpty();
        pref.getTabellenStaende().put("guild42/member", new TableState());
        assertThat(pref.getTabellenStaende()).containsKey("guild42/member");
    }

    @Test
    @DisplayName("Ein Stand mit Breiten, Sichtbarkeit und Profil kommt aus der Spalte zurueck, wie er hineinging")
    void rundreiseDurchDenKonverter() {
        TableState stand = new TableState();
        stand.getColumnVisible().put("nr", true);
        stand.getColumnVisible().put("typ", false);
        stand.getColumnWidths().put("nr", "88px");
        stand.setColsExpanded(true);
        stand.setActiveProfile("Breit");
        stand.setPaginatorTop(false);          // Karte 1336
        stand.setRowsPerPage(1000);
        stand.setColumnOrder(List.of("typ", "nr", "name"));   // Karte 1853
        TableColumnProfile profil = new TableColumnProfile();
        profil.getColumnWidths().put("nr", "88px");
        profil.getColumnVisible().put("typ", false);
        profil.setPaginatorBottom(false);
        profil.setRowsPerPage(3000);
        stand.setSpaltenListeHoehe(310);   // Karte 2333
        stand.getProfiles().put("Breit", profil);

        UserPreference pref = new UserPreference();
        pref.setUniqueId("daniel@plaintext.ch");
        pref.getTabellenSpalten().put("useradmin", List.of("username"));
        pref.getTabellenStaende().put("guild42/guild-member", stand);

        String json = converter.convertToDatabaseColumn(pref);
        assertThat(json).isNotNull().startsWith("{").contains("tabellenStaende").contains("guild42/guild-member");
        // Daniel 15.11. (Karte 2160): der Modus wird nie gespeichert — die Schalter der
        // Runde 6/7 duerfen im JSON nicht mehr auftauchen.
        assertThat(json).doesNotContain("moveEnabled", "resizeEnabled");

        SimpleStorable gelesen = converter.convertToEntityAttribute(json);
        assertThat(gelesen).isInstanceOf(UserPreference.class);
        UserPreference zurueck = (UserPreference) gelesen;

        // Das Alte bleibt lesbar ...
        assertThat(zurueck.getTabellenSpalten()).containsEntry("useradmin", List.of("username"));
        // ... und das Neue kommt vollstaendig zurueck.
        TableState geladen = zurueck.getTabellenStaende().get("guild42/guild-member");
        assertThat(geladen).isNotNull();
        assertThat(geladen.getColumnVisible()).containsEntry("nr", true).containsEntry("typ", false);
        assertThat(geladen.getColumnWidths()).containsEntry("nr", "88px");
        assertThat(geladen.isColsExpanded()).isTrue();
        assertThat(geladen.getActiveProfile()).isEqualTo("Breit");
        assertThat(geladen.getProfiles()).containsKey("Breit");
        assertThat(geladen.getProfiles().get("Breit").getColumnWidths()).containsEntry("nr", "88px");
        // Karte 1336: Paginator und Seitengroesse, am Stand und im Profil; nie gesetzt bleibt null.
        assertThat(geladen.getPaginatorTop()).isFalse();
        assertThat(geladen.getPaginatorBottom()).isNull();
        assertThat(geladen.getRowsPerPage()).isEqualTo(1000);
        // Karte 1853: Reihenfolge ueberlebt den Konverter (Default-Typing inklusive). Der Modus
        // (bis 3.31.0 moveEnabled/resizeEnabled) wird seit Daniel 15.11. (Karte 2160) nicht
        // mehr gespeichert — siehe altfelderDerBreitenkarteWerdenUeberlesen.
        assertThat(geladen.getColumnOrder()).containsExactly("typ", "nr", "name");
        assertThat(geladen.getProfiles().get("Breit").getPaginatorBottom()).isFalse();
        assertThat(geladen.getProfiles().get("Breit").getPaginatorTop()).isNull();
        assertThat(geladen.getProfiles().get("Breit").getRowsPerPage()).isEqualTo(3000);
        assertThat(geladen.getSpaltenListeHoehe()).isEqualTo(310);   // Karte 2333
    }

    /**
     * Der Mapper des Konverters bricht bei unbekannten Feldern ab und liefert dann {@code null}
     * fuer den ganzen Datensatz. Ein Feld, das eine spaetere root-Version anhaengt, darf diese
     * Version deshalb nicht aus der Bahn werfen — sonst steht der Benutzer nach einem Rollback
     * mit leeren Einstellungen da, und das naechste Speichern ueberschreibt den alten Datensatz.
     */
    @Test
    @DisplayName("Unbekannte Felder (aus einer spaeteren Version) werden ueberlesen statt den Datensatz zu verwerfen")
    void unbekannteFelderWerdenUeberlesen() {
        UserPreference pref = new UserPreference();
        pref.setUniqueId("daniel@plaintext.ch");
        TableState stand = new TableState();
        stand.getColumnWidths().put("nr", "88px");
        pref.getTabellenStaende().put("guild42/guild-member", stand);
        String json = converter.convertToDatabaseColumn(pref);

        // Ein Feld am Datensatz und eines am Stand, die es heute nicht gibt. (Karte 1853:
        // "columnOrder" ist seitdem KEIN Zukunftsfeld mehr — der Dummy muss einen anderen
        // Namen tragen, sonst laesst der Konverter den Datensatz nicht mehr los.)
        String zukunft = json
                .replace("\"tabellenStaende\"", "\"zukunftsFeld\":\"x\",\"tabellenStaende\"")
                .replace("\"columnWidths\"", "\"zukunftsListe\":[\"nr\"],\"columnWidths\"");
        assertThat(zukunft).isNotEqualTo(json);

        SimpleStorable gelesen = converter.convertToEntityAttribute(zukunft);

        assertThat(gelesen).as("Datensatz mit unbekannten Feldern muss lesbar bleiben").isInstanceOf(UserPreference.class);
        TableState geladen = ((UserPreference) gelesen).getTabellenStaende().get("guild42/guild-member");
        assertThat(geladen).isNotNull();
        assertThat(geladen.getColumnWidths()).containsEntry("nr", "88px");
    }

    @Test
    @DisplayName("Karte 2160: Alt-Stände mit totalWidth/targetColumnWidth (Stand, Profil, fester Stand) laden weiter")
    void altfelderDerBreitenkarteWerdenUeberlesen() {
        TableState stand = new TableState();
        stand.getColumnWidths().put("nr", "88px");
        stand.setColsExpanded(true);
        TableColumnProfile profil = new TableColumnProfile();
        profil.getColumnWidths().put("nr", "120px");
        stand.getProfiles().put("Breit", profil);

        UserPreference pref = new UserPreference();
        pref.setUniqueId("daniel@plaintext.ch");
        pref.getTabellenStaende().put("guild42/guild-member", stand);
        String json = converter.convertToDatabaseColumn(pref);

        // Die Felder aus der Zeit vor Karte 2160 in beide Ebenen injizieren: Stand und Profil
        // tragen je ein columnWidths-Map — der Ersetzungspunkt. Dazu der feste Stand (Karte 1853
        // Runde 7, mit Karte 2333 entfernt): Alt-Staende und Profile mit ihm muessen laden.
        // (Hier KEINE Zukunftsfelder: genau diese Namen existierten bis 3.31.0 wirklich.)
        // Daniel 15.11.: dazu die Modus-Schalter der Runde 6/7 — der Modus wird nicht mehr
        // gespeichert, Alt-Staende und Profile mit ihnen muessen trotzdem laden.
        String alt = json.replace("\"columnWidths\"",
                "\"totalWidth\":1000,\"targetColumnWidth\":120,\"columnWidths\"");
        alt = alt.replace("\"colsExpanded\"",
                "\"moveEnabled\":true,\"resizeEnabled\":false,"
                + "\"festerStand\":{\"columnWidths\":{\"nr\":\"90px\"},\"columnOrder\":[\"nr\",\"typ\"]},\"colsExpanded\"");
        assertThat(alt).isNotEqualTo(json);

        SimpleStorable gelesen = converter.convertToEntityAttribute(alt);

        assertThat(gelesen).as("Altstand mit Breiten-Feldern muss lesbar bleiben").isInstanceOf(UserPreference.class);
        TableState geladen = ((UserPreference) gelesen).getTabellenStaende().get("guild42/guild-member");
        assertThat(geladen).isNotNull();
        assertThat(geladen.getColumnWidths()).containsEntry("nr", "88px");
        assertThat(geladen.getProfiles()).containsKey("Breit");
        assertThat(geladen.getProfiles().get("Breit").getColumnWidths()).containsEntry("nr", "120px");
    }
}
