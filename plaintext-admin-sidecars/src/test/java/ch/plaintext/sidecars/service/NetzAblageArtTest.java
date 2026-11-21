/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.sidecars.service;

import ch.plaintext.ablagen.DateiAblage;
import ch.plaintext.secrets.SecretResolver;
import ch.plaintext.sidecars.ablage.NextcloudAblage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Karte 2425: nextcloud aus der application.yml, Passwort nur per Verweis in den Secret-Store. */
class NetzAblageArtTest {

    @SuppressWarnings("unchecked")
    private static NetzAblageArt nextcloud(SecretResolver r) {
        ObjectProvider<SecretResolver> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(r);
        return new NetzAblageArt(NetzAblageArt.NEXTCLOUD, p, "", NextcloudAblage.standardClient(), null);
    }

    @Test
    void nextcloudMitSecretVerweis() throws IOException {
        SecretResolver r = mock(SecretResolver.class);
        when(r.resolve("nextcloud-app")).thenReturn(Optional.of("geheim-pw"));
        DateiAblage a = nextcloud(r).oeffne("ablage", Map.of("url", "https://93.184.215.14", "benutzer", "app",
                "pfad", "Apps", "passwort-secret", "nextcloud-app"));
        assertThat(a).isInstanceOf(NextcloudAblage.class);
        assertThat(a.name()).isEqualTo("ablage");
    }

    @Test
    void fehlendesSecretInterneAdresseOderOhneUrlWirdAbgewiesen() {
        SecretResolver r = mock(SecretResolver.class);
        when(r.resolve("fehlt")).thenReturn(Optional.empty());
        NetzAblageArt art = nextcloud(r);
        assertThatThrownBy(() -> art.oeffne("a", Map.of("url", "https://93.184.215.14", "passwort-secret", "fehlt")))
                .isInstanceOf(IOException.class).hasMessageContaining("fehlt im Secret-Store");
        assertThatThrownBy(() -> art.oeffne("a", Map.of("url", "http://127.0.0.1:8080"))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> art.oeffne("a", Map.of())).isInstanceOf(IOException.class).hasMessageContaining("url");
        assertThatThrownBy(() -> nextcloud(null).oeffne("a", Map.of("url", "https://93.184.215.14", "passwort-secret", "x")))
                .isInstanceOf(IOException.class).hasMessageContaining("kein Secret-Store");
    }
}
