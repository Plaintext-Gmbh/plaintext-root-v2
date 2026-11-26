/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ClassUtils;

import java.util.function.Predicate;

/**
 * Karte 2377 ROOT-11: {@code plaintext-root-files} ist veraltet, Nachfolger {@code ch.plaintext:plaintext-modul-files}.
 * Liegen beide auf dem Klassenpfad, scheitert der Start sonst erst beim Einlesen der Beans an einem Namenskonflikt
 * ({@code filesService} und sieben weitere) ohne Hinweis auf die Ursache. Diese Prüfung läuft vorher (über
 * {@code META-INF/spring.factories}, auch in der Hülle mit Component-Scan {@code ch.plaintext}) und sagt, was zu tun
 * ist. Allein geladen meldet sie «veraltet» auf {@code System.err} (Logging ist hier noch nicht eingerichtet).
 */
public class FilesNachfolgerPruefung implements EnvironmentPostProcessor {

    /** Eine Klasse des Nachfolgers; per Name, damit root nicht an das Modul hängt. */
    static final String NACHFOLGER = "ch.plaintext.filesmodul.service.FilesService";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        ClassLoader cl = application.getClassLoader();
        pruefe(name -> ClassUtils.isPresent(name, cl));
    }

    /** @throws IllegalStateException wenn {@code plaintext-modul-files} mitgeladen ist */
    static void pruefe(Predicate<String> klasseVorhanden) {
        if (klasseVorhanden.test(NACHFOLGER)) {
            throw new IllegalStateException("plaintext-root-files und plaintext-modul-files sind beide geladen. "
                    + "plaintext-root-files ist veraltet: die Abhängigkeit plaintext-root-files entfernen "
                    + "(Umstieg siehe CHANGELOG plaintext-root-v2, Karte 2377 ROOT-11).");
        }
        System.err.println("[Files] plaintext-root-files ist veraltet, Nachfolger ist ch.plaintext:plaintext-modul-files "
                + "(Umstieg siehe CHANGELOG plaintext-root-v2, Karte 2377 ROOT-11)");
    }
}
