/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.sidecars.service;

import ch.plaintext.ablagen.DateiAblageArt;
import ch.plaintext.boot.plugins.netz.AusgehendesZiel;
import ch.plaintext.secrets.SecretResolver;
import ch.plaintext.sidecars.ablage.NextcloudAblage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/** Karte 2425: nextcloud und git als benannte Arten ({@link DateiAblageArt}); dieselben Eigenschaften wie Sidecars. */
@Configuration
public class NetzAblageArtenKonfiguration {

    @Bean
    public DateiAblageArt nextcloudAblageArt(ObjectProvider<SecretResolver> secrets,
                                             @Value("${" + AusgehendesZiel.EIGENSCHAFT_ERLAUBTE_HOSTS + ":}") String erlaubteHosts) {
        return new NetzAblageArt(NetzAblageArt.NEXTCLOUD, secrets, erlaubteHosts, NextcloudAblage.standardClient(), null);
    }

    @Bean
    public DateiAblageArt gitAblageArt(ObjectProvider<SecretResolver> secrets,
                                       @Value("${" + AusgehendesZiel.EIGENSCHAFT_ERLAUBTE_HOSTS + ":}") String erlaubteHosts,
                                       @Value("${plaintext.ablagen.git.verzeichnis:${java.io.tmpdir}/plaintext-git-ablagen}") String gitKlone) {
        return new NetzAblageArt(NetzAblageArt.GIT, secrets, erlaubteHosts, null, Path.of(gitKlone));
    }
}
