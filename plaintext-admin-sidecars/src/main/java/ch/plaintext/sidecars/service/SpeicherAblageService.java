/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.sidecars.service;

import ch.plaintext.modules.ModulApiUmsetzung;

import ch.plaintext.ablagen.DateiAblage;
import ch.plaintext.boot.ablage.LokaleDateiAblage;
import ch.plaintext.ablagen.DateiAblagenRegister;
import ch.plaintext.boot.plugins.netz.AusgehendesZiel;
import ch.plaintext.sidecars.ablage.GitAblage;
import ch.plaintext.sidecars.ablage.NextcloudAblage;
import ch.plaintext.sidecars.entity.SpeicherAblage;
import ch.plaintext.sidecars.repository.SpeicherAblageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * Speicher-Ablagen (Karte 1406, Daniel 02.10.2026): unter Root → Sidecars eine Nextcloud mit Zugang
 * und Pfad hinterlegen, damit Module wie Draw.io dort Dateien ablegen ({@link DateiAblagenRegister}).
 *
 * <p>Die Adresse läuft durch {@link AusgehendesZiel}: nur öffentliche Hosts oder die in
 * {@code plaintext.ausgehend.erlaubte-hosts} freigegebenen (z. B. eine Nextcloud im LAN). Das
 * App-Passwort liegt verschlüsselt ({@link SidecarCrypto}) und wird nie angezeigt.</p>
 *
 * <p>Karte 1471: zweite Art {@code GIT} ({@link GitAblage}) mit Repo-Adresse, Zweig, Unterordner und
 * Token (verschlüsselt wie das App-Passwort). Die Arbeitsklone liegen unter
 * {@code plaintext.ablagen.git.verzeichnis}.</p>
 */
@Slf4j
@Service
@ModulApiUmsetzung(beschreibung = "Manages the file stores set up under \"Root → Sidecars\" and hands a store to modules by name.",
        seiteneffekte = ModulApiUmsetzung.Seiteneffekte.INTERN,
        hinweise = {"Addresses must be public or explicitly allowed", "If a store cannot be opened, ablage() returns an empty Optional and logs the reason"},
        beispiele = {"ablage(\"drawio\")"})
public class SpeicherAblageService implements DateiAblagenRegister {

    private final SpeicherAblageRepository repo;
    private final SidecarCrypto crypto;
    private final Set<String> erlaubteHosts;
    private final HttpClient http;
    private final Path gitKlone;
    /** Karte 2096: Basis der lokalen Ablagen, {@code null} = Art LOKAL ist aus. */
    private final Path lokalBasis;

    @org.springframework.beans.factory.annotation.Autowired
    public SpeicherAblageService(SpeicherAblageRepository repo, SidecarCrypto crypto,
                                 @Value("${" + AusgehendesZiel.EIGENSCHAFT_ERLAUBTE_HOSTS + ":}") String erlaubteHosts,
                                 @Value("${plaintext.ablagen.git.verzeichnis:${java.io.tmpdir}/plaintext-git-ablagen}") String gitKlone,
                                 @Value("${plaintext.ablagen.lokal.basis:}") String lokalBasis) {
        this(repo, crypto, erlaubteHosts, NextcloudAblage.standardClient(), Path.of(gitKlone),
                lokalBasis == null || lokalBasis.isBlank() ? null : Path.of(lokalBasis.strip()));
    }

    SpeicherAblageService(SpeicherAblageRepository repo, SidecarCrypto crypto, String erlaubteHosts, HttpClient http, Path gitKlone) {
        this(repo, crypto, erlaubteHosts, http, gitKlone, null);
    }

    SpeicherAblageService(SpeicherAblageRepository repo, SidecarCrypto crypto, String erlaubteHosts, HttpClient http, Path gitKlone,
                          Path lokalBasis) {
        this.lokalBasis = lokalBasis;
        this.repo = repo;
        this.crypto = crypto;
        this.erlaubteHosts = AusgehendesZiel.allowlist(erlaubteHosts);
        this.http = http;
        this.gitKlone = gitKlone;
    }

    /** Karte 2096: darf eine Ablage der Art LOKAL eingerichtet werden (Basis gesetzt)? */
    public boolean lokalVerfuegbar() {
        return lokalBasis != null;
    }

    /** @return alle Ablagen, nach Name */
    public List<SpeicherAblage> liste() {
        return repo.findByDeletedFalseOrderByNameAsc();
    }

    /** @throws NoSuchElementException wenn es die Ablage nicht gibt */
    public SpeicherAblage eintrag(String name) {
        return repo.findFirstByNameAndDeletedFalse(name == null ? "" : name.trim())
                .orElseThrow(() -> new NoSuchElementException("Ablage «" + name + "» ist nicht eingerichtet."));
    }

