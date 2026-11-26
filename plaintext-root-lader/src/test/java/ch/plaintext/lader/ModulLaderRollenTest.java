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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Karte 2377 ROOT-8: Rollen der Jars mit und ohne Manifest, als Startlog und als Tabelle. */
class ModulLaderRollenTest {

    private static Path mitManifest(Path dir) throws Exception {
        Path jar = dir.resolve("plaintext-root-files-3.84.0.jar");
        Files.write(jar, TestJars.zip(ModulManifest.PFAD, ModulManifestLeserTest.FILES.alsYaml(),
                "META-INF/maven/ch.plaintext.v2/plaintext-root-files/pom.properties", "version=3.84.0\n"));
        return jar;
    }

    private static Path ohneManifest(Path dir) throws Exception {
        Path jar = dir.resolve("alt-1.0.0.jar");
        Files.write(jar, TestJars.zip("a/A.class", "x"));
        return jar;
    }

    @Test
    void startlogNenntRollenUndFehlendesManifest(@TempDir Path dir) throws Exception {
        List<String> log = ModulLader.rollenLog(List.of(mitManifest(dir), ohneManifest(dir)));

        assertEquals(List.of("files 3.84.0: Rollen files (ZUGANG)",
                "alt-1.0.0.jar: kein Modul-Manifest (META-INF/plaintext/modul.yaml), Rollen unbekannt"), log);
    }

    @Test
    void tabelleListetBeideJars(@TempDir Path dir) throws Exception {
        List<String> t = ModulLader.rollenTabelle(List.of(mitManifest(dir), ohneManifest(dir), dir.resolve("fehlt.jar")));

        assertEquals("modul\tversion\tauthority\tart\tohneUmgehung\tbeschreibung", t.get(0));
        assertTrue(t.get(1).startsWith("files\t3.84.0\tROLE_FILES\tZUGANG\tfalse\t"), t.get(1));
        assertEquals("# alt-1.0.0.jar: kein Modul-Manifest (META-INF/plaintext/modul.yaml)", t.get(2));
        assertTrue(t.get(3).startsWith("# UNLESBAR "), t.get(3));
    }
}
