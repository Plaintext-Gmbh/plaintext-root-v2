/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.rollenzuteilung.bedarf;

import ch.plaintext.rollen.RollenArt;
import ch.plaintext.rollen.RollenBedarf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Karte 2377 ROOT-6: MCP list_roles nur für ADMIN/ROOT, Ausgabe ohne Namen. */
class RollenMcpToolsTest {

    private final RollenBedarfService service = mock(RollenBedarfService.class);
    private final RollenMcpTools tools = new RollenMcpTools(service);

    @AfterEach
    void aufraeumen() {
        SecurityContextHolder.clearContext();
    }

    private static void als(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "wer", "x", AuthorityUtils.createAuthorityList(authorities)));
    }

    @Test
    void adminSiehtDieListe() {
        when(service.bedarf("terminal")).thenReturn(List.of(new RollenBedarf("ROLE_TERMINAL", "terminal", "Terminal",
                RollenBedarf.DEKLARIERT, "terminal", RollenArt.ZUGANG, true, "", List.of("terminal"), 3)));
        als("SCOPE_READ", "ROLE_ADMIN");

        assertThat(tools.listRoles("terminal")).contains("OIDC-Gruppen nicht gezaehlt")
                .contains("- ROLE_TERMINAL quelle=deklariert modul=terminal art=ZUGANG ohneUmgehung module=terminal "
                        + "vergeben=3 - Terminal");
    }

    @Test
    void userBekommtNichts() {
        als("SCOPE_READ", "ROLE_USER");
        assertThat(tools.listRoles(null)).startsWith("FEHLER");
    }
}
