/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files.config;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Karte 2377 ROOT-11: root-files und der Nachfolger plaintext-modul-files dürfen nicht zusammen laufen. Geprüft mit
 * einem echten Start: die Meldung kommt vor dem Einlesen der Beans (sonst gewänne der Namenskonflikt
 * {@code filesService} ohne Hinweis).
 */
class FilesAutoConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    static class Leer {
    }

    private static SpringApplication start(ClassLoader cl) {
        SpringApplication app = new SpringApplication(Leer.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setResourceLoader(new DefaultResourceLoader(cl));
        return app;
    }

    @Test
    void mitNachfolgerBrichtDerStartMitHinweisAb() {
        ClassLoader mitNachfolger = new ByteBuddy().subclass(Object.class).name(FilesNachfolgerPruefung.NACHFOLGER)
                .make().load(getClass().getClassLoader(), ClassLoadingStrategy.Default.WRAPPER).getLoaded()
                .getClassLoader();

        assertThatThrownBy(() -> start(mitNachfolger).run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plaintext-root-files und plaintext-modul-files sind beide geladen");
    }

    @Test
    void ohneNachfolgerStartetEs() {
        try (ConfigurableApplicationContext ctx = start(getClass().getClassLoader()).run()) {
            assertThat(ctx.isActive()).isTrue();
        }
        assertThatCode(() -> FilesNachfolgerPruefung.pruefe(name -> false)).doesNotThrowAnyException();
    }
}
