/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files.web;

import ch.plaintext.files.service.Datei;
import ch.plaintext.files.service.FilesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Ablage-Schnittstelle (Karte 1559, aus dem Inventar {@code FilesApiController}) im Format der Draw.io-Ablage
 * ({@code ablage.js}): GET {@code dateien}, POST {@code loeschen}, {@code aktualisieren}, dazu Hochladen und
 * Herunterladen. Jeder sieht und ändert nur seine eigene Ablage; Ordner gibt es keine. POST braucht das CSRF-Token
 * wie jedes Formular.
 *
 * <p><b>Admin und Root (Karte 2354)</b> sehen zusätzlich die Ablagen aller: {@code dateien?alle=true} listet sie
 * mit {@code besitzer}, und {@code datei} wie {@code loeschen} nehmen diesen Besitzer als {@code benutzer}. Wer
 * weder Admin noch Root ist und danach fragt, bekommt 403 - keine leere Liste und nicht die eigene Datei gleichen
 * Namens. Hochgeladen wird immer in die eigene Ablage.</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class FilesApiController {

    static final String BASIS = "/files/api";
    private static final String FEHLER = "fehler";
    private static final String BENUTZER = "benutzer";
    private static final DateTimeFormatter TAG = DateTimeFormatter.ISO_LOCAL_DATE.withZone(FilesService.ZONE);
    private static final DateTimeFormatter AB = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(FilesService.ZONE);

    private final FilesService filesService;
    private final FilesZugriff zugriff;

    @GetMapping(value = BASIS + "/dateien", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> dateien(@RequestParam(value = "alle", required = false) boolean alle) {
        if (alle && !zugriff.siehtAlle()) {
            return keinZugriff();
        }
        return mitZugriff(benutzer -> {
            List<Map<String, Object>> dateien = new ArrayList<>();
            for (Datei d : alle ? filesService.alle() : filesService.liste(benutzer)) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("pfad", d.name());
                if (alle) {
                    e.put("besitzer", d.besitzer());
                }
                e.put("geaendert", TAG.format(d.geaendert()));
                e.put("untertitel", groesse(d.groesse()) + " · läuft ab " + AB.format(d.ablauf()));
                dateien.add(e);
            }
            Map<String, Object> antwort = new LinkedHashMap<>();
            antwort.put("ordner", List.of());
            antwort.put("dateien", dateien);
            antwort.put("gesperrt", List.of());
            antwort.put("darfBearbeiten", true);
            return antwort;
        });
    }

    @PostMapping(value = BASIS + "/aktualisieren", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> aktualisieren() {
        return mitZugriff(benutzer -> Map.of("ok", true));
    }

    @PostMapping(value = BASIS + "/hochladen", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> hochladen(@RequestParam("datei") MultipartFile datei) {
        return mitZugriff(benutzer -> {
            try {
                return Map.of("ok", true, "name", filesService.hochladen(benutzer, datei.getOriginalFilename(), datei.getBytes()));
            } catch (IOException e) {
                throw new IllegalStateException("Upload nicht lesbar: " + e.getMessage(), e);
            }
        });
    }

    @PostMapping(value = BASIS + "/loeschen", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> loeschen(@RequestBody Map<String, String> auftrag) {
        String ordner = zugriff.ordner(auftrag.get(BENUTZER));
        if (ordner == null) {
            return keinZugriff();
        }
        return mitZugriff(benutzer -> {
            fremd("löscht", benutzer, ordner, auftrag.get("pfad"));
            filesService.loeschen(ordner, auftrag.get("pfad"));
            return Map.of("ok", true);
        });
    }

    @PostMapping(value = {BASIS + "/ordner", BASIS + "/verschieben"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> nichtUnterstuetzt() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(FEHLER, "Ordner und Umbenennen gibt es hier nicht - neu hochladen ersetzt eine Datei"));
    }

    @GetMapping(BASIS + "/datei")
    public ResponseEntity<byte[]> datei(@RequestParam("pfad") String pfad,
                                        @RequestParam(value = BENUTZER, required = false) String besitzer) {
        String ordner = zugriff.ordner(besitzer);
        if (ordner == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        byte[] inhalt;
        try {
            fremd("liest", zugriff.benutzer(), ordner, pfad);
            inhalt = filesService.herunterladen(ordner, pfad);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        if (inhalt == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pfad, StandardCharsets.UTF_8).build().toString())
                .body(inhalt);
    }

    private interface Aktion {
        Map<String, Object> ausfuehren(String benutzer);
    }

    private static ResponseEntity<Map<String, Object>> keinZugriff() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(FEHLER, "Kein Zugriff"));
    }

    /** Wer in eine fremde Ablage greift, steht im Log - der Besitzer sieht es sonst nirgends (Karte 2354). */
    private void fremd(String was, String benutzer, String ordner, String datei) {
        if (!zugriff.eigener(ordner)) {
            log.info("[FilesApi] {} fremde Datei | wer={} | besitzer={} | datei={}", was, benutzer, ordner, datei);
        }
    }

    private ResponseEntity<Map<String, Object>> mitZugriff(Aktion aktion) {
        String benutzer = zugriff.benutzer();
        if (benutzer == null) {
            return keinZugriff();
        }
        try {
            return ResponseEntity.ok(aktion.ausfuehren(benutzer));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(FEHLER, e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("[FilesApi] fehlgeschlagen | {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(FEHLER, String.valueOf(e.getMessage())));
        }
    }

    static String groesse(long b) {
        if (b < 1024) {
            return b + " B";
        }
        return b < 1024 * 1024 ? (b / 1024) + " KB" : String.format(java.util.Locale.ROOT, "%.1f MB", b / (1024.0 * 1024));
    }
}
