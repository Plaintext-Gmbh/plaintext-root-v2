/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files;

import ch.plaintext.files.config.FilesProperties;
import ch.plaintext.files.service.Datei;
import ch.plaintext.files.service.FilesService;
import ch.plaintext.files.speicher.DateisystemDateiSpeicher;
import ch.plaintext.files.web.FilesBean;
import ch.plaintext.linktoken.LinkToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Karte 2354 an der Seite: Admin/Root sehen alle Dateien mit Besitzer, teilen aber nur eigene; der Haken im Dialog
 * macht aus dem Link einen Anzeige-Link. Ohne FacesContext - die Meldungen sind dort stumm.
 */
class FilesBeanTest {

    @TempDir
    Path wurzel;
    private final SpeicherLinkTokenStore links = new SpeicherLinkTokenStore();
    private FilesService service;
    private FilesBean bean;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        FilesProperties p = new FilesProperties();
        p.setLinkBasis("https://app.example.ch");
        service = new FilesService(new DateisystemDateiSpeicher(wurzel), links, p, mock(ObjectProvider.class));
        bean = new FilesBean();
        ReflectionTestUtils.setField(bean, "filesService", service);
        ReflectionTestUtils.setField(bean, "zugriff", Anmeldung.zugriffOhneSeitenschutz());
        service.hochladen("anna", "a.txt", "x".getBytes());
        service.hochladen("bert", "b.txt", "y".getBytes());
    }

    @AfterEach
    void aufraeumen() {
        Anmeldung.ab();
    }

    @Test
    void benutzerSiehtNurSeineDateienUndLoeschtKeineFremden() {
        Anmeldung.als("anna", "ROLE_FILES");

        assertThat(bean.isSiehtAlle()).isFalse();
        assertThat(bean.isAlleSichtbar()).isFalse();
        assertThat(bean.getDateien()).extracting(Datei::name).containsExactly("a.txt");
        assertThat(bean.eigene(bean.getDateien().get(0))).isTrue();

        bean.loeschen("bert", "b.txt");
        assertThat(wurzel.resolve("files/bert/b.txt")).as("fremde Datei bleibt").exists();
        bean.loeschen("anna", "a.txt");
        assertThat(bean.getDateien()).isEmpty();
    }

    @Test
    void adminSiehtAlleUndSchaltetAufNurEigene() {
        service.hochladen("chef", "c.txt", "z".getBytes());
        Anmeldung.als("chef", "ROLE_ADMIN");

        assertThat(bean.isSiehtAlle()).isTrue();
        assertThat(bean.getDateien()).extracting(d -> d.besitzer() + "/" + d.name())
                .containsExactlyInAnyOrder("anna/a.txt", "bert/b.txt", "chef/c.txt");
        Datei fremd = bean.getDateien().stream().filter(d -> d.besitzer().equals("anna")).findFirst().orElseThrow();
        assertThat(bean.eigene(fremd)).as("fremde Datei: kein «Link teilen»").isFalse();

        bean.setNurEigene(true);
        assertThat(bean.isAlleSichtbar()).isFalse();
        assertThat(bean.getDateien()).extracting(Datei::name).containsExactly("c.txt");

        bean.loeschen("bert", "b.txt");
        assertThat(wurzel.resolve("files/bert/b.txt")).as("Admin räumt auf").doesNotExist();
    }

    @Test
    void ohneRolleBleibtDieSeiteLeer() {
        Anmeldung.als("carl", "ROLE_USER");

        assertThat(bean.getDateien()).isEmpty();
        assertThat(bean.getLinks()).isEmpty();
        bean.loeschen("anna", "a.txt");
        assertThat(wurzel.resolve("files/anna/a.txt")).exists();
    }

    @Test
    void hakenImDialogMachtEinenAnzeigeLink() {
        Anmeldung.als("anna", "ROLE_FILES");
        bean.dialogOeffnen("a.txt");
        assertThat(bean.isLinkImBrowser()).as("Vorgabe: Download wie bisher").isFalse();

        bean.linkErzeugen();
        bean.setLinkImBrowser(true);
        bean.linkErzeugen();

        assertThat(bean.getNeueUrl()).startsWith("https://app.example.ch/nosec/files/");
        assertThat(bean.getLinks()).extracting(bean::oeffnet).containsExactlyInAnyOrder("Download", "im Browser");
        assertThat(links.zeilen).extracting(LinkToken::art).containsExactly("files", "files-inline");

        bean.dialogOeffnen("a.txt");
        assertThat(bean.isLinkImBrowser()).as("der Haken gilt nicht für die nächste Datei mit").isFalse();
        for (LinkToken l : bean.getLinks()) {
            bean.linkZurueckziehen(l.id());
        }
        assertThat(links.zeilen).isEmpty();
    }
}
