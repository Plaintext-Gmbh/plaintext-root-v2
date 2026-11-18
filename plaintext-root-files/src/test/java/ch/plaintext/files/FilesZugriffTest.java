/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files;

import ch.plaintext.boot.security.PageAccessGuardService;
import ch.plaintext.files.service.FilesRoleProvider;
import ch.plaintext.files.web.FilesZugriff;
import ch.plaintext.framework.PlaintextRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Karte 2354: die Ablage braucht die Rolle {@code files} (oder Admin/Root), und einen fremden Ordner bekommt nur,
 * wer Admin oder Root ist.
 */
class FilesZugriffTest {

    private final FilesZugriff zugriff = Anmeldung.zugriffOhneSeitenschutz();

    @AfterEach
    void aufraeumen() {
        Anmeldung.ab();
    }

    @Test
    void ohneRolleFilesKeinZugriff() {
        assertThat(zugriff.benutzer()).as("nicht angemeldet").isNull();

        Anmeldung.als("carl", "ROLE_USER");
        assertThat(zugriff.benutzer()).as("USER genügte bis Karte 2354").isNull();
        assertThat(zugriff.ordner(null)).isNull();
        assertThat(zugriff.siehtAlle()).isFalse();

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("k", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_FILES"))));
        assertThat(zugriff.benutzer()).as("anonym, auch mit Rolle").isNull();
    }

    @Test
    void rolleFilesOeffnetNurDenEigenenOrdner() {
        Anmeldung.als("Anna.Muster@example.ch", "ROLE_USER", "ROLE_FILES");

        assertThat(zugriff.benutzer()).isEqualTo("Anna.Muster@example.ch");
        assertThat(zugriff.siehtAlle()).isFalse();
        assertThat(zugriff.ordner(null)).isEqualTo("anna.muster@example.ch");
        assertThat(zugriff.ordner(" ")).isEqualTo("anna.muster@example.ch");
        assertThat(zugriff.ordner("ANNA.muster@example.ch")).as("die eigene Kennung, anders geschrieben")
                .isEqualTo("anna.muster@example.ch");
        assertThat(zugriff.ordner("bert")).as("fremder Ordner").isNull();
        assertThat(zugriff.ordner("../anna.muster@example.ch")).as("kein Umweg über den Pfad").isNull();
        assertThat(zugriff.eigener("anna.muster@example.ch")).isTrue();
        assertThat(zugriff.eigener("bert")).isFalse();
    }

    @Test
    void adminUndRootSehenAlle() {
        for (String rolle : new String[]{"ROLE_ADMIN", "ROLE_ROOT", "ADMIN"}) {
            Anmeldung.als("chef", rolle);
            assertThat(zugriff.benutzer()).as(rolle).isEqualTo("chef");
            assertThat(zugriff.siehtAlle()).as(rolle).isTrue();
            assertThat(zugriff.ordner("bert")).as(rolle).isEqualTo("bert");
            assertThat(zugriff.ordner(null)).as(rolle).isEqualTo("chef");
            assertThat(zugriff.ordner("..")).as("kein Ordner").isNull();
            assertThat(zugriff.eigener("bert")).isFalse();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void mitSeitenschutzEntscheidetDerMenueeintrag() {
        PageAccessGuardService guard = mock(PageAccessGuardService.class);
        when(guard.isEnabled()).thenReturn(true);
        ObjectProvider<PageAccessGuardService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(guard);
        FilesZugriff mitSchutz = new FilesZugriff(provider);

        // Die Rolle allein reicht nicht, wenn das Menü den Eintrag verbirgt (Modul aus, Mandant ohne Files).
        Anmeldung.als("anna", "ROLE_FILES");
        when(guard.hasAccessToView(FilesZugriff.VIEW)).thenReturn(false);
        assertThat(mitSchutz.benutzer()).isNull();

        when(guard.hasAccessToView(FilesZugriff.VIEW)).thenReturn(true);
        assertThat(mitSchutz.benutzer()).isEqualTo("anna");
        assertThat(mitSchutz.siehtAlle()).as("sehen dürfen macht noch keinen Admin").isFalse();

        // Abgeschalteter Seitenschutz fällt auf die Rollen zurück, nicht auf «angemeldet genügt».
        when(guard.isEnabled()).thenReturn(false);
        Anmeldung.als("carl", "ROLE_USER");
        assertThat(mitSchutz.benutzer()).isNull();
    }

    @Test
    void rolleIstFuerDieBenutzerverwaltungAngemeldet() {
        FilesRoleProvider provider = new FilesRoleProvider();

        assertThat(provider.getRoles()).containsExactly("files");
        PlaintextRole rolle = provider.getDeclaredRoles().iterator().next();
        assertThat(rolle.authorityName()).isEqualTo("ROLE_FILES");
        assertThat(rolle.description()).isNotBlank();
    }
}
