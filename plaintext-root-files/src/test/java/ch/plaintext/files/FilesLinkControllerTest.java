/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files;

import ch.plaintext.files.config.FilesProperties;
import ch.plaintext.files.service.FilesService;
import ch.plaintext.files.speicher.DateisystemDateiSpeicher;
import ch.plaintext.files.web.FilesLinkController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Karte 2354: ein Link mit «Direkt im Browser anzeigen» liefert Bilder, PDF und Text {@code inline} mit ihrem Typ;
 * ohne den Haken bleibt es beim Download. Was im Browser etwas ausführen könnte, lädt auch ein Anzeige-Link herunter.
 */
class FilesLinkControllerTest {

    private static final String CSP = "Content-Security-Policy";

    @TempDir
    Path wurzel;
    private final SpeicherLinkTokenStore links = new SpeicherLinkTokenStore();
    private FilesService service;
    private FilesLinkController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new FilesService(new DateisystemDateiSpeicher(wurzel), links, new FilesProperties(),
                mock(ObjectProvider.class));
        controller = new FilesLinkController(service);
    }

    private ResponseEntity<byte[]> oeffne(String datei, boolean imBrowser) {
        service.hochladen("anna", datei, "inhalt".getBytes());
        return controller.herunterladen(service.linkErstellen("anna", datei, 0, null, imBrowser).token());
    }

    private static String disposition(ResponseEntity<byte[]> antwort) {
        return antwort.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
    }

    @Test
    void ohneHakenWirdHeruntergeladenWieBisher() {
        ResponseEntity<byte[]> antwort = oeffne("bild.png", false);

        assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(disposition(antwort)).startsWith("attachment").contains("bild.png");
        assertThat(antwort.getHeaders().getContentType()).hasToString("application/octet-stream");
        assertThat(antwort.getBody()).isEqualTo("inhalt".getBytes());
    }

    @Test
    void mitHakenZeigtDerBrowserBildPdfUndText() {
        String[][] faelle = {
                {"bild.png", "image/png"}, {"Foto.JPG", "image/jpeg"}, {"a.jpeg", "image/jpeg"}, {"a.gif", "image/gif"},
                {"a.webp", "image/webp"}, {"bericht.pdf", "application/pdf"},
                {"notiz.txt", "text/plain;charset=UTF-8"}, {"lies.md", "text/plain;charset=UTF-8"},
                {"daten.json", "text/plain;charset=UTF-8"}, {"daten.xml", "text/plain;charset=UTF-8"}};
        for (String[] fall : faelle) {
            ResponseEntity<byte[]> antwort = oeffne(fall[0], true);

            assertThat(disposition(antwort)).as(fall[0]).startsWith("inline").contains(fall[0]);
            assertThat(antwort.getHeaders().getContentType()).as(fall[0]).hasToString(fall[1]);
            assertThat(antwort.getHeaders().getFirst("X-Content-Type-Options")).as(fall[0]).isEqualTo("nosniff");
            assertThat(antwort.getHeaders().getFirst(CSP)).as(fall[0]).isNull();
            assertThat(antwort.getBody()).isEqualTo("inhalt".getBytes());
        }
    }

    @Test
    void svgWirdGezeigtAberNichtAusgefuehrt() {
        ResponseEntity<byte[]> antwort = oeffne("logo.svg", true);

        assertThat(disposition(antwort)).startsWith("inline");
        assertThat(antwort.getHeaders().getContentType()).hasToString("image/svg+xml");
        assertThat(antwort.getHeaders().getFirst(CSP)).isEqualTo("sandbox");
    }

    @Test
    void wasEtwasAusfuehrenKoennteLaedtAuchMitHakenHerunter() {
        for (String datei : new String[]{"seite.html", "seite.HTM", "skript.js", "archiv.zip", "ohne-endung", "bild.png.exe"}) {
            ResponseEntity<byte[]> antwort = oeffne(datei, true);

            assertThat(disposition(antwort)).as(datei).startsWith("attachment");
            assertThat(antwort.getHeaders().getContentType()).as(datei).hasToString("application/octet-stream");
        }
    }

    @Test
    void anzeigeZaehltDenAufrufUndStirbtMitDerDatei() {
        service.hochladen("anna", "bild.png", "inhalt".getBytes());
        String token = service.linkErstellen("anna", "bild.png", 0, null, true).token();

        controller.herunterladen(token);
        assertThat(links.zeilen.get(0).aufrufe()).isEqualTo(1);

        service.loeschen("anna", "bild.png");
        assertThat(controller.herunterladen(token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.herunterladen("gibt-es-nicht").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
