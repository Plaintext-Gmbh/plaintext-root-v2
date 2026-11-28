/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.plugins.security.selfservice;

import ch.plaintext.framework.EigeneAdresse;
import ch.plaintext.boot.plugins.security.SessionLoginFinalizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import ch.plaintext.boot.plugins.jsf.oberflaeche.Oberflaeche;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Public-facing endpoints for self-registration and password-reset flows.
 *
 * <p>Renders minimal inline HTML — the framework has no Thymeleaf and these
 * pages are visited at most a handful of times per user, so a dedicated
 * template engine would be overkill. The HTML borrows the same fonts/colours
 * as the maintenance page so the look stays consistent across the
 * unauthenticated flows.
 *
 * <p>All user input is HTML-escaped before rendering so a stale token in the
 * URL or a typoed e-mail cannot reflect script back into the page.
 *
 * <p><b>The base URL of the links is never taken from the request (Karte 1068).</b> Until
 * 05.09.2026 the fallback for {@code plaintext.selfservice.public-base-url} was
 * {@code request.getServerName()}. Behind the {@code ForwardedHeaderFilter} that value comes from
 * {@code X-Forwarded-Host}, which the reverse proxy did not overwrite (Karte 1054) — so whoever
 * requested a password reset for a foreign address with a forged header made the victim receive
 * a genuine mail with a genuine token pointing to a foreign host. The proxy line has since been
 * added, but a link that leaves the house must not depend on a proxy line: the base comes from
 * {@link EigeneAdresse} (setting {@code app.ownhost}, then {@code plaintext.app.ownhost}, then
 * {@code plaintext.baseurl}), the same source every other outgoing link uses (Karte 1046).
 * {@code MagicLinkService} documents the same decision.
 */
@Controller
@Slf4j
public class SelfServiceController {

    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;
    private final SelfServiceProperties properties;
    private final EigeneAdresse eigeneAdresse;
    private final String plaintextBaseurl;
    private final UserDetailsService userDetailsService;
    private final SessionLoginFinalizer sessionLoginFinalizer;

    public SelfServiceController(RegistrationService registrationService,
                                 PasswordResetService passwordResetService,
                                 SelfServiceProperties properties,
                                 EigeneAdresse eigeneAdresse,
                                 @Value("${plaintext.baseurl:}") String plaintextBaseurl,
                                 UserDetailsService userDetailsService,
                                 SessionLoginFinalizer sessionLoginFinalizer) {
        this.userDetailsService = userDetailsService;
        this.sessionLoginFinalizer = sessionLoginFinalizer;
        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
        this.properties = properties;
        this.eigeneAdresse = eigeneAdresse;
        this.plaintextBaseurl = plaintextBaseurl == null ? "" : plaintextBaseurl;
    }

    /** Karte 2566: Links und Stylesheets der Instanz; fehlt die Bean (andere App), bleibt die Seite wie bisher. */
    private Oberflaeche oberflaeche;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setOberflaeche(Oberflaeche oberflaeche) {
        this.oberflaeche = oberflaeche;
    }

    // ---------- Registration ----------

