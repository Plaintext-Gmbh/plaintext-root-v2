/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.web;

import jakarta.el.ELProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Card 2042: the auto-redirect on the login page must not fire after a logout or a failed sign-in. Spring appends
 * {@code ?logout} / {@code ?error} WITHOUT a value, so {@code param.logout} is "" - and {@code empty ""} is true.
 * The test evaluates the real {@code rendered} expression of login.xhtml with EL.
 */
class LoginAutoRedirectSchleifenschutzTest {

    /** Stand-in for loginSetupBean: auto-redirect is configured. */
    public static class AutoRedirectAn {
        public boolean isOidcAutoRedirectActive() {
            return true;
        }
    }

    private static String renderedAusdruck() throws IOException {
        String seite;
        try (InputStream in = LoginAutoRedirectSchleifenschutzTest.class.getResourceAsStream("/META-INF/resources/login.xhtml")) {
            assertNotNull(in, "login.xhtml im Klassenpfad");
            seite = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher m = Pattern.compile("rendered=\"#\\{(loginSetupBean\\.oidcAutoRedirectActive[^}]*)}\"").matcher(seite);
        assertTrue(m.find(), "Auto-Redirect-Block in login.xhtml");
        return m.group(1);
    }

    private static boolean leitetWeiter(String ausdruck, Map<String, String> param) {
        ELProcessor el = new ELProcessor();
        el.defineBean("loginSetupBean", new AutoRedirectAn());
        el.defineBean("param", param);
        return (Boolean) el.eval(ausdruck);
    }

    private static Map<String, String> param(String... schluesselWert) {
        Map<String, String> p = new HashMap<>();
        for (int i = 0; i < schluesselWert.length; i += 2) {
            p.put(schluesselWert[i], schluesselWert[i + 1]);
        }
        return p;
    }

    @Test
    @DisplayName("Ohne Parameter: Weiterleitung; nach ?logout, ?error und ?error=oidc: keine")
    void keineWeiterleitungNachLogoutOderFehler() throws IOException {
        String ausdruck = renderedAusdruck();

        assertEquals(true, leitetWeiter(ausdruck, param()));
        assertEquals(false, leitetWeiter(ausdruck, param("logout", "")), "?logout ohne Wert (so haengt Spring es an)");
        assertEquals(false, leitetWeiter(ausdruck, param("error", "")), "?error ohne Wert");
        assertEquals(false, leitetWeiter(ausdruck, param("error", "oidc")), "?error=oidc");
    }

    @Test
    @DisplayName("Positivkontrolle: der alte Ausdruck mit empty haette nach ?logout weitergeleitet")
    void alterAusdruckHaetteGeschleift() {
        String alt = "loginSetupBean.oidcAutoRedirectActive and empty param.error and empty param.logout";
        assertEquals(true, leitetWeiter(alt, param("logout", "")));
    }
}
