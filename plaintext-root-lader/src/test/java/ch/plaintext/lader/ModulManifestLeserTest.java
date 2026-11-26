/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.lader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Karte 2377 ROOT-7: Manifest schreiben, lesen, aus einem Jar ohne Spring. */
class ModulManifestLeserTest {

    static final ModulManifest FILES = new ModulManifest("files", null,
            List.of(new ModulManifest.Rolle("files", "ROLE_FILES", "Persönliche \"Ablage\", eigene Dateien", "ZUGANG",
                    false)),
            List.of(new ModulManifest.Menue("files", "Misc", 10, "Files", "files/ablage.html",
                    List.of("FILES", "ADMIN", "ROOT"))),
            List.of("plaintext.files"));

    @Test
    void schreibenUndLesenIstVerlustfrei() {
        String yaml = FILES.alsYaml();
        ModulManifest zurueck = ModulManifest.aus(yaml);

        assertEquals(FILES.rollen(), zurueck.rollen());
        assertEquals(FILES.menue(), zurueck.menue());
        assertEquals(FILES.properties(), zurueck.properties());
        assertEquals("files", zurueck.modul());
        assertNull(zurueck.version());
        assertEquals(yaml, zurueck.alsYaml());
    }

    @Test
    void leereAbschnitte() {
        ModulManifest leer = new ModulManifest("x", null, List.of(), List.of(), List.of());
        assertEquals(leer.alsYaml(), ModulManifest.aus(leer.alsYaml()).alsYaml());
    }

    @Test
    void ausDemJarMitVersionAusPomProperties(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("plaintext-root-files-3.84.0.jar");
        Files.write(jar, TestJars.zip(ModulManifest.PFAD, FILES.alsYaml(),
                "META-INF/maven/ch.plaintext.v2/plaintext-root-files/pom.properties", "version=3.84.0\n"));

        ModulManifest m = ModulManifest.lesen(jar).orElseThrow();
        assertEquals("3.84.0", m.version());
        assertEquals("ROLE_FILES", m.rollen().getFirst().authority());
    }

    @Test
    void jarOhneManifestIstLeer(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("a.jar");
        Files.write(jar, TestJars.zip("a/A.class", "x"));
        assertTrue(ModulManifest.lesen(jar).isEmpty());
    }

    @Test
    void unbekannteZeileIstEinFehler() {
        String kaputt = FILES.alsYaml().replace("    art: ZUGANG\n", "    art: ZUGANG\n    url: http://x\n");
        assertThrows(IllegalArgumentException.class, () -> ModulManifest.aus(kaputt));
        assertThrows(IllegalArgumentException.class, () -> ModulManifest.aus("modul: a b\n"));
    }
}
