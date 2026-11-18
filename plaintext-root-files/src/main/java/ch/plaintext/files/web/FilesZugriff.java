/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files.web;

import ch.plaintext.boot.security.PageAccessGuardService;
import ch.plaintext.files.service.FilesRollen;
import ch.plaintext.files.service.FilesService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Wer darf die Ablage benutzen, und in wessen Ordner (Karte 1559, Karte 2354)? Seite, REST und MCP fragen dasselbe.
 *
 * <p><b>Benutzen.</b> Angemeldet und berechtigt für die Seite {@value #VIEW}. Mit eingeschaltetem Seitenschutz
 * entscheidet der Menüeintrag ({@link FilesMenu}: Rolle {@code files}, Admin, Root) - dieselbe Stelle, die den
 * Eintrag zeigt oder verbirgt, damit Menü und Zugriff nie auseinanderlaufen. Ohne Seitenschutz galt bis Karte 2354
 * «angemeldet genügt»; jetzt gelten dort dieselben drei Rollen direkt ({@link FilesRollen}).</p>
 *
 * <p><b>In wessen Ordner.</b> Im eigenen. Admin und Root dürfen zusätzlich einen fremden nennen
 * ({@link #ordner(String)}); wer das nicht ist und einen fremden nennt, bekommt keinen - nicht stillschweigend
 * den eigenen, sonst sähe eine abgewiesene Anfrage aus wie eine leere Ablage.</p>
 */
@Component
@RequiredArgsConstructor
public class FilesZugriff {

    public static final String VIEW = "/files/ablage.xhtml";

    private final ObjectProvider<PageAccessGuardService> guard;

    /** @return die Kennung des Benutzers, {@code null} ohne Anmeldung oder Recht */
    public String benutzer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!FilesRollen.angemeldet(auth)) {
            return null;
        }
        PageAccessGuardService g = guard.getIfAvailable();
        boolean darf = g == null || !g.isEnabled() ? FilesRollen.darfBenutzen(auth) : g.hasAccessToView(VIEW);
        return darf ? auth.getName() : null;
    }

    /** Sieht der Benutzer die Ablagen aller - darf er die Ablage benutzen und ist er Admin oder Root? */
    public boolean siehtAlle() {
        return benutzer() != null && FilesRollen.siehtAlle(SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * Der Ordner, in dem ein Aufruf arbeitet.
     *
     * @param gewuenscht Ordner oder Kennung eines Benutzers; leer = der eigene
     * @return der Ordnername; {@code null} ohne Zugriff - nicht angemeldet, ohne Recht, oder ein fremder Ordner
     *         für jemanden, der nicht Admin oder Root ist
     */
    public String ordner(String gewuenscht) {
        String b = benutzer();
        if (b == null) {
            return null;
        }
        try {
            String eigener = FilesService.ordnerName(b);
            if (gewuenscht == null || gewuenscht.isBlank()) {
                return eigener;
            }
            String ziel = FilesService.ordnerName(gewuenscht);
            return ziel.equals(eigener) || siehtAlle() ? ziel : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Gehört der Ordner dem angemeldeten Benutzer? */
    public boolean eigener(String ordner) {
        String b = benutzer();
        try {
            return b != null && ordner != null && FilesService.ordnerName(b).equals(ordner);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
