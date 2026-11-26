/*
 * Copyright (C) plaintext.ch, 2026.
 */
package ch.plaintext.lader;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Karte 2406: {@code quellen=} im Index — das zugehoerige {@code -sources.jar} ist optional je Modul
 * vorhanden, der sha256 gehoert zum signierten Inhalt.
 *
 * <p>Rueckwaertskompatibilitaet ist die zentrale Regel: Zeilen OHNE {@code quellen} muessen
 * byte-identisch zum Bestand bleiben, sonst verweigern bestehende Loader die Signaturpruefung.
 */
class ModulAuswahlQuellenTest {

    private static final String INDEX = "http://registry.example/reg/index.txt";
    private static final String SHA = "0".repeat(64);
    private static final String QUELLEN_SHA = "1".repeat(64);

    private KeyPair schluessel() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private String signieren(ModulAuswahl.Zeile z, KeyPair k) throws Exception {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(k.getPrivate());
        s.update(z.nachricht().getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(s.sign());
    }

    private ModulAuswahl.Zeile zeile(String quellen, String signatur) {
        return new ModulAuswahl.Zeile("modul", "modul-x", "1.0.0", "3.0.0", SHA, quellen,
                List.of(), List.of(), "maven/modul-x-1.0.0.jar", signatur, "test", URI.create(INDEX));
    }

    @Test
    void zeileMitQuellenRoundtripUeberDenIndex() throws Exception {
        KeyPair k = schluessel();
        ModulAuswahl.Zeile z = zeile(QUELLEN_SHA, signieren(zeile(QUELLEN_SHA, "x"), k));

        String text = ModulAuswahl.indexText(List.of(z), "Test");

        ModulAuswahl.Zeile gelesen = ModulAuswahl.indexParsen(text, "test", URI.create(INDEX)).get(0);
        assertThat(gelesen.quellen()).isEqualTo(QUELLEN_SHA);
        assertThat(ModulAuswahl.signaturGueltig(gelesen, List.of(k.getPublic()))).isTrue();
    }

    @Test
    void quellenGehoertZurSignatur_manipulationFaelltAuf() throws Exception {
        KeyPair k = schluessel();
        ModulAuswahl.Zeile original = zeile(QUELLEN_SHA, signieren(zeile(QUELLEN_SHA, "x"), k));
        ModulAuswahl.Zeile manipuliert = zeile("2".repeat(64), original.signatur());

        assertThat(ModulAuswahl.signaturGueltig(manipuliert, List.of(k.getPublic())))
                .as("Quellen-Hash im Index getauscht — Signatur muss verweigern")
                .isFalse();
    }

    @Test
    void zeileOhneQuellenBleibtByteIdentischZumBestand() {
        ModulAuswahl.Zeile alt = zeile(null, "sig");
        assertThat(alt.nachricht())
                .isEqualTo("plaintext-modul-signatur-1\nmodul\nmodul-x\n1.0.0\n3.0.0\n" + SHA + "\n\n\n");
        assertThat(alt.alsIndexZeile()).doesNotContain("quellen=");
    }

    @Test
    void indexMitQuellenBleibtFuerAlteZeilenLesbar() throws Exception {
        KeyPair k = schluessel();
        ModulAuswahl.Zeile alt = zeile(null, signieren(zeile(null, "x"), k));
        ModulAuswahl.Zeile neu = new ModulAuswahl.Zeile("modul", "modul-y", "2.0.0", "3.0.0", SHA, QUELLEN_SHA,
                List.of(), List.of(), "maven/modul-y-2.0.0.jar",
                signieren(new ModulAuswahl.Zeile("modul", "modul-y", "2.0.0", "3.0.0", SHA, QUELLEN_SHA,
                        List.of(), List.of(), "maven/modul-y-2.0.0.jar", "x", "test", URI.create(INDEX)), k),
                "test", URI.create(INDEX));

        List<ModulAuswahl.Zeile> gelesen = ModulAuswahl.indexParsen(
                ModulAuswahl.indexText(List.of(alt, neu), "Test"), "test", URI.create(INDEX));

        assertThat(gelesen.get(0).quellen()).isNull();
        assertThat(gelesen.get(1).quellen()).isEqualTo(QUELLEN_SHA);
        assertThat(ModulAuswahl.signaturGueltig(gelesen.get(0), List.of(k.getPublic()))).isTrue();
        assertThat(ModulAuswahl.signaturGueltig(gelesen.get(1), List.of(k.getPublic()))).isTrue();
    }

    @Test
    void ungueltigerQuellenHashWirdBeimParsenAbgelehnt() {
        ModulAuswahl.Zeile kaputt = zeile("kein-hash", "x");
        assertThatThrownBy(() -> ModulAuswahl.indexParsen(
                ModulAuswahl.indexText(List.of(kaputt), "Test"), "test", URI.create(INDEX)))
                .hasMessageContaining("quellen");
    }
}
