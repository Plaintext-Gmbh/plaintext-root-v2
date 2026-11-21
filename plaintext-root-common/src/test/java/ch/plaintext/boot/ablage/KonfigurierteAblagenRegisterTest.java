/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.ablage;

import ch.plaintext.ablagen.DateiAblage;
import ch.plaintext.ablagen.DateiAblageArt;
import ch.plaintext.ablagen.DateiAblagenRegister;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Karte 2425: benannte Speicher aus der application.yml, zusammen mit weiteren Registern. */
class KonfigurierteAblagenRegisterTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AblagenAutoConfiguration.class));

    @Test
    void lokalAusDerYmlUnterDerBasis(@TempDir Path basis) throws Exception {
        runner.withPropertyValues("plaintext.ablagen.lokal.basis=" + basis, "plaintext.speicher.files.art=lokal",
                "plaintext.speicher.files.pfad=files").run(ctx -> {
            DateiAblagenRegister r = ctx.getBean(DateiAblagenRegister.class);
            assertThat(r.namen()).containsExactly("files");
            DateiAblage a = r.ablage("files").orElseThrow();
            assertThat(a.name()).isEqualTo("files");
            a.schreibe("a.txt", "hallo".getBytes(StandardCharsets.UTF_8), null);
            assertThat(Files.readString(basis.resolve("files/a.txt"))).isEqualTo("hallo");
        });
    }

    @Test
    void ohneBasisUnbekannteArtOderAusbruchKeinAbsturz(@TempDir Path basis) {
        runner.withPropertyValues("plaintext.speicher.ohne.art=lokal", "plaintext.speicher.ohne.pfad=x").run(ctx ->
                assertThat(ctx.getBean(DateiAblagenRegister.class).ablage("ohne")).isEmpty());
        runner.withPropertyValues("plaintext.ablagen.lokal.basis=" + basis, "plaintext.speicher.fremd.art=codeservice",
                "plaintext.speicher.raus.art=lokal", "plaintext.speicher.raus.pfad=../x",
                "plaintext.speicher.klar.art=lokal", "plaintext.speicher.klar.pfad=k", "plaintext.speicher.klar.passwort=pw",
                "plaintext.speicher.gross.art=lokal", "plaintext.speicher.gross.pfad=g", "plaintext.speicher.gross[Token]=t").run(ctx -> {
            DateiAblagenRegister r = ctx.getBean(DateiAblagenRegister.class);
            assertThat(r.ablage("klar")).as("Geheimnis im Klartext wird abgewiesen").isEmpty();
            assertThat(r.ablage("gross")).as("auch gross geschrieben").isEmpty();
            assertThat(r.ablage("fremd")).isEmpty();
            assertThat(r.ablage("raus")).isEmpty();
            assertThat(r.ablage("gibtsnicht")).isEmpty();
        });
    }

    @Test
    void eigeneArtUndWeiteresRegisterYmlGewinnt(@TempDir Path basis) {
        runner.withUserConfiguration(Weitere.class)
                .withPropertyValues("plaintext.speicher.doku.art=codeservice", "plaintext.speicher.doku.ordner=/a",
                        "plaintext.speicher.db.art=codeservice")
                .run(ctx -> {
                    DateiAblagenRegister r = ctx.getBean(DateiAblagenRegister.class);
                    assertThat(r).isInstanceOf(KonfigurierteAblagenRegister.class);
                    assertThat(r.namen()).containsExactly("db", "doku", "sidecar");
                    assertThat(r.ablage("doku").orElseThrow().name()).isEqualTo("doku:/a");
                    assertThat(r.ablage("db").orElseThrow().name()).as("yml gewinnt").isEqualTo("db:null");
                    assertThat(r.ablage("sidecar").orElseThrow().name()).isEqualTo("aus-sidecars");
                });
    }

    @Configuration
    static class Weitere {
        /** Wie eine Art, die eine Anwendung selbst beiträgt (z.B. das Inventar); kein Geheimnis in konfig. */
        @Bean
        DateiAblageArt codeservice() {
            return new DateiAblageArt() {
                @Override
                public String art() {
                    return "codeservice";
                }

                @Override
                public DateiAblage oeffne(String name, Map<String, String> konfig) {
                    assertThat(konfig).doesNotContainKey("art");
                    return benannt(name + ":" + konfig.get("ordner"));
                }
            };
        }

        /** Wie Root > Sidecars (DB). */
        @Bean
        DateiAblagenRegister sidecars() {
            DateiAblagenRegister r = mock(DateiAblagenRegister.class);
            when(r.namen()).thenReturn(List.of("db", "sidecar"));
            DateiAblage s = benannt("aus-sidecars");
            when(r.ablage("sidecar")).thenReturn(Optional.of(s));
            return r;
        }
    }

    static DateiAblage benannt(String name) {
        DateiAblage a = mock(DateiAblage.class);
        when(a.name()).thenReturn(name);
        return a;
    }
}
