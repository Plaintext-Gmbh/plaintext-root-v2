/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.settings.service;

import ch.plaintext.modules.ModuleDescriptor;
import ch.plaintext.settings.ISettingsService;
import ch.plaintext.settings.SettingsKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Karte 2425 PR B: Vorgaben nur, wenn sie fehlen; Gruppe aus dem Präfix. */
class ModulVorgabenTest {

    private static ModuleDescriptor modul(String id, ModuleDescriptor.Vorgabe... v) {
        return new ModuleDescriptor() {
            @Override
            public String moduleId() {
                return id;
            }

            @Override
            public List<Vorgabe> einstellungen() {
                return List.of(v);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static ModulVorgaben vorgaben(ISettingsService s, ModuleDescriptor... m) {
        ObjectProvider<ModuleDescriptor> p = mock(ObjectProvider.class);
        when(p.orderedStream()).thenAnswer(i -> Stream.of(m));
        return new ModulVorgaben(p, s);
    }

    @Test
    void fehlendeWirdGeschriebenVorhandeneBleibt() {
        ISettingsService s = mock(ISettingsService.class);
        when(s.exists("files.frist", SettingsKeys.MANDAT_GLOBAL)).thenReturn(false);
        when(s.exists("files.max-mb", SettingsKeys.MANDAT_GLOBAL)).thenReturn(true);
        ModulVorgaben v = vorgaben(s, modul("files",
                new ModuleDescriptor.Vorgabe("files.frist", "21", "INTEGER", "Ablauf in Tagen"),
                new ModuleDescriptor.Vorgabe("files.max-mb", "50", "INTEGER", "Grösste Datei"),
                new ModuleDescriptor.Vorgabe("fremd.x", "1", null, "falsches Präfix"),
                new ModuleDescriptor.Vorgabe("files.zugang.passwort", "x", null, "Geheimnis")));
        assertThat(v.schreiben()).isEqualTo(1);
        verify(s).setSetting("files.frist", SettingsKeys.MANDAT_GLOBAL, "21", "INTEGER", "Ablauf in Tagen");
        verify(s, never()).setSetting(eq("files.max-mb"), anyString(), anyString(), anyString(), anyString());
        verify(s, never()).setSetting(eq("fremd.x"), anyString(), anyString(), anyString(), anyString());
        verify(s, never()).setSetting(eq("files.zugang.passwort"), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void gruppeAusDemPraefixUndMarkierung() {
        ModulVorgaben v = vorgaben(mock(ISettingsService.class), modul("files",
                new ModuleDescriptor.Vorgabe("files.frist", "21", "INTEGER", "Ablauf")), modul("notizen"));
        assertThat(v.gruppen()).containsExactly("root", "files", "notizen");
        assertThat(v.gruppe("files.frist")).isEqualTo("files");
        assertThat(v.gruppe("files.ablauf.616e6e61")).as("persönlich, aber in der Gruppe").isEqualTo("files");
        assertThat(v.gruppe("mail.sync.minuten")).isEqualTo("root");
        assertThat(v.gruppe("files")).isEqualTo("root");
        assertThat(v.istVorgabe("files.frist")).isTrue();
        assertThat(v.istVorgabe("files.ablauf.616e6e61")).isFalse();
    }
}
