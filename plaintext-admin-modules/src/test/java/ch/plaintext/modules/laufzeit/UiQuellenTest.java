/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.modules.laufzeit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Karte 2408: JSON plaintext-modul-quelle/1 streng, quellen-ui.txt ohne Zugang. */
class UiQuellenTest {

    private static String json(String extra, String format, String index) throws Exception {
        String k = Base64.getEncoder().encodeToString(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
        return "{\"format\":\"" + format + "\",\"name\":\"kunde\",\"basis\":\"https://modules.plaintext.ch\",\"index\":\"" + index
                + "\",\"schluessel\":\"ed25519 " + k + "\",\"benutzer\":\"k-1\",\"passwort\":\"geheim-pw\"" + extra + "}";
    }

    @Test
    void gueltigesJson() throws Exception {
        UiQuellen.Eingabe e = UiQuellen.lesen(json("", UiQuellen.FORMAT, "https://modules.plaintext.ch/nosec/k/index.txt"));
        assertThat(e.name()).isEqualTo("kunde");
        assertThat(e.benutzer()).isEqualTo("k-1");
        assertThat(e.toString()).doesNotContain("geheim-pw");
    }

    @Test
    void strengeRegelnOhneDasPasswortInDerMeldung() throws Exception {
        String https = "https://modules.plaintext.ch/nosec/k/index.txt";
        assertThatThrownBy(() -> UiQuellen.lesen(json(",\"neu\":1", UiQuellen.FORMAT, https)))
                .hasMessageContaining("unbekanntem Feld").hasMessageNotContaining("geheim-pw");
        assertThatThrownBy(() -> UiQuellen.lesen(json("", "plaintext-modul-quelle/2", https))).hasMessageContaining("nicht unterstuetzt");
        assertThatThrownBy(() -> UiQuellen.lesen(json("", UiQuellen.FORMAT, "http://modules.plaintext.ch/x"))).hasMessageContaining("https");
        assertThatThrownBy(() -> UiQuellen.lesen(json("", UiQuellen.FORMAT, "https://u:geheim-pw@modules.plaintext.ch/x")))
                .hasMessageContaining("ohne Zugang").hasMessageNotContaining("geheim-pw");
        assertThatThrownBy(() -> UiQuellen.lesen(json("", UiQuellen.FORMAT, https).replace("\"kunde\"", "\"../x\"")))
                .hasMessageContaining("name");
        assertThatThrownBy(() -> UiQuellen.lesen(json("", UiQuellen.FORMAT, https).replace("ed25519 ", "ed25519 kaputt")))
                .hasMessageContaining("Ed25519");
        assertThatThrownBy(() -> UiQuellen.lesen("kein json geheim-pw")).hasMessageNotContaining("geheim-pw");
    }

    @Test
    void dateiHinUndZurueck(@TempDir Path d) throws Exception {
        List<UiQuellen.Quelle> q = List.of(new UiQuellen.Quelle("kunde", "https://a.example/i.txt", "QUJD"),
                new UiQuellen.Quelle("zwei", "https://b.example/i.txt", "REVG"));
        Path f = d.resolve("quellen-ui.txt");
        Files.writeString(f, UiQuellen.text(q, "root"));
        assertThat(UiQuellen.lesen(f)).isEqualTo(q);
        assertThat(Files.readString(f)).doesNotContain("passwort");
    }
}
