/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.settings.service;

import ch.plaintext.PlaintextSecurity;
import ch.plaintext.boot.menu.ModuleRoleProperties;
import ch.plaintext.boot.menu.ModuleRoleService;
import ch.plaintext.settings.ISettingsService;
import ch.plaintext.settings.SettingsKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Karte 2390: das Zugriffs-Set je Modul in settings (Vorgabe aus der yml, danach massgeblich, ohne Neustart). */
class ModulZugriffTest {

    private final Map<String, String> global = new TreeMap<>();
    private final ModuleRoleService rollenDienst = mock(ModuleRoleService.class);

    private ISettingsService settings() {
        ISettingsService s = mock(ISettingsService.class);
        when(s.exists(anyString(), eq(SettingsKeys.MANDAT_GLOBAL))).thenAnswer(a -> global.containsKey(a.getArgument(0, String.class)));
        when(s.getString(anyString(), eq(SettingsKeys.MANDAT_GLOBAL))).thenAnswer(a -> global.get(a.getArgument(0, String.class)));
        when(s.getAllKeys(SettingsKeys.MANDAT_GLOBAL)).thenAnswer(a -> new ArrayList<>(global.keySet()));
        doAnswer(a -> global.put(a.getArgument(0), a.getArgument(2))).when(s)
                .setSetting(anyString(), eq(SettingsKeys.MANDAT_GLOBAL), anyString(), anyString(), anyString());
        return s;
    }

    private ModulZugriff zugriff(ModuleRoleProperties p) {
        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("rollen", rollenDienst);
        bf.addBean("security", mock(PlaintextSecurity.class));
        return new ModulZugriff(p, bf.getBeanProvider(ModuleRoleService.class), settings(), bf.getBeanProvider(PlaintextSecurity.class));
    }

    private static ModuleRoleProperties yml() {
        ModuleRoleProperties p = new ModuleRoleProperties();
        p.setModuleRoles(new LinkedHashMap<>(Map.of("wiki", "wiki", "mailbox", "mail")));
        p.setStrengeModuleRoles(List.of("files"));
        return p;
    }

    @Test
    void ymlWirdEinmalVorgabeUndNieUeberschrieben() {
        global.put("mailbox.zugriff.admin", "false");
        ModulZugriff z = zugriff(yml());
        assertThat(z.vorgabenSchreiben()).isEqualTo(8);
        assertThat(global).containsEntry("wiki.zugriff.rolle", "wiki").containsEntry("wiki.zugriff.root", "true")
                .containsEntry("wiki.zugriff.admin", "true").containsEntry("mailbox.zugriff.rolle", "mail")
                .containsEntry("mailbox.zugriff.admin", "false")
                .containsEntry("files.zugriff.rolle", "files").containsEntry("files.zugriff.root", "false")
                .containsEntry("files.zugriff.admin", "false");
        assertThat(global.keySet()).noneMatch(k -> k.startsWith("kalender."));
        assertThat(z.vorgabenSchreiben()).as("zweiter Start schreibt nichts").isZero();
    }

    @Test
    void settingsGiltDanachMitYmlAlsRueckfall() {
        ModuleRoleProperties p = yml();
        ModulZugriff z = zugriff(p);
        z.vorgabenSchreiben();
        global.put("wiki.zugriff.root", "false");
        global.put("mailbox.zugriff.rolle", "");
        global.put("fotos.zugriff.rolle", "fotos");
        global.put("fotos.zugriff.admin", "false");
        global.remove("files.zugriff.rolle");
        global.remove("files.zugriff.root");
        global.remove("files.zugriff.admin");
        z.anwenden();
        assertThat(p.canonicalModuleRoles()).containsEntry("wiki", "WIKI").containsEntry("fotos", "FOTOS")
                .containsEntry("files", "FILES").doesNotContainKey("mailbox");
        assertThat(p.canonicalOhneRootUmgehung()).as("wiki per settings, files aus der yml (fehlt in settings)")
                .containsExactlyInAnyOrder("wiki", "files");
        assertThat(p.canonicalOhneAdminUmgehung()).containsExactlyInAnyOrder("fotos", "files");
        verify(rollenDienst).neuAufloesen();
    }

    @Test
    void aenderungWirktSofortEigeneVorgabenNicht() {
        ModuleRoleProperties p = yml();
        ModulZugriff z = zugriff(p);
        z.vorgabenSchreiben();
        z.anwenden();
        assertThat(p.canonicalOhneAdminUmgehung()).containsExactly("files");
        global.put("wiki.zugriff.admin", "false");
        z.geaendert(new EinstellungGeaendert("wiki.zugriff.admin", SettingsKeys.MANDAT_GLOBAL, "true", "false"));
        assertThat(p.canonicalOhneAdminUmgehung()).containsExactlyInAnyOrder("wiki", "files");
        z.geaendert(new EinstellungGeaendert("anderes.thema", SettingsKeys.MANDAT_GLOBAL, "a", "b"));
        z.geaendert(new EinstellungGeaendert("wiki.zugriff.root", "mandant-x", "true", "false"));
        assertThat(p.canonicalOhneRootUmgehung()).as("nur Mandant global zaehlt").containsExactly("files");
    }
}
