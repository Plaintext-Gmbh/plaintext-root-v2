/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.lader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Das Modul-Manifest {@value #PFAD} (Karte 2377, ROOT-7; Konzept «Rollen von aussen lesen», Stufe B): welche Rollen,
 * Menü-Vorgaben und Property-Präfixe ein Modul-Jar mitbringt, lesbar ohne Start und ohne Spring. Geschrieben vom
 * Test {@code ModulManifestTest} (plaintext-root-testsupport), gelesen vom Lader ({@code --rollen}, Startlog) und von
 * der Ablage-App.
 *
 * <p>Nur JDK, darum eine feste Teilmenge von YAML, genau so, wie {@link #alsYaml()} sie schreibt:</p>
 * <pre>
 * format: 1
 * modul: files
 * rollen:
 *   - name: files
 *     authority: ROLE_FILES
 *     beschreibung: "Persönliche Dateiablage"
 *     art: ZUGANG
 *     ohneUmgehung: false
 * menue:
 *   - menuId: files
 *     parent: "Misc"
 *     order: 10
 *     title: "Files"
 *     link: "files/ablage.html"
 *     rollen: [FILES, ADMIN, ROOT]
 * properties:
 *   - plaintext.files
 * </pre>
 * Die Version steht nicht in der Datei (sie wäre nach jedem Release-Bump veraltet), sondern kommt beim Lesen aus dem
 * Jar ({@code META-INF/maven/…/pom.properties}). Unbekannte Schlüssel und Zeilen ausserhalb des Musters sind ein
 * Fehler.
 */
public final class ModulManifest {

    /** Ort im Jar. */
    public static final String PFAD = "META-INF/plaintext/modul.yaml";

    /** Formatstand; ein Leser lehnt einen anderen ab, statt ein neueres Manifest falsch zu lesen. */
    public static final String FORMAT = "1";

    private static final String KOPF = "# Erzeugt von ModulManifestTest (plaintext-root-testsupport), Karte 2377 ROOT-7.\n"
            + "# Nicht von Hand aendern: -Dplaintext.manifest.schreiben=true schreibt die Datei neu.\n";
    private static final Pattern WORT = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern ZAHL = Pattern.compile("-?[0-9]+");

    /** Eine Rolle, die das Modul deklariert. */
    public record Rolle(String name, String authority, String beschreibung, String art, boolean ohneUmgehung) {
    }

    /** Ein Menüeintrag mit der Vorgabe des Moduls (vor jeder Platzierung von aussen). */
    public record Menue(String menuId, String parent, int order, String title, String link, List<String> rollen) {
        public Menue {
            rollen = List.copyOf(rollen);
        }
    }

    private final String modul;
    private final String version;
    private final List<Rolle> rollen;
    private final List<Menue> menue;
    private final List<String> properties;

    public ModulManifest(String modul, String version, List<Rolle> rollen, List<Menue> menue, List<String> properties) {
        this.modul = pruefeWort("modul", modul);
        this.version = version;
        this.rollen = List.copyOf(rollen);
        this.menue = List.copyOf(menue);
        this.properties = List.copyOf(properties);
    }

    public String modul() {
        return modul;
    }

    /** @return die Version aus dem Jar, {@code null} beim Lesen aus Text */
    public String version() {
        return version;
    }

    public List<Rolle> rollen() {
        return rollen;
    }

    public List<Menue> menue() {
        return menue;
    }

    public List<String> properties() {
        return properties;
    }

    /** @return das Manifest als Text (ohne Version), stabil für den Vergleich im Test */
    public String alsYaml() {
        StringBuilder sb = new StringBuilder(KOPF).append("format: ").append(FORMAT).append('\n')
                .append("modul: ").append(modul).append('\n');
        sb.append("rollen:").append(rollen.isEmpty() ? " []\n" : "\n");
        for (Rolle r : rollen) {
            sb.append("  - name: ").append(pruefeWort("name", r.name())).append('\n')
                    .append("    authority: ").append(pruefeWort("authority", r.authority())).append('\n')
                    .append("    beschreibung: ").append(text(r.beschreibung())).append('\n')
                    .append("    art: ").append(pruefeWort("art", r.art())).append('\n')
                    .append("    ohneUmgehung: ").append(r.ohneUmgehung()).append('\n');
        }
        sb.append("menue:").append(menue.isEmpty() ? " []\n" : "\n");
        for (Menue m : menue) {
            sb.append("  - menuId: ").append(pruefeWort("menuId", m.menuId())).append('\n')
                    .append("    parent: ").append(text(m.parent())).append('\n')
                    .append("    order: ").append(m.order()).append('\n')
                    .append("    title: ").append(text(m.title())).append('\n')
                    .append("    link: ").append(text(m.link())).append('\n')
                    .append("    rollen: [");
            for (int i = 0; i < m.rollen().size(); i++) {
                sb.append(i == 0 ? "" : ", ").append(pruefeWort("rollen", m.rollen().get(i)));
            }
            sb.append("]\n");
        }
        sb.append("properties:").append(properties.isEmpty() ? " []\n" : "\n");
        for (String p : properties) {
            sb.append("  - ").append(pruefeWort("properties", p)).append('\n');
        }
        return sb.toString();
    }

    /**
     * @param yaml Text wie von {@link #alsYaml()}
     * @return das Manifest ohne Version
     * @throws IllegalArgumentException bei einer Zeile ausserhalb des Musters
     */
    public static ModulManifest aus(String yaml) {
        return new Leser(yaml).lesen(null);
    }

    /**
     * @param jar ein Modul-Jar
     * @return das Manifest mit der Version aus {@code pom.properties}, leer, wenn das Jar keins hat
     * @throws IllegalArgumentException wenn das Manifest kaputt ist
     */
    public static Optional<ModulManifest> lesen(Path jar) {
        try (JarFile j = new JarFile(jar.toFile())) {
            JarEntry e = j.getJarEntry(PFAD);
            if (e == null) {
                return Optional.empty();
            }
            String text;
            try (InputStream in = j.getInputStream(e)) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return Optional.of(new Leser(text).lesen(version(j)));
        } catch (IOException ex) {
            throw new UncheckedIOException("Manifest in " + jar + " nicht lesbar", ex);
        }
    }

    private static String version(JarFile j) throws IOException {
        var it = j.entries();
        while (it.hasMoreElements()) {
            JarEntry e = it.nextElement();
            if (e.getName().startsWith("META-INF/maven/") && e.getName().endsWith("/pom.properties")) {
                Properties p = new Properties();
                try (InputStream in = j.getInputStream(e)) {
                    p.load(in);
                }
                return p.getProperty("version");
            }
        }
        return null;
    }

    private static String pruefeWort(String feld, String wert) {
        if (wert == null || !WORT.matcher(wert).matches()) {
            throw new IllegalArgumentException("Manifest: " + feld + " '" + wert + "' ist kein einfacher Name");
        }
        return wert;
    }

    private static String text(String s) {
        String t = s == null ? "" : s;
        return '"' + t.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }

    /** Zeilenleser für genau das Muster von {@link #alsYaml()}. */
    private static final class Leser {

        private final List<String> zeilen = new ArrayList<>();
        private int i;

        Leser(String yaml) {
            for (String z : yaml.split("\n", -1)) {
                String s = z.stripTrailing();
                if (!s.isBlank() && !s.stripLeading().startsWith("#")) {
                    zeilen.add(s);
                }
            }
        }

        ModulManifest lesen(String version) {
            String format = wert(naechste(), "", "format");
            if (!FORMAT.equals(format)) {
                i--;
                throw fehler("Format " + format + " unbekannt, dieser Leser kennt " + FORMAT);
            }
            String modul = wert(naechste(), "", "modul");
            List<Rolle> rollen = new ArrayList<>();
            List<Menue> menue = new ArrayList<>();
            List<String> properties = new ArrayList<>();
            if (abschnitt("rollen")) {
                while (i < zeilen.size() && zeilen.get(i).startsWith("  - name: ")) {
                    String name = wert(naechste(), "  - ", "name");
                    String authority = wert(naechste(), "    ", "authority");
                    String beschreibung = zeichenkette(wert(naechste(), "    ", "beschreibung"));
                    String art = wert(naechste(), "    ", "art");
                    String ohne = wert(naechste(), "    ", "ohneUmgehung");
                    if (!ohne.equals("true") && !ohne.equals("false")) {
                        throw fehler("ohneUmgehung '" + ohne + "' ist weder true noch false");
                    }
                    rollen.add(new Rolle(pruefeWort("name", name), pruefeWort("authority", authority), beschreibung,
                            pruefeWort("art", art), Boolean.parseBoolean(ohne)));
                }
            }
            if (abschnitt("menue")) {
                while (i < zeilen.size() && zeilen.get(i).startsWith("  - menuId: ")) {
                    String menuId = pruefeWort("menuId", wert(naechste(), "  - ", "menuId"));
                    String parent = zeichenkette(wert(naechste(), "    ", "parent"));
                    String order = wert(naechste(), "    ", "order");
                    if (!ZAHL.matcher(order).matches()) {
                        throw fehler("order '" + order + "' ist keine Zahl");
                    }
                    String title = zeichenkette(wert(naechste(), "    ", "title"));
                    String link = zeichenkette(wert(naechste(), "    ", "link"));
                    menue.add(new Menue(menuId, parent, Integer.parseInt(order), title, link,
                            liste(wert(naechste(), "    ", "rollen"))));
                }
            }
            if (abschnitt("properties")) {
                while (i < zeilen.size() && zeilen.get(i).startsWith("  - ")) {
                    properties.add(pruefeWort("properties", naechste().substring(4)));
                }
            }
            if (i < zeilen.size()) {
                throw fehler("unerwartete Zeile");
            }
            return new ModulManifest(modul, version, rollen, menue, properties);
        }

        /** @return true, wenn Einträge folgen; false bei {@code name: []} */
        private boolean abschnitt(String name) {
            String z = naechste();
            if (z.equals(name + ": []")) {
                return false;
            }
            if (!z.equals(name + ":")) {
                throw fehler("erwartet '" + name + ":'");
            }
            return true;
        }

        private String naechste() {
            if (i >= zeilen.size()) {
                throw new IllegalArgumentException("Manifest: Ende der Datei zu früh");
            }
            return zeilen.get(i++);
        }

        private String wert(String zeile, String einzug, String schluessel) {
            String vorne = einzug + schluessel + ": ";
            if (!zeile.startsWith(vorne)) {
                i--;
                throw fehler("erwartet '" + vorne.strip() + "'");
            }
            return zeile.substring(vorne.length());
        }

        private IllegalArgumentException fehler(String was) {
            return new IllegalArgumentException("Manifest Zeile '" + (i < zeilen.size() ? zeilen.get(i) : "") + "': "
                    + was);
        }

        private String zeichenkette(String roh) {
            if (roh.length() < 2 || roh.charAt(0) != '"' || roh.charAt(roh.length() - 1) != '"') {
                throw fehler("Text in \"…\" erwartet");
            }
            StringBuilder sb = new StringBuilder();
            for (int k = 1; k < roh.length() - 1; k++) {
                char c = roh.charAt(k);
                if (c == '\\' && k + 1 < roh.length() - 1) {
                    char n = roh.charAt(++k);
                    sb.append(n == 'n' ? '\n' : n);
                } else if (c == '"') {
                    throw fehler("ungeschütztes \" im Text");
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private List<String> liste(String roh) {
            if (!roh.startsWith("[") || !roh.endsWith("]")) {
                throw fehler("Liste in [...] erwartet");
            }
            String innen = roh.substring(1, roh.length() - 1).strip();
            List<String> ret = new ArrayList<>();
            if (!innen.isEmpty()) {
                for (String s : innen.split(",")) {
                    ret.add(pruefeWort("rollen", s.strip()));
                }
            }
            return ret;
        }
    }
}
