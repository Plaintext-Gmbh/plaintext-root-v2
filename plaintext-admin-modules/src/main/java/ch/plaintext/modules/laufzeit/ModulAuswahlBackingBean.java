/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.modules.laufzeit;

import ch.plaintext.boot.plugins.jsf.FacesMessages;
import ch.plaintext.lader.ModulAuswahl;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Karte 1608: Seite {@code modulauswahl.html}, nur ROOT (Karte 1612). Schalt-Aktionen eines Nicht-ROOT
 * enden mit HTTP 403 (der Dienst prueft zusaetzlich selbst).
 *
 * <p>Feldinjektion mit {@code transient} wie {@code ModulesBackingBean} (Sitzungs-Bean, Karte 1273).</p>
 */
@Slf4j
@Scope("session")
@Component
@Data
@SuppressWarnings("java:S6813") // wie ModulesBackingBean (Karte 1273)
public class ModulAuswahlBackingBean implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Eine Tabellenzeile: Modul, aktiv, gewaehlte Version oder latest. */
    @Data
    public static class Zeile implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private boolean aktiv;
        private String version;
        /** Werte: {@code latest}, {@code <version>} oder {@code <version>@<registry>}, wenn eine Version in mehreren Registries liegt. */
        private List<jakarta.faces.model.SelectItem> optionen = new ArrayList<>();
        private String geladen;
        private String hinweis;
    }

    @Autowired
    private transient ModulAuswahlService service;

    private ModulAuswahlService.Ansicht ansicht;
    private List<Zeile> zeilen = new ArrayList<>();
    /** Karte 2408: Quellen aus der Oberflaeche (ohne Zugang). */
    private List<UiQuellen.Quelle> uiQuellen = new ArrayList<>();
    /** Karte 2408: eingefuegtes JSON mit Passwort; nie in toString, nach jedem Versuch geleert. */
    @lombok.ToString.Exclude
    private String quelleJson;

    /** preRenderView: feuert auch im Postback, dort darf die Eingabe nicht zurueckgesetzt werden. */
    public void load() {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (fc != null && fc.isPostback()) {
            return;
        }
        neuLesen();
    }

    void neuLesen() {
        try {
            ansicht = service.ansicht();
            uiQuellen = new ArrayList<>(service.uiQuellen());
            List<Zeile> neu = new ArrayList<>();
            for (ModulAuswahlService.Modul m : ansicht.module()) {
                Zeile z = new Zeile();
                z.setName(m.name());
                z.setAktiv(m.gewaehlt() != null);
                z.getOptionen().add(new jakarta.faces.model.SelectItem(ModulAuswahl.LATEST, ModulAuswahl.LATEST));
                // ROOT-9/Karte 2456: jede Version einmal. Die Registry nennt der Wert (version@registry) nur, wenn die Kopien
                // einer Version sich unterscheiden (Inhalt, Signatur, Passung); gleiche Kopien laedt der Lader ohne Registry
                // aus der ersten, das Ergebnis ist dasselbe.
                Set<String> verschieden = verschiedeneKopien(m);
                Set<String> schon = new HashSet<>();
                for (ModulAuswahlService.Version v : m.versionen()) {
                    if (verschieden.contains(v.version())) {
                        z.getOptionen().add(new jakarta.faces.model.SelectItem(v.version() + "@" + v.registry(), v.version() + " (" + v.registry() + ")"));
                    } else if (schon.add(v.version())) {
                        z.getOptionen().add(new jakarta.faces.model.SelectItem(v.version(), v.version()));
                    }
                }
                // Eine gespeicherte Registry bei gleichen Kopien zeigt die Seite als die eine Version (sonst stuende sie doppelt).
                String gewaehlt = m.gewaehlt() == null ? ModulAuswahl.LATEST
                        : m.gewaehltRegistry() == null || !verschieden.contains(m.gewaehlt()) && schon.contains(m.gewaehlt())
                        ? m.gewaehlt() : m.gewaehlt() + "@" + m.gewaehltRegistry();
                // Karte 1612: die gespeicherte Version bleibt waehlbar, auch wenn keine Registry sie gerade anbietet
                // (sonst zeigt die Seite die erste Option vorgewaehlt, und "Auswahl speichern" stellte still um).
                if (z.getOptionen().stream().noneMatch(o -> gewaehlt.equals(o.getValue()))) {
                    z.getOptionen().add(new jakarta.faces.model.SelectItem(gewaehlt, gewaehlt.replace("@", " (") + (gewaehlt.contains("@") ? ")" : "")));
                }
                z.setVersion(gewaehlt);
                z.setGeladen(m.geladen());
                z.setHinweis(hinweis(m));
                neu.add(z);
            }
            zeilen = neu;
        } catch (Exception ex) {
            ansicht = null;
            zeilen = new ArrayList<>();
            meldung(FacesMessage.SEVERITY_ERROR, ex.getMessage());
        }
    }

    /** Versionen, deren Kopien in den Registries sich unterscheiden (sha256, Signatur oder Passung). */
    static Set<String> verschiedeneKopien(ModulAuswahlService.Modul m) {
        Map<String, Set<String>> je = new HashMap<>();
        m.versionen().forEach(v -> je.computeIfAbsent(v.version(), x -> new HashSet<>()).add(v.sha256() + "|" + v.signiert() + "|" + v.passt()));
        Set<String> aus = new HashSet<>();
        je.forEach((v, k) -> {
            if (k.size() > 1) {
                aus.add(v);
            }
        });
        return aus;
    }

    /**
     * Karte 2456: kurz statt jede Kopie aufzuzaehlen (die Versionen stehen im Dropdown): Anzahl Versionen und Registries,
     * dahinter nur, was nicht geht (Signatur ungueltig, passt nicht zur Huelle).
     */
    static String hinweis(ModulAuswahlService.Modul m) {
        if (m.versionen().isEmpty()) {
            return "in keiner Registry";
        }
        long versionen = m.versionen().stream().map(ModulAuswahlService.Version::version).distinct().count();
        String registries = String.join(", ", new LinkedHashSet<>(m.versionen().stream().map(ModulAuswahlService.Version::registry).toList()));
        List<String> t = new ArrayList<>();
        t.add(versionen + (versionen == 1 ? " Version" : " Versionen") + " (" + registries + ")");
        for (ModulAuswahlService.Version v : m.versionen()) {
            if (!v.signiert() || v.passt() != null) {
                t.add(v.version() + " (" + v.registry() + "): " + (v.signiert() ? "" : "Signatur ungültig")
                        + (!v.signiert() && v.passt() != null ? ", " : "") + (v.passt() == null ? "" : "passt nicht: " + v.passt()));
            }
        }
        return String.join("; ", t);
    }

    /** {@code <version>@<registry>} aus der Auswahlliste zurueck in eine Wahl mit Registry. */
    static ModulAuswahl.Wahl wahl(String name, String wert) {
        int at = wert.indexOf('@');
        return at < 0 ? new ModulAuswahl.Wahl(name, wert) : new ModulAuswahl.Wahl(name, wert.substring(0, at), wert.substring(at + 1));
    }

    public void speichern() {
        if (verweigert()) {
            return;
        }
        try {
            List<ModulAuswahl.Wahl> w = zeilen.stream().filter(Zeile::isAktiv)
                    .map(z -> wahl(z.getName(), z.getVersion())).toList();
            service.speichern(w);
            meldung(FacesMessage.SEVERITY_INFO, "Auswahl gespeichert (" + w.size() + " Module). Wirksam nach «Neu starten».");
            neuLesen();
        } catch (Exception ex) {
            log.warn("Modul-Auswahl abgewiesen: {}", ex.getMessage());
            meldung(FacesMessage.SEVERITY_ERROR, "Nicht gespeichert: " + ex.getMessage());
        }
    }

    /** Karte 2408: JSON von modules uebernehmen. */
    public void quelleAnlegen() {
        if (verweigert()) {
            return;
        }
        String json = quelleJson;
        quelleJson = null; // das Passwort bleibt nicht in der Sitzung, auch nicht nach einem Fehler
        try {
            UiQuellen.Quelle q = service.quelleAnlegen(json);
            meldung(FacesMessage.SEVERITY_INFO, "Quelle '" + q.name() + "' übernommen, Zugang im Secret-Store. "
                    + "Module wählen, «Auswahl speichern», dann «Neu starten».");
            neuLesen();
        } catch (Exception ex) {
            log.warn("Modul-Quelle abgewiesen: {}", ex.getMessage());
            meldung(FacesMessage.SEVERITY_ERROR, "Quelle nicht übernommen: " + ex.getMessage());
        }
    }

    public void quelleEntfernen(String name) {
        if (verweigert()) {
            return;
        }
        try {
            service.quelleEntfernen(name);
            meldung(FacesMessage.SEVERITY_INFO, "Quelle '" + name + "' entfernt. Wirksam nach «Neu starten».");
            neuLesen();
        } catch (Exception ex) {
            meldung(FacesMessage.SEVERITY_ERROR, "Quelle nicht entfernt: " + ex.getMessage());
        }
    }

    public void neustart() {
        if (verweigert()) {
            return;
        }
        try {
            service.neustart();
            meldung(FacesMessage.SEVERITY_WARN, "Neustart läuft. Die Instanz ist gleich für etwa eine Minute weg.");
        } catch (Exception ex) {
            meldung(FacesMessage.SEVERITY_ERROR, "Neustart nicht möglich: " + ex.getMessage());
        }
    }

    /** Nicht-ROOT: HTTP 403, nichts geschaltet. */
    boolean verweigert() {
        if (service.darfSchalten()) {
            return false;
        }
        log.warn("Laufzeit-Module: Schalten ohne ROOT abgewiesen (403)");
        FacesContext fc = FacesContext.getCurrentInstance();
        if (fc != null) {
            try {
                fc.getExternalContext().responseSendError(403, "Laufzeit-Module schalten: nur ROOT");
            } catch (IOException ex) {
                log.warn("403 nicht gesendet: {}", ex.getMessage());
            }
            fc.responseComplete();
        }
        return true;
    }

    private void meldung(FacesMessage.Severity s, String text) {
        if (FacesContext.getCurrentInstance() != null) {
            FacesMessages.meldung(s, text, null);
        }
    }
}
