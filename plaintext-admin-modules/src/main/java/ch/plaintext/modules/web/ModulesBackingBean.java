/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.modules.web;

import ch.plaintext.PlaintextRoles;
import ch.plaintext.PlaintextSecurity;
import ch.plaintext.boot.plugins.jsf.FacesMessages;
import ch.plaintext.boot.menu.ModuleRoleProperties;
import ch.plaintext.modules.ModuleDangerZoneService;
import ch.plaintext.modules.ModuleDataService;
import ch.plaintext.modules.ModuleService;
import ch.plaintext.modules.ModuleView;
import ch.plaintext.modules.katalog.ModulAnalyse;
import ch.plaintext.modules.katalog.SchnittstellenKatalog;
import ch.plaintext.settings.ISettingsService;
import ch.plaintext.settings.SettingsKeys;
import jakarta.faces.application.FacesMessage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Backing Bean of the module management (module.html): display the list + switch modules on/off
 * as well as export/import of the module data as JSON (Task #016 phase 2, PR 3).
 *
 * <h2>Why field injection stays here (java:S6813, card 1273)</h2>
 *
 * <p>This bean is <b>session-scoped and serializable</b> — the one case in which constructor
 * injection is the wrong answer. On a deserialization <b>no constructor runs</b>: a service
 * field set only through the
 * constructor stays {@code null} forever, and being {@code final} nothing can set it afterwards,
 * not even the context. A {@code NotSerializableException} would thereby turn into a permanent
 * {@code NullPointerException} (cards 915/1246). Field injection ({@code @Autowired}, not
 * {@code final}) lets the context refill the field after a deserialization — that is the house
 * rule, and {@code PlaintextSessionBeanSerialisierbarTest} enforces it as a build-breaking
 * guard.</p>
 *
 * <p>{@code java:S6813} demands the exact opposite at these fields, so both cannot hold at once.
 * The guard wins: it protects against a defect that is silent and permanent, the rule protects a
 * style. The suppression sits on the class because every injected service of such a bean falls
 * under the house rule — card 1273 carries the measurement and the decision.</p>
 */
@Slf4j
@Scope("session")
@Component
@Data
@SuppressWarnings("java:S6813") // begruendet im Klassenkommentar oben (Karte 1273) — nicht umbauen
public class ModulesBackingBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String SUCCESS = "success";
    private static final String SECONDARY = "secondary";

    @Autowired
    private transient ModuleService moduleService;

    @Autowired
    private transient ModuleDataService moduleDataService;

    @Autowired
    private transient ModuleDangerZoneService dangerZoneService;

    /** Karte 1422: Schnittstellen der Module (Katalog), dieselben Daten wie über MCP. */
    @Autowired
    private transient SchnittstellenKatalog katalog;

    @Autowired
    private transient ModulAnalyse analyse;

    /** Karte 2390 Teil 3: «Zugriff» je Modul schreibt {@code <key>.zugriff.*} in settings (nur wenn admin-settings da ist). */
    @Autowired
    private transient ObjectProvider<ISettingsService> settings;

    /** Karte 2390 Teil 3 (Review kimi1): serverseitiger ROOT-Schutz — «rendered» schützt nur die Anzeige. */
    @Autowired
    private transient ObjectProvider<PlaintextSecurity> security;

    private List<ModuleView> module = new ArrayList<>();

    // ── Schnittstellen (Karte 1422) ─────────────────────────
    /** Suchtext in Name, Zweck und Methoden. */
    private String schnittstellenSuche = "";
    /** Filter: leer = alle, {@code SCHNITTSTELLE}, {@code DTO}, {@code OHNE} (nicht mit @ModulApi markiert). */
    private String schnittstellenArt = "";
    /** Card 1438: filter for modules and interfaces — empty = all, {@code root}, {@code modul}. */
    private String ebene = "";

    // ── Export (Download) ──────────────────────────────────
    private StreamedContent exportFile;

    // ── Import (Upload) ────────────────────────────────────
    private String importModuleId;
    private String importModuleDisplayName;
    private transient byte[] importBytes;
    private String importFileName;

    // ── Danger zone (clear data) ───────────────────────────
    private String clearModuleId;
    private String clearModuleDisplayName;
    private String clearBestaetigung;

    // ── Modul-Zugriff (Karte 2390 Teil 3) ─────────────────
    private String zugriffModuleId;
    private String zugriffModuleDisplayName;
    /** Rolle des Moduls, leer = frei. */
    private String zugriffRolle = "";
    /** ROOT darf ohne die Rolle. */
    private boolean zugriffRoot = true;
    /** ADMIN darf ohne die Rolle. */
    private boolean zugriffAdmin = true;
    /** Menü und Kachel zeigen das Modul (Seite bleibt per Link erreichbar). */
    private boolean zugriffSichtbar = true;
    /** Wer das Modul nutzen darf, darf es auch ändern (false = nur lesen, auch für ROOT/ADMIN). */
    private boolean zugriffBearbeiten = true;

    /** @return true, wenn admin-settings in dieser App läuft und der «Zugriff»-Dialog angeboten wird */
    public boolean isZugriffVerfuegbar() {
        return settings.getIfAvailable() != null;
    }

    /** Serverseitiger Schutz der Zugriffs-Pflege (Review kimi1): nur ROOT, ohne Kontext gar nichts. */
    private void nurRoot() {
        if (!PlaintextRoles.isRoot(security.getIfAvailable())) {
            throw new SecurityException("Der Modul-Zugriff ist nur für ROOT.");
        }
    }

    /** Öffnet den Zugriffs-Dialog: liest die fünf {@code <key>.zugriff.*}-Schlüssel (Standard: frei/alle ja). */
    public void zugriffVorbereiten(ModuleView m) {
        nurRoot();
        ISettingsService s = settings.getIfAvailable();
        zugriffModuleId = ModuleRoleProperties.canonicalKey(m.getModuleId());
        zugriffModuleDisplayName = m.getDisplayName();
        zugriffRolle = s == null ? "" : s.getString(zugriffModuleId + ".zugriff.rolle", SettingsKeys.MANDAT_GLOBAL);
        if (zugriffRolle == null) {
            zugriffRolle = "";
        }
        zugriffRoot = alsJa(s == null ? null : s.getString(zugriffModuleId + ".zugriff.root", SettingsKeys.MANDAT_GLOBAL));
        zugriffAdmin = alsJa(s == null ? null : s.getString(zugriffModuleId + ".zugriff.admin", SettingsKeys.MANDAT_GLOBAL));
        zugriffSichtbar = alsJa(s == null ? null : s.getString(zugriffModuleId + ".zugriff.sichtbar", SettingsKeys.MANDAT_GLOBAL));
        zugriffBearbeiten = alsJa(s == null ? null : s.getString(zugriffModuleId + ".zugriff.bearbeiten", SettingsKeys.MANDAT_GLOBAL));
    }

    /** Fehlt der Schalter, gilt der Standard «ja»; nur exakt «true» öffnet sonst (fail-closed, wie ModulZugriff.setzen). */
    private static boolean alsJa(String wert) {
        return wert == null || wert.isBlank() || Boolean.parseBoolean(wert.strip());
    }

    /**
     * Speichert die fünf Schalter; jedes Schreiben meldet {@code EinstellungGeaendert}, {@code ModulZugriff}
     * wendet sie nach dem Commit ohne Neustart an und protokolliert wer/was. Ein fehlender Dienst
     * (kein admin-settings) bricht ab, bevor etwas geschrieben wird.
     */
    public void zugriffSpeichern() {
        nurRoot();
        ISettingsService s = settings.getIfAvailable();
        if (s == null || zugriffModuleId == null || zugriffModuleId.isBlank()) {
            return;
        }
        String k = zugriffModuleId;
        String rolle = ModuleRoleProperties.canonicalRole(zugriffRolle);
        s.setSetting(k + ".zugriff.rolle", SettingsKeys.MANDAT_GLOBAL, rolle, "STRING",
                "Karte 2390: Modulrolle für " + k + " (leer = frei)");
        s.setSetting(k + ".zugriff.root", SettingsKeys.MANDAT_GLOBAL, String.valueOf(zugriffRoot), "BOOLEAN",
                "Karte 2390: ROOT darf " + k + " ohne die Rolle");
        s.setSetting(k + ".zugriff.admin", SettingsKeys.MANDAT_GLOBAL, String.valueOf(zugriffAdmin), "BOOLEAN",
                "Karte 2390: ADMIN darf " + k + " ohne die Rolle");
        s.setSetting(k + ".zugriff.sichtbar", SettingsKeys.MANDAT_GLOBAL, String.valueOf(zugriffSichtbar), "BOOLEAN",
                "Karte 2390: Menü und Kachel zeigen " + k + " (Seite bleibt per Link erreichbar)");
        s.setSetting(k + ".zugriff.bearbeiten", SettingsKeys.MANDAT_GLOBAL, String.valueOf(zugriffBearbeiten), "BOOLEAN",
                "Karte 2390: wer " + k + " nutzen darf, darf es auch ändern");
        addMessage(FacesMessage.SEVERITY_INFO,
                "Zugriff für Modul '" + zugriffModuleDisplayName + "' gespeichert.");
    }

    public void load() {
        module = moduleService.list();
    }

    /** Ajax listener of the on/off switch: {@code m.enabled} is already set, persist it. */
    public void toggle(ModuleView m) {
        moduleService.setEnabled(m.getModuleId(), m.isEnabled());
        addMessage(FacesMessage.SEVERITY_INFO,
                "Modul '" + m.getDisplayName() + "' " + (m.isEnabled() ? "aktiviert" : "deaktiviert") + ".");
    }

    /** Exports the data of a module as a JSON download ({@code p:fileDownload}, non-ajax). */
    public void export(ModuleView m) {
        try {
            String json = moduleDataService.export(m.getModuleId());
            String zeitstempel = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String dateiname = "modul_" + m.getModuleId() + "_" + zeitstempel + ".json";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exportFile = DefaultStreamedContent.builder()
                    .name(dateiname)
                    .contentType("application/json")
                    .contentLength((long) bytes.length)
                    .stream(() -> new ByteArrayInputStream(bytes))
                    .build();
        } catch (Exception e) {
            log.error("Export von Modul '{}' fehlgeschlagen", m.getModuleId(), e);
            addMessage(FacesMessage.SEVERITY_ERROR, "Export fehlgeschlagen: " + e.getMessage());
        }
    }

    /** Opens the import dialog for a module. */
    public void importVorbereiten(ModuleView m) {
        importModuleId = m.getModuleId();
        importModuleDisplayName = m.getDisplayName();
        importBytes = null;
        importFileName = null;
    }

    /** Accepts an uploaded module export file (not imported yet). */
    public void handleImportUpload(FileUploadEvent event) {
        UploadedFile file = event.getFile();
        if (file == null || file.getContent() == null || file.getContent().length == 0) {
            return;
        }
        importBytes = file.getContent();
        importFileName = file.getFileName();
    }

    /** Imports the uploaded file into the module {@link #importModuleId}. */
    public void importUebernehmen() {
        if (importBytes == null) {
            addMessage(FacesMessage.SEVERITY_ERROR, "Bitte zuerst eine Export-Datei hochladen.");
            return;
        }
        try {
            ModuleDataService.ImportResult result = moduleDataService.importData(importModuleId, importBytes);
            if (result.fehler().isEmpty()) {
                addMessage(FacesMessage.SEVERITY_INFO,
                        result.gespeichert() + " von " + result.gesamt() + " Einträgen importiert.");
            } else {
                addMessage(FacesMessage.SEVERITY_WARN,
                        result.gespeichert() + " von " + result.gesamt() + " Einträgen importiert, Fehler: "
                                + String.join("; ", result.fehler()));
            }
        } catch (Exception e) {
            log.error("Import in Modul '{}' fehlgeschlagen", importModuleId, e);
            addMessage(FacesMessage.SEVERITY_ERROR, "Import fehlgeschlagen: " + e.getMessage());
        } finally {
            importBytes = null;
            importFileName = null;
        }
    }

    /** Opens the danger-zone dialog for a module. */
    public void clearVorbereiten(ModuleView m) {
        clearModuleId = m.getModuleId();
        clearModuleDisplayName = m.getDisplayName();
        clearBestaetigung = null;
    }

    /** Client-side pre-check for the "clear data" button (re-validated on the server side). */
    public boolean isClearBestaetigungOk() {
        return clearModuleDisplayName != null && clearModuleDisplayName.equals(clearBestaetigung);
    }

    /**
     * Clears the data of the module {@link #clearModuleId} — a backup is created automatically
     * beforehand and (on success) offered as a download ({@code p:fileDownload} on {@link #exportFile}).
     */
    public void clearData() {
        try {
            ModuleDangerZoneService.ClearResult result = dangerZoneService.clearData(clearModuleId, clearBestaetigung);
            byte[] bytes = result.exportJson().getBytes(StandardCharsets.UTF_8);
            String zeitstempel = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            exportFile = DefaultStreamedContent.builder()
                    .name("modul_" + clearModuleId + "_backup-vor-leeren_" + zeitstempel + ".json")
                    .contentType("application/json")
                    .contentLength((long) bytes.length)
                    .stream(() -> new ByteArrayInputStream(bytes))
                    .build();
            addMessage(FacesMessage.SEVERITY_WARN,
                    "Daten von Modul '" + clearModuleDisplayName + "' geleert: " + result.geloeschtProEntity()
                            + " — Backup wurde vor dem Leeren automatisch heruntergeladen.");
        } catch (Exception e) {
            log.error("Daten leeren von Modul '{}' fehlgeschlagen", clearModuleId, e);
            addMessage(FacesMessage.SEVERITY_ERROR, "Leeren fehlgeschlagen: " + e.getMessage());
        } finally {
            clearBestaetigung = null;
        }
    }

    /**
     * Schnittstellen nach Suche und Art gefiltert. Bewusst nicht im Feld gehalten: die Bean ist
     * sitzungsgebunden und serialisierbar, der Katalog liegt einmal gelesen im Speicher.
     */
    public List<SchnittstellenKatalog.Schnittstelle> getSchnittstellen() {
        return katalog == null ? List.of()
                : SchnittstellenKatalog.nachEbene(filtere(katalog.suche(schnittstellenSuche), schnittstellenArt), ebene);
    }

    /** Card 1438: the module rows, filtered by {@link #ebene}. */
    public List<ModuleView> getModuleGefiltert() {
        return nachEbene(module, ebene);
    }

    static List<ModuleView> nachEbene(List<ModuleView> l, String wert) {
        return wert == null || wert.isBlank() ? l : l.stream().filter(m -> wert.equals(m.getEbene())).toList();
    }

    /**
     * Card 1438: what the module offers, implements and uses, each list filtered by {@link #ebene}
     * (the level of the interface, not of the module).
     */
    public ModulAnalyse.ModulBild modulBild(ModuleView m) {
        if (analyse == null || katalog == null) {
            return new ModulAnalyse.ModulBild(m.getJar(), List.of(), List.of(), List.of());
        }
        ModulAnalyse.ModulBild b = analyse.bildVon(m.getJar());
        return new ModulAnalyse.ModulBild(b.modul(), passend(b.bietetAn()), passend(b.setztUm()), passend(b.nutzt()));
    }

    private List<String> passend(List<String> namen) {
        return ebene == null || ebene.isBlank() ? namen : namen.stream().filter(n -> ebene.equals(ebeneVon(n))).toList();
    }

    /** @return root or modul of the interface {@code name}, from the module that declares it */
    public String ebeneVon(String name) {
        return katalog == null ? "" : katalog.eine(name).map(SchnittstellenKatalog.Schnittstelle::ebene).orElse("");
    }

    /** @return short name of the interface for lists ({@code ch.plaintext.mail.MailSender} → {@code MailSender}) */
    public String kurzVon(String name) {
        return katalog == null ? name : katalog.eine(name).map(SchnittstellenKatalog.Schnittstelle::kurz).orElse(name);
    }

    public String ebeneText(String wert) {
        return switch (wert == null ? "" : wert) {
            case SchnittstellenKatalog.ROOT -> "root";
            case SchnittstellenKatalog.EBENE_MODUL -> "Modul";
            default -> "";
        };
    }

    /** root in the default tag colour, modules green; PrimeFaces 15 knows success/info/warning/danger only. */
    public String ebeneSchwere(String wert) {
        return SchnittstellenKatalog.EBENE_MODUL.equals(wert) ? SUCCESS : null;
    }

    static List<SchnittstellenKatalog.Schnittstelle> filtere(List<SchnittstellenKatalog.Schnittstelle> l, String art) {
        String a = art == null ? "" : art;
        return switch (a) {
            case "" -> l;
            case "OHNE" -> l.stream().filter(s -> !s.annotiert()).toList();
            default -> l.stream().filter(s -> a.equals(s.art())).toList();
        };
    }

    /** Wer die Schnittstelle in der laufenden Version bezieht (Karte 1422). */
    public List<ModulAnalyse.Nutzung> nutzer(SchnittstellenKatalog.Schnittstelle s) {
        return analyse == null ? List.of() : analyse.nutzer(s.name());
    }

    /** Module, in denen die Schnittstelle umgesetzt ist, oder ein Strich. */
    public String umgesetztIn(SchnittstellenKatalog.Schnittstelle s) {
        String m = String.join(", ", s.umsetzer().stream().map(SchnittstellenKatalog.Umsetzer::modul).distinct().toList());
        return m.isEmpty() ? "—" : m;
    }

    public String artText(SchnittstellenKatalog.Schnittstelle s) {
        return switch (s.art()) {
            case "SCHNITTSTELLE" -> "Dienst";
            case "DTO" -> "DTO";
            case "ERWEITERUNG" -> "Erweiterungspunkt";
            default -> "nicht markiert";
        };
    }

    public String artSchwere(SchnittstellenKatalog.Schnittstelle s) {
        return s.annotiert() ? "info" : SECONDARY;
    }

    public String stabilitaetSchwere(SchnittstellenKatalog.Schnittstelle s) {
        return switch (s.stabilitaet()) {
            case "STABIL" -> SUCCESS;
            case "VERALTET" -> "warning";
            case "NEU" -> "info";
            default -> SECONDARY;
        };
    }

    /** KEINE grün, INTERN blau, AUSSEN rot — Mail, Messenger und Zahlung fallen auf. */
    public String seiteneffekteSchwere(String seiteneffekte) {
        return switch (seiteneffekte == null ? "" : seiteneffekte) {
            case "KEINE" -> SUCCESS;
            case "INTERN" -> "info";
            case "AUSSEN" -> "danger";
            default -> SECONDARY;
        };
    }

    private void addMessage(FacesMessage.Severity severity, String text) {
        FacesMessages.meldung(severity, text, null);
    }
}
