/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;

/**
 * Die Rollen der Dateiablage (Karte 2354): {@value #ROLLE} öffnet sie, {@code admin} und {@code root} öffnen sie
 * auch und sehen dazu die Ablagen aller Benutzer.
 *
 * <p>Direkt an den Authorities und nicht über den {@code SecurityProvider} der Menübibliothek: REST und MCP haben
 * keinen Menükontext, und die Antwort soll dieselbe sein, egal über welchen Weg jemand kommt. Verglichen wird wie
 * dort - mit und ohne {@code ROLE_}, sonst buchstabengetreu.</p>
 */
public final class FilesRollen {

    /** Name der Rolle, so wie sie die Benutzerverwaltung anbietet und speichert. */
    public static final String ROLLE = "files";
    /** Dieselben Rollen in der Schreibweise von {@code @MenuAnnotation(roles = …)}. */
    public static final String FILES = "FILES";
    public static final String ADMIN = "ADMIN";
    public static final String ROOT = "ROOT";

    private static final String PRAEFIX = "ROLE_";

    private FilesRollen() {
    }

    /** Darf die Ablage benutzen: Rolle {@value #ROLLE}, Admin oder Root. */
    public static boolean darfBenutzen(Authentication auth) {
        return haelt(auth, Set.of(FILES, ADMIN, ROOT));
    }

    /** Sieht die Ablagen aller Benutzer: Admin oder Root. */
    public static boolean siehtAlle(Authentication auth) {
        return haelt(auth, Set.of(ADMIN, ROOT));
    }

    /** Angemeldet, nicht anonym. */
    public static boolean angemeldet(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    private static boolean haelt(Authentication auth, Set<String> rollen) {
        if (!angemeldet(auth)) {
            return false;
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            String name = a.getAuthority();
            if (name != null && rollen.contains(name.startsWith(PRAEFIX) ? name.substring(PRAEFIX.length()) : name)) {
                return true;
            }
        }
        return false;
    }
}
