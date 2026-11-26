/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.plugins.security;

import ch.plaintext.boot.plugins.security.model.MyUserEntity;
import ch.plaintext.boot.plugins.security.persistence.MyUserRepository;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Karte 2377 ROOT-5: Bestand je Rolle aus my_user_entity.roles. */
class MyUserRollenBestandTest {

    private static MyUserEntity benutzer(String... rollen) {
        MyUserEntity u = new MyUserEntity();
        u.setRoles(new HashSet<>(Set.of(rollen)));
        return u;
    }

    @Test
    void zaehltJeRolleOhneMandatsMarker() {
        MyUserRepository repo = mock(MyUserRepository.class);
        when(repo.findAll()).thenReturn(List.of(benutzer("admin", "ROLE_FILES", "PROPERTY_MANDAT_X"),
                benutzer("files", "user"), benutzer()));

        assertThat(new MyUserRollenBestand(repo).vergebeneRollen())
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of("admin", 1, "files", 2, "user", 1));
    }
}
