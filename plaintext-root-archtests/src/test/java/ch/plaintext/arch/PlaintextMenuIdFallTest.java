/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.arch;

import ch.plaintext.boot.menu.MenuAnnotation;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Karte 2377 (ROOT-4): die Regel {@code menuIdPflichtBeiModuleId} ist rot ohne menuId, gruen mit, und ohne moduleId frei. */
class PlaintextMenuIdFallTest {

    @MenuAnnotation(title = "Ohne", link = "ohne.html", parent = "Root", moduleId = "probe")
    static class OhneMenuId {
    }

    @MenuAnnotation(title = "Mit", link = "mit.html", parent = "Root", moduleId = "probe", menuId = "probe")
    static class MitMenuId {
    }

    @MenuAnnotation(title = "Frei", link = "frei.html", parent = "Admin")
    static class OhneModuleId {
    }

    private static JavaClasses importiere(Class<?>... k) {
        return new ClassFileImporter().importClasses(k);
    }

    @Test
    @DisplayName("enforce: moduleId ohne menuId rot, mit menuId gruen, ohne moduleId frei")
    void regel() {
        String vorher = System.getProperty(PlaintextArchitectureTest.MENU_ID_SCHALTER);
        System.setProperty(PlaintextArchitectureTest.MENU_ID_SCHALTER, "enforce");
        try {
            regelScharf();
        } finally {
            if (vorher == null) {
                System.clearProperty(PlaintextArchitectureTest.MENU_ID_SCHALTER);
            } else {
                System.setProperty(PlaintextArchitectureTest.MENU_ID_SCHALTER, vorher);
            }
        }
    }

    /** Vorgabe (Konsumenten-Repos, Uebergang ein Release): Verstoss wird nur gemeldet, der Test bleibt gruen. */
    @Test
    @DisplayName("ohne Schalter: moduleId ohne menuId nur gemeldet")
    void ohneSchalterNurGemeldet() {
        String vorher = System.getProperty(PlaintextArchitectureTest.MENU_ID_SCHALTER);
        System.clearProperty(PlaintextArchitectureTest.MENU_ID_SCHALTER);
        try {
            assertDoesNotThrow(() -> PlaintextArchitectureTest.menuIdPflichtBeiModuleId.check(importiere(OhneMenuId.class)));
        } finally {
            if (vorher != null) {
                System.setProperty(PlaintextArchitectureTest.MENU_ID_SCHALTER, vorher);
            }
        }
    }

    private void regelScharf() {
        AssertionError f = assertThrows(AssertionError.class,
                () -> PlaintextArchitectureTest.menuIdPflichtBeiModuleId.check(importiere(OhneMenuId.class)));
        assertTrue(f.getMessage().contains("ohne menuId") && f.getMessage().contains(PlaintextArchitectureTest.MENU_ID_REGEL),
                () -> "erwartet Hinweis auf menuId und Allowlist in: " + f.getMessage());
        assertDoesNotThrow(() -> PlaintextArchitectureTest.menuIdPflichtBeiModuleId.check(importiere(MitMenuId.class)));
        assertDoesNotThrow(() -> PlaintextArchitectureTest.menuIdPflichtBeiModuleId.check(importiere(OhneModuleId.class)));
    }
}