    /**
     * Legt eine Ablage an oder ändert sie und prüft sie sofort.
     *
     * @param neuesPasswort leer = bisheriges behalten (beim Anlegen Pflicht)
     * @return der gespeicherte Eintrag mit Prüfergebnis
     * @throws IllegalArgumentException bei ungültigen Angaben
     */
    public SpeicherAblage speichere(String name, String url, String benutzer, String neuesPasswort, String pfad) {
        return speichere(name, SpeicherAblage.ART_NEXTCLOUD, url, benutzer, neuesPasswort, pfad, null);
    }

    /**
     * Wie oben, mit Art (NEXTCLOUD oder GIT) und bei GIT dem Zweig (Karte 1471).
     *
     * @param neuesPasswort App-Passwort bzw. Token; leer = bisheriges behalten (beim Anlegen Pflicht)
     */
    public SpeicherAblage speichere(String name, String art, String url, String benutzer, String neuesPasswort, String pfad, String zweig) {
        String n = name == null ? "" : name.strip();
        String t = normiereArt(art);
        if (SpeicherAblage.ART_LOKAL.equals(t)) {
            return speichereLokal(n, pfad);
        }
        boolean git = SpeicherAblage.ART_GIT.equals(t);
        URI u = pruefeAngaben(n, url, benutzer, git ? "/" : pfad);
        if (git && (zweig == null || zweig.isBlank())) {
            throw new IllegalArgumentException("Bitte den Zweig angeben (z. B. main).");
        }
        SpeicherAblage a = repo.findFirstByNameAndDeletedFalse(n).orElseGet(SpeicherAblage::new);
        boolean neu = a.getId() == null;
        verlangePasswort(neu, neuesPasswort, git);
        a.setName(n);
        a.setArt(t);
        a.setMandat(ch.plaintext.sidecars.entity.Sidecar.MANDAT);
        a.setUrl(u.toString());
        a.setBenutzer(benutzer.strip());
        a.setPfad(pfad == null ? "" : pfad.strip());
        a.setZweig(git ? zweig.strip() : null);
        if (neuesPasswort != null && !neuesPasswort.isBlank()) {
            a.setPasswortEncrypted(crypto.encrypt(neuesPasswort.strip()));
        }
        baueZurProbe(a, git);
        log.info("Speicher-Ablage «{}» {}: {} {} als {}, Pfad {}{}", n, neu ? "angelegt" : "geändert", t, a.getUrl(), a.getBenutzer(),
                a.getPfad(), git ? ", Zweig " + a.getZweig() : "");
        return pruefe(repo.save(a));
    }

    /**
     * Karte 2096: Ordner auf dem Server, eingesperrt unter {@code plaintext.ablagen.lokal.basis}. Ohne Adresse,
     * Benutzer und Passwort; das Formular waehlt nur den Unterordner, nie einen absoluten Pfad.
     */
    private SpeicherAblage speichereLokal(String n, String pfad) {
        if (!SidecarBeschreibung.NAME_MUSTER.matcher(n).matches()) {
            throw new IllegalArgumentException("Name: a-z, 0-9 und -, höchstens 40 Zeichen.");
        }
        String unter = pfad == null ? "" : pfad.strip();
        try {
            LokaleDateiAblage.unter(n, lokalBasis, unter);
        } catch (IOException e) {
            throw new IllegalArgumentException("Pfad: " + e.getMessage());
        }
        SpeicherAblage a = repo.findFirstByNameAndDeletedFalse(n).orElseGet(SpeicherAblage::new);
        boolean neu = a.getId() == null;
        a.setName(n);
        a.setArt(SpeicherAblage.ART_LOKAL);
        a.setMandat(ch.plaintext.sidecars.entity.Sidecar.MANDAT);
        a.setUrl("");
        a.setBenutzer("");
        a.setPasswortEncrypted(null);
        a.setPfad(unter);
        a.setZweig(null);
        log.info("Speicher-Ablage «{}» {}: LOKAL, Unterordner {}", n, neu ? "angelegt" : "geändert", unter.isEmpty() ? "(Basis)" : unter);
        return pruefe(repo.save(a));
    }

    /** NEXTCLOUD (Vorgabe), GIT oder LOKAL; Sonar java:S3776 (Karte 1554): aus speichere() herausgeloest. */
    private static String normiereArt(String art) {
        String t = art == null || art.isBlank() ? SpeicherAblage.ART_NEXTCLOUD : art.strip().toUpperCase(Locale.ROOT);
        if (!SpeicherAblage.ART_GIT.equals(t) && !SpeicherAblage.ART_NEXTCLOUD.equals(t) && !SpeicherAblage.ART_LOKAL.equals(t)) {
            throw new IllegalArgumentException("Art: NEXTCLOUD, GIT oder LOKAL.");
        }
        return t;
    }

    /** Beim Anlegen ist das Passwort bzw. Token Pflicht. */
    private static void verlangePasswort(boolean neu, String neuesPasswort, boolean git) {
        if (neu && (neuesPasswort == null || neuesPasswort.isBlank())) {
            throw new IllegalArgumentException(git ? "Bitte das Zugangs-Token angeben." : "Bitte das App-Passwort angeben.");
        }
    }

