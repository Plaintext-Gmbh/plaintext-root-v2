/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.i18n.web;

import ch.plaintext.PlaintextSecurity;
import ch.plaintext.i18n.service.I18nService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Karte 2464: die Übersetzungs-Tabelle trägt Tabellen-Einstellungen wie die übrigen Seiten (Vorbild useradmin). */
class I18nTabelleTest {

    @Test
    void onLoadInitialisiertAnzeigeUndStandardSpaltenSindSichtbar() {
        I18nBackingBean bean = new I18nBackingBean();
        PlaintextSecurity security = mock(PlaintextSecurity.class);
        when(security.ifGranted(anyString())).thenReturn(true);
        I18nService service = mock(I18nService.class);
        bean.setSecurity(security);
        bean.setI18nService(service);

        bean.onLoad();

        assertThat(bean.getAnzeige()).isNotNull();
        assertThat(bean.spalteSichtbar("defaultLabel")).isTrue();
        assertThat(bean.spalteSichtbar("language")).isTrue();
        assertThat(bean.spalteSichtbar("translation")).isTrue();
        assertThat(bean.spalteSichtbar("aktionen")).isTrue();
    }
}
