/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files.speicher;

import ch.plaintext.files.DateiEintrag;
import ch.plaintext.files.DateiSpeicher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Karte 2377 (ROOT-12): Ordner, Verschieben und rekursives Loeschen, nativ und ueber die Default-Methoden. */
class DateisystemDateiSpeicherTest {

    @TempDir
    Path wurzel;

    @Test
    void nativOrdnerAnlegenVerschiebenLoeschen() {
        DateisystemDateiSpeicher s = new DateisystemDateiSpeicher(wurzel);
        s.legeOrdnerAn("files/anna/leer");
        assertTrue(s.istOrdner("files/anna/leer"));
        assertFalse(s.istOrdner("files/anna/nix"));
        s.upload("files/anna/a/x.txt", "x".getBytes());
        s.upload("files/anna/a/tief/y.txt", "y".getBytes());

        s.verschiebe("files/anna/a", "files/anna/b");
        assertFalse(Files.exists(wurzel.resolve("files/anna/a")));
        assertArrayEquals("y".getBytes(), s.download("files/anna/b/tief/y.txt"));
        s.verschiebe("files/anna/b/x.txt", "files/anna/c/x.txt");
        assertTrue(s.exists("files/anna/c/x.txt"));
        assertThrows(IllegalArgumentException.class, () -> s.verschiebe("files/anna/b", "files/anna/c"));
        assertThrows(IllegalArgumentException.class, () -> s.verschiebe("files/anna/fehlt", "files/anna/d"));
        assertThrows(IllegalArgumentException.class, () -> s.verschiebe("files/anna/b", "../raus"));

        assertTrue(s.loescheRekursiv("files/anna/b"));
        assertFalse(Files.exists(wurzel.resolve("files/anna/b")));
        assertFalse(s.loescheRekursiv("files/anna/b"));
        assertTrue(s.loescheRekursiv("files/anna/c/x.txt"));
        assertNull(s.download("files/anna/c/x.txt"));
    }

    @Test
    void defaultMethodenNurUeberDieFuenfPrimitive() {
        NurPrimitive s = new NurPrimitive();
        s.upload("files/anna/a/x.txt", "x".getBytes());
        s.upload("files/anna/a/tief/y.txt", "y".getBytes());
        assertTrue(s.istOrdner("files/anna/a"));
        assertTrue(s.istOrdner("files/anna/a/tief"));
        assertFalse(s.istOrdner("files/anna/a/x.txt"));
        s.legeOrdnerAn("files/anna/leer"); // Standard: kein Fehler, Ordner entsteht mit dem ersten Upload

        s.verschiebe("files/anna/a", "files/anna/b");
        assertNull(s.download("files/anna/a/x.txt"));
        assertArrayEquals("x".getBytes(), s.download("files/anna/b/x.txt"));
        assertArrayEquals("y".getBytes(), s.download("files/anna/b/tief/y.txt"));
        assertThrows(IllegalArgumentException.class, () -> s.verschiebe("files/anna/b/x.txt", "files/anna/b/tief/y.txt"));
        assertThrows(IllegalArgumentException.class, () -> s.verschiebe("files/anna/fehlt", "files/anna/z"));

        assertTrue(s.loescheRekursiv("files/anna/b"));
        assertEquals(List.of(), s.list("files/anna/b"));
        assertFalse(s.loescheRekursiv("files/anna/b"));
    }

    /** Speicher, der nur die fuenf alten Methoden umsetzt (wie ein fremder Adapter): Pfade als Schluessel. */
    static final class NurPrimitive implements DateiSpeicher {
        private final Map<String, byte[]> dateien = new LinkedHashMap<>();

        @Override
        public void upload(String pfad, byte[] inhalt) {
            dateien.put(pfad, inhalt.clone());
        }

        @Override
        public byte[] download(String pfad) {
            return dateien.get(pfad);
        }

        @Override
        public boolean exists(String pfad) {
            return dateien.containsKey(pfad);
        }

        @Override
        public List<DateiEintrag> list(String ordner) {
            String praefix = ordner == null || ordner.isBlank() ? "" : ordner + "/";
            List<DateiEintrag> aus = new ArrayList<>();
            List<String> gesehen = new ArrayList<>();
            for (Map.Entry<String, byte[]> e : dateien.entrySet()) {
                if (!e.getKey().startsWith(praefix)) {
                    continue;
                }
                String rest = e.getKey().substring(praefix.length());
                int i = rest.indexOf('/');
                String name = i < 0 ? rest : rest.substring(0, i);
                if (gesehen.contains(name)) {
                    continue;
                }
                gesehen.add(name);
                aus.add(new DateiEintrag(name, i >= 0, i >= 0 ? 0 : e.getValue().length, Instant.EPOCH));
            }
            return aus;
        }

        @Override
        public boolean delete(String pfad) {
            return dateien.remove(pfad) != null;
        }
    }
}
