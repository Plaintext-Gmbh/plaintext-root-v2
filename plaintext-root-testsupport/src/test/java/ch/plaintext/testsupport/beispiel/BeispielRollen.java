/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.testsupport.beispiel;

import ch.plaintext.framework.PlaintextRole;
import ch.plaintext.framework.PlaintextRoleProvider;
import ch.plaintext.rollen.RollenArt;

import java.util.Set;

/** Beispielmodul für den ModulManifestTest (Karte 2377 ROOT-7). */
public class BeispielRollen implements PlaintextRoleProvider {

    /** Von aussen änderbar, damit der Test den Drift-Fall zeigen kann. */
    public static String beschreibung = "Terminal-Sitzungen im Browser bedienen";

    @Override
    public Set<String> getRoles() {
        return Set.of("terminal");
    }

    @Override
    public Set<PlaintextRole> getDeclaredRoles() {
        return Set.of(new PlaintextRole("terminal", beschreibung).modul("terminal").ohneUmgehung(true),
                new PlaintextRole("terminal-admin", "Sitzungen anderer sehen").modul("terminal").art(RollenArt.ADMIN));
    }
}
