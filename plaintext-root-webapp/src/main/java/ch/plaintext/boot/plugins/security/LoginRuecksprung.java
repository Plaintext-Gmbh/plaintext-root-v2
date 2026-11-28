/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.plugins.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Karte 2566 (Presenter, KI-Verbindung): where the login goes back to when a module sent the user there. The
 * OAuth authorization of plaintext-modul-ki-verbindung needs it: Claude opens /ki/oauth/authorize, the user signs
 * in, and must then see the consent page, not the start page.
 *
 * <p>The module keeps its target in its own session attribute and hands it out once. root accepts only a local
 * path ({@code /…}, not {@code //…}, no backslash, no scheme), so a module cannot turn the login into an open
 * redirect. A forced password change and a remembered deep link take precedence.
 */
public interface LoginRuecksprung {

    /**
     * @return the local path to continue with after the login (for example {@code /ki/oauth/authorize?…}), or
     *         {@code null}; called once per successful login and may remove its session attribute
     */
    String ziel(HttpServletRequest request);
}
