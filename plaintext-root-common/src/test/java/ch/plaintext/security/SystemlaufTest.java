/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Karte 1863: Systemlauf setzt den Maschinenkontext und stellt den vorherigen immer wieder her (auch auf dem
 * Fehlerpfad); der TaskDecorator gibt Pool-Threads ROLE_SYSTEM, ohne einen vorhandenen Kontext zu ueberschreiben; die
 * Benutzerrolle {@code system} wird erkannt; eine Fachrolle gibt es nur im Systemlauf.
 */
class SystemlaufTest {

    @AfterEach
    void aufraeumen() {
        SecurityContextHolder.clearContext();
    }

    private static Set<String> rechte() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? Set.of() : a.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private static Object principal() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getPrincipal();
    }

    @Test
    void alsSetztSystemkontextUndRaeumtDanachAuf() {
        AtomicReference<Set<String>> drin = new AtomicReference<>();
        AtomicReference<Object> wer = new AtomicReference<>();
        Systemlauf.als("Plaintext", () -> {
            drin.set(rechte());
            wer.set(principal());
        });
        assertThat(drin.get()).containsExactlyInAnyOrder("ROLE_SYSTEM", "PROPERTY_MANDAT_plaintext");
        assertThat(wer.get()).isEqualTo("SYSTEM");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void vorherigerBenutzerKommtZurueckAuchNachFehler() {
        var benutzer = new UsernamePasswordAuthenticationToken("dora", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(benutzer);
        assertThatThrownBy(() -> Systemlauf.als("global", () -> {
            throw new IllegalStateException("Absturz im Lauf");
        })).hasMessage("Absturz im Lauf");
        assertThat(principal()).isEqualTo("dora");
        assertThat(rechte()).containsExactly("ROLE_USER");
    }

    @Test
    void alsBenutzerTraegtDenBenutzerAlsPrincipal() {
        AtomicReference<Object> wer = new AtomicReference<>();
        Systemlauf.alsBenutzer("plaintext", "dora@example.ch", () -> wer.set(principal()));
        assertThat(wer.get()).isEqualTo("dora@example.ch");
    }

    @Test
    void fachrolleNurImSystemlauf() {
        assertThatThrownBy(() -> Systemlauf.mitFachrolle("mitglieder", () -> { }))
                .isInstanceOf(IllegalStateException.class);
        AtomicReference<Set<String>> drin = new AtomicReference<>();
        AtomicReference<Set<String>> danach = new AtomicReference<>();
        Systemlauf.als("guild", () -> {
            Systemlauf.mitFachrolle("mitglieder", () -> drin.set(rechte()));
            danach.set(rechte());
        });
        assertThat(drin.get()).contains("ROLE_SYSTEM", "ROLE_MITGLIEDER", "PROPERTY_MANDAT_guild");
        assertThat(danach.get()).doesNotContain("ROLE_MITGLIEDER");
    }

    @Test
    void umhuellenGibtPoolThreadsDenSystemkontextUndLaesstSieSauber() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Set<String> imLauf = CompletableFuture.supplyAsync(() -> {
                AtomicReference<Set<String>> r = new AtomicReference<>();
                Systemlauf.umhuellen("global", () -> r.set(rechte())).run();
                return r.get();
            }, pool).get(10, TimeUnit.SECONDS);
            Set<String> danach = CompletableFuture.supplyAsync(SystemlaufTest::rechte, pool).get(10, TimeUnit.SECONDS);
            assertThat(imLauf).containsExactlyInAnyOrder("ROLE_SYSTEM", "PROPERTY_MANDAT_global");
            assertThat(danach).isEmpty();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void umhuellenUeberschreibtKeinenVorhandenenKontext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("dora", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        AtomicReference<Object> wer = new AtomicReference<>();
        Systemlauf.umhuellen("global", () -> wer.set(principal())).run();
        assertThat(wer.get()).isEqualTo("dora");
    }

    @Test
    void systemrolleWirdErkannt() {
        assertThat(Systemlauf.istSystemrolle("system")).isTrue();
        assertThat(Systemlauf.istSystemrolle(" SYSTEM ")).isTrue();
        assertThat(Systemlauf.istSystemrolle("role_system")).isTrue();
        assertThat(Systemlauf.istSystemrolle("admin")).isFalse();
        assertThat(Systemlauf.istSystemrolle(null)).isFalse();
    }

    @Configuration
    @EnableScheduling
    static class MitScheduling {
    }

    @Test
    void scheduledLaufImBootKontextTraegtRoleSystem() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class, SystemlaufAutoConfiguration.class))
                .withUserConfiguration(MitScheduling.class)
                .run(ctx -> {
                    CompletableFuture<Set<String>> imLauf = new CompletableFuture<>();
                    ctx.getBean(TaskScheduler.class).schedule(() -> imLauf.complete(rechte()), Instant.now());
                    assertThat(imLauf.get(10, TimeUnit.SECONDS)).containsExactlyInAnyOrder("ROLE_SYSTEM", "PROPERTY_MANDAT_global");
                });
    }
}
