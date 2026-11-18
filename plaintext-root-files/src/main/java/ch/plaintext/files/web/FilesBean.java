/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files.web;

import ch.plaintext.boot.plugins.jsf.FacesMessages;
import ch.plaintext.files.service.Datei;
import ch.plaintext.files.service.FilesService;
import ch.plaintext.linktoken.LinkToken;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;

/**
 * Seite {@code files/ablage.html} (Karte 1559): Hochladen, Liste, Löschen und der Dialog «Link teilen» - dasselbe
 * Formular wie im Inventar ({@code FilesLinkBean}): Gültigkeit, Zweck, erzeugen und kopieren, bestehende Links mit
 * Aufrufen. Nur Lesen - Files haben keinen Editor.
 *
 * <p><b>Admin und Root (Karte 2354)</b> sehen die Dateien aller Benutzer, mit Besitzer; der Schalter «Nur meine»
 * blendet die fremden aus. Fremde Dateien lassen sich herunterladen und löschen, aber nicht teilen: ein Link macht
 * eine Datei öffentlich, und das entscheidet, wem sie gehört.</p>
 *
 * <p>Abweichung vom Inventar: die Seite ist eine PrimeFaces-Tabelle statt des Ablage-Widgets {@code ablage.js}
 * (das liegt in den Anwendungen, nicht in root); die REST-Schnittstelle dafür bietet {@link FilesApiController}.</p>
 */
@Slf4j
@Component("filesBean")
@Scope("session")
@SuppressWarnings("java:S6813") // Feldinjektion mit transient: Session-Bean muss serialisierbar bleiben
public class FilesBean implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Vorgabe: so lange, wie die Datei höchstens lebt. */
    static final int STANDARD_TAGE = 21;
    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(FilesService.ZONE);

    @Autowired
    private transient FilesService filesService;
    @Autowired
    private transient FilesZugriff zugriff;

    @Getter
    private String datei;
    @Getter
    @Setter
    private int linkTage = STANDARD_TAGE;
    @Getter
    @Setter
    private String linkZweck;
    @Getter
    private String neueUrl;
    /** «Direkt im Browser anzeigen statt Download» im Dialog; Vorgabe Download wie bisher. */
    @Getter
    @Setter
    private boolean linkImBrowser;
    /** Admin/Root: nur die eigenen Dateien zeigen statt aller. */
    @Getter
    @Setter
    private boolean nurEigene;

    public List<Datei> getDateien() {
        String b = zugriff.benutzer();
        if (b == null) {
            return List.of();
        }
        try {
            return isAlleSichtbar() ? filesService.alle() : filesService.liste(b);
        } catch (RuntimeException e) {
            FacesMessages.error(FacesMessages.TITEL_FEHLER, e.getMessage());
            return List.of();
        }
    }

    public void hochladen(FileUploadEvent event) {
        UploadedFile f = event.getFile();
        String b = zugriff.benutzer();
        if (b == null || f == null) {
            FacesMessages.error("Kein Zugriff", "Bitte anmelden.");
            return;
        }
        try {
            String name = filesService.hochladen(b, f.getFileName(), f.getContent());
            FacesMessages.info("Hochgeladen", name + " ist abgelegt und läuft in " + filesService.frist().toDays() + " Tagen ab.");
        } catch (RuntimeException e) {
            FacesMessages.error("Nicht hochgeladen", e.getMessage());
        }
    }

    /** Darf der Benutzer die Ablagen aller sehen (Admin, Root)? Steuert den Schalter «Nur meine». */
    public boolean isSiehtAlle() {
        return zugriff.siehtAlle();
    }

    /** Zeigt die Liste gerade die Dateien aller Benutzer? Steuert die Spalte «Besitzer». */
    public boolean isAlleSichtbar() {
        return !nurEigene && zugriff.siehtAlle();
    }

    /** Gehört die Datei dem Angemeldeten? Nur dann gibt es «Link teilen». */
    public boolean eigene(Datei d) {
        return d != null && zugriff.eigener(d.besitzer());
    }

    /**
     * Löscht eine Datei - die eigene, oder als Admin/Root eine fremde.
     *
     * @param besitzer Ordner aus {@link Datei#besitzer()}; die Seite schickt ihn mit, geprüft wird er hier
     */
    public void loeschen(String besitzer, String name) {
        String ordner = zugriff.ordner(besitzer);
        if (ordner == null) {
            FacesMessages.error("Kein Zugriff", "Diese Datei gehört jemand anderem.");
            return;
        }
        try {
            if (!zugriff.eigener(ordner)) {
                // Wie an der REST-Schnittstelle: der Besitzer sieht sonst nirgends, wer seine Datei gelöscht hat.
                log.info("[FilesBean] löscht fremde Datei | wer={} | besitzer={} | datei={}", zugriff.benutzer(), ordner, name);
            }
            filesService.loeschen(ordner, name);
            FacesMessages.info("Gelöscht", name + " und ihre Links sind weg.");
        } catch (RuntimeException e) {
            FacesMessages.error(FacesMessages.TITEL_FEHLER, e.getMessage());
        }
    }

    /** Öffnet den Dialog für eine Datei. */
    public void dialogOeffnen(String name) {
        datei = name;
        linkZweck = null;
        linkTage = STANDARD_TAGE;
        linkImBrowser = false;
        neueUrl = null;
    }

    public List<LinkToken> getLinks() {
        String b = zugriff.benutzer();
        if (b == null || datei == null || datei.isBlank()) {
            return List.of();
        }
        try {
            return filesService.links(b, datei);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** @return die Adresse, wenn der Store das Token kennt; sonst {@code null} (nur beim Anlegen sichtbar) */
    public String url(LinkToken l) {
        return filesService.url(l.token());
    }

    /** Für die Linkliste: was der Link tut, wenn ihn jemand öffnet. */
    public String oeffnet(LinkToken l) {
        return FilesService.imBrowser(l) ? "im Browser" : "Download";
    }

    public long getFristTage() {
        return filesService.frist().toDays();
    }

    public String zeit(TemporalAccessor t) {
        return t == null ? "" : ZEIT.format(t);
    }

    public String groesse(long bytes) {
        return FilesApiController.groesse(bytes);
    }

    public void linkErzeugen() {
        try {
            neueUrl = filesService.url(
                    filesService.linkErstellen(zugriff.benutzer(), datei, linkTage, linkZweck, linkImBrowser).token());
            FacesMessages.info("Link angelegt", "Die Adresse wird nur jetzt angezeigt.");
        } catch (RuntimeException e) {
            FacesMessages.error("Kein Link erzeugt", e.getMessage());
        }
    }

    public void linkZurueckziehen(String id) {
        if (!filesService.linkWiderrufen(zugriff.benutzer(), id)) {
            FacesMessages.warn("Nicht gefunden", "Diesen Link gibt es nicht mehr.");
        }
    }
}
