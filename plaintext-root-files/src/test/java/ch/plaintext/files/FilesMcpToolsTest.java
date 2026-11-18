/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files;

import ch.plaintext.files.config.FilesProperties;
import ch.plaintext.files.mcp.FilesMcpTools;
import ch.plaintext.files.service.Datei;
import ch.plaintext.files.service.FilesService;
import ch.plaintext.linktoken.LinkRecht;
import ch.plaintext.linktoken.LinkToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Karte 1559, portiert aus dem Inventar ({@code FilesMcpControllerTest}): mit eingestellter Identität kommt nur
 * dieser Name durch - jede andere oder keine Antwort auf «Wer bist du?» wird abgewiesen. Ohne Einstellung fragt
 * niemand.
 */
class FilesMcpToolsTest {

    private final FilesService service = mock(FilesService.class);
    private final FilesProperties properties = new FilesProperties();
    private final FilesMcpTools tools = new FilesMcpTools(service, properties, Anmeldung.zugriffOhneSeitenschutz());

    @AfterEach
    void aufraeumen() {
        SecurityContextHolder.clearContext();
    }

    private static void anmelden() {
        // Der MCP-Filter lädt die echten Rollen des Token-Inhabers in den Kontext (Karte 2354: ohne files kein Zugriff).
        Anmeldung.als("anna.muster@example.ch", "SCOPE_READ", "SCOPE_WRITE", "ROLE_USER", "ROLE_FILES");
    }

    @Test
    void ohneRolleFilesOeffnetAuchDasTokenNichts() {
        Anmeldung.als("carl", "SCOPE_READ", "SCOPE_WRITE", "ROLE_USER");

        assertThat(tools.listFiles(null)).startsWith("FEHLER: ").contains("Rolle 'files'");
        assertThat(tools.getFile(null, "a.txt")).startsWith("FEHLER: ");
        assertThat(tools.saveFile(null, "x.txt", "inhalt", false)).startsWith("FEHLER: ");
        assertThat(tools.createFileLink(null, "a.txt", true)).startsWith("FEHLER: ");
        verifyNoInteractions(service);
    }

    @Test
    void linkZumAnzeigenVerwendetKeinenDownloadLinkWieder() {
        anmelden();
        String ich = "anna.muster@example.ch";
        LinkToken download = link("1", "files", "t-download");
        LinkToken anzeige = link("2", "files-inline", "t-anzeige");
        when(service.liste(ich)).thenReturn(List.of(new Datei("anna.muster_example.ch", "bild.png", 1, Instant.now(), Instant.now())));
        when(service.links(ich, "bild.png")).thenReturn(List.of(download));
        when(service.linkErstellen(ich, "bild.png", 0, null, true)).thenReturn(anzeige);
        when(service.url(any())).thenAnswer(i -> "https://app.example.ch/nosec/files/" + i.getArgument(0));

        assertThat(tools.createFileLink(null, "bild.png", true)).contains("/nosec/files/t-anzeige").contains("im Browser");
        assertThat(tools.createFileLink(null, "bild.png", null)).as("ohne Angabe: Download wie bisher")
                .contains("/nosec/files/t-download").contains("Lädt ohne Anmeldung herunter");
        verify(service, never()).linkErstellen(ich, "bild.png", 0, null, false);
        assertThat(tools.createFileLink(null, "fehlt.txt", false)).startsWith("FEHLER: ");
    }

    private static LinkToken link(String id, String art, String token) {
        return new LinkToken(id, token, art, "anna/bild.png", LinkRecht.LESEN, "anna", null, "", Instant.now(), null, 0, null);
    }

    @Test
    void nurDieEingestellteIdentitaetKommtDurch() {
        properties.setMcpIdentitaet("Anna Muster");
        anmelden();
        when(service.liste("anna.muster@example.ch")).thenReturn(List.of());

        assertThat(tools.listFiles("  anna   MUSTER ")).isEqualTo("Keine Dateien.");
        for (String wer : new String[]{null, "", "Hans Muster", "Anna", "ich bin Anna Muster"}) {
            assertThat(tools.listFiles(wer)).as(String.valueOf(wer)).startsWith("FEHLER: ").contains("Wer bist du?");
        }
        tools.saveFile("Hans Muster", "x.txt", "inhalt", false);
        verify(service, never()).hochladen(any(), any(), any());
    }

    @Test
    void ohneEinstellungFragtNiemand() {
        anmelden();
        when(service.liste("anna.muster@example.ch")).thenReturn(List.of());
        assertThat(tools.listFiles(null)).isEqualTo("Keine Dateien.");
    }

    @Test
    void ablegenAlsTextUndBase64() {
        anmelden();
        when(service.hochladen(any(), any(), any())).thenAnswer(i -> i.getArgument(1));
        when(service.frist()).thenReturn(java.time.Duration.ofDays(21));

        assertThat(tools.saveFile(null, "a.txt", "Grüezi", false)).startsWith("OK: a.txt");
        verify(service).hochladen(eq("anna.muster@example.ch"), eq("a.txt"), eq("Grüezi".getBytes(StandardCharsets.UTF_8)));
        assertThat(tools.saveFile(null, "b.bin", "AAEC", true)).contains("(3 Bytes)");
    }

    @Test
    void textOderBase64BeimLesen() {
        anmelden();
        when(service.herunterladen("anna.muster@example.ch", "a.txt")).thenReturn("Grüezi".getBytes(StandardCharsets.UTF_8));
        when(service.herunterladen("anna.muster@example.ch", "b.bin")).thenReturn(new byte[]{0, 1, 2});
        assertThat(tools.getFile(null, "a.txt")).isEqualTo("Grüezi");
        assertThat(tools.getFile(null, "b.bin")).isEqualTo("BASE64:AAEC");
        assertThat(tools.getFile(null, "fehlt")).startsWith("FEHLER: ");
    }
}
