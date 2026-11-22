/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.security;

import ch.plaintext.PlaintextSecurity;
import ch.plaintext.boot.menu.ModuleRoleProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Karte 1500: dieselbe Regel wie der Page-Guard, die Zuordnung aus der Konfiguration. */
class ModulRolleTest {

    private static ModulRolle modulRolle(Map<String, String> zuordnung, String... rollen) {
        return modulRolle(zuordnung, java.util.List.of(), rollen);
    }

    private static ModulRolle modulRolle(Map<String, String> zuordnung, java.util.List<String> streng, String... rollen) {
        ModuleRoleProperties p = new ModuleRoleProperties();
        p.setModuleRoles(zuordnung);
        p.setStrengeModuleRoles(streng);
        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("props", p);
        if (rollen != null) {
            Set<String> hat = Set.of(rollen);
            PlaintextSecurity s = mock(PlaintextSecurity.class);
            // wie PlaintextSecurityImpl.ifGranted: ROLE_-Praefix optional, Gross/Klein egal
            when(s.ifGranted(anyString())).thenAnswer(a -> {
                String r = a.getArgument(0, String.class).toUpperCase();
                return hat.contains(r.startsWith("ROLE_") ? r : "ROLE_" + r);
            });
            bf.addBean("security", s);
        }
        return new ModulRolle(bf.getBeanProvider(ModuleRoleProperties.class), bf.getBeanProvider(PlaintextSecurity.class));
    }

    @Test
    @DisplayName("Mit der konfigurierten Rolle (klein geschrieben wie in application.yml) darf man")
    void mitRolle() {
        assertThat(modulRolle(Map.of("fotos", "fotos"), "ROLE_USER", "ROLE_FOTOS").darf("fotos")).isTrue();
    }

    @Test
    @DisplayName("Ohne die Rolle nicht, auch mit einer anderen Modulrolle")
    void ohneRolle() {
        assertThat(modulRolle(Map.of("fotos", "fotos"), "ROLE_USER", "ROLE_WIKI").darf("fotos")).isFalse();
    }

    @Test
    @DisplayName("ADMIN und ROOT immer")
    void admin() {
        assertThat(modulRolle(Map.of("fotos", "fotos"), "ROLE_ADMIN").darf("fotos")).isTrue();
        assertThat(modulRolle(Map.of("fotos", "fotos"), "ROLE_ROOT").darf("fotos")).isTrue();
    }

    @Test
    @DisplayName("Karte 2377 ROOT-3: strenges Modul ohne ADMIN/ROOT-Umgehung")
    void strengesModul() {
        assertThat(strengeModulRolle(Map.of("terminal", "terminal"), "ROLE_ROOT").darf("terminal")).isFalse();
        assertThat(strengeModulRolle(Map.of("terminal", "terminal"), "ROLE_ADMIN").darf("terminal")).isFalse();
        assertThat(strengeModulRolle(Map.of("terminal", "terminal"), "ROLE_ROOT", "ROLE_TERMINAL").darf("terminal")).isTrue();
        assertThat(strengeModulRolle(Map.of(), "ROLE_ROOT").darf("terminal")).as("ohne Zuordnung: gleichnamige Rolle").isFalse();
        assertThat(strengeModulRolle(Map.of(), "ROLE_USER", "ROLE_TERMINAL").darf("terminal")).isTrue();
        assertThat(strengeModulRolle(Map.of("fotos", "fotos"), "ROLE_ADMIN").darf("fotos")).as("nicht gelistet: Umgehung bleibt").isTrue();
    }

