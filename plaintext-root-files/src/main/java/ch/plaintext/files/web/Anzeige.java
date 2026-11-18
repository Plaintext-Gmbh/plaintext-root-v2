/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files.web;

import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Wie ein Freigabe-Link eine Datei im Browser zeigt (Karte 2354): Bilder, PDF und Text - nach der Endung, als
 * feste Liste.
 *
 * <p><b>Warum eine Liste und nicht «alles inline».</b> Der Link liegt unter derselben Adresse wie die Anwendung.
 * Eine HTML-Datei, die dort als Seite ausgeliefert wird, läuft mit den Rechten dessen, der den Link öffnet - mit
 * seiner Sitzung, wenn er angemeldet ist. Hochladen kann jeder mit der Rolle {@code files}; «im Browser anzeigen»
 * wäre damit ein Weg, einem Admin ein Skript unterzuschieben. Darum zeigt der Link nur, was im Browser nichts
 * ausführt, und lädt alles andere herunter wie ein Link ohne den Haken.</p>
 *
 * <p><b>Text ist immer {@code text/plain}</b> - auch JSON, XML, Markdown und CSV. Der Browser zeigt sie als Text;
 * ein eigener Typ brächte nichts ausser der Frage, was er damit sonst noch tut (XML mit Stylesheet). Dazu geht
 * {@code X-Content-Type-Options: nosniff} mit jeder Anzeige hinaus, damit kein Browser aus einer Textdatei doch
 * noch HTML errät.</p>
 *
 * <p><b>SVG</b> ist ein Bild, kann aber Skript enthalten. Es geht mit {@code Content-Security-Policy: sandbox}
 * hinaus: gezeigt wird es, ausgeführt wird nichts, und der Ursprung ist ein eigener. PDF bekommt die Sandbox
 * nicht - der eingebaute PDF-Betrachter von Chrome lädt darin nicht, und ein PDF führt im Ursprung der Seite
 * nichts aus.</p>
 *
 * @param typ     Content-Type der Antwort
 * @param sandbox {@code true} = mit {@code Content-Security-Policy: sandbox} ausliefern
 */
record Anzeige(MediaType typ, boolean sandbox) {

    private static final MediaType TEXT = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

    private static final Map<String, Anzeige> NACH_ENDUNG = Map.ofEntries(
            Map.entry("png", new Anzeige(MediaType.IMAGE_PNG, false)),
            Map.entry("jpg", new Anzeige(MediaType.IMAGE_JPEG, false)),
            Map.entry("jpeg", new Anzeige(MediaType.IMAGE_JPEG, false)),
            Map.entry("gif", new Anzeige(MediaType.IMAGE_GIF, false)),
            Map.entry("webp", new Anzeige(MediaType.parseMediaType("image/webp"), false)),
            Map.entry("bmp", new Anzeige(MediaType.parseMediaType("image/bmp"), false)),
            Map.entry("svg", new Anzeige(MediaType.parseMediaType("image/svg+xml"), true)),
            Map.entry("pdf", new Anzeige(MediaType.APPLICATION_PDF, false)),
            Map.entry("txt", new Anzeige(TEXT, false)),
            Map.entry("log", new Anzeige(TEXT, false)),
            Map.entry("md", new Anzeige(TEXT, false)),
            Map.entry("csv", new Anzeige(TEXT, false)),
            Map.entry("json", new Anzeige(TEXT, false)),
            Map.entry("xml", new Anzeige(TEXT, false)),
            Map.entry("yaml", new Anzeige(TEXT, false)),
            Map.entry("yml", new Anzeige(TEXT, false)));

    /** @return wie die Datei gezeigt wird; leer = nicht zeigbar, herunterladen */
    static Optional<Anzeige> fuer(String dateiname) {
        int punkt = dateiname == null ? -1 : dateiname.lastIndexOf('.');
        if (punkt < 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(NACH_ENDUNG.get(dateiname.substring(punkt + 1).toLowerCase(Locale.ROOT)));
    }
}
