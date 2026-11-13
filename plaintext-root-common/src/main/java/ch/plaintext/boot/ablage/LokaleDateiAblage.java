/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.ablage;

import ch.plaintext.ablagen.AblageEintrag;
import ch.plaintext.ablagen.DateiAblage;
import ch.plaintext.modules.ModulApiUmsetzung;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Speicher-Ablage «Lokale Datei» (Karte 2096): ein Ordner im Dateisystem des Servers.
 *
 * <p><b>Eingesperrt.</b> Jeder Pfad ist relativ zur Wurzel ({@code /}-getrennt, ohne {@code ..},
 * {@code .}, {@code \} und Steuerzeichen). Zusätzlich muss der tiefste schon vorhandene Teil des
 * Pfads <i>real</i> in der Wurzel liegen: ein Symlink in der Ablage, der nach draussen zeigt, wird
 * abgewiesen. Die Wurzel selbst setzt der Betreiber (z.B. {@code plaintext.ablagen.lokal.basis}),
 * nie ein Formular — sonst hätte jeder Admin Lesezugriff auf das Dateisystem.</p>
 *
 * <p>Geschrieben wird atomar (Temp-Datei im Zielordner, dann {@code move}): ein Leser sieht nie
 * eine halbe Datei, auch nicht beim Autosave im Sekundentakt.</p>
 *
 * <p><b>Restrisiko (Review claude1, #42):</b> Zwischen Prüfung und Zugriff kann jemand mit Shell-Zugang
 * auf dem Server einen Pfadteil gegen einen Symlink tauschen (TOCTOU). Ohne {@code openat}/{@code O_NOFOLLOW}
 * ist das in Java nicht ganz zu schliessen; die Ablage selbst legt nie Links an, und über die API lassen sich
 * keine anlegen.</p>
 */
@ModulApiUmsetzung(beschreibung = "Stores, reads, lists, moves and deletes files and folders in a folder of the server's file system,"
        + " below a base the operator sets (plaintext.ablagen.lokal.basis).",
        seiteneffekte = ModulApiUmsetzung.Seiteneffekte.INTERN,
        hinweise = {"Paths stay below the folder: no dot segments, no symlink leading outside", "A store never covers the whole base",
                "Writes are atomic (temp file, then move)", "Reads at most 50 MB per file"},
        beispiele = {"schreibe(\"notizen/default/anna.md\", bytes)"})
public final class LokaleDateiAblage implements DateiAblage {

    private static final String UNGUELTIG = "Ungültiger Pfad.";
    private static final String TEMP_PRAEFIX = ".pt-tmp-";
    private static final long MAX_LESEN = 50L * 1024 * 1024;

    private final String name;
    private final Path wurzel;

    /** @param wurzel Ordner der Ablage; wird angelegt, falls er fehlt */
    public LokaleDateiAblage(String name, Path wurzel) throws IOException {
        this.name = name;
        Files.createDirectories(wurzel);
        this.wurzel = wurzel.toRealPath();
    }

    /**
     * Ablage im Unterordner einer vom Betreiber gesetzten Basis.
     *
     * <p>Die Wurzel liegt <i>real</i> unter der Basis: ist {@code basis/<unterordner>} selbst ein Symlink
     * nach draussen, wird abgewiesen, bevor etwas angelegt wird (Review claude1, #42).</p>
     *
     * @param basis {@code null} = lokale Ablagen sind ausgeschaltet
     * @param unterordner relativ zur Basis, Pflicht: eine Ablage sieht nie die ganze Basis und damit nie
     *                    die anderen lokalen Ablagen
     */
    public static LokaleDateiAblage unter(String name, Path basis, String unterordner) throws IOException {
        if (basis == null) {
            throw new IOException("Lokale Ablagen sind aus: plaintext.ablagen.lokal.basis ist nicht gesetzt.");
        }
        String u = normiere(unterordner, true);
        if (u.isEmpty()) {
            throw new IOException("Bitte einen Unterordner angeben.");
        }
        Path basisReal = Files.createDirectories(basis.toAbsolutePath().normalize()).toRealPath();
        LokaleDateiAblage basisAblage = new LokaleDateiAblage(name, basisReal);
        basisAblage.legeOrdnerAn(u);   // datei() prueft real gegen die Basis, bevor etwas angelegt wird
        LokaleDateiAblage a = new LokaleDateiAblage(name, basisAblage.datei(u, false));
        if (!a.wurzel.startsWith(basisReal)) {
            throw new IOException(UNGUELTIG);
        }
        return a;
    }

    @Override
    public String name() {
        return name;
    }

    /** Probe für «Prüfen»: schreiben, lesen, löschen. Die Meldung nennt bewusst keinen Server-Pfad. */
    public String pruefe() throws IOException {
        Path probe = Files.createTempFile(wurzel, TEMP_PRAEFIX, ".probe");   // exklusiv neu, folgt keinem Link
        Files.writeString(probe, "ok");
        try {
            if (!"ok".equals(Files.readString(probe))) {
                throw new IOException("Probe nicht lesbar.");
            }
        } finally {
            Files.deleteIfExists(probe);
        }
        return "Lokaler Ordner les- und schreibbar.";
    }

    @Override
    public void schreibe(String pfad, byte[] daten, String inhaltTyp) throws IOException {
        Path d = datei(pfad, false);
        if (Files.isDirectory(d)) {
            throw new IOException("An diesem Pfad liegt ein Ordner.");
        }
        Files.createDirectories(d.getParent());
        datei(pfad, false);   // erneut: die eben angelegten Ordner liegen in der Wurzel
        Path temp = Files.createTempFile(d.getParent(), TEMP_PRAEFIX, ".tmp");
        try {
            Files.write(temp, daten);
            Files.move(temp, d, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public byte[] lies(String pfad) throws IOException {
        Path d = datei(pfad, false);
        if (!Files.isRegularFile(d)) {
            throw new IOException("Datei nicht gefunden.");
        }
        if (Files.size(d) > MAX_LESEN) {
            throw new IOException("Datei grösser als 50 MB.");
        }
        return Files.readAllBytes(d);
    }

    @Override
    public boolean existiert(String pfad) throws IOException {
        return Files.exists(datei(pfad, true));
    }

    /** Wie die Git-Ablage: ein fehlender Ordner ist leer; Pfade relativ zur Ablage. Symlinks erscheinen nicht. */
    @Override
    public List<AblageEintrag> liste(String ordner) throws IOException {
        Path d = datei(ordner == null ? "" : ordner, true);
        List<AblageEintrag> l = new ArrayList<>();
        if (!Files.isDirectory(d)) {
            return l;
        }
        try (Stream<Path> s = Files.list(d)) {
            for (Path p : s.sorted().toList()) {
                BasicFileAttributes at = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (p.getFileName().toString().startsWith(TEMP_PRAEFIX) || at.isSymbolicLink()) {
                    continue;   // halbe Schreibvorgaenge und Links (deren Ziel kann draussen liegen) zeigen wir nicht
                }
                l.add(new AblageEintrag(wurzel.relativize(p).toString().replace('\\', '/'), at.isDirectory(),
                        at.isDirectory() ? -1 : at.size(), at.lastModifiedTime().toInstant()));
            }
        }
        return l;
    }

    @Override
    public void loesche(String pfad) throws IOException {
        Path d = datei(pfad, false);
        if (!Files.isRegularFile(d, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Datei nicht gefunden.");
        }
        Files.delete(d);
    }

    @Override
    public void legeOrdnerAn(String pfad) throws IOException {
        Path d = datei(pfad, false);
        if (Files.exists(d) && !Files.isDirectory(d)) {
            throw new IOException("An diesem Pfad liegt eine Datei.");
        }
        Files.createDirectories(d);
    }

    @Override
    public void verschiebe(String von, String nach) throws IOException {
        Path q = datei(von, false);
        Path z = datei(nach, false);
        if (z.startsWith(q)) {
            throw new IOException("Ein Ordner lässt sich nicht in sich selbst verschieben.");
        }
        if (!Files.exists(q, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Quelle nicht gefunden.");
        }
        if (Files.exists(z, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Ziel existiert bereits.");
        }
        Files.createDirectories(z.getParent());
        Files.move(q, z);
    }

    @Override
    public void loescheOrdner(String pfad, boolean rekursiv) throws IOException {
        Path d = datei(pfad, false);
        if (!Files.isDirectory(d, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Ordner nicht gefunden.");
        }
        if (!rekursiv) {
            try (Stream<Path> s = Files.list(d)) {
                if (s.findAny().isPresent()) {
                    throw new DirectoryNotEmptyException(pfad);
                }
            }
            Files.delete(d);
            return;
        }
        // Files.walk folgt keinen Symlinks: gelöscht wird der Link, nie sein Ziel.
        try (Stream<Path> s = Files.walk(d)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    /** Pfad im Dateisystem, nie ausserhalb der Wurzel — weder über {@code ..} noch über einen Symlink. */
    Path datei(String pfad, boolean leerErlaubt) throws IOException {
        String p = normiere(pfad, leerErlaubt);
        Path d = p.isEmpty() ? wurzel : wurzel.resolve(p).normalize();
        if (!d.startsWith(wurzel)) {
            throw new IOException(UNGUELTIG);
        }
        Path vorhanden = d;
        while (vorhanden != null && !Files.exists(vorhanden, LinkOption.NOFOLLOW_LINKS)) {
            vorhanden = vorhanden.getParent();
        }
        if (vorhanden == null || !vorhanden.toRealPath().startsWith(wurzel)) {
            throw new IOException(UNGUELTIG);
        }
        return d;
    }

    /** Relative Segmente ohne {@code ..}, {@code .}, {@code \} und Steuerzeichen; ohne Rand-Schrägstriche. */
    static String normiere(String pfad, boolean leerErlaubt) throws IOException {
        if (pfad == null || pfad.contains("\\") || pfad.chars().anyMatch(c -> c < 0x20)) {
            throw new IOException(UNGUELTIG);
        }
        String p = pfad.strip();
        int a = 0;
        int e = p.length();
        while (a < e && p.charAt(a) == '/') {
            a++;
        }
        while (e > a && p.charAt(e - 1) == '/') {
            e--;
        }
        p = p.substring(a, e);
        if (p.isEmpty()) {
            if (leerErlaubt) {
                return "";
            }
            throw new IOException(UNGUELTIG);
        }
        for (String teil : p.split("/", -1)) {
            if (teil.isEmpty() || teil.equals("..") || teil.equals(".")) {
                throw new IOException(UNGUELTIG);
            }
        }
        return p;
    }
}
