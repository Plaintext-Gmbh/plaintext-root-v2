/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files;

import ch.plaintext.files.config.FilesProperties;
import ch.plaintext.bus.ExecutionScope;
import ch.plaintext.files.service.Datei;
import ch.plaintext.files.service.FilesAufraeumen;
import ch.plaintext.files.service.FilesService;
import ch.plaintext.files.speicher.DateisystemDateiSpeicher;
import ch.plaintext.framework.EigeneAdresse;
import ch.plaintext.linktoken.LinkToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Persönliche Ablage (Karte 1559): jeder nur im eigenen Ordner, Links öffnen genau eine Datei und sterben mit ihr.
 * Portiert aus dem Inventar ({@code FilesServiceTest}); statt des Code-Service-Mocks der echte Dateisystem-Speicher
 * in einem Temp-Verzeichnis, statt der cie-meta-Liste ein LinkTokenStore im Speicher.
 */
class FilesServiceTest {

    @TempDir
    Path wurzel;
    private final SpeicherLinkTokenStore links = new SpeicherLinkTokenStore();
    private DateisystemDateiSpeicher speicher;
    private FilesService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        FilesProperties p = new FilesProperties();
        p.setLinkBasis("https://app.example.ch/");
        speicher = new DateisystemDateiSpeicher(wurzel);
        service = new FilesService(speicher, links, p, mock(ObjectProvider.class));
    }

    @Test
    void hochladenLandetImEigenenOrdnerUndLaeuftNach3WochenAb() {
        String name = service.hochladen("Anna.Muster@example.ch", "C:\\temp\\bericht.pdf", new byte[]{1, 2, 3});

        assertThat(name).isEqualTo("bericht.pdf");
        assertThat(wurzel.resolve("files/anna.muster@example.ch/bericht.pdf")).exists();
        Datei d = service.liste("anna.muster@example.ch").get(0);
        assertThat(d.groesse()).isEqualTo(3);
        assertThat(Duration.between(d.geaendert(), d.ablauf())).isEqualTo(Duration.ofDays(21));
        assertThat(service.liste("andere")).isEmpty();
    }

    @Test
    void keinPfadAusbruch() throws Exception {
        assertThat(service.hochladen("../root", "../../backup.zip", new byte[]{1})).isEqualTo("backup.zip");
        assertThat(wurzel.resolve("files/.._root/backup.zip")).exists();
        assertThatThrownBy(() -> service.hochladen("anna", "..", new byte[]{1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.hochladen(" ", "x", new byte[]{1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.hochladen("anna", "x.upload-1", new byte[]{1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> speicher.upload("../ausserhalb", new byte[]{1})).isInstanceOf(IllegalArgumentException.class);
        try (var s = Files.list(wurzel)) {
            assertThat(s.map(x -> x.getFileName().toString()).toList()).containsExactly("files");
        }
    }

    @Test
    void linkOeffnetDieDateiUndStirbtMitIhr() {
        service.hochladen("anna", "a.txt", "hallo".getBytes());
        LinkToken l = service.linkErstellen("anna", "a.txt");

        assertThat(service.url(l.token())).isEqualTo("https://app.example.ch/nosec/files/" + l.token());
        assertThat(service.finde(l.token())).isPresent();
        assertThat(service.inhalt(service.finde(l.token()).get(), l.token())).isEqualTo("hallo".getBytes());
        assertThat(links.zeilen.get(0).aufrufe()).isEqualTo(1);
        assertThat(service.linkWiderrufen("bert", l.id())).as("fremder Link").isFalse();

        service.loeschen("anna", "a.txt");
        assertThat(service.finde(l.token())).isEmpty();
        assertThat(links.zeilen).isEmpty();
        assertThat(service.finde("../etc/passwd")).isEmpty();
    }

    @Test
    void linkMitGueltigkeitUndZweckWieDrawio() {
        service.hochladen("anna", "a.txt", "x".getBytes());
        LinkToken l = service.linkErstellen("anna", "a.txt", 7, " Workshop ");

        assertThat(l.zweck()).isEqualTo("Workshop");
        assertThat(l.gueltigBis()).isEqualTo(LocalDate.now().plusDays(7));
        assertThat(service.finde(l.token())).isPresent();

        links.setzeBis(0, LocalDate.now().minusDays(1));
        assertThat(service.finde(l.token())).as("abgelaufen öffnet nicht").isEmpty();
        assertThat(service.linkErstellen("anna", "a.txt").gueltigBis()).as("0 = mit der Datei").isNull();
        assertThatThrownBy(() -> service.linkErstellen("anna", "a.txt", 4000, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.linkErstellen("anna", "fehlt.txt", 1, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void abgelaufeneDateiIstWegSamtLinkUndAufraeumenLoescht() throws Exception {
        service.hochladen("anna", "alt.txt", "x".getBytes());
        service.hochladen("bert", "auch-alt.txt", "y".getBytes());
        service.hochladen("bert", "neu.txt", "z".getBytes());
        LinkToken l = service.linkErstellen("anna", "alt.txt");
        FileTime vor22Tagen = FileTime.from(Instant.now().minus(Duration.ofDays(22)));
        Files.setLastModifiedTime(wurzel.resolve("files/anna/alt.txt"), vor22Tagen);
        Files.setLastModifiedTime(wurzel.resolve("files/bert/auch-alt.txt"), vor22Tagen);

        assertThat(service.finde(l.token())).isEmpty();
        assertThat(service.herunterladen("anna", "alt.txt")).isNull();
        assertThat(links.zeilen).isEmpty();
        assertThat(service.aufraeumen()).isEqualTo(1);
        assertThat(wurzel.resolve("files/bert/auch-alt.txt")).doesNotExist();
        assertThat(wurzel.resolve("files/bert/neu.txt")).exists();
    }

    @Test
    void alleZeigtJedeAblageMitBesitzer() throws Exception {
        service.hochladen("Anna.Muster@example.ch", "a.txt", "x".getBytes());
        service.hochladen("bert", "b.txt", "y".getBytes());
        service.hochladen("bert", "alt.txt", "z".getBytes());
        Files.setLastModifiedTime(wurzel.resolve("files/bert/alt.txt"), FileTime.from(Instant.now().minus(Duration.ofDays(22))));
        // Von Hand angelegt, kein Ordnername: jede andere Methode käme mit diesem Namen in den Ordner "fremd".
        Files.createDirectories(wurzel.resolve("files/Fremd"));
        Files.writeString(wurzel.resolve("files/Fremd/x.txt"), "x");
        Files.writeString(wurzel.resolve("files/lose.txt"), "keinem Benutzer zugeordnet");

        assertThat(service.alle()).extracting(d -> d.besitzer() + "/" + d.name())
                .containsExactlyInAnyOrder("anna.muster@example.ch/a.txt", "bert/b.txt");
        assertThat(wurzel.resolve("files/bert/alt.txt")).as("abgelaufen: weg wie in liste()").doesNotExist();
        assertThat(service.liste("bert")).extracting(Datei::besitzer).containsExactly("bert");
        // Mit dem Besitzer aus der Liste kommt ein Admin an die Datei.
        assertThat(service.herunterladen("anna.muster@example.ch", "a.txt")).isEqualTo("x".getBytes());
    }

    @Test
    void anzeigeLinkIstEineEigeneArtUndVerhaeltSichSonstGleich() {
        service.hochladen("anna", "bild.png", "x".getBytes());
        LinkToken download = service.linkErstellen("anna", "bild.png", 7, "zum Laden");
        LinkToken anzeige = service.linkErstellen("anna", "bild.png", 0, "zum Zeigen", true);

        assertThat(download.art()).isEqualTo("files");
        assertThat(anzeige.art()).isEqualTo("files-inline");
        assertThat(FilesService.imBrowser(download)).isFalse();
        assertThat(FilesService.imBrowser(anzeige)).isTrue();
        assertThat(service.finde(anzeige.token()).map(LinkToken::art)).contains("files-inline");
        assertThat(service.finde(download.token()).map(LinkToken::art)).contains("files");
        assertThat(service.url(anzeige.token())).as("dieselbe Adresse, die Wahl steckt im Link")
                .isEqualTo("https://app.example.ch/nosec/files/" + anzeige.token());
        assertThat(service.links("anna")).hasSize(2);
        assertThat(service.links("anna", "bild.png")).extracting(LinkToken::zweck)
                .containsExactlyInAnyOrder("zum Laden", "zum Zeigen");
        assertThat(service.links("bert")).isEmpty();

        assertThat(service.linkWiderrufen("bert", anzeige.id())).as("fremder Link").isFalse();
        assertThat(service.linkWiderrufen("anna", anzeige.id())).isTrue();
        assertThat(service.finde(anzeige.token())).isEmpty();
        assertThat(service.finde(download.token())).as("der andere Link lebt weiter").isPresent();
    }

    @Test
    void loeschenNimmtLinksBeiderArtenMit() {
        service.hochladen("anna", "bild.png", "x".getBytes());
        LinkToken download = service.linkErstellen("anna", "bild.png");
        LinkToken anzeige = service.linkErstellen("anna", "bild.png", 0, null, true);

        service.loeschen("anna", "bild.png");

        assertThat(links.zeilen).isEmpty();
        // Sonst öffnete ein alter Link später eine neue Datei, die zufällig gleich heisst.
        service.hochladen("anna", "bild.png", "neu".getBytes());
        assertThat(service.finde(download.token())).isEmpty();
        assertThat(service.finde(anzeige.token())).isEmpty();
    }

    @Test
    void aufraeumCronIstPrototypeUndLoeschtAbgelaufene() throws Exception {
        // Ohne prototype bricht plaintext-admin-cron den Start der Anwendung ab (CronBeanPostProcessor).
        assertThat(FilesAufraeumen.class.getAnnotation(Scope.class)).as("jeder PlaintextCron ist prototype").isNotNull();
        assertThat(FilesAufraeumen.class.getAnnotation(Scope.class).value()).isEqualTo("prototype");

        service.hochladen("anna", "alt.txt", "x".getBytes());
        Files.setLastModifiedTime(wurzel.resolve("files/anna/alt.txt"), FileTime.from(Instant.now().minus(Duration.ofDays(22))));
        FilesAufraeumen cron = new FilesAufraeumen(service);
        cron.run("global");

        assertThat(wurzel.resolve("files/anna/alt.txt")).doesNotExist();
        assertThat(cron.getScope()).isEqualTo(ExecutionScope.APPLICATION);
        assertThat(cron.getDisplayName()).isNotBlank();
        assertThat(cron.getDefaultCronExpression()).isEqualTo("30 3 * * *");
    }

    @Test
    void speicherDerSelbstAufraeumtWirdNichtVorgegriffen() throws Exception {
        DateiSpeicher selbst = new DateisystemDateiSpeicher(wurzel) {
            @Override
            public boolean raeumtSelbstAuf() {
                return true;
            }
        };
        @SuppressWarnings("unchecked")
        ObjectProvider<EigeneAdresse> ohne = mock(ObjectProvider.class);
        FilesService s = new FilesService(selbst, links, new FilesProperties(), ohne);
        s.hochladen("anna", "alt.txt", "x".getBytes());
        Files.setLastModifiedTime(wurzel.resolve("files/anna/alt.txt"), FileTime.from(Instant.now().minus(Duration.ofDays(30))));

        assertThat(s.liste("anna")).hasSize(1);
        assertThat(s.aufraeumen()).isZero();
        assertThat(s.url("t")).isEqualTo("https://app.plaintext.ch/nosec/files/t");
    }
}
