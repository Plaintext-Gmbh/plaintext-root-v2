/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Karte 2483 (Sonar S5852): die Tag-Ausdruecke der Linter finden dasselbe wie vorher und laufen
 * bei langen, nie geschlossenen Tags linear statt polynomiell.
 */
class LinterRegexLaufzeitTest {

    @TempDir
    Path dir;

    private void xhtml(String name, String inhalt) throws IOException {
        Files.writeString(dir.resolve(name), inhalt);
    }

    @Test
    void trefferBleibenGleich() throws IOException {
        xhtml("a.xhtml", """
                <p:selectOneMenu id="x" onchange=" this.form.submit() ; "/>
                <h:selectOneMenu onchange="submit()"/>
                <div data-pt-only="1" title="a>b" onclick="go()"/>
                <p:commandButton onclick="ok()"/>
                """);
        List<JsfViewLinter.Violation> jsf = JsfViewLinter.scan(dir);
        assertEquals(1, jsf.stream().filter(v -> v.rule().equals(JsfViewLinter.RULE_ONCHANGE_SUBMIT)).count());
        List<InlineJsLinter.Violation> js = InlineJsLinter.scan(dir);
        // h:selectOneMenu onchange und div onclick, nicht das p:-Tag und nicht data-pt-only
        assertEquals(2, js.stream().filter(v -> v.rule().equals(InlineJsLinter.RULE_INLINE_HANDLER)).count());
    }

    @Test
    void langeOffeneTagsLaufenLinear() throws IOException {
        xhtml("b.xhtml", "<p:" + "a".repeat(50_000) + " onchange=\"submit()" + " ".repeat(50_000)
                + "\n<" + "x".repeat(50_000));
        long t0 = System.nanoTime();
        JsfViewLinter.scan(dir);
        InlineJsLinter.scan(dir);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 2_000, "Linter brauchten " + ms + " ms");
    }
}