    /** Baut die Ablage einmal auf; ein Fehler wird zur Meldung fuer das Formular. */
    private void baueZurProbe(SpeicherAblage a, boolean git) {
        try {
            baue(a, null);
        } catch (IOException e) {
            throw new IllegalArgumentException((git && e.getMessage().contains("Zweig") ? "" : "Pfad: ") + e.getMessage());
        }
    }

    /**
     * Name, Adresse (SSRF-Pruefung ueber AusgehendesZiel, https ausser freigegebenen Hosts), Benutzer und Pfad,
     * in dieser Reihenfolge (Karte 1416, Sonar java:S3776: aus speichere() herausgeloest).
     */
    private URI pruefeAngaben(String n, String url, String benutzer, String pfad) {
        if (!SidecarBeschreibung.NAME_MUSTER.matcher(n).matches()) {
            throw new IllegalArgumentException("Name: a-z, 0-9 und -, höchstens 40 Zeichen.");
        }
        URI u;
        try {
            u = AusgehendesZiel.pruefeUrl(url, erlaubteHosts);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Adresse: " + e.getMessage());
        }
        if (!"https".equals(u.getScheme()) && !erlaubteHosts.contains(u.getHost().toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Adresse: nur https, ausser für freigegebene interne Hosts.");
        }
        if (benutzer == null || benutzer.isBlank()) {
            throw new IllegalArgumentException("Bitte den Benutzer angeben.");
        }
        if (pfad == null || pfad.isBlank()) {
            throw new IllegalArgumentException("Bitte den Pfad angeben (Ordner in der Nextcloud).");
        }
        return u;
    }

    /** Prüft Anmeldung und Ordner und speichert das Ergebnis. */
    public SpeicherAblage pruefe(SpeicherAblage a) {
        a.setLetztePruefung(Instant.now());
        try {
            DateiAblage d = oeffne(a);
            a.setMeldung(d instanceof GitAblage g ? g.pruefe()
                    : d instanceof LokaleDateiAblage l ? l.pruefe() : ((NextcloudAblage) d).pruefe());
            a.setOk(true);
        } catch (IOException | RuntimeException e) {
            a.setOk(false);
            a.setMeldung(e.getMessage() == null ? e.getClass().getSimpleName() : kurz(e.getMessage()));
        }
        return repo.save(a);
    }

    public void entferne(String name) {
        SpeicherAblage a = eintrag(name);
        a.setDeleted(true);
        repo.save(a);
        log.info("Speicher-Ablage «{}» entfernt", name);
    }

    DateiAblage oeffne(SpeicherAblage a) throws IOException {
        if (SpeicherAblage.ART_LOKAL.equals(a.getArt())) {
            return baue(a, null);   // kein Netz, also keine Zielpruefung
        }
        // Bei jedem Öffnen erneut prüfen: ein Host kann seit dem Speichern auf eine interne Adresse zeigen.
        try {
            AusgehendesZiel.pruefeUrl(a.getUrl(), erlaubteHosts);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage());
        }
        return baue(a, a.hatPasswort() ? crypto.decrypt(a.getPasswortEncrypted()) : null);
    }

    private DateiAblage baue(SpeicherAblage a, String pw) throws IOException {
        if (SpeicherAblage.ART_LOKAL.equals(a.getArt())) {
            return LokaleDateiAblage.unter(a.getName(), lokalBasis, a.getPfad());
        }
        if (SpeicherAblage.ART_GIT.equals(a.getArt())) {
            return new GitAblage(a.getName(), a.getUrl(), a.getZweig(), a.getPfad(), a.getBenutzer(), pw,
                    gitKlone.resolve(a.getName() + "-" + kennung(a.getUrl() + "\n" + a.getZweig())), SpeicherAblageService::angemeldet);
        }
        return new NextcloudAblage(a.getName(), a.getUrl(), a.getBenutzer(), pw, a.getPfad(), http);
    }

    /** Eigener Klon je Adresse und Zweig: ändert sich eins davon, wird nicht im alten weitergearbeitet. */
    private static String kennung(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)), 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Autor der Git-Commits: der angemeldete Benutzer (Login, in den Apps eine Mail-Adresse). */
    static String angemeldet() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || !auth.isAuthenticated() ? null : auth.getName();
    }

    // ---------- DateiAblagenRegister ----------

    @Override
    public List<String> namen() {
        return liste().stream().map(SpeicherAblage::getName).toList();
    }

    @Override
    public Optional<DateiAblage> ablage(String name) {
        return repo.findFirstByNameAndDeletedFalse(name == null ? "" : name.trim()).flatMap(a -> {
            try {
                return Optional.of(oeffne(a));
            } catch (IOException | RuntimeException e) {
                log.warn("Speicher-Ablage «{}» nicht nutzbar: {}", name, e.getMessage());
                return Optional.empty();
            }
        });
    }

    private static String kurz(String s) {
        return s.length() > 500 ? s.substring(0, 499) + "…" : s;
    }
}
