/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files;

import ch.plaintext.boot.menu.MenuAccessPolicy;
import ch.plaintext.boot.menu.MenuAnnotationScanner;
import ch.plaintext.boot.menu.MenuItemImpl;
import ch.plaintext.boot.menu.SecurityProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Karte 2354: «Files» hängt unter «Misc» und ist nur mit der Rolle {@code files} (oder als Admin/Root) sichtbar -
 * unter beiden Menüpolitiken. Gelesen wird mit dem echten Scanner, damit ein Tippfehler in der Annotation auffällt.
 */
class FilesMenuTest {

    private static List<MenuItemImpl> menue(MenuAccessPolicy politik, MockEnvironment umgebung, String... rollen) {
        Set<String> traegt = Set.of(rollen);
        SecurityProvider wer = new SecurityProvider() {
            @Override
            public boolean hasRole(String rolle) {
                return traegt.contains(rolle.startsWith("ROLE_") ? rolle.substring(5) : rolle);
            }

            @Override
            public boolean isSecurityEnabled() {
                return true;
            }
        };
        return new MenuAnnotationScanner(wer, null, null, politik).mitUmgebung(umgebung)
                .findAnnotatedClasses("ch.plaintext.files");
    }

    private static MenuItemImpl eintrag(List<MenuItemImpl> menue, String titel) {
        return menue.stream().filter(m -> m.getTitle().equals(titel)).findFirst().orElseThrow();
    }

    @Test
    void filesHaengtUnterMiscUndDasModulBringtMiscMit() {
        List<MenuItemImpl> menue = menue(MenuAccessPolicy.STRICT, new MockEnvironment());

        MenuItemImpl files = eintrag(menue, "Files");
        assertThat(files.getParent()).isEqualTo("Misc");
        assertThat(files.getLink()).isEqualTo("files/ablage.html");
        assertThat(files.getRoles()).containsExactlyInAnyOrder("FILES", "ADMIN", "ROOT");
        assertThat(files.getAutoRole()).as("bleibt trotz Umzug unter Misc").isEqualTo("ROLE_MENU_FILES");
        assertThat(eintrag(menue, "Misc").getParent()).isEmpty();
    }

    @Test
    void nurMitRolleFilesOderAlsAdminUndRootSichtbar() {
        for (MenuAccessPolicy politik : MenuAccessPolicy.values()) {
            for (String rolle : new String[]{"FILES", "ADMIN", "ROOT"}) {
                List<MenuItemImpl> menue = menue(politik, new MockEnvironment(), "USER", rolle);
                assertThat(eintrag(menue, "Files").isRoleVisible()).as(politik + " " + rolle).isTrue();
                assertThat(eintrag(menue, "Misc").isRoleVisible()).as(politik + " Misc " + rolle).isTrue();
            }
            List<MenuItemImpl> ohne = menue(politik, new MockEnvironment(), "USER");
            assertThat(eintrag(ohne, "Files").isRoleVisible()).as(politik + " ohne Rolle").isFalse();
            assertThat(eintrag(ohne, "Misc").isRoleVisible()).as(politik + " kein leeres Misc").isFalse();
        }
    }

    @Test
    void anwendungMitEigenemMiscSchaltetDasMitgebrachteAb() {
        List<MenuItemImpl> menue = menue(MenuAccessPolicy.STRICT,
                new MockEnvironment().withProperty("plaintext.files.misc-menue", "false"));

        assertThat(menue).extracting(MenuItemImpl::getTitle).containsExactly("Files");
    }
}
