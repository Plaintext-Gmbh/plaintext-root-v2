/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.lader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Inhaltspruefung der Jars vor dem Start (Karte 1514, Konzept 1503 Abschnitt 4).
 *
 * <p>Jars aus {@code loader.path} stehen im Klassenpfad VOR der Huelle. Darum:
 * <ul>
 *   <li>Kein Jar darf eine Spring-Konfigurationsdatei ({@code application*.yml/yaml/properties},
 *       {@code bootstrap*}) mitbringen: sie verdraengt die der Huelle ganz (Schatten-Probe 1503).</li>
 *   <li>Ein Modul (nicht: Bibliothek) darf sich nicht per {@code META-INF/spring/*.imports} oder
 *       {@code META-INF/spring.factories} in den Spring-Start einhaengen; Module werden per Component-Scan
 *       gefunden. Fremd-Bibliotheken (z.B. Starter) duerfen das, ebenso die Framework-Module von root selbst
 *       ({@code plaintext-root-*}, {@code plaintext-admin-*}, Karte 2514): sie registrieren sich fuer Anwendungen,
 *       die {@code ch.plaintext} nicht scannen, und wirken nachgeladen genau wie in der Huelle.</li>
 *   <li>Keine Klasse darf in zwei geladenen Jars oder in einem geladenen Jar und in der Huelle
 *       ({@code BOOT-INF/classes}, {@code BOOT-INF/lib}) liegen: sonst gewinnt still das Modul.</li>
 * </ul>
 */
final class ModulPruefung {

    private static final Pattern KONFIG = Pattern.compile(
            "(config/)?(application|bootstrap)(-[^/]*)?\\.(yml|yaml|properties)");
    private static final Pattern SPRING_EINHAENGEN = Pattern.compile(
            "META-INF/spring/[^/]+\\.imports|META-INF/spring\\.factories");
    private static final Pattern RAHMEN = Pattern.compile("plaintext-(root|admin)-[a-z0-9]+(-[a-z0-9]+)*");
    private static final int MAX_MELDUNGEN = 5;
    /** Obergrenze je Archiv; die groessten Jars der Huelle haben einige zehntausend Eintraege. */
    static final int MAX_EINTRAEGE = 200_000;

    private ModulPruefung() {
    }

    static void pruefen(List<ModulKonfig.Eintrag> soll, List<Path> jars, Path huelle) throws IOException {
        List<String> fehler = new ArrayList<>();
        Map<String, String> klasseZuJar = new HashMap<>();
        for (int i = 0; i < soll.size(); i++) {
            jarPruefen(soll.get(i), jars.get(i), klasseZuJar, fehler);
        }
        if (fehler.isEmpty()) {
            if (huelle == null) {
                throw new IllegalStateException("Exec-Jar der Huelle unbekannt: Klassen-Duplikate nicht pruefbar,"
                        + " keine Module geladen");
            }
            huelleVergleichen(huelle, klasseZuJar, fehler);
        }
        if (!fehler.isEmpty()) {
            int mehr = fehler.size() - MAX_MELDUNGEN;
            throw new IllegalStateException("Modul-Pruefung: " + String.join("; ",
                    fehler.subList(0, Math.min(MAX_MELDUNGEN, fehler.size())))
                    + (mehr > 0 ? "; und " + mehr + " weitere" : ""));
        }
    }

    /** Liest nur das Inhaltsverzeichnis des Jars (nichts wird entpackt), hoechstens {@link #MAX_EINTRAEGE} Namen. */
    private static void jarPruefen(ModulKonfig.Eintrag e, Path jar, Map<String, String> klasseZuJar,
                                   List<String> fehler) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            mengePruefen(zip.size(), e.dateiname());
            Enumeration<? extends ZipEntry> eintraege = zip.entries();
            while (eintraege.hasMoreElements()) {
                eintragPruefen(e, eintraege.nextElement().getName(), klasseZuJar, fehler);
            }
        }
    }

    private static void eintragPruefen(ModulKonfig.Eintrag e, String n, Map<String, String> klasseZuJar,
                                       List<String> fehler) {
        if (KONFIG.matcher(n).matches()) {
            fehler.add(e.dateiname() + " enthaelt " + n + " (wuerde die Konfig der Huelle verdraengen)");
        } else if (!e.bibliothek() && !RAHMEN.matcher(e.name()).matches() && SPRING_EINHAENGEN.matcher(n).matches()) {
            fehler.add(e.dateiname() + " enthaelt " + n + " (Module haengen sich nicht in den Spring-Start)");
        } else if (istKlasse(n)) {
            String vorher = klasseZuJar.putIfAbsent(n, e.dateiname());
            if (vorher != null) {
                fehler.add("Klasse " + klassenname(n) + " doppelt: " + vorher + " und " + e.dateiname());
            }
        }
    }

    /** Karte 2483 (Sonar S5042): ein Archiv mit unplausibel vielen Eintraegen ist kein Jar, sondern ein Angriff. */
    static void mengePruefen(int anzahl, String archiv) {
        if (anzahl > MAX_EINTRAEGE) {
            throw new IllegalStateException("Modul-Pruefung: " + archiv + " hat mehr als " + MAX_EINTRAEGE
                    + " Eintraege, keine Module geladen");
        }
    }

    private static void huelleVergleichen(Path huelle, Map<String, String> klasseZuJar, List<String> fehler)
            throws IOException {
        Set<String> gesehen = new HashSet<>();
        try (ZipFile zip = new ZipFile(huelle.toFile())) {
            mengePruefen(zip.size(), huelle.getFileName().toString());
            Enumeration<? extends ZipEntry> eintraege = zip.entries();
            while (eintraege.hasMoreElements()) {
                ZipEntry z = eintraege.nextElement();
                String n = z.getName();
                if (n.startsWith("BOOT-INF/classes/")) {
                    treffer(n.substring("BOOT-INF/classes/".length()), "BOOT-INF/classes", klasseZuJar, gesehen, fehler);
                } else if (n.startsWith("BOOT-INF/lib/") && n.endsWith(".jar")) {
                    innenVergleichen(zip, z, klasseZuJar, gesehen, fehler);
                }
            }
        }
    }

    /** Ein Jar in {@code BOOT-INF/lib}: nur die Namen, hoechstens {@link #MAX_EINTRAEGE}; Inhalte werden ueberlesen, nie gespeichert. */
    private static void innenVergleichen(ZipFile zip, ZipEntry z, Map<String, String> klasseZuJar, Set<String> gesehen,
                                         List<String> fehler) throws IOException {
        String lib = z.getName().substring("BOOT-INF/lib/".length());
        int anzahl = 0;
        try (InputStream rein = zip.getInputStream(z); ZipInputStream inner = new ZipInputStream(rein)) {
            ZipEntry k;
            while ((k = inner.getNextEntry()) != null) {
                mengePruefen(++anzahl, lib);
                treffer(k.getName(), lib, klasseZuJar, gesehen, fehler);
            }
        }
    }

    private static void treffer(String n, String ort, Map<String, String> klasseZuJar, Set<String> gesehen,
                                List<String> fehler) {
        String jar = klasseZuJar.get(n);
        if (jar != null && istKlasse(n) && gesehen.add(n)) {
            fehler.add("Klasse " + klassenname(n) + " aus " + jar + " gibt es schon in der Huelle (" + ort + ")");
        }
    }

    /** Klassen ohne die harmlosen Doppelgaenger (package-info, module-info, Multi-Release-Varianten). */
    private static boolean istKlasse(String n) {
        return n.endsWith(".class") && !n.startsWith("META-INF/")
                && !n.endsWith("package-info.class") && !n.endsWith("module-info.class");
    }

    private static String klassenname(String n) {
        return n.substring(0, n.length() - ".class".length()).replace('/', '.');
    }
}
