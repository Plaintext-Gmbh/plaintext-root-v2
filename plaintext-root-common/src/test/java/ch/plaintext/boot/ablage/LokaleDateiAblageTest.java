/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.ablage;

import ch.plaintext.ablagen.AblageEintrag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Karte 2096: die Ablage «Lokale Datei» — vor allem, dass sie ihren Ordner nie verlässt. */
class LokaleDateiAblageTest {

    @TempDir
    Path tmp;

    private LokaleDateiAblage ablage() throws IOException {
        return LokaleDateiAblage.unter("notizen", tmp.resolve("basis"), "demo");
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void schreibenLesenUeberschreiben_mitFehlendenOrdnern() throws IOException {
        LokaleDateiAblage a = ablage();
        a.schreibe("anna/notiz.md", b("eins"), "text/markdown");
        a.schreibe("/anna/notiz.md/", b("zwei"), null);
        assertThat(new String(a.lies("anna/notiz.md"), StandardCharsets.UTF_8)).isEqualTo("zwei");
        assertThat(a.existiert("anna")).isTrue();
        assertThat(a.existiert("")).isTrue();
        assertThat(Files.readString(tmp.resolve("basis/demo/anna/notiz.md"))).isEqualTo("zwei");
    }

    @Test
    void listeRelativOhneTempDateien_fehlenderOrdnerIstLeer() throws IOException {
        LokaleDateiAblage a = ablage();
        a.schreibe("a.md", b("x"), null);
        a.legeOrdnerAn("ordner/unter");
        Files.writeString(tmp.resolve("basis/demo/.pt-tmp-rest.tmp"), "halb");
        assertThat(a.liste("")).extracting(AblageEintrag::pfad).containsExactly("a.md", "ordner");
        assertThat(a.liste("ordner")).extracting(AblageEintrag::pfad, AblageEintrag::ordner)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("ordner/unter", true));
        assertThat(a.liste("gibts-nicht")).isEmpty();
    }

    @Test
    void ungueltigePfadeWerdenAbgewiesen() throws IOException {
        LokaleDateiAblage a = ablage();
        for (String p : new String[] {"../raus.md", "a/../../raus.md", "a/./b.md", "a\\b.md", "a//b.md", "zeile\nneu.md", ""}) {
            assertThatThrownBy(() -> a.schreibe(p, b("x"), null)).as(p).isInstanceOf(IOException.class);
        }
        assertThat(tmp.resolve("basis/raus.md")).doesNotExist();
        assertThat(tmp.resolve("raus.md")).doesNotExist();
    }

    @Test
    void symlinkNachDraussenWirdAbgewiesen() throws IOException {
        LokaleDateiAblage a = ablage();
        Path draussen = Files.createDirectories(tmp.resolve("draussen"));
        Files.writeString(draussen.resolve("geheim.txt"), "geheim");
        Files.createSymbolicLink(tmp.resolve("basis/demo/link"), draussen);
        Files.createSymbolicLink(tmp.resolve("basis/demo/datei-link.txt"), draussen.resolve("geheim.txt"));

        assertThatThrownBy(() -> a.lies("link/geheim.txt")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> a.lies("datei-link.txt")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> a.schreibe("link/neu.txt", b("x"), null)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> a.liste("link")).isInstanceOf(IOException.class);
        assertThat(draussen.resolve("neu.txt")).doesNotExist();
    }

    @Test
    void verschiebenUndLoeschen() throws IOException {
        LokaleDateiAblage a = ablage();
        a.schreibe("o/a.md", b("x"), null);
        a.verschiebe("o/a.md", "archiv/b.md");
        assertThat(a.existiert("o/a.md")).isFalse();
        assertThat(a.existiert("archiv/b.md")).isTrue();
        assertThatThrownBy(() -> a.verschiebe("archiv", "archiv/tief")).hasMessageContaining("in sich selbst");
        assertThatThrownBy(() -> a.verschiebe("fehlt.md", "x.md")).hasMessageContaining("Quelle");
        a.schreibe("x.md", b("x"), null);
        assertThatThrownBy(() -> a.verschiebe("x.md", "archiv/b.md")).hasMessageContaining("existiert");

        assertThatThrownBy(() -> a.loescheOrdner("archiv", false)).isInstanceOf(DirectoryNotEmptyException.class);
        a.loescheOrdner("archiv", true);
        assertThat(a.existiert("archiv")).isFalse();
        a.loesche("x.md");
        assertThatThrownBy(() -> a.loesche("x.md")).hasMessageContaining("nicht gefunden");
    }

    @Test
    void rekursivesLoeschenFolgtKeinemSymlink() throws IOException {
        LokaleDateiAblage a = ablage();
        Path draussen = Files.createDirectories(tmp.resolve("draussen"));
        Files.writeString(draussen.resolve("bleibt.txt"), "bleibt");
        a.legeOrdnerAn("weg");
        Files.createSymbolicLink(tmp.resolve("basis/demo/weg/link"), draussen);
        a.loescheOrdner("weg", true);
        assertThat(draussen.resolve("bleibt.txt")).exists();
    }

    @Test
    void ohneBasisAus_undPruefenNenntKeinenPfad() throws IOException {
        assertThatThrownBy(() -> LokaleDateiAblage.unter("x", null, "")).hasMessageContaining("plaintext.ablagen.lokal.basis");
        assertThatThrownBy(() -> LokaleDateiAblage.unter("x", tmp, "../raus")).isInstanceOf(IOException.class);
        String meldung = ablage().pruefe();
        assertThat(meldung).doesNotContain(tmp.toString());
        assertThat(ablage().liste("")).isEmpty();
    }
}
