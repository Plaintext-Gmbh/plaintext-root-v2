/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Karte 1904: Vertrag des eigenstaendigen Artefakts plaintext-root-tablesettings. Eine App ohne
 * plaintext-root-template (Inventar/BIT, Oblique-Theme) bekommt hieraus genau pt:tableSettings:
 * Taglib im Namensraum der Seiten, das Tag-File daneben, JS und CSS unter der Bibliothek
 * plaintext-layout und die Java-Klassen - aber nichts, was an root haengt.
 */
class TablesettingsArtefaktTest {

    private static final ClassLoader CL = TablesettingsArtefaktTest.class.getClassLoader();

    private static String lies(String pfad) throws IOException {
        try (InputStream in = CL.getResourceAsStream(pfad)) {
            assertNotNull(in, "Ressource fehlt im Artefakt: " + pfad);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("Das Taglib liefert pt:tableSettings im Namensraum http://plaintext.ch/ui, das Tag-File liegt daneben")
    void taglibUndTagFile() throws IOException {
        String taglib = lies("META-INF/plaintext-tablesettings.taglib.xml");
        assertTrue(taglib.contains("<namespace>http://plaintext.ch/ui</namespace>"), taglib);
        assertTrue(taglib.contains("<tag-name>tableSettings</tag-name>"), taglib);
        assertTrue(taglib.contains("<source>tags/tableSettings.xhtml</source>"), taglib);
        assertTrue(lies("META-INF/tags/tableSettings.xhtml").contains("table-settings.js"));
    }

    @Test
    @DisplayName("JS und CSS liegen unter der Bibliothek plaintext-layout, wie das Tag sie einbindet")
    void jsUndCss() throws IOException {
        assertFalse(lies("META-INF/resources/plaintext-layout/js/table-settings.js").isBlank());
        assertFalse(lies("META-INF/resources/plaintext-layout/css/table-settings.css").isBlank());
    }

    @Test
    @DisplayName("Die Java-Klassen sind dabei, der root-gebundene UserPreferenceTableStateStore nicht")
    void klassenOhneRootBindung() throws ClassNotFoundException {
        assertNotNull(Class.forName("ch.plaintext.boot.table.TableSettings"));
        assertNotNull(Class.forName("ch.plaintext.boot.table.TableStateStore"));
        URL store = CL.getResource("ch/plaintext/boot/table/UserPreferenceTableStateStore.class");
        assertNull(store, "UserPreferenceTableStateStore haengt an root und darf nicht ins Artefakt");
    }

    @Test
    @DisplayName("Keine weiteren Tags oder Layout-Dateien aus dem Template (sonst Kollision mit dem Theme)")
    void nichtsWeiteresAusDemTemplate() {
        assertNull(CL.getResource("META-INF/plaintext.taglib.xml"), "Template-Taglib gehoert nicht hierher");
        assertNull(CL.getResource("META-INF/tags/table.xhtml"));
        assertNull(CL.getResource("META-INF/resources/includes/template.xhtml"));
    }
}
