/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.modules.laufzeit;

import ch.plaintext.lader.ModulAuswahl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Karte 1612: Ist eine Registry weg, bleibt die gespeicherte Version gewaehlt (nicht still die erste Option). */
class ModulAuswahlBackingBeanTest {

    @Test
    void gespeicherteVersionBleibtWaehlbarOhneAngebot() throws Exception {
        ModulAuswahlService service = mock(ModulAuswahlService.class);
        when(service.ansicht()).thenReturn(new ModulAuswahlService.Ansicht(Map.of(), List.of(
                new ModulAuswahlService.Modul("plaintext-z-a", List.of(
                        new ModulAuswahlService.Version("3.0.1", "lokal", false, null)), "3.0.0", null, "3.0.0")),
                Map.of("nextcloud", "antwortet 503"), null, List.of(), "2.811.1", true, null, List.of()));
        ModulAuswahlBackingBean bean = new ModulAuswahlBackingBean();
        bean.setService(service);

        bean.neuLesen();

        ModulAuswahlBackingBean.Zeile z = bean.getZeilen().get(0);
        assertThat(z.getVersion()).isEqualTo("3.0.0");
        assertThat(z.getOptionen()).extracting(jakarta.faces.model.SelectItem::getValue).containsExactly("latest", "3.0.1", "3.0.0");
    }

    /** Karte 2377 (ROOT-9): dieselbe Version in zwei Registries -> Wert nennt die Registry; die Wahl traegt sie zurueck. */
    @Test
    void gleicheVersionInZweiRegistriesNenntDieRegistry() throws Exception {
        ModulAuswahlService service = mock(ModulAuswahlService.class);
        when(service.ansicht()).thenReturn(new ModulAuswahlService.Ansicht(Map.of(), List.of(
                new ModulAuswahlService.Modul("plaintext-z-a", List.of(
                        new ModulAuswahlService.Version("3.0.1", "lokal", true, null),
                        new ModulAuswahlService.Version("3.0.1", "nextcloud", true, null),
                        new ModulAuswahlService.Version("3.0.0", "nextcloud", true, null)), "3.0.1", "nextcloud", null)),
                Map.of(), null, List.of(), "2.811.1", true, null,
                List.of(new ModulAuswahlService.Registry("lokal", "1 Modul(e)"), new ModulAuswahlService.Registry("nextcloud", "2 Modul(e)"))));
        ModulAuswahlBackingBean bean = new ModulAuswahlBackingBean();
        bean.setService(service);
        bean.neuLesen();
        ModulAuswahlBackingBean.Zeile z = bean.getZeilen().get(0);
        assertThat(z.getVersion()).isEqualTo("3.0.1@nextcloud");
        assertThat(z.getOptionen()).extracting(jakarta.faces.model.SelectItem::getValue)
                .containsExactly("latest", "3.0.1@lokal", "3.0.1@nextcloud", "3.0.0");
        assertThat(z.getOptionen()).extracting(jakarta.faces.model.SelectItem::getLabel)
                .containsExactly("latest", "3.0.1 (lokal)", "3.0.1 (nextcloud)", "3.0.0");
        assertThat(ModulAuswahlBackingBean.wahl("plaintext-z-a", "3.0.1@nextcloud"))
                .isEqualTo(new ModulAuswahl.Wahl("plaintext-z-a", "3.0.1", "nextcloud"));
        assertThat(ModulAuswahlBackingBean.wahl("plaintext-z-a", "3.0.0")).isEqualTo(new ModulAuswahl.Wahl("plaintext-z-a", "3.0.0"));
    }
}