    @Test
    @DisplayName("Karte 2390: Root- und Admin-Umgehung getrennt, gleiche Regel wie das Menue")
    void rootUndAdminGetrennt() {
        ModuleRoleProperties p = new ModuleRoleProperties();
        p.setModuleRoles(Map.of("fotos", "fotos"));
        p.setOhneRootUmgehung(java.util.List.of("fotos"));
        assertThat(mitProps(p, "ROLE_ROOT").darf("fotos")).as("ohne Root-Umgehung: ROOT allein nicht").isFalse();
        assertThat(mitProps(p, "ROLE_ADMIN").darf("fotos")).as("ohne Root-Umgehung: ADMIN weiter").isTrue();
        assertThat(mitProps(p, "ROLE_ROOT", "ROLE_FOTOS").darf("fotos")).isTrue();
        ModuleRoleProperties q = new ModuleRoleProperties();
        q.setModuleRoles(Map.of("fotos", "fotos"));
        q.setOhneAdminUmgehung(java.util.List.of("fotos"));
        assertThat(mitProps(q, "ROLE_ADMIN").darf("fotos")).as("ohne Admin-Umgehung: ADMIN allein nicht").isFalse();
        assertThat(mitProps(q, "ROLE_ROOT").darf("fotos")).as("ohne Admin-Umgehung: ROOT weiter").isTrue();
        assertThat(modulRolle(Map.of("fotos", "fotos"), "ROLE_ADMIN").darf("fotos")).as("ohne Schalter wie bisher").isTrue();
    }

    private static ModulRolle mitProps(ModuleRoleProperties p, String... rollen) {
        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("props", p);
        Set<String> hat = Set.of(rollen);
        PlaintextSecurity s = mock(PlaintextSecurity.class);
        when(s.ifGranted(anyString())).thenAnswer(a -> {
            String r = a.getArgument(0, String.class).toUpperCase();
            return hat.contains(r.startsWith("ROLE_") ? r : "ROLE_" + r);
        });
        bf.addBean("security", s);
        return new ModulRolle(bf.getBeanProvider(ModuleRoleProperties.class), bf.getBeanProvider(PlaintextSecurity.class));
    }

    private static ModulRolle strengeModulRolle(Map<String, String> zuordnung, String... rollen) {
        return modulRolle(zuordnung, java.util.List.of("terminal"), rollen);
    }

    @Test
    @DisplayName("Andere Instanz, andere Rolle: guild ordnet auszahlungen der Rolle auszahlung zu")
    void zuordnungAusKonfiguration() {
        Map<String, String> guild = Map.of("auszahlungen", "auszahlung");
        assertThat(modulRolle(guild, "ROLE_USER", "ROLE_AUSZAHLUNG").darf("auszahlungen")).isTrue();
        assertThat(modulRolle(guild, "ROLE_USER", "ROLE_AUSZAHLUNGEN").darf("auszahlungen")).isFalse();
    }

    @Test
    @DisplayName("Modul ohne Zuordnung bleibt frei wie bisher")
    void ohneZuordnung() {
        assertThat(modulRolle(Map.of("wiki", "wiki"), "ROLE_USER").darf("fotos")).isTrue();
    }

    @Test
    @DisplayName("Karte 2390 Teil 2: bearbeiten=false ist nur-lesen, auch für ROOT/ADMIN")
    void darfBearbeiten() {
        ModuleRoleProperties p = new ModuleRoleProperties();
        p.setModuleRoles(Map.of("fotos", "fotos", "wiki", "wiki"));
        p.setOhneBearbeitung(java.util.List.of("fotos"));
        assertThat(mitProps(p, "ROLE_FOTOS").darfBearbeiten("fotos")).as("mit Rolle").isFalse();
        assertThat(mitProps(p, "ROLE_ROOT").darfBearbeiten("fotos")).as("Sperre gilt auch fuer ROOT").isFalse();
        assertThat(mitProps(p, "ROLE_ADMIN").darfBearbeiten("fotos")).as("Sperre gilt auch fuer ADMIN").isFalse();
        assertThat(mitProps(p, "ROLE_FOTOS").darf("fotos")).as("Nutzung bleibt").isTrue();
        assertThat(mitProps(p, "ROLE_ROOT").darf("fotos")).as("Nutzung bleibt").isTrue();
        assertThat(mitProps(p, "ROLE_WIKI").darfBearbeiten("wiki")).as("ohne Schalter wie bisher").isTrue();
        assertThat(mitProps(p, "ROLE_USER").darfBearbeiten("wiki")).as("Nutzung verweigert: auch keine Bearbeitung").isFalse();
        assertThat(mitProps(p, "ROLE_USER").darfBearbeiten("kalender")).as("freies Modul").isTrue();
    }

    @Test
    @DisplayName("Ohne PlaintextSecurity und mit Zuordnung: zu")
    void ohneSecurity() {
        assertThat(modulRolle(Map.of("fotos", "fotos"), (String[]) null).darf("fotos")).isFalse();
    }
}
