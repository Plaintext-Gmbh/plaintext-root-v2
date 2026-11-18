/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files.web;

import ch.plaintext.files.service.FilesService;
import ch.plaintext.linktoken.LinkToken;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Download per Freigabe-Link - {@code /nosec/files/{token}}, ohne Anmeldung (Karte 1559, Adresse wie im Inventar,
 * damit bestehende Links weiter gehen). Geschützt allein durch das Token.
 *
 * <p><b>Anzeige statt Download (Karte 2354).</b> Ein Link, der mit «Direkt im Browser anzeigen» angelegt wurde,
 * liefert {@code Content-Disposition: inline} und den Typ der Datei - für das, was {@link Anzeige} kennt (Bilder,
 * PDF, Text). Alles andere lädt auch ein solcher Link herunter; warum, steht dort.</p>
 */
@RestController
@RequiredArgsConstructor
public class FilesLinkController {

    private final FilesService filesService;

    @GetMapping(FilesService.PFAD + "{token}")
    public ResponseEntity<byte[]> herunterladen(@PathVariable String token) {
        Optional<LinkToken> link = filesService.finde(token);
        byte[] inhalt = link.map(l -> filesService.inhalt(l, token)).orElse(null);
        if (inhalt == null) {
            return ResponseEntity.status(404).cacheControl(CacheControl.noStore())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Datei nicht gefunden - der Link ist widerrufen oder die Datei abgelaufen."
                            .getBytes(StandardCharsets.UTF_8));
        }
        String name = FilesService.datei(link.get());
        Optional<Anzeige> anzeige = FilesService.imBrowser(link.get()) ? Anzeige.fuer(name) : Optional.empty();
        ResponseEntity.BodyBuilder antwort = ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff");
        if (anzeige.isEmpty()) {
            return antwort.contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(name, StandardCharsets.UTF_8).build().toString())
                    .body(inhalt);
        }
        if (anzeige.get().sandbox()) {
            antwort.header("Content-Security-Policy", "sandbox");
        }
        return antwort.contentType(anzeige.get().typ())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(name, StandardCharsets.UTF_8).build().toString())
                .body(inhalt);
    }
}
