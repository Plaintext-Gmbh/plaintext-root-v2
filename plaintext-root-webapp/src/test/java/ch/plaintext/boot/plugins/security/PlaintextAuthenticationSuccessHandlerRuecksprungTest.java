/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.plugins.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Karte 2566: Ruecksprung nach dem Login nur auf einen Pfad dieser Anwendung. */
class PlaintextAuthenticationSuccessHandlerRuecksprungTest {

    @Test
    void nurLokalePfade() {
        assertEquals("ki/oauth/authorize?client_id=a&state=b",
                PlaintextAuthenticationSuccessHandler.lokalerPfad("/ki/oauth/authorize?client_id=a&state=b"));
        for (String boese : new String[]{null, "", "/", "//evil.example", "/\\evil.example", "https://evil.example",
                "evil.example/x", "/a b", "/a\nLocation: x", "/ä"}) {
            assertNull(PlaintextAuthenticationSuccessHandler.lokalerPfad(boese), String.valueOf(boese));
        }
    }

    @Test
    void ersterGueltigerBeitragGewinntFehlerWerdenUebergangen() {
        PlaintextAuthenticationSuccessHandler h = new PlaintextAuthenticationSuccessHandler(null, null, null, null, null);
        LoginRuecksprung kaputt = r -> { throw new IllegalStateException("x"); };
        LoginRuecksprung fremd = r -> "//evil.example";
        LoginRuecksprung gut = r -> "/ki/oauth/weiter";
        ReflectionTestUtils.setField(h, "ruecksprung", List.of(kaputt, fremd, gut));
        assertEquals("ki/oauth/weiter", h.ruecksprungZiel(new MockHttpServletRequest()));
        ReflectionTestUtils.setField(h, "ruecksprung", List.of());
        assertNull(h.ruecksprungZiel(new MockHttpServletRequest()));
    }
}
