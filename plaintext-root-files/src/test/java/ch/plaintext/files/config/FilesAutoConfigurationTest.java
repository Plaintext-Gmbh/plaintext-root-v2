/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Karte 2377 ROOT-11: root-files und der Nachfolger plaintext-modul-files dürfen nicht zusammen laufen. */
class FilesAutoConfigurationTest {

    @Test
    void mitNachfolgerBrichtDerStartMitHinweisAb() {
        assertThatThrownBy(() -> FilesAutoConfiguration.pruefeOhneNachfolger(FilesAutoConfiguration.NACHFOLGER::equals))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plaintext-root-files und plaintext-modul-files sind beide geladen")
                .hasMessageContaining("ohne Migration");
    }

    @Test
    void ohneNachfolgerStartetEs() {
        assertThatCode(() -> FilesAutoConfiguration.pruefeOhneNachfolger(name -> false)).doesNotThrowAnyException();
        assertThatCode(FilesAutoConfiguration::new).as("echter Klassenpfad ohne plaintext-modul-files")
                .doesNotThrowAnyException();
    }
}
