/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.flyway;

import ch.plaintext.testsupport.EmbeddedPg;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Karte 2341/2343: Eine gescheiterte Flyway-Validierung beim Start wird eindeutig gemeldet.
 *
 * <p>Reproduktion des Vorfalls 17.11.2026 in der Konfiguration der app-PROD-Huelle
 * ({@code validate-on-migrate: true}, {@code "*:missing"}, {@code out-of-order: true}): Die board-Migrationen
 * waren die hoechsten der DB, ohne geladenes Modul also FUTURE — das bricht ab, mit Hinweis auf die
 * Modul-Auswahl. Applied-Eintraege bewusst entfernter Module (MISSING, Karte 1533; auf app-PROD 22 Stueck)
 * und neue Migrationen (PENDING) duerfen den Start nicht stoppen.</p>
 */
@DisplayName("Karte 2343: gescheiterte Flyway-Validierung wird mit Modul-Hinweis gemeldet")
class FlywayMigrationStrategieConfigTest {

    private static final Map<String, Supplier<Object>> DB = new HashMap<>();

    @BeforeAll
    static void datenbank() {
        EmbeddedPg.registrieren(DB::put, "flywaystrategietest");
    }

    private static String db(String schluessel) {
        return (String) DB.get("spring.datasource." + schluessel).get();
    }

    /** Flyway wie die app-PROD-Instanz-Config (/huelle/config/application.yml). */
    private static Flyway flyway() {
        return Flyway.configure()
                .dataSource(db("url"), db("username"), db("password"))
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .ignoreMigrationPatterns("*:missing")
                .outOfOrder(true)
                .cleanDisabled(false)
                .load();
    }

    private static void sql(String anweisung, String... werte) throws Exception {
        try (Connection c = DriverManager.getConnection(db("url"), db("username"), db("password"));
             PreparedStatement ps = c.prepareStatement(anweisung)) {
            for (int i = 0; i < werte.length; i++) {
                ps.setString(i + 1, werte[i]);
            }
            ps.executeUpdate();
        }
    }

    /** Basisstand: echte Migrationen applied, keine Testzeilen (die DB wird von allen Tests geteilt). */
    private static void basisstand() throws Exception {
        // erst Testzeilen weg (eine FUTURE-Zeile liesse migrate scheitern), dann migrieren
        if (flyway().info().applied().length > 0) {
            sql("DELETE FROM flyway_schema_history WHERE description = 'nur in der db'");
        }
        flyway().migrate();
    }

    /** Traegt eine Applied-Zeile ein, zu der kein Skript auf dem Klassenpfad existiert. */
    private static void fremdeAppliedZeileEintragen(String version, String skript) throws Exception {
        sql("INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, "
                + "checksum, installed_by, installed_on, execution_time, success) "
                + "VALUES ((SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history), "
                + "?, 'nur in der db', 'SQL', ?, 0, 'test', now(), 0, true)", version, skript);
    }

    private static void strategie(Flyway flyway) {
        new FlywayMigrationStrategieConfig().migrationMitLueckenMeldung().migrate(flyway);
    }

    @Test
    @DisplayName("FUTURE (Vorfall board): Abbruch mit Version und Hinweis auf die Modul-Auswahl")
    void futureBrichtMitKlarerMeldung() throws Exception {
        basisstand();
        fremdeAppliedZeileEintragen("9999999999", "V9999999999__nur_in_db_board_tabellen.sql");

        // frische Instanz: Flyway cached info() pro Instanz, die Zeile muss sichtbar sein
        Flyway flyway = flyway();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> strategie(flyway));
        assertTrue(ex.getMessage().contains("9999999999"), "Meldung nennt die Version, war: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("auswahl.yaml"), "Meldung weist auf die Modul-Auswahl, war: " + ex.getMessage());
    }

    @Test
    @DisplayName("MISSING (bewusst entferntes Modul, PROD-Altbestand): Start laeuft")
    void altesMissingStartLaeuft() throws Exception {
        basisstand();
        fremdeAppliedZeileEintragen("1", "V1__nur_in_db_entferntes_modul.sql");

        Flyway flyway = flyway();
        assertDoesNotThrow(() -> strategie(flyway));
    }

    @Test
    @DisplayName("ohne Luecke laeuft das Migrate unveraendert durch")
    void ohneLueckeKeinEingriff() throws Exception {
        basisstand();
        Flyway flyway = flyway();
        assertDoesNotThrow(() -> strategie(flyway));
    }

    @Test
    @DisplayName("PENDING (neue Migrationen im Deploy) ist kein Fehler: Strategy migriert sie")
    void neueMigrationenSindKeinFehler() {
        Flyway leer = flyway();
        leer.clean();
        MigrationVersion[] versionen = Arrays.stream(leer.info().all()).map(MigrationInfo::getVersion)
                .filter(Objects::nonNull).distinct().sorted().toArray(MigrationVersion[]::new);
        Flyway.configure().configuration(leer.getConfiguration())
                .target(versionen[versionen.length - 2]).load().migrate(); // die hoechste bleibt PENDING

        Flyway flyway = flyway();
        assertDoesNotThrow(() -> strategie(flyway));
        assertEquals(0, flyway().info().pending().length, "die offene Migration ist danach applied");
    }
}
