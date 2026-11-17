/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.flyway;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.ValidateResult;
import org.flywaydb.core.api.pattern.ValidatePattern;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Karte 2341/2343: Eine gescheiterte Flyway-Validierung beim Start eindeutig MELDEN statt roh crashen.
 *
 * <p>Vorfall 17.11.2026: Der ModulLader startete app ohne {@code plaintext-modul-board}, weil es in
 * {@code module/gemeinsam/auswahl.yaml} fehlte. Die board-Migrationen waren die hoechsten der DB, ohne
 * das Modul also FUTURE — und {@code ignore-migration-patterns: "*:missing"} (Karte 1533) ersetzt
 * Flyways Vorgabe {@code "*:future"}. Flyway brach mit {@code Detected applied migration not resolved
 * locally} und nur Versionsnummern ab, Crash-Loop.</p>
 *
 * <p>Die Strategy faellt kein eigenes Urteil: Sie validiert VOR dem Migrate genau wie
 * {@link Flyway#migrate()} (konfigurierte Muster plus {@code "*:pending"}) und ergaenzt nur die Meldung
 * um Versionen, Beschreibungen und den Hinweis auf die Modul-Auswahl. Sie liegt hier und nicht in den
 * Apps, weil die Huelle nur root und die Module laedt — die app-Fassung (2341) lief auf PROD nie.</p>
 */
@Slf4j
@Configuration
public class FlywayMigrationStrategieConfig {

    @Bean
    public FlywayMigrationStrategy migrationMitLueckenMeldung() {
        return flyway -> {
            org.flywaydb.core.api.configuration.Configuration konfig = flyway.getConfiguration();
            if (konfig.isValidateOnMigrate()) {
                // Wie Flyway.migrate(): "*:pending" dazu, sonst waere jede neue Migration ein Fehler.
                ValidatePattern[] muster = Stream.concat(Arrays.stream(konfig.getIgnoreMigrationPatterns()),
                        Stream.of(ValidatePattern.fromPattern("*:pending"))).toArray(ValidatePattern[]::new);
                ValidateResult ergebnis = Flyway.configure(konfig.getClassLoader()).configuration(konfig)
                        .ignoreMigrationPatterns(muster).load().validateWithResult();
                if (!ergebnis.validationSuccessful) {
                    String meldung = "Flyway-Validierung gescheitert fuer "
                            + ergebnis.invalidMigrations.stream().map(m -> m.version + " (" + m.description + ")").toList()
                            + " — kennt die DB Migrationen eines nicht geladenen Moduls? Modul-Auswahl pruefen "
                            + "(module/gemeinsam/auswahl.yaml), Karte 2343. Flyway: " + ergebnis.getAllErrorMessages();
                    log.error(meldung);
                    throw new IllegalStateException(meldung);
                }
                // Beleg im Containerlog, dass die Pruefung wirklich laeuft (2341 lief auf PROD unbemerkt nie).
                log.info("Flyway-Vorpruefung ok: {} Migrationen validiert (Karte 2343)", ergebnis.validateCount);
            }
            flyway.migrate();
        };
    }
}
