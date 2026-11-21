/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.modules.laufzeit;

import ch.plaintext.lader.ModulAuswahl;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Karte 2408 Teil 2: Quellen aus der Oberflaeche. Das JSON {@code plaintext-modul-quelle/1} kommt von modules
 * (Seite «Meine Zugaenge», Karte 2409); {@code quellen-ui.txt} (neben {@code auswahl.yaml}) liest der Lader, sie
 * enthaelt nie den Zugang. Der liegt im Secret-Store unter {@code modul-quelle.<name>}.
 */
public final class UiQuellen {

    static final String FORMAT = "plaintext-modul-quelle/1";
    static final String SECRET_PRAEFIX = "modul-quelle.";
    /** Wie {@code ModulKonfig.NAME} im Lader: der Name wird Verzeichnisname unter {@code vorbereitet/}. */
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]*(\\.[a-z0-9-]+)*");

    /** Eine Quelle ohne Zugang (so steht sie in quellen-ui.txt). {@code schluessel} = Base64 (X.509, Ed25519). */
    public record Quelle(String name, String index, String schluessel) implements Serializable {
        ModulAuswahl.Registry registry() {
            return new ModulAuswahl.Registry(name, URI.create(index));
        }
    }

    /** Das JSON von modules; unbekannte Felder sind ein Fehler (jede Erweiterung hebt die Formatversion). */
    record Eingabe(String format, String name, String basis, String index, String schluessel, String benutzer,
                   String passwort) {
        @Override
        public String toString() { // nie das Passwort, auch nicht in einer Ausnahme oder im Log
            return "Eingabe[" + name + ", " + index + ", " + benutzer + "]";
        }
    }

    private UiQuellen() {
    }

    /** Prueft das JSON vollstaendig; liefert Quelle und {@code benutzer:passwort}. */
    static Eingabe lesen(String json) {
        Eingabe e;
        try {
            e = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()
                    .readValue(json == null ? "" : json.strip(), Eingabe.class);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("JSON unlesbar oder mit unbekanntem Feld (erwartet " + FORMAT
                    + ": format, name, basis, index, schluessel, benutzer, passwort)");
        }
        if (e == null || !FORMAT.equals(e.format())) {
            throw new IllegalArgumentException("Format '" + (e == null ? null : e.format()) + "' nicht unterstuetzt, erwartet " + FORMAT);
        }
        if (e.name() == null || !NAME.matcher(e.name()).matches()) {
            throw new IllegalArgumentException("name: nur a-z, 0-9, '-' und '.' (z. B. kunde)");
        }
        URI index = e.index() == null ? null : URI.create(e.index());
        if (index == null || !"https".equals(index.getScheme()) || index.getHost() == null) {
            throw new IllegalArgumentException("index: nur eine https-Adresse");
        }
        if (e.benutzer() == null || e.benutzer().isBlank() || e.benutzer().contains(":") || e.passwort() == null
                || e.passwort().isBlank()) {
            throw new IllegalArgumentException("benutzer und passwort fehlen (benutzer ohne ':')");
        }
        schluessel(schluesselBase64(e.schluessel()));
        return e;
    }

    /** «ed25519 MCow…» oder nur das Base64. */
    static String schluesselBase64(String s) {
        String t = s == null ? "" : s.strip();
        return t.startsWith("ed25519 ") ? t.substring(8).strip() : t;
    }

    static PublicKey schluessel(String base64) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (Exception ex) {
            throw new IllegalArgumentException("schluessel: kein Ed25519-Schluessel (erwartet 'ed25519 <Base64>')");
        }
    }

    /** quellen-ui.txt: je Quelle ein Kommentar, die Schluessel- und die Registry-Zeile (so schreibt es {@link #text}). */
    static List<Quelle> lesen(Path datei) throws IOException {
        List<Quelle> out = new ArrayList<>();
        if (!Files.isRegularFile(datei)) {
            return out;
        }
        String schluessel = null;
        for (String roh : Files.readAllLines(datei, StandardCharsets.UTF_8)) {
            String z = roh.strip();
            String[] t = z.split("\\s+");
            if (z.isEmpty() || z.startsWith("#")) {
                continue;
            }
            if (t.length == 3 && t[0].equals("schluessel") && t[1].equals("ed25519")) {
                schluessel = t[2];
            } else if (t.length == 3 && t[0].equals("registry") && schluessel != null) {
                out.add(new Quelle(t[1], t[2], schluessel));
                schluessel = null;
            } else {
                throw new IllegalArgumentException(datei + ": unerwartete Zeile '" + z + "' (die Datei schreibt die Seite)");
            }
        }
        return out;
    }

    static String text(List<Quelle> quellen, String wer) {
        StringBuilder sb = new StringBuilder("# Karte 2408: Quellen aus der Seite Laufzeit-Module (").append(wer)
                .append("). Kein Zugang hier: der liegt im Secret-Store (modul-quelle.<name>).\n");
        for (Quelle q : quellen) {
            sb.append("# quelle ").append(q.name()).append('\n')
                    .append("schluessel ed25519 ").append(q.schluessel()).append('\n')
                    .append("registry ").append(q.name()).append(' ').append(q.index()).append('\n');
        }
        return sb.toString();
    }
}
