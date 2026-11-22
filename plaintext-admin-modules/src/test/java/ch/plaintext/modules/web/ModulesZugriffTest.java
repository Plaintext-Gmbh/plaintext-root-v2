/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.modules.web;

import ch.plaintext.modules.ModuleView;
import ch.plaintext.settings.ISettingsService;
import ch.plaintext.settings.SettingsKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Karte 2390 Teil 3: der «Zugriff»-Dialog der Modulverwaltung liest und schreibt die fünf Schlüssel je Modul. */
class ModulesZugriffTest {

    private final Map<String, String> global = new TreeMap<>();

    private ISettingsService settings() {
        ISettingsService s = mock(ISettingsService.class);
        when(s.getString(anyString(), eq(SettingsKeys.MANDAT_GLOBAL)))
                .thenAnswer(a -> global.get(a.getArgument(0, String.class)));
        return s;
    }

    private static ModulesBackingBean bean(ISettingsService settings) {
        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        if (settings != null) {
            bf.addBean("settings", settings);
        }
        ModulesBackingBean bean = new ModulesBackingBean();
        bean.setSettings(bf.getBeanProvider(ISettingsService.class));
        return bean;
    }

    @Test
    void vorbereitenLiestDefaultsWennNichtsGesetzt() {
        ModulesBackingBean bean = bean(settings());
        bean.zugriffVorbereiten(new ModuleView("Wiki", "Wiki", "1.0", true, "plaintext-z-wiki"));
        assertThat(bean.getZugriffModuleId()).isEqualTo("wiki");
        assertThat(bean.getZugriffRolle()).isEmpty();
        assertThat(bean.isZugriffRoot()).isTrue();
        assertThat(bean.isZugriffAdmin()).isTrue();
        assertThat(bean.isZugriffSichtbar()).isTrue();
        assertThat(bean.isZugriffBearbeiten()).isTrue();
        assertThat(bean.isZugriffVerfuegbar()).isTrue();
    }

    @Test
    void vorbereitenLiestGesetzteSchalter() {
        global.put("wiki.zugriff.rolle", "files");
        global.put("wiki.zugriff.root", "false");
        global.put("wiki.zugriff.bearbeiten", "false");
        ModulesBackingBean bean = bean(settings());
        bean.zugriffVorbereiten(new ModuleView("Wiki", "Wiki", "1.0", true, "plaintext-z-wiki"));
        assertThat(bean.getZugriffRolle()).isEqualTo("files");
        assertThat(bean.isZugriffRoot()).isFalse();
        assertThat(bean.isZugriffAdmin()).isTrue();
        assertThat(bean.isZugriffBearbeiten()).isFalse();
    }

    @Test
    void speichernSchreibtAlleFuenfSchluesselKanonisch() {
        ISettingsService settings = settings();
        ModulesBackingBean bean = bean(settings);
        bean.zugriffVorbereiten(new ModuleView("Wiki", "Wiki", "1.0", true, "plaintext-z-wiki"));
        bean.setZugriffRolle(" Files ");
        bean.setZugriffRoot(false);
        bean.setZugriffAdmin(true);
        bean.setZugriffSichtbar(false);
        bean.setZugriffBearbeiten(true);
        bean.zugriffSpeichern();
        verify(settings).setSetting(eq("wiki.zugriff.rolle"), eq(SettingsKeys.MANDAT_GLOBAL), eq("FILES"),
                eq("STRING"), anyString());
        verify(settings).setSetting(eq("wiki.zugriff.root"), eq(SettingsKeys.MANDAT_GLOBAL), eq("false"),
                eq("BOOLEAN"), anyString());
        verify(settings).setSetting(eq("wiki.zugriff.admin"), eq(SettingsKeys.MANDAT_GLOBAL), eq("true"),
                eq("BOOLEAN"), anyString());
        verify(settings).setSetting(eq("wiki.zugriff.sichtbar"), eq(SettingsKeys.MANDAT_GLOBAL), eq("false"),
                eq("BOOLEAN"), anyString());
        verify(settings).setSetting(eq("wiki.zugriff.bearbeiten"), eq(SettingsKeys.MANDAT_GLOBAL), eq("true"),
                eq("BOOLEAN"), anyString());
    }

    @Test
    void ohneSettingsWirdNichtsGeschriebenUndDialogIstNichtVerfuegbar() {
        ISettingsService settings = settings();
        ModulesBackingBean bean = bean(null);
        assertThat(bean.isZugriffVerfuegbar()).isFalse();
        bean.setZugriffModuleId("wiki");
        bean.zugriffSpeichern();
        verify(settings, never()).setSetting(anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
