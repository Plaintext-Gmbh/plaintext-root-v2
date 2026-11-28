/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.plugins.security.oidc;

import ch.plaintext.boot.plugins.security.model.MyUserEntity;
import ch.plaintext.boot.plugins.security.persistence.MyUserRepository;
import ch.plaintext.oidc.entity.OidcConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Karte 2352: das automatisch angelegte OIDC-Konto übernimmt die Default-Rollen der Konfiguration,
 * aber nie die Maschinenrolle {@code system} — auch nicht, wenn sie dort eingetragen ist.
 */
class PlaintextOidcUserServiceNeuerBenutzerTest {

    @Test
    void defaultRollenOhneMaschinenrolle() throws Exception {
        MyUserRepository repo = mock(MyUserRepository.class);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        PlaintextOidcUserService service = new PlaintextOidcUserService(repo, null, null);
        OidcConfig config = new OidcConfig();
        config.setDefaultRoles("user, system,ROLE_SYSTEM , System");
        config.setDefaultMandat("m1");

        Method m = PlaintextOidcUserService.class.getDeclaredMethod(
                "createNewUser", String.class, String.class, OidcConfig.class);
        m.setAccessible(true);
        MyUserEntity neu = (MyUserEntity) m.invoke(service, "neu@example.invalid", "sub-1", config);

        assertEquals(Set.of("user", "PROPERTY_MANDAT_M1"), neu.getRoles());
    }

    /** Karte 2566: Neuanlage mit den Rollen der Selbstregistrierung (eigener Mandant), Maschinenrolle bleibt draussen. */
    @Test
    void neuanlageMitEigenemMandanten() throws Exception {
        MyUserRepository repo = mock(MyUserRepository.class);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        PlaintextOidcUserService service = new PlaintextOidcUserService(repo, null, null);

        Method m = PlaintextOidcUserService.class.getDeclaredMethod(
                "createNewUser", String.class, String.class, Set.class);
        m.setAccessible(true);
        MyUserEntity neu = (MyUserEntity) m.invoke(service, "neu@example.invalid", "sub-2",
                Set.of("user", "PROPERTY_MANDAT_kabc", "praesentation", "system"));

        assertEquals(Set.of("user", "PROPERTY_MANDAT_kabc", "praesentation"), neu.getRoles());
        assertEquals("sub-2", neu.getOidcSubject());
    }
}
