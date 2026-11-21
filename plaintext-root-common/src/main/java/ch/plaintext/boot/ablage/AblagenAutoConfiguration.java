/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.ablage;

import ch.plaintext.ablagen.DateiAblageArt;
import ch.plaintext.ablagen.DateiAblagenRegister;
import ch.plaintext.settings.ISettingsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;

import java.nio.file.Path;
import java.util.Map;

/**
 * Karte 2425: benannte Speicher. Die Art {@code lokal} und das Register, das Module bekommen; jede Anwendung kann
 * weitere Arten als Bean beitragen (sidecars: nextcloud, git).
 *
 * <pre>
 * plaintext:
 *   ablagen.lokal.basis: /data        # Basis der Art lokal (wie bisher)
 *   speicher:
 *     files:                          # Name der Ablage, z.B. plaintext.files.ablage: files
 *       art: lokal
 *       pfad: files
 * </pre>
 */
@AutoConfiguration
public class AblagenAutoConfiguration {

    @Bean
    public LokaleAblageArt lokaleAblageArt(@Value("${plaintext.ablagen.lokal.basis:}") String basis) {
        return new LokaleAblageArt(basis == null || basis.isBlank() ? null : Path.of(basis.strip()));
    }

    @Bean
    @Primary
    public KonfigurierteAblagenRegister konfigurierteAblagenRegister(Environment env, ObjectProvider<DateiAblageArt> arten,
                                                                     ObjectProvider<DateiAblagenRegister> weitere,
                                                                     ObjectProvider<ISettingsService> settings) {
        ResolvableType text = ResolvableType.forClass(String.class);
        Bindable<Map<String, Map<String, String>>> typ = Bindable.of(ResolvableType.forClassWithGenerics(Map.class, text,
                ResolvableType.forClassWithGenerics(Map.class, String.class, String.class)));
        return new KonfigurierteAblagenRegister(Binder.get(env).bind("plaintext.speicher", typ).orElse(Map.of()), arten, weitere, settings);
    }
}
