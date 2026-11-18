/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files.service;

import ch.plaintext.framework.PlaintextRole;
import ch.plaintext.framework.PlaintextRoleProvider;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Meldet die Rolle {@value FilesRollen#ROLLE} an (Karte 2354), damit die Benutzerverwaltung und die Rollenzuteilung
 * sie zur Auswahl anbieten. Ohne diese Meldung müsste ein Admin den Namen von Hand tippen - und ein Tippfehler
 * sähe aus wie eine vergebene Rolle, die nichts öffnet.
 */
@Component
public class FilesRoleProvider implements PlaintextRoleProvider {

    @Override
    public Set<String> getRoles() {
        return Set.of(FilesRollen.ROLLE);
    }

    @Override
    public Set<PlaintextRole> getDeclaredRoles() {
        return Set.of(new PlaintextRole(FilesRollen.ROLLE,
                "Persönliche Dateiablage (Misc > Files): eigene Dateien hochladen, teilen und löschen"));
    }
}
