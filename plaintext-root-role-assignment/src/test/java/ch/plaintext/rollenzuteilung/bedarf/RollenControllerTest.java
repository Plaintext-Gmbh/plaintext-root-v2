/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.rollenzuteilung.bedarf;

import ch.plaintext.rollen.RollenArt;
import ch.plaintext.rollen.RollenBedarf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Karte 2377 ROOT-6: drei Rollenlagen, Token-Fall, Filter nach Modul. */
class RollenControllerTest {

    private static final RollenBedarf TERMINAL = new RollenBedarf("ROLE_TERMINAL", "terminal", "Terminal",
            RollenBedarf.DEKLARIERT, "terminal", RollenArt.ZUGANG, true, "", List.of("terminal"), 3);

    private final RollenBedarfService service = mock(RollenBedarfService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RollenController(service)).build();

    @AfterEach
    void aufraeumen() {
        SecurityContextHolder.clearContext();
    }

    private static void als(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "wer", "x", AuthorityUtils.createAuthorityList(authorities)));
    }

    @Test
    void anonymIst401() throws Exception {
        mvc.perform(get(RollenController.PFAD)).andExpect(status().isUnauthorized());
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("k", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        mvc.perform(get(RollenController.PFAD)).andExpect(status().isUnauthorized());
    }

    @Test
    void userIst403() throws Exception {
        als("ROLE_USER");
        mvc.perform(get(RollenController.PFAD)).andExpect(status().isForbidden());
    }

    @Test
    void adminBekommtDieListeMitHinweis() throws Exception {
        when(service.bedarf((String) null)).thenReturn(List.of(TERMINAL));
        als("ROLE_ADMIN");
        mvc.perform(get(RollenController.PFAD)).andExpect(status().isOk())
                .andExpect(header().string(RollenController.HINWEIS, "OIDC-Gruppen nicht gezaehlt"))
                .andExpect(jsonPath("$[0].authority").value("ROLE_TERMINAL"))
                .andExpect(jsonPath("$[0].quelle").value("deklariert"))
                .andExpect(jsonPath("$[0].ohneUmgehung").value(true))
                .andExpect(jsonPath("$[0].vergeben").value(3));
    }

    @Test
    void modulFiltert() throws Exception {
        when(service.bedarf("terminal")).thenReturn(List.of(TERMINAL));
        als("ROLE_ROOT");
        mvc.perform(get(RollenController.PFAD).param("modul", "terminal")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void tokenBrauchtLesenUndInhaberAdmin() throws Exception {
        when(service.bedarf((String) null)).thenReturn(List.of());
        als("SCOPE_READ", "ROLE_ADMIN");
        mvc.perform(get(RollenController.PFAD)).andExpect(status().isOk());
        als("SCOPE_READ", "ROLE_USER");
        mvc.perform(get(RollenController.PFAD)).andExpect(status().isForbidden());
        als("SCOPE_WRITE", "ROLE_ADMIN");
        mvc.perform(get(RollenController.PFAD)).andExpect(status().isForbidden());
    }
}