    @GetMapping(value = "/register", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> registerForm(HttpServletRequest request) {
        return html(page("Registrieren", """
                <form method="post" action="/register" class="card">
                  <h1>Konto anlegen</h1>
                  %s
                  <label>E-Mail-Adresse
                    <input type="email" name="email" required autofocus/>
                  </label>
                  <button type="submit">Verifikations-E-Mail senden</button>
                  %s
                  <p class="subtle"><a href="/login.html">Zur Anmeldung</a></p>
                </form>
                """.formatted(csrfField(request), rechtlicherHinweis())));
    }

    @PostMapping(value = "/register", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> registerSubmit(@RequestParam("email") String email,
                                                 HttpServletRequest request) {
        RegistrationService.RegistrationOutcome outcome =
                registrationService.startRegistration(email, mandat(), basisUrl());
        if (outcome == RegistrationService.RegistrationOutcome.VERSAND_FEHLER) {
            // Karte 2554: say it, instead of «check your inbox» for a mail that was never sent.
            return html(page("Registrieren", """
                    <div class="card">
                      <h1>Mail konnte nicht gesendet werden</h1>
                      <p>An diese Adresse konnten wir den Bestätigungslink nicht schicken.
                      Bitte prüfen Sie die Schreibweise oder versuchen Sie es später erneut.</p>
                      <p class="subtle"><a href="/register">Erneut versuchen</a></p>
                    </div>
                    """));
        }
        return html(page("Registrieren", """
                <div class="card">
                  <h1>Prüfen Sie Ihren Posteingang</h1>
                  <p>Wir haben einen Verifikationslink an die angegebene Adresse
                  geschickt — sofern dort ein Konto eröffnet werden darf.</p>
                  <p class="subtle"><a href="/login.html">Zur Anmeldung</a></p>
                </div>
                """));
    }

    @GetMapping(value = "/register/verify", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> registerVerifyForm(@RequestParam("token") String token,
                                                     HttpServletRequest request) {
        // Karte 2410: ein verbrauchter oder abgelaufener Link zeigt gleich «Link ungültig», nicht erst nach dem Absenden.
        if (!registrationService.tokenGueltig(token)) {
            return registrierungLinkUngueltig();
        }
        return html(page("Passwort setzen", """
                <form method="post" action="/register/verify" class="card">
                  <h1>Passwort wählen</h1>
                  %s
                  <input type="hidden" name="token" value="%s"/>
                  <label>Neues Passwort (mindestens 8 Zeichen)
                    <input type="password" name="password" minlength="8" required autofocus/>
                  </label>
                  <button type="submit">Konto anlegen</button>
                </form>
                """.formatted(csrfField(request), escape(token))));
    }

    @PostMapping(value = "/register/verify", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> registerVerifySubmit(@RequestParam("token") String token,
                                                       @RequestParam("password") String password,
                                                       HttpServletRequest request,
                                                       HttpServletResponse response) {
        RegistrationService.RegistrationResult result =
                registrationService.completeRegistration(token, password);
        if (!result.ok()) {
            return registrierungLinkUngueltig();
        }
        // Karte 2554: the user has just proven the address and chosen the password — log in right away,
        // along the same path as the form login (session fixation, lockout, 2FA gate, start page).
        try {
            UserDetails user = userDetailsService.loadUserByUsername(result.username());
            sessionLoginFinalizer.finalizeLogin(user, user.getAuthorities(), "Selbstregistrierung", request, response);
            return null; // the success handler has written the redirect
        } catch (Exception e) {
            log.warn("Self-Service: automatische Anmeldung nach der Registrierung nicht moeglich: {}", e.getMessage());
        }
        return html(page("Konto angelegt", """
                <div class="card">
                  <h1>Willkommen!</h1>
                  <p>Ihr Konto wurde angelegt. Sie können sich jetzt anmelden.</p>
                  <p><a href="/login.html">Zur Anmeldung</a></p>
                </div>
                """));
    }

    // ---------- Password reset ----------

    @GetMapping(value = "/password-reset", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> resetForm(HttpServletRequest request) {
        return html(page("Passwort zurücksetzen", """
                <form method="post" action="/password-reset" class="card">
                  <h1>Passwort zurücksetzen</h1>
                  %s
                  <label>Benutzername / E-Mail
                    <input type="text" name="username" required autofocus/>
                  </label>
                  <button type="submit">Reset-Link senden</button>
                  <p class="subtle"><a href="/login.html">Zur Anmeldung</a></p>
                </form>
                """.formatted(csrfField(request))));
    }

    @PostMapping(value = "/password-reset", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> resetSubmit(@RequestParam("username") String username,
                                              HttpServletRequest request) {
        passwordResetService.startReset(username, mandat(), basisUrl());
        return html(page("Passwort zurücksetzen", """
                <div class="card">
                  <h1>Prüfen Sie Ihren Posteingang</h1>
                  <p>Wir haben einen Reset-Link an die hinterlegte Adresse geschickt —
                  sofern es ein passendes Konto gibt.</p>
                  <p class="subtle"><a href="/login.html">Zur Anmeldung</a></p>
                </div>
                """));
    }

    @GetMapping(value = "/password-reset/confirm", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> resetConfirmForm(@RequestParam("token") String token,
                                                   HttpServletRequest request) {
        // Karte 2410: wie bei der Registrierung - ungültiger Link sofort, ohne Formular.
        if (!passwordResetService.tokenGueltig(token)) {
            return resetLinkUngueltig();
        }
        return html(page("Neues Passwort", """
                <form method="post" action="/password-reset/confirm" class="card">
                  <h1>Neues Passwort wählen</h1>
                  %s
                  <input type="hidden" name="token" value="%s"/>
                  <label>Neues Passwort (mindestens 8 Zeichen)
                    <input type="password" name="password" minlength="8" required autofocus/>
                  </label>
                  <button type="submit">Passwort speichern</button>
                </form>
                """.formatted(csrfField(request), escape(token))));
    }

    @PostMapping(value = "/password-reset/confirm", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> resetConfirmSubmit(@RequestParam("token") String token,
                                                     @RequestParam("password") String password) {
        PasswordResetService.ResetResult result =
                passwordResetService.completeReset(token, password);
        if (!result.ok()) {
            return resetLinkUngueltig();
        }
        return html(page("Passwort gespeichert", """
                <div class="card">
                  <h1>Erledigt</h1>
                  <p>Ihr Passwort wurde aktualisiert. Sie können sich jetzt anmelden.</p>
                  <p><a href="/login.html">Zur Anmeldung</a></p>
                </div>
                """));
    }

    // ---------- Helpers ----------

    /**
     * Karte 2574: Hinweis auf AGB und Datenschutzerklaerung unter dem Formular, wenn die Instanz die Adressen setzt
     * ({@code plaintext.selfservice.agb-url}, {@code datenschutz-url}); sonst nichts (bestehende Instanzen unveraendert).
     */
    String rechtlicherHinweis() {
        String agb = properties.getAgbUrl() == null ? "" : properties.getAgbUrl().strip();
        String ds = properties.getDatenschutzUrl() == null ? "" : properties.getDatenschutzUrl().strip();
        if (agb.isEmpty() && ds.isEmpty()) {
            return "";
        }
        String agbLink = agb.isEmpty() ? "" : "<a href=\"" + escape(agb) + "\" target=\"_blank\" rel=\"noopener\">AGB</a>";
        String dsLink = ds.isEmpty() ? "" : "<a href=\"" + escape(ds) + "\" target=\"_blank\" rel=\"noopener\">Datenschutzerklärung</a>";
        String beide = agbLink.isEmpty() ? dsLink : dsLink.isEmpty() ? agbLink : agbLink + " und die " + dsLink;
        return "<p class=\"subtle\">Mit dem Konto gelten die " + beide + ".</p>";
    }

    private ResponseEntity<String> registrierungLinkUngueltig() {
        return html(page("Fehler", """
                <div class="card">
                  <h1>Link ungültig</h1>
                  <p>Der Verifikationslink ist abgelaufen oder bereits verbraucht.
                  Bitte starten Sie die Registrierung neu.</p>
                  <p class="subtle"><a href="/register">Erneut registrieren</a></p>
                </div>
                """));
    }

    private ResponseEntity<String> resetLinkUngueltig() {
        return html(page("Fehler", """
                <div class="card">
                  <h1>Link ungültig</h1>
                  <p>Der Reset-Link ist abgelaufen oder bereits verbraucht.
                  Bitte fordern Sie einen neuen an.</p>
                  <p class="subtle"><a href="/password-reset">Neuen Link anfordern</a></p>
                </div>
                """));
    }

    /**
     * Hidden CSRF input field for the HTML POST forms. Without this field Spring Security rejects
     * the POST with a 403 (the self-service paths are permitAll, but NOT exempt from CSRF) — the browser
     * then lands on an error/login page instead of on the confirmation.
     */
    private static String csrfField(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            return "";
        }
        return "<input type=\"hidden\" name=\"" + escape(token.getParameterName())
                + "\" value=\"" + escape(token.getToken()) + "\"/>";
    }

    private String mandat() {
        return properties.getDefaultMandat();
    }

    /**
     * Base URL for the links in verification and reset mails — from configuration only, never
     * from the request (see the class comment, Karte 1068). The services still prefer
     * {@code plaintext.selfservice.public-base-url} when it is set; this value is their fallback.
     *
     * <p>If nothing is configured at all the result is empty and the mail carries a relative
     * link. That is a broken link, not a phishing link — and the warning below says what to set.
     */
    String basisUrl() {
        String basis = eigeneAdresse.basis(plaintextBaseurl);
        if (basis == null || basis.isBlank()) {
            log.warn("Self-Service: keine eigene Adresse konfiguriert (app.ownhost, plaintext.app.ownhost "
                    + "oder plaintext.baseurl) — der Link in der Mail wird relativ und damit unbrauchbar. "
                    + "Aus dem Request wird die Adresse bewusst NICHT abgeleitet (Karte 1068).");
            return "";
        }
        return basis;
    }

    private static ResponseEntity<String> html(String body) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body);
    }

    private static String escape(String s) {
        // org.springframework.web.util.HtmlUtils.htmlEscape is recognized by CodeQL as an XSS sanitizer
        // (and is correct for the HTML context). It replaces the hand-written variant, which neutralized the same
        // meta characters but was not modelled as a sanitizer by the code scanning
        // (java/xss high alert on the html() output).
        return s == null ? "" : org.springframework.web.util.HtmlUtils.htmlEscape(s);
    }

    String page(String title, String body) {
        StringBuilder css = new StringBuilder();
        StringBuilder links = new StringBuilder();
        boolean dunkel = false;
        if (oberflaeche != null) {
            oberflaeche.getStylesheets().stream().filter(u -> u != null && !u.isBlank())
                    .forEach(u -> css.append("<link rel=\"stylesheet\" href=\"").append(escape(u)).append("\"/>"));
            oberflaeche.getLinks().stream().filter(l -> l.getUrl() != null && !l.getUrl().isBlank())
                    .forEach(l -> links.append(links.isEmpty() ? "" : " &middot; ")
                            .append("<a href=\"").append(escape(l.getUrl())).append("\">")
                            .append(escape(l.getText())).append("</a>"));
            dunkel = "dark".equals(oberflaeche.getStandardModus());
        }
        String fuss = links.isEmpty() ? "" : "<nav class=\"pt-oeffentlich-links\">" + links + "</nav>";
        return seite(escape(title), body + fuss, css.toString(), dunkel ? "pt-dunkel" : "");
    }

    private static String seite(String title, String body, String css, String htmlKlasse) {
        return """
                <!DOCTYPE html>
                <html lang="de" class="%s">
                <head>
                    <meta charset="UTF-8">
                    <title>%s &middot; Plaintext</title>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                    <style>
                        :root { color-scheme: light dark; }
                        * { box-sizing: border-box; }
                        body {
                            margin: 0;
                            min-height: 100vh;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI",
                                         Roboto, "Helvetica Neue", Arial, sans-serif;
                            background: linear-gradient(135deg, #f8fbff 0%%, #e8f4fd 100%%);
                            color: #333;
                        }
                        @media (prefers-color-scheme: dark) {
                            body { background: linear-gradient(135deg, #1a1a2e 0%%, #16213e 100%%); color: #e0e0e0; }
                            .card { background: #2a2b3d; border-color: #3c3d4f; }
                            input { background: #1a1a2e; color: #e0e0e0; border-color: #3c3d4f; }
                        }
                        .card {
                            background: #fff;
                            border: 1px solid #dee2e6;
                            border-radius: 12px;
                            padding: 2.5rem 2rem;
                            max-width: 26rem;
                            width: 90%%;
                            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
                        }
                        h1 { margin: 0 0 1rem 0; font-size: 1.4rem; }
                        label { display: block; margin: 1rem 0 0.25rem; font-size: 0.9rem; }
                        input { width: 100%%; padding: 0.6rem 0.75rem; font-size: 1rem;
                                border: 1px solid #ced4da; border-radius: 6px; }
                        button { width: 100%%; margin-top: 1.25rem; padding: 0.75rem;
                                 background: #4183c4; color: white; border: 0;
                                 border-radius: 6px; font-size: 1rem; cursor: pointer; }
                        button:hover { background: #3170b0; }
                        p { margin: 0.75rem 0; line-height: 1.45; }
                        .subtle { color: #6c757d; font-size: 0.9rem; text-align: center; }
                        a { color: #4183c4; text-decoration: none; }
                        a:hover { text-decoration: underline; }
                        .pt-dunkel { color-scheme: dark; }
                        .pt-dunkel body { background: linear-gradient(135deg, #1a1a2e 0%%, #16213e 100%%); color: #e0e0e0; }
                        .pt-dunkel .card { background: #2a2b3d; border-color: #3c3d4f; }
                        .pt-dunkel input { background: #1a1a2e; color: #e0e0e0; border-color: #3c3d4f; }
                        body { flex-direction: column; }
                        .pt-oeffentlich-links { margin-top: 1.25rem; font-size: 0.85rem; text-align: center; }
                    </style>
                    %s
                </head>
                <body>
                    %s
                </body>
                </html>
                """.formatted(htmlKlasse, title, css, body);
    }
}
