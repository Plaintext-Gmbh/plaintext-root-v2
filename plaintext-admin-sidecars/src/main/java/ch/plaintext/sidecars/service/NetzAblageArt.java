/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.sidecars.service;

import ch.plaintext.ablagen.DateiAblage;
import ch.plaintext.ablagen.DateiAblageArt;
import ch.plaintext.boot.plugins.netz.AusgehendesZiel;
import ch.plaintext.modules.ModulApiUmsetzung;
import ch.plaintext.secrets.SecretResolver;
import ch.plaintext.sidecars.ablage.GitAblage;
import ch.plaintext.sidecars.ablage.NextcloudAblage;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Karte 2425: die Arten {@code nextcloud} und {@code git} für Ablagen aus der {@code application.yml}
 * ({@code plaintext.speicher.<name>}). Dieselben Klassen wie unter Root &gt; Sidecars; das Passwort bzw. Token kommt
 * nie aus der Konfiguration, sondern über {@code passwort-secret} (Name im Secret-Store).
 *
 * <p>Einstellungen: {@code url}, {@code benutzer}, {@code pfad}, {@code passwort-secret}; bei git zusätzlich
 * {@code zweig}. Die Adresse geht bei jedem Öffnen durch {@link AusgehendesZiel}, wie unter Sidecars.</p>
 */
@ModulApiUmsetzung(beschreibung = "Opens a Nextcloud folder or a Git repository declared in the application.yml; the password "
        + "comes from the secret store by reference.",
        seiteneffekte = ModulApiUmsetzung.Seiteneffekte.AUSSEN,
        hinweise = {"Settings: url, benutzer, pfad, passwort-secret (name in the secret store); git also zweig",
                "The address must be public or allowed in plaintext.ausgehend.erlaubte-hosts",
                "A missing secret makes opening fail; the value never appears in a message"},
        beispiele = {"plaintext.speicher.ablage with art nextcloud, url https://cloud.example.ch, benutzer app, pfad /Apps "
                + "and the name of the password in the secret store"})
public class NetzAblageArt implements DateiAblageArt {

    public static final String NEXTCLOUD = "nextcloud";
    public static final String GIT = "git";

    private final String art;
    private final ObjectProvider<SecretResolver> secrets;
    private final Set<String> erlaubteHosts;
    private final HttpClient http;
    private final Path gitKlone;

    NetzAblageArt(String art, ObjectProvider<SecretResolver> secrets, String erlaubteHosts, HttpClient http, Path gitKlone) {
        this.art = art;
        this.secrets = secrets;
        this.erlaubteHosts = AusgehendesZiel.allowlist(erlaubteHosts);
        this.http = http;
        this.gitKlone = gitKlone;
    }

    @Override
    public String art() {
        return art;
    }

    @Override
    public DateiAblage oeffne(String name, Map<String, String> konfig) throws IOException {
        String url = pflicht(konfig, "url");
        try {
            AusgehendesZiel.pruefeUrl(url, erlaubteHosts);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage());
        }
        String benutzer = konfig.get("benutzer");
        String pfad = konfig.getOrDefault("pfad", "");
        String pw = geheimnis(konfig.get("passwort-secret"));
        if (GIT.equals(art)) {
            String zweig = konfig.getOrDefault("zweig", "main");
            return new GitAblage(name, url, zweig, pfad, benutzer, pw,
                    gitKlone.resolve(name + "-" + SpeicherAblageService.kennung(url + "\n" + zweig)), SpeicherAblageService::angemeldet);
        }
        return new NextcloudAblage(name, url, benutzer, pw, pfad, http);
    }

    private String geheimnis(String referenz) throws IOException {
        if (referenz == null || referenz.isBlank()) {
            return null;
        }
        SecretResolver r = secrets.getIfAvailable();
        if (r == null) {
            throw new IOException("passwort-secret gesetzt, aber kein Secret-Store in der Anwendung");
        }
        return r.resolve(referenz.strip()).orElseThrow(() -> new IOException("Secret «" + referenz.strip() + "» fehlt im Secret-Store"));
    }

    private static String pflicht(Map<String, String> konfig, String schluessel) throws IOException {
        String w = konfig.get(schluessel);
        if (w == null || w.isBlank()) {
            throw new IOException("Einstellung «" + schluessel + "» fehlt");
        }
        return w.strip();
    }
}
