/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.plugins.jsf.oberflaeche;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Karte 2566 (Presenter, Karte 2565): what an instance adds to or removes from every page, including
 * the public ones that do not use the template (login, registration, confirmation, password reset).
 * A module such as plaintext-modul-datenschutz or plaintext-modul-stil only ships its page or its
 * stylesheet; the instance configuration wires it in here, so root needs no knowledge of the module.
 *
 * <pre>
 * plaintext.oberflaeche:
 *   links: [{text: Datenschutz, url: /datenschutz.html}]
 *   stylesheets: [/stil/presenter.css]
 *   topbar-benachrichtigungen: false
 *   topbar-api-token: false
 *   standard-modus: dark
 * </pre>
 *
 * Pages test {@code oberflaeche == null} first: an application without this bean keeps today's look.
 */
@Component("oberflaeche")
@ConfigurationProperties(prefix = "plaintext.oberflaeche")
@Getter
@Setter
public class Oberflaeche {

    /** Links in the footer of every page (privacy policy, imprint). Relative or absolute. */
    private List<Link> links = new ArrayList<>();

    /** Extra stylesheets, loaded after root's own CSS so that they win the cascade. */
    private List<String> stylesheets = new ArrayList<>();

    /** Bell with notifications in the top bar. */
    private boolean topbarBenachrichtigungen = true;

    /** «API Token» in the user menu of the top bar (a module may offer its own page instead). */
    private boolean topbarApiToken = true;

    /** {@code dark} or {@code light} for anonymous visitors and new users; empty = as before. */
    private String standardModus = "";

    /** {@code true} when a valid default mode is configured. */
    public boolean hatStandardModus() {
        return "dark".equals(standardModus) || "light".equals(standardModus);
    }

    @Getter
    @Setter
    public static class Link {
        private String text;
        private String url;
    }
}
