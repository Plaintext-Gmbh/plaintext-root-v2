/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.testsupport;

import ch.plaintext.lader.ModulManifest;
import ch.plaintext.testsupport.beispiel.BeispielRollen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Karte 2377 ROOT-7: die Basisklasse am Beispielmodul (Paket {@code beispiel}). */
class ModulManifestTestTest {

    private static final ModulManifestTest BEISPIEL = new ModulManifestTest() {
        @Override
        protected String modul() {
            return "terminal";
        }

        @Override
        protected String paket() {
            return "ch.plaintext.testsupport.beispiel";
        }

        @Override
        protected boolean nurHauptcode() {
            return false;
        }
    };

    @Test
    void erzeugtRollenMenueUndProperties() {
        ModulManifest m = BEISPIEL.erzeugen();

        assertEquals("terminal", m.modul());
        assertEquals(2, m.rollen().size());
        ModulManifest.Rolle t = m.rollen().getFirst();
        assertEquals("terminal", t.name());
        assertEquals("ROLE_TERMINAL", t.authority());
        assertEquals("ZUGANG", t.art());
        assertTrue(t.ohneUmgehung());
        assertEquals("ADMIN", m.rollen().get(1).art());
        ModulManifest.Menue menue = m.menue().getFirst();
        assertEquals("terminal", menue.menuId());
        assertEquals("Misc", menue.parent());
        assertEquals(10, menue.order());
        assertEquals("/misc-terminal.html", menue.link());
        assertEquals(java.util.List.of("terminal"), menue.rollen());
        assertEquals(java.util.List.of("plaintext.terminal"), m.properties());
    }

    @Test
    void schreibtUndVergleichtUndWirdBeiGeaenderterRolleRot(@TempDir Path dir) throws Exception {
        Path ziel = dir.resolve(ModulManifest.PFAD);
        assertThrows(AssertionError.class, () -> ModulManifestTest.pruefeOderSchreibe("x", ziel, false), "fehlt");

        ModulManifestTest.pruefeOderSchreibe(BEISPIEL.erzeugen().alsYaml(), ziel, true);
        assertTrue(Files.exists(ziel));
        ModulManifestTest.pruefeOderSchreibe(BEISPIEL.erzeugen().alsYaml(), ziel, false);

        String vorher = BeispielRollen.beschreibung;
        try {
            BeispielRollen.beschreibung = "geändert";
            String neu = BEISPIEL.erzeugen().alsYaml();
            assertThrows(AssertionError.class, () -> ModulManifestTest.pruefeOderSchreibe(neu, ziel, false));
        } finally {
            BeispielRollen.beschreibung = vorher;
        }
    }
}
