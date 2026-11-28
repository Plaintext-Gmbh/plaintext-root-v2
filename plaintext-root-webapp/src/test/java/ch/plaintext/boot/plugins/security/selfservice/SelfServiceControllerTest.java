/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.plugins.security.selfservice;

import ch.plaintext.framework.EigeneAdresse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Karte 1068: the base URL of reset and verification links must never come from the request.
 *
 * <p>Why a test for something this small: the old code was correct in every single-host set-up
 * and only wrong behind a proxy that does not overwrite {@code X-Forwarded-Host}. That is the
 * kind of regression a reviewer does not see in a diff — a future "let's fall back to the request
 * when nothing is configured" would look reasonable and reopen the phishing path. These tests
 * pin the decision: configured address, or empty, but not the request.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SelfServiceControllerTest {

    @Mock RegistrationService registrationService;
    @Mock PasswordResetService passwordResetService;
    @Mock HttpServletRequest request;
    @Mock jakarta.servlet.http.HttpServletResponse response;
    @Mock org.springframework.security.core.userdetails.UserDetailsService userDetailsService;
    @Mock ch.plaintext.boot.plugins.security.SessionLoginFinalizer sessionLoginFinalizer;

    private SelfServiceProperties properties;
    private EigeneAdresse eigeneAdresse;

    @BeforeEach
    void setUp() {
        properties = new SelfServiceProperties();
        // Without a settings module the address comes from plaintext.app.ownhost or the given default.
        eigeneAdresse = new EigeneAdresse(null);
        // The request looks like an attack: a forged host, and the code must not even look at it.
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("pruefung.invalid");
        when(request.getServerPort()).thenReturn(443);
        when(request.getHeader("X-Forwarded-Host")).thenReturn("pruefung.invalid");
    }

    private SelfServiceController controller(String plaintextBaseurl) {
        return new SelfServiceController(registrationService, passwordResetService, properties,
                eigeneAdresse, plaintextBaseurl, userDetailsService, sessionLoginFinalizer);
    }

    /** Karte 2410: ein verbrauchter oder abgelaufener Link zeigt beim Aufruf gleich «Link ungültig», kein Formular. */
    @Test
    void verbrauchterLinkZeigtGleichLinkUngueltig() {
        SelfServiceController c = controller(null);
        when(registrationService.tokenGueltig("alt")).thenReturn(false);
        when(registrationService.tokenGueltig("frisch")).thenReturn(true);
        when(passwordResetService.tokenGueltig("alt")).thenReturn(false);
        when(passwordResetService.tokenGueltig("frisch")).thenReturn(true);

        String reg = c.registerVerifyForm("alt", request).getBody();
        org.assertj.core.api.Assertions.assertThat(reg).contains("Link ungültig").doesNotContain("type=\"password\"");
        org.assertj.core.api.Assertions.assertThat(c.registerVerifyForm("frisch", request).getBody())
                .contains("Passwort wählen").contains("type=\"password\"");

        String res = c.resetConfirmForm("alt", request).getBody();
        org.assertj.core.api.Assertions.assertThat(res).contains("Link ungültig").doesNotContain("type=\"password\"");
        org.assertj.core.api.Assertions.assertThat(c.resetConfirmForm("frisch", request).getBody())
                .contains("Neues Passwort wählen").contains("type=\"password\"");
    }

    /** Karte 2574: Registrierung zeigt AGB und Datenschutz nur, wenn die Instanz sie setzt; Adressen werden escaped. */
    @Test
    void rechtlicherHinweisNurMitAdressen() {
        org.assertj.core.api.Assertions.assertThat(controller("").registerForm(request).getBody()).doesNotContain("AGB");
        properties.setAgbUrl("/agb");
        properties.setDatenschutzUrl("/datenschutz?a=1&b=\"x\"");
        String body = controller("").registerForm(request).getBody();
        org.assertj.core.api.Assertions.assertThat(body).contains("Mit dem Konto gelten die")
                .contains("href=\"/agb\"").contains("/datenschutz?a=1&amp;b=&quot;x&quot;").doesNotContain("b=\"x\"");
    }

    /** Karte 2554: Versandfehler zeigt eine ehrliche Meldung, nicht «Prüfen Sie Ihren Posteingang». */
    @Test
    void versandfehlerWirdAngezeigt() {
        when(registrationService.startRegistration(anyString(), anyString(), anyString()))
                .thenReturn(RegistrationService.RegistrationOutcome.VERSAND_FEHLER);

        String body = controller("https://x.example").registerSubmit("a@example.org", request).getBody();

        org.assertj.core.api.Assertions.assertThat(body).contains("nicht gesendet").doesNotContain("Posteingang");
    }

    /** Karte 2554: nach dem Passwort direkt angemeldet, über den gemeinsamen Login-Weg. */
    @Test
    void nachBestaetigungDirektAngemeldet() throws Exception {
        when(registrationService.completeRegistration("t", "passwort-1"))
                .thenReturn(RegistrationService.RegistrationResult.success("a@example.org"));
        org.springframework.security.core.userdetails.UserDetails ud =
                org.springframework.security.core.userdetails.User.withUsername("a@example.org").password("x").roles("USER").build();
        when(userDetailsService.loadUserByUsername("a@example.org")).thenReturn(ud);

        var antwort = controller("").registerVerifySubmit("t", "passwort-1", request, response);

        org.junit.jupiter.api.Assertions.assertNull(antwort);
        verify(sessionLoginFinalizer).finalizeLogin(eq(ud), any(), eq("Selbstregistrierung"), eq(request), eq(response));
    }

    /** Karte 2554: scheitert die Anmeldung (z.B. gesperrt), bleibt die bisherige Seite «Zur Anmeldung». */
    @Test
    void ohneAnmeldungBleibtHinweisAufLogin() throws Exception {
        when(registrationService.completeRegistration("t", "passwort-1"))
                .thenReturn(RegistrationService.RegistrationResult.success("a@example.org"));
        when(userDetailsService.loadUserByUsername("a@example.org")).thenThrow(new RuntimeException("weg"));

        String body = controller("").registerVerifySubmit("t", "passwort-1", request, response).getBody();

        org.assertj.core.api.Assertions.assertThat(body).contains("Zur Anmeldung");
    }

    @Test
    void resetLinkNimmtDieKonfigurierteAdresse_nichtDenRequest() {
        ReflectionTestUtils.setField(eigeneAdresse, "ausKonfiguration", "https://app.plaintext.ch/");

        controller("https://fallback.example").resetSubmit("jemand", request);

        verify(passwordResetService).startReset(eq("jemand"), eq("default"), eq("https://app.plaintext.ch"));
        verify(request, never()).getServerName();
        verify(request, never()).getHeader("X-Forwarded-Host");
    }

    @Test
    void registrierungslinkNimmtPlaintextBaseurl_wennKeineEigeneAdresseGesetztIst() {
        controller("https://fallback.example/").registerSubmit("neu@example.org", request);

        verify(registrationService).startRegistration(eq("neu@example.org"), eq("default"),
                eq("https://fallback.example"));
        verify(request, never()).getServerName();
    }

    @Test
    void ohneJedeKonfigurationBleibtDieBasisLeer_stattAusDemRequestZuKommen() {
        SelfServiceController controller = controller("");

        String basis = controller.basisUrl();

        assertEquals("", basis);
        assertFalse(basis.contains("pruefung.invalid"));
        verify(request, never()).getServerName();
    }

    @Test
    void publicBaseUrlDerSelfServicePropertiesBleibtVorrangig_imService() {
        // Not this class's decision, but the contract the controller relies on: when
        // plaintext.selfservice.public-base-url is set, the services use it and ignore what the
        // controller passes. The controller therefore only has to guarantee that its value is
        // never request-derived — which the tests above pin.
        properties.setPublicBaseUrl("https://explizit.example");
        assertEquals("https://explizit.example", properties.getPublicBaseUrl());
    }

    /** Karte 2566: Links und Stylesheets der Instanz erscheinen auf den oeffentlichen Seiten, maskiert. */
    @Test
    void oeffentlicheSeiteTraegtLinksUndStil() {
        SelfServiceController c = controller(null);
        assertFalse(c.page("T", "<p>x</p>").contains("<nav class=\"pt-oeffentlich-links\">"));

        ch.plaintext.boot.plugins.jsf.oberflaeche.Oberflaeche o = new ch.plaintext.boot.plugins.jsf.oberflaeche.Oberflaeche();
        ch.plaintext.boot.plugins.jsf.oberflaeche.Oberflaeche.Link l = new ch.plaintext.boot.plugins.jsf.oberflaeche.Oberflaeche.Link();
        l.setText("Datenschutz");
        l.setUrl("/datenschutz.html?a=1&b=\"2\"");
        o.getLinks().add(l);
        o.getStylesheets().add("/stil/presenter.css");
        o.setStandardModus("dark");
        c.setOberflaeche(o);

        String html = c.page("T", "<p>x</p>");
        assertTrue(html.contains("<a href=\"/datenschutz.html?a=1&amp;b=&quot;2&quot;\">Datenschutz</a>"));
        assertTrue(html.contains("<link rel=\"stylesheet\" href=\"/stil/presenter.css\"/>"));
        assertTrue(html.contains("class=\"pt-dunkel\""));
    }
}
