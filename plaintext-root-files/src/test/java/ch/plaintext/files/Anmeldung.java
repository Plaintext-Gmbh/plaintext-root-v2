/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files;

import ch.plaintext.boot.security.PageAccessGuardService;
import ch.plaintext.files.web.FilesZugriff;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;

import static org.mockito.Mockito.mock;

/** Testhilfe: meldet einen Benutzer mit Rollen an und baut den {@link FilesZugriff} ohne Seitenschutz. */
public final class Anmeldung {

    private Anmeldung() {
    }

    public static void als(String benutzer, String... rollen) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(benutzer, null,
                Arrays.stream(rollen).map(SimpleGrantedAuthority::new).toList()));
    }

    public static void ab() {
        SecurityContextHolder.clearContext();
    }

    /** Ohne Seitenschutz-Bean entscheiden die Rollen direkt - so läuft eine Anwendung ohne plaintext-root-pageguard. */
    @SuppressWarnings("unchecked")
    public static FilesZugriff zugriffOhneSeitenschutz() {
        ObjectProvider<PageAccessGuardService> ohne = mock(ObjectProvider.class);
        return new FilesZugriff(ohne);
    }
}
