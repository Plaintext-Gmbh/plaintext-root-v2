/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.lader;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Karte 2343 (Vorfall 17.11.2026): board war ausgerollt, fehlte aber in auswahl.yaml und wurde still nicht
 * geladen. Der Lader meldet solche Module jetzt mit Name und Version.
 */
class NichtAusgewaehltTest {

    private static Map<String, String> ausgerollt() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("plaintext-app-basis", "2.2110.0");
        m.put("plaintext-modul-board", "1.0.9");
        m.put("plaintext-modul-alkohol", "3.0.0");
        return m;
    }

    @Test
    void ausgerolltAberNichtGewaehltWirdGemeldet() {
        List<ModulAuswahl.Wahl> wahl = List.of(new ModulAuswahl.Wahl("plaintext-app-basis", "latest"),
                new ModulAuswahl.Wahl("plaintext-modul-alkohol", "latest"));
        assertThat(ModulLader.nichtAusgewaehlt(ausgerollt(), wahl)).containsExactly("plaintext-modul-board:1.0.9");
    }

    @Test
    void vollstaendigeAuswahlMeldetNichts() {
        List<ModulAuswahl.Wahl> wahl = ausgerollt().keySet().stream().map(n -> new ModulAuswahl.Wahl(n, "latest")).toList();
        assertThat(ModulLader.nichtAusgewaehlt(ausgerollt(), wahl)).isEmpty();
    }
}
