/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files;

import ch.plaintext.files.config.FilesProperties;
import ch.plaintext.files.service.FilesService;
import ch.plaintext.files.speicher.DateisystemDateiSpeicher;
import ch.plaintext.files.web.FilesApiController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Karte 2354 an der REST-Schnittstelle: Benutzer A sieht nur die Dateien von A, Benutzer B nur die von B - auch
 * wenn B den Ordner von A ausdrücklich nennt. Admin und Root sehen alle. Echter Service auf einem Temp-Verzeichnis,
 * damit geprüft ist, was auf der Platte liegt, und nicht, was ein Mock zurückgibt.
 */
class FilesApiControllerTest {

    @TempDir
    Path wurzel;
    private FilesApiController api;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        FilesService service = new FilesService(new DateisystemDateiSpeicher(wurzel), new SpeicherLinkTokenStore(),
                new FilesProperties(), mock(ObjectProvider.class));
        api = new FilesApiController(service, Anmeldung.zugriffOhneSeitenschutz());

        Anmeldung.als("anna", "ROLE_FILES");
        api.hochladen(new MockMultipartFile("datei", "geheim.txt", "text/plain", "von anna".getBytes()));
        Anmeldung.als("bert", "ROLE_FILES");
        api.hochladen(new MockMultipartFile("datei", "notiz.txt", "text/plain", "von bert".getBytes()));
    }

    @AfterEach
    void aufraeumen() {
        Anmeldung.ab();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> dateien(ResponseEntity<Map<String, Object>> antwort) {
        return (List<Map<String, Object>>) antwort.getBody().get("dateien");
    }

    @Test
    void jederSiehtNurSeineDateien() {
        Anmeldung.als("anna", "ROLE_FILES");
        assertThat(dateien(api.dateien(false))).extracting(d -> d.get("pfad")).containsExactly("geheim.txt");
        assertThat(dateien(api.dateien(false)).get(0)).doesNotContainKey("besitzer");

        Anmeldung.als("bert", "ROLE_FILES");
        assertThat(dateien(api.dateien(false))).extracting(d -> d.get("pfad")).containsExactly("notiz.txt");
    }

    @Test
    void fremderOrdnerIstFuerBenutzerZu() {
        Anmeldung.als("bert", "ROLE_FILES");

        assertThat(api.datei("geheim.txt", "anna").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.datei("geheim.txt", null).getStatusCode()).as("im eigenen Ordner gibt es sie nicht")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api.datei("../anna/geheim.txt", null).getStatusCode()).as("kein Umweg über den Dateinamen")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api.dateien(true).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.loeschen(Map.of("pfad", "geheim.txt", "benutzer", "anna")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(wurzel.resolve("files/anna/geheim.txt")).as("nichts gelöscht").exists();

        assertThat(api.datei("notiz.txt", "bert").getBody()).as("der eigene Ordner, ausdrücklich genannt")
                .isEqualTo("von bert".getBytes());
    }

    @Test
    void adminUndRootSehenAlle() {
        for (String rolle : new String[]{"ROLE_ADMIN", "ROLE_ROOT"}) {
            Anmeldung.als("chef", rolle);

            assertThat(dateien(api.dateien(true))).as(rolle)
                    .extracting(d -> d.get("besitzer") + "/" + d.get("pfad"))
                    .containsExactlyInAnyOrder("anna/geheim.txt", "bert/notiz.txt");
            assertThat(dateien(api.dateien(false))).as("ohne alle=true die eigene, leere Ablage").isEmpty();
            ResponseEntity<byte[]> datei = api.datei("geheim.txt", "anna");
            assertThat(datei.getBody()).isEqualTo("von anna".getBytes());
            assertThat(datei.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment");
        }

        assertThat(api.loeschen(Map.of("pfad", "geheim.txt", "benutzer", "anna")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wurzel.resolve("files/anna/geheim.txt")).doesNotExist();
        assertThat(wurzel.resolve("files/bert/notiz.txt")).exists();
    }

    @Test
    void ohneRolleFilesGehtNichts() {
        Anmeldung.als("carl", "ROLE_USER");

        assertThat(api.dateien(false).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.datei("geheim.txt", "anna").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.aktualisieren().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.hochladen(new MockMultipartFile("datei", "x.txt", "text/plain", new byte[]{1})).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.loeschen(Map.of("pfad", "x.txt")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(wurzel.resolve("files/carl")).doesNotExist();

        Anmeldung.ab();
        assertThat(api.dateien(false).getStatusCode()).as("nicht angemeldet").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void eigeneDateiLoeschenUndWasEsNichtGibt() {
        Anmeldung.als("anna", "ROLE_FILES");

        assertThat(api.loeschen(Map.of("pfad", "geheim.txt")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dateien(api.dateien(false))).isEmpty();
        assertThat(api.loeschen(Map.of("pfad", "..")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.nichtUnterstuetzt().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
