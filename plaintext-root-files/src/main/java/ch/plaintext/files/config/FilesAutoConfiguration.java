/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files.config;

import ch.plaintext.files.DateiSpeicher;
import ch.plaintext.files.speicher.DateisystemDateiSpeicher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import org.springframework.util.ClassUtils;

import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Bindet die persönliche Dateiablage (Karte 1559) in jede Anwendung ein, die das Modul auf dem Klassenpfad hat.
 * Die Freigabe-Links gehen über den {@link ch.plaintext.linktoken.LinkTokenStore} (Standard in
 * {@code plaintext-root-freigabe}).
 *
 * <p><b>Veraltet</b> (Karte 2377, ROOT-11): Nachfolger ist {@code ch.plaintext:plaintext-modul-files}. Beide zusammen
 * auf dem Klassenpfad gehen nicht (gleiche Bean-Namen, gleiche Adressen {@code /nosec/files/}, {@code /files/api},
 * gleiche MCP-Werkzeuge); der Start bricht dann mit einer klaren Meldung ab, statt dass eine Seite die andere still
 * überschreibt.
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(FilesProperties.class)
@ComponentScan({"ch.plaintext.files.service", "ch.plaintext.files.web", "ch.plaintext.files.mcp"})
public class FilesAutoConfiguration {

    /** Eine Klasse des Nachfolgers {@code plaintext-modul-files}; per Name, damit root nicht an das Modul hängt. */
    static final String NACHFOLGER = "ch.plaintext.filesmodul.service.FilesService";

    public FilesAutoConfiguration() {
        pruefeOhneNachfolger(name -> ClassUtils.isPresent(name, FilesAutoConfiguration.class.getClassLoader()));
    }

    /** Bricht ab, wenn {@code plaintext-modul-files} mitgeladen ist; sonst nur der Hinweis «veraltet». */
    static void pruefeOhneNachfolger(Predicate<String> klasseVorhanden) {
        if (klasseVorhanden.test(NACHFOLGER)) {
            throw new IllegalStateException("plaintext-root-files und plaintext-modul-files sind beide geladen. "
                    + "plaintext-root-files ist veraltet: die Abhängigkeit plaintext-root-files entfernen, "
                    + "Daten unter plaintext.files.verzeichnis und Freigabe-Links (Art files) übernimmt "
                    + "plaintext-modul-files ohne Migration (Karte 2377, ROOT-11).");
        }
        log.warn("[Files] plaintext-root-files ist veraltet, Nachfolger ist ch.plaintext:plaintext-modul-files "
                + "(Umstieg siehe CHANGELOG, Karte 2377 ROOT-11)");
    }

    /** Dateisystem unter {@code plaintext.files.verzeichnis}, solange die Anwendung keinen eigenen Speicher bringt. */
    @Bean
    @ConditionalOnMissingBean(DateiSpeicher.class)
    public DateiSpeicher dateiSpeicher(FilesProperties properties) {
        return new DateisystemDateiSpeicher(Path.of(properties.getVerzeichnis()));
    }
}
