/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.modules.laufzeit;

import ch.plaintext.PlaintextSecurity;
import ch.plaintext.audit.DestructiveActionAuditService;
import ch.plaintext.lader.ModulAuswahl;
import ch.plaintext.lader.ModulLader;
import ch.plaintext.secrets.SecretResolver;
import ch.plaintext.secrets.SecretWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Karte 1608: Seite «Laufzeit-Module» (Auswahl aus signierten Registries, wirksam per «Neu starten»).
 *
 * <ul>
 *   <li>Lesen und Schalten (Auswahl speichern, Neu starten): <b>nur ROOT</b> (Karte 1612), hier im Dienst geprueft.</li>
 *   <li>Schreibt nur {@code auswahl.yaml} im Modulverzeichnis (Pfad aus der Umgebung, Dateiname fest), atomar mit
 *       {@code .vorher}; was der Lader-Parser ablehnt, wird nicht geschrieben. Registries und Schluessel stehen in
 *       der Instanz-Konfig und sind hier nicht aenderbar.</li>
 *   <li>Neu starten: Audit, dann beendet sich die App mit {@link #EXIT_NEUSTART}; die Restart-Policy des Containers
 *       startet neu und der ModulLader liest die Auswahl. Kein Docker-Socket.</li>
 * </ul>
 */
@Slf4j
@Service
public class ModulAuswahlService {

    /** Eigener Exit-Code fuer den gewollten Neustart (EX_TEMPFAIL), im Log von einem Absturz unterscheidbar. */
    public static final int EXIT_NEUSTART = 75;
    static final String KANAL = "UI";

    /** Eine Version eines Moduls im Angebot. */
    public record Version(String version, String registry, boolean signiert, String passt) implements Serializable {
    }

    /** Ein waehlbares Modul mit allen Versionen aus allen Registries (neueste zuerst). */
    /** {@code gewaehltRegistry}: Registry aus der Auswahl oder {@code null} (erste mit passender Version). */
    public record Modul(String name, List<Version> versionen, String gewaehlt, String gewaehltRegistry, String geladen)
            implements Serializable {
    }

    /** Karte 2377 (ROOT-9): eine Registry der Quellen mit Status fuer die Seite. */
    public record Registry(String name, String status) implements Serializable {
    }

    /** Was die Seite zeigt. */
    public record Ansicht(Map<String, String> status, List<Modul> module, Map<String, String> registryFehler,
                          String quellenFehler, List<String> geladen, String huelle, boolean darfSchalten,
                          String auswahlFehler, List<Registry> registries) implements Serializable {
    }

    private final PlaintextSecurity security;
    private final DestructiveActionAuditService audit;
    private final ApplicationContext kontext;
    private final Path modulVerzeichnis;
    private final Path konfigVerzeichnis;
    /** Karte 1618: hier liegt auswahl.yaml (gemeinsam fuer beide Slots); ohne Angabe das Modulverzeichnis. */
    private final Path auswahlVerzeichnis;
    /** Karte 2408: Zugang der UI-Quellen; optional wie bei jedem Verbraucher des Secret-Stores. */
    private SecretResolver secretLeser;
    private SecretWriter secretSchreiber;

    @Autowired(required = false)
    void setSecretLeser(SecretResolver r) {
        this.secretLeser = r;
    }

    @Autowired(required = false)
    void setSecretSchreiber(SecretWriter w) {
        this.secretSchreiber = w;
    }

    /** Karte 2408: Datei- und UI-Quellen zusammen, so wie der Lader sie beim Start sieht (Datei zuerst). */
    record Gesamt(ModulAuswahl.Angebot angebot, List<PublicKey> schluessel, List<String> registryNamen,
                  List<UiQuellen.Quelle> ui, Map<String, String> zugaenge, String quellenFehler) {
        @Override
        public String toString() { // nie die Zugaenge (benutzer:passwort), auch nicht in einem spaeteren Log
            return "Gesamt[" + registryNamen + ", " + angebot.zeilen().size() + " Zeilen, Zugaenge fuer " + zugaenge.keySet() + "]";
        }
    }

    public ModulAuswahlService(PlaintextSecurity security, DestructiveActionAuditService audit, ApplicationContext kontext,
                               @Value("${" + ModulLader.VERZEICHNIS_ENV + ":" + ModulLader.STANDARD_VERZEICHNIS + "}")
                               String modulVerzeichnis,
                               @Value("${" + ModulAuswahl.KONFIG_ENV + ":" + ModulAuswahl.STANDARD_KONFIG + "}")
                               String konfigVerzeichnis,
                               @Value("${" + ModulAuswahl.AUSWAHL_VERZEICHNIS_ENV + ":}") String auswahlVerzeichnis) {
        this.security = security;
        this.audit = audit;
        this.kontext = kontext;
        this.modulVerzeichnis = Path.of(modulVerzeichnis);
        this.konfigVerzeichnis = Path.of(konfigVerzeichnis);
        this.auswahlVerzeichnis = auswahlVerzeichnis == null || auswahlVerzeichnis.isBlank()
                ? this.modulVerzeichnis : Path.of(auswahlVerzeichnis);
    }

    public boolean darfSchalten() {
        return security.ifGranted("ROLE_ROOT");
    }

    // ------------------------------------------------------------------------------------------- lesen

    public Ansicht ansicht() throws IOException {
        if (!darfSchalten()) {
            throw new SecurityException("Laufzeit-Module: nur ROOT");
        }
        ModulLader.Huelle huelle = huelle();
        Gesamt gesamt = gesamt();
        String quellenFehler = gesamt.quellenFehler();
        List<String> registryNamen = gesamt.registryNamen();
        ModulAuswahl.Angebot angebot = gesamt.angebot();
        List<java.security.PublicKey> schluessel = gesamt.schluessel();
        String auswahlFehler = null;
        Map<String, ModulAuswahl.Wahl> gewaehlt = new LinkedHashMap<>();
        try {
            auswahl().forEach(w -> gewaehlt.put(w.name(), w));
        } catch (RuntimeException ex) {
            auswahlFehler = ex.getMessage();
        }
        List<String> geladen = geladen();
        Map<String, List<Version>> jeName = new LinkedHashMap<>();
        for (ModulAuswahl.Zeile z : angebot.zeilen()) {
            if (z.istModul()) {
                jeName.computeIfAbsent(z.name(), n -> new ArrayList<>()).add(new Version(z.version(), z.registry(),
                        ModulAuswahl.signaturGueltig(z, schluessel),
                        huelle == null ? null : ModulAuswahl.rootMinPasst(z.rootMin(), huelle)));
            }
        }
        gewaehlt.keySet().forEach(n -> jeName.computeIfAbsent(n, x -> new ArrayList<>()));
        List<Modul> module = new ArrayList<>();
        jeName.forEach((n, v) -> {
            v.sort(Comparator.comparing(Version::version, ModulAuswahl::versionVergleich).reversed());
            String im = geladen.stream().filter(g -> g.startsWith(n + "-")).map(g -> g.substring(n.length() + 1, g.length() - 4))
                    .findFirst().orElse(null);
            ModulAuswahl.Wahl w = gewaehlt.get(n);
            module.add(new Modul(n, List.copyOf(v), w == null ? null : w.version(), w == null ? null : w.registry(), im));
        });
        module.sort(Comparator.comparing(Modul::name));
        return new Ansicht(ModulAuswahl.statusLesen(modulVerzeichnis), List.copyOf(module), angebot.fehler(),
                quellenFehler, geladen, huelle == null ? null : huelle.version(), darfSchalten(), auswahlFehler,
                registries(registryNamen, angebot, schluessel));
    }

    /** Status je Registry der Quellen: Fehler aus dem Angebot, sonst Anzahl Module und ungueltige Signaturen, sonst leer. */
    static List<Registry> registries(List<String> namen, ModulAuswahl.Angebot angebot, List<java.security.PublicKey> schluessel) {
        List<Registry> aus = new ArrayList<>();
        for (String n : namen) {
            String fehler = angebot.fehler().get(n);
            if (fehler != null) {
                aus.add(new Registry(n, "Fehler: " + fehler));
                continue;
            }
            List<ModulAuswahl.Zeile> z = angebot.zeilen().stream().filter(x -> x.istModul() && n.equals(x.registry())).toList();
            long ungueltig = z.stream().filter(x -> !ModulAuswahl.signaturGueltig(x, schluessel)).count();
            aus.add(new Registry(n, z.isEmpty() ? "leer" : z.size() + " Modul(e)"
                    + (ungueltig == 0 ? "" : ", " + ungueltig + " mit ungueltiger Signatur")));
        }
        return List.copyOf(aus);
    }

    List<ModulAuswahl.Wahl> auswahl() throws IOException {
        Path p = auswahlVerzeichnis.resolve(ModulAuswahl.AUSWAHL);
        return Files.isRegularFile(p)
                ? ModulAuswahl.auswahlParsen(Files.readString(p, StandardCharsets.UTF_8), p.toString()) : List.of();
    }

    // ------------------------------------------------------------------------------------------- schalten

    /** Schreibt die Auswahl; jedes Modul muss im Angebot stehen, die Version dort oder {@code latest}. */
    public synchronized List<ModulAuswahl.Wahl> speichern(List<ModulAuswahl.Wahl> neu) throws IOException {
        nurRoot();
        Gesamt g = gesamt();
        Map<String, List<ModulAuswahl.Zeile>> angebot = new LinkedHashMap<>();
        g.angebot().zeilen().stream().filter(ModulAuswahl.Zeile::istModul)
                .forEach(z -> angebot.computeIfAbsent(z.name(), n -> new ArrayList<>()).add(z));
        for (ModulAuswahl.Wahl w : neu) {
            List<ModulAuswahl.Zeile> v = angebot.get(w.name());
            if (v == null) {
                throw new IllegalArgumentException("'" + w.name() + "' steht in keiner erreichbaren Registry");
            }
            // ROOT-9: mit registry muss das Modul (in dieser Version) genau dort liegen
            List<ModulAuswahl.Zeile> dort = w.registry() == null ? v
                    : v.stream().filter(z -> w.registry().equals(z.registry())).toList();
            String wo = w.registry() == null ? "in keiner Registry" : "nicht in Registry '" + w.registry() + "'";
            if (dort.isEmpty()) {
                throw new IllegalArgumentException("'" + w.name() + "' steht " + wo);
            }
            if (!ModulAuswahl.LATEST.equals(w.version()) && dort.stream().noneMatch(z -> z.version().equals(w.version()))) {
                throw new IllegalArgumentException("'" + w.name() + "' " + w.version() + " steht " + wo);
            }
        }
        vorladen(neu, g);
        List<ModulAuswahl.Wahl> alt = nachsichtig();
        String text = ModulAuswahl.auswahlText(neu, "geschrieben von der Seite Laufzeit-Module (Karte 1608), "
                + security.getUser());
        Path ziel = auswahlVerzeichnis.resolve(ModulAuswahl.AUSWAHL);
        Files.createDirectories(auswahlVerzeichnis);
        if (Files.isRegularFile(ziel)) {
            atomar(auswahlVerzeichnis.resolve(ModulAuswahl.AUSWAHL_VORHER), Files.readString(ziel, StandardCharsets.UTF_8));
        }
        atomar(ziel, text);
        protokoll("MODUL_AUSWAHL_SPEICHERN", text(neu), "vorher: " + text(alt));
        return neu;
    }

    /** Kontrolliert beenden; die Restart-Policy startet den Container neu. */
    public void neustart() {
        nurRoot();
        protokoll("MODUL_AUSWAHL_NEUSTART", "Neustart mit Exit " + EXIT_NEUSTART, "Auswahl: " + text(nachsichtig()));
        beenden();
    }

    /** Eigener Thread, damit die Antwort noch ausgeliefert wird. */
    void beenden() {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            log.warn("Laufzeit-Module: Neustart angefordert, App beendet sich mit Exit {}", EXIT_NEUSTART);
            System.exit(SpringApplication.exit(kontext, () -> EXIT_NEUSTART));
        }, "modul-auswahl-neustart");
        t.setDaemon(false);
        t.start();
    }

    // ------------------------------------------------------------------------------------------- UI-Quellen (Karte 2408)

    /** Quellen aus der Oberflaeche (ohne Zugang), fuer die Seite. */
    public List<UiQuellen.Quelle> uiQuellen() throws IOException {
        nurRoot();
        return UiQuellen.lesen(auswahlVerzeichnis.resolve(ModulAuswahl.QUELLEN_UI));
    }

    /**
     * JSON von modules uebernehmen: pruefen, Index mit dem Zugang abrufen (mindestens eine gueltig signierte Zeile),
     * Zugang in den Secret-Store, Quelle (ohne Zugang) nach quellen-ui.txt. Gleicher Name ersetzt die Quelle.
     */
    public synchronized UiQuellen.Quelle quelleAnlegen(String json) throws IOException {
        nurRoot();
        return uebernehmen(UiQuellen.lesen(json));
    }

    /** Nach der Formatpruefung (dort auch: nur https). */
    UiQuellen.Quelle uebernehmen(UiQuellen.Eingabe e) throws IOException {
        nurRoot();
        if (secretSchreiber == null) {
            throw new IllegalStateException("kein Secret-Store in dieser App (plaintext-admin-secrets): Quellen aus der Oberflaeche gehen nicht");
        }
        if (dateiQuellen().stream().anyMatch(r -> r.name().equals(e.name()))) {
            throw new IllegalArgumentException("'" + e.name() + "' steht schon in " + ModulAuswahl.QUELLEN + " (anderen Namen waehlen)");
        }
        UiQuellen.Quelle q = new UiQuellen.Quelle(e.name(), e.index(), UiQuellen.schluesselBase64(e.schluessel()));
        String zugang = e.benutzer() + ":" + e.passwort();
        ModulAuswahl.Angebot probe = ModulAuswahl.angebot(List.of(q.registry()), Map.of(q.name(), zugang));
        if (!probe.fehler().isEmpty()) {
            throw new IllegalArgumentException("Index nicht abrufbar: " + probe.fehler().get(q.name()));
        }
        PublicKey k = UiQuellen.schluessel(q.schluessel());
        if (probe.zeilen().stream().noneMatch(z -> z.istModul() && ModulAuswahl.signaturGueltig(z, List.of(k)))) {
            throw new IllegalArgumentException("Index ohne Modul mit gueltiger Signatur dieses Schluessels");
        }
        secretSchreiber.store(UiQuellen.SECRET_PRAEFIX + q.name(), zugang, "Karte 2408: Zugang der Modul-Quelle " + q.name());
        List<UiQuellen.Quelle> alle = new ArrayList<>(UiQuellen.lesen(auswahlVerzeichnis.resolve(ModulAuswahl.QUELLEN_UI)));
        alle.removeIf(x -> x.name().equals(q.name()));
        alle.add(q);
        Files.createDirectories(auswahlVerzeichnis);
        atomar(auswahlVerzeichnis.resolve(ModulAuswahl.QUELLEN_UI), UiQuellen.text(alle, security.getUser()));
        protokoll("MODUL_QUELLE_ANLEGEN", q.name() + " " + q.index(), "Zugang im Secret-Store " + UiQuellen.SECRET_PRAEFIX + q.name());
        return q;
    }

    /** Quelle entfernen: aus quellen-ui.txt, Vorgeladenes und Zugang weg. Wirksam nach «Neu starten». */
    public synchronized void quelleEntfernen(String name) throws IOException {
        nurRoot();
        List<UiQuellen.Quelle> alle = new ArrayList<>(UiQuellen.lesen(auswahlVerzeichnis.resolve(ModulAuswahl.QUELLEN_UI)));
        if (!alle.removeIf(x -> x.name().equals(name))) {
            throw new IllegalArgumentException("keine Quelle '" + name + "'");
        }
        atomar(auswahlVerzeichnis.resolve(ModulAuswahl.QUELLEN_UI), UiQuellen.text(alle, security.getUser()));
        ModulAuswahl.loeschen(auswahlVerzeichnis.resolve(ModulAuswahl.VORBEREITET).resolve(name));
        if (secretSchreiber != null) {
            secretSchreiber.remove(UiQuellen.SECRET_PRAEFIX + name);
        }
        protokoll("MODUL_QUELLE_ENTFERNEN", name, "Vorgeladenes und Zugang entfernt");
    }

    private List<ModulAuswahl.Registry> dateiQuellen() {
        try {
            return ModulAuswahl.quellenLesen(konfigVerzeichnis.resolve(ModulAuswahl.QUELLEN)).registries();
        } catch (IOException | RuntimeException ex) {
            return List.of();
        }
    }

    /** Datei-Quellen wie bisher, dazu die UI-Quellen mit Zugang aus dem Secret-Store (gleicher Name: Datei gewinnt). */
    Gesamt gesamt() throws IOException {
        String fehler = null;
        List<PublicKey> schluessel = new ArrayList<>();
        List<String> namen = new ArrayList<>();
        ModulAuswahl.Angebot datei = new ModulAuswahl.Angebot(List.of(), Map.of());
        try {
            ModulAuswahl.Quellen q = ModulAuswahl.quellenLesen(konfigVerzeichnis.resolve(ModulAuswahl.QUELLEN));
            schluessel.addAll(q.schluessel());
            q.registries().forEach(r -> namen.add(r.name()));
            datei = ModulAuswahl.angebot(konfigVerzeichnis);
        } catch (RuntimeException ex) {
            fehler = ex.getMessage();
        }
        List<UiQuellen.Quelle> ui = new ArrayList<>();
        try {
            UiQuellen.lesen(auswahlVerzeichnis.resolve(ModulAuswahl.QUELLEN_UI)).stream()
                    .filter(u -> !namen.contains(u.name())).forEach(ui::add);
        } catch (RuntimeException ex) {
            fehler = (fehler == null ? "" : fehler + "; ") + ex.getMessage();
        }
        Map<String, String> zugaenge = new LinkedHashMap<>();
        Map<String, String> uiFehler = new LinkedHashMap<>();
        for (UiQuellen.Quelle u : ui) {
            namen.add(u.name());
            schluessel.add(UiQuellen.schluessel(u.schluessel()));
            java.util.Optional<String> z = secretLeser == null ? java.util.Optional.empty()
                    : secretLeser.resolve(UiQuellen.SECRET_PRAEFIX + u.name());
            if (z.isPresent()) {
                zugaenge.put(u.name(), z.get());
            } else {
                uiFehler.put(u.name(), "Zugang fehlt im Secret-Store (" + UiQuellen.SECRET_PRAEFIX + u.name() + ")");
            }
        }
        ModulAuswahl.Angebot uiAngebot = ModulAuswahl.angebot(
                ui.stream().filter(u -> zugaenge.containsKey(u.name())).map(UiQuellen.Quelle::registry).toList(), zugaenge);
        List<ModulAuswahl.Zeile> zeilen = new ArrayList<>(datei.zeilen());
        zeilen.addAll(uiAngebot.zeilen());
        Map<String, String> alleFehler = new LinkedHashMap<>(datei.fehler());
        alleFehler.putAll(uiAngebot.fehler());
        alleFehler.putAll(uiFehler);
        if (fehler == null && namen.isEmpty()) {
            fehler = "keine Registry in " + konfigVerzeichnis.resolve(ModulAuswahl.QUELLEN) + " und keine Quelle aus der Oberflaeche";
        }
        return new Gesamt(new ModulAuswahl.Angebot(List.copyOf(zeilen), alleFehler), List.copyOf(schluessel), List.copyOf(namen),
                List.copyOf(ui), zugaenge, fehler);
    }

    /**
     * Wahlen, die beim Start aus einer UI-Quelle kommen (registry = ihr Name, oder ohne registry die erste Quelle mit
     * dem Modul, Datei zuerst, wie der Lader), werden jetzt mit dem Zugang vorgeladen; scheitert das, wird nichts
     * gespeichert.
     */
    void vorladen(List<ModulAuswahl.Wahl> neu, Gesamt g) throws IOException {
        for (UiQuellen.Quelle u : g.ui()) {
            List<ModulAuswahl.Wahl> hier = neu.stream().filter(w -> u.name().equals(w.registry())
                    || w.registry() == null && u.name().equals(ersteQuelle(w, g))).toList();
            if (hier.isEmpty() || !g.zugaenge().containsKey(u.name())) {
                continue;
            }
            try {
                ModulAuswahl.vorladen(auswahlVerzeichnis, u.registry(), g.zugaenge().get(u.name()), g.schluessel(), hier, huelle());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Vorladen unterbrochen", ex);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Vorladen aus '" + u.name() + "': " + ex.getMessage(), ex);
            }
        }
    }

    private static String ersteQuelle(ModulAuswahl.Wahl w, Gesamt g) {
        return g.angebot().zeilen().stream().filter(z -> z.istModul() && z.name().equals(w.name())
                        && (ModulAuswahl.LATEST.equals(w.version()) || z.version().equals(w.version())))
                .map(ModulAuswahl.Zeile::registry).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------------------------------- intern

    private void nurRoot() {
        if (!darfSchalten()) {
            throw new SecurityException("Laufzeit-Module schalten: nur ROOT");
        }
    }

    ModulLader.Huelle huelle() {
        try {
            return ModulLader.huelleAusManifest();
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private List<ModulAuswahl.Wahl> nachsichtig() {
        try {
            return auswahl();
        } catch (IOException | RuntimeException ex) {
            return List.of();
        }
    }

    private static String text(List<ModulAuswahl.Wahl> w) {
        return w.isEmpty() ? "(keine)" : String.join(", ", w.stream().map(x -> x.name() + ":" + x.version()).toList());
    }

    private void atomar(Path ziel, String text) throws IOException {
        Path temp = Files.createTempFile(ziel.getParent(), "." + ziel.getFileName() + ".", ".tmp"); // gleiches Dateisystem wie ziel (atomar)
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            Files.move(temp, ziel, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void protokoll(String aktion, String neu, String alt) {
        String wer = security.getUser();
        log.warn("{}: {} - {} ({}) in {}", aktion, wer, neu, alt, modulVerzeichnis);
        audit.logDestructiveAction(KANAL, aktion, ModulAuswahl.AUSWAHL, kurz(neu), kurz("Benutzer " + wer + "; " + alt));
    }

    private static String kurz(String s) {
        return s.length() <= 2000 ? s : s.substring(0, 1997) + "...";
    }

    /** Dateinamen der Jars, die der Lader in {@code loader.path} gelegt hat (leer: ohne Module gestartet). */
    static List<String> geladen() {
        String p = System.getProperty("loader.path");
        if (p == null || p.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(p.split(",")).map(x -> Path.of(x.strip()).getFileName().toString()).toList();
    }
}
