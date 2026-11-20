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
import java.util.List;
import java.util.Map;

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
            List<Zeile> neu = new ArrayList<>();
            for (ModulAuswahlService.Modul m : ansicht.module()) {
                Zeile z = new Zeile();
                z.setName(m.name());
                z.setAktiv(m.gewaehlt() != null);
                z.getOptionen().add(new jakarta.faces.model.SelectItem(ModulAuswahl.LATEST, ModulAuswahl.LATEST));
                // ROOT-9: liegt eine Version in mehreren Registries, nennt der Wert die Registry (version@registry)
                Map<String, Long> jeVersion = m.versionen().stream()
                        .collect(java.util.stream.Collectors.groupingBy(ModulAuswahlService.Version::version, java.util.stream.Collectors.counting()));
                m.versionen().forEach(v -> z.getOptionen().add(jeVersion.get(v.version()) > 1
                        ? new jakarta.faces.model.SelectItem(v.version() + "@" + v.registry(), v.version() + " (" + v.registry() + ")")
                        : new jakarta.faces.model.SelectItem(v.version(), v.version())));
                String gewaehlt = m.gewaehlt() == null ? ModulAuswahl.LATEST
                        : m.gewaehltRegistry() == null ? m.gewaehlt() : m.gewaehlt() + "@" + m.gewaehltRegistry();
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

    private static String hinweis(ModulAuswahlService.Modul m) {
        List<String> t = new ArrayList<>();
        for (ModulAuswahlService.Version v : m.versionen()) {
            String s = v.version() + " (" + v.registry() + (v.signiert() ? "" : ", Signatur ungültig")
                    + (v.passt() == null ? "" : ", passt nicht: " + v.passt()) + ")";
            t.add(s);
        }
        return t.isEmpty() ? "in keiner Registry" : String.join("; ", t);
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
