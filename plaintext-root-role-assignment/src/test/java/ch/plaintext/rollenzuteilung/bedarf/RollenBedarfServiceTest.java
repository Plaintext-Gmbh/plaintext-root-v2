/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.rollenzuteilung.bedarf;

import ch.plaintext.boot.menu.MenuAccessPolicy;
import ch.plaintext.boot.menu.MenuItemImpl;
import ch.plaintext.framework.PlaintextRole;
import ch.plaintext.rollen.RollenArt;
import ch.plaintext.rollen.RollenBedarf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Karte 2377 ROOT-5: alle Quellen, Dubletten, Sortierung, Bestand. */
class RollenBedarfServiceTest {

    private static MenuItemImpl menue(String menuId, MenuAccessPolicy politik, String... rollen) {
        MenuItemImpl m = new MenuItemImpl();
        m.setMenuId(menuId);
        m.setTitle(menuId);
        m.setAccessPolicy(politik);
        m.setRoles(List.of(rollen));
        return m;
    }

    private static RollenBedarf eintrag(List<RollenBedarf> liste, String authority) {
        return liste.stream().filter(r -> r.authority().equals(authority)).findFirst().orElseThrow();
    }

    private static final List<PlaintextRole> DEKLARIERT = List.of(
            new PlaintextRole("terminal", "Terminal bedienen").modul("terminal").ohneUmgehung(true),
            new PlaintextRole("admin", "Administration").art(RollenArt.ADMIN));

    @Test
    void demoMitTerminal() {
        List<RollenBedarf> l = RollenBedarfService.sammle(DEKLARIERT, Map.of("terminal", "TERMINAL", "files", "FILES"),
                List.of(menue("terminal", MenuAccessPolicy.PERMISSIVE, "terminal")), Map.of("terminal", 3, "alt", 1));

        RollenBedarf t = eintrag(l, "ROLE_TERMINAL");
        assertThat(t.quelle()).isEqualTo(RollenBedarf.DEKLARIERT);
        assertThat(t.art()).isEqualTo(RollenArt.ZUGANG);
        assertThat(t.ohneUmgehung()).isTrue();
        assertThat(t.module()).containsExactly("terminal");
        assertThat(t.vergeben()).isEqualTo(3);
        assertThat(eintrag(l, "ROLE_FILES").quelle()).isEqualTo(RollenBedarf.MODULE_ROLES);
        assertThat(eintrag(l, "ROLE_FILES").module()).containsExactly("files");
        RollenBedarf alt = eintrag(l, "ROLE_ALT");
        assertThat(alt.quelle()).isEqualTo(RollenBedarf.BESTAND);
        assertThat(alt.vergeben()).isEqualTo(1);
        assertThat(l).as("PERMISSIVE: keine Auto-Rolle").noneMatch(r -> r.authority().startsWith("ROLE_MENU_"));
        assertThat(l).extracting(RollenBedarf::authority).isSorted();
    }

    @Test
    void autoRolleNurUnterStrict() {
        List<RollenBedarf> l = RollenBedarfService.sammle(List.of(), Map.of(),
                List.of(menue("drawio", MenuAccessPolicy.STRICT), menue("wiki", MenuAccessPolicy.PERMISSIVE)), null);

        assertThat(l).extracting(RollenBedarf::authority).containsExactly("ROLE_MENU_DRAWIO");
        assertThat(l.getFirst().quelle()).isEqualTo(RollenBedarf.MENU_AUTO);
        assertThat(l.getFirst().menuId()).isEqualTo("drawio");
        assertThat(l.getFirst().vergeben()).as("Bestand unbekannt").isNull();
    }

    @Test
    void menueRolleOhneDeklarationUndDeklarierteGehtVor() {
        List<RollenBedarf> l = RollenBedarfService.sammle(DEKLARIERT, Map.of(),
                List.of(menue("misc", MenuAccessPolicy.PERMISSIVE, "ROLE_MISC", "admin")), Map.of());

        assertThat(eintrag(l, "ROLE_MISC").quelle()).isEqualTo(RollenBedarf.MENU_ANNOTATION);
        assertThat(eintrag(l, "ROLE_MISC").menuId()).isEqualTo("misc");
        assertThat(eintrag(l, "ROLE_ADMIN").quelle()).as("Dublette: deklariert gewinnt").isEqualTo(RollenBedarf.DEKLARIERT);
        assertThat(eintrag(l, "ROLE_ADMIN").vergeben()).isZero();
        assertThat(l).filteredOn(r -> r.authority().equals("ROLE_ADMIN")).hasSize(1);
    }

    @Test
    void mandatsMarkerSindKeinBestand() {
        List<RollenBedarf> l = RollenBedarfService.sammle(List.of(), Map.of(), List.of(),
                Map.of("property_mandat_x", 4, "user", 2));

        assertThat(l).extracting(RollenBedarf::authority).containsExactly("ROLE_USER");
    }
}
