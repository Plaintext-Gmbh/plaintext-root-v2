/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.ablage;

import ch.plaintext.ablagen.DateiAblage;
import ch.plaintext.ablagen.DateiAblageArt;
import ch.plaintext.ablagen.DateiAblagenRegister;
import ch.plaintext.settings.ISettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

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

    /** Karte 2425 PR C: yml als Vorgabe nach settings, danach gilt settings; neue Ablage nur in settings. */
    @Test
    void settingsUeberschreibtYmlUndLegtAn(@TempDir Path basis) {
        Map<String, String> db = new java.util.concurrent.ConcurrentHashMap<>();
        ISettingsService settings = mock(ISettingsService.class);
        when(settings.exists(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("global")))
                .thenAnswer(i -> db.containsKey(i.<String>getArgument(0)));
        org.mockito.Mockito.doAnswer(i -> db.put(i.getArgument(0), i.getArgument(2))).when(settings)
                .setSetting(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("global"),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
        when(settings.getChildKeys("speicher", "global")).thenAnswer(i -> List.copyOf(db.keySet()));
        when(settings.getString(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("global")))
                .thenAnswer(i -> db.get(i.<String>getArgument(0)));
        runner.withBean(ISettingsService.class, () -> settings)
                .withPropertyValues("plaintext.ablagen.lokal.basis=" + basis, "plaintext.speicher.files.art=lokal",
                        "plaintext.speicher.files.pfad=files", "plaintext.speicher.files.passwort=nie")
                .run(ctx -> {
                    KonfigurierteAblagenRegister r = ctx.getBean(KonfigurierteAblagenRegister.class);
                    assertThat(r.vorgabenNachSettings()).isEqualTo(2);
                    assertThat(db).containsEntry("speicher.files.art", "lokal").containsEntry("speicher.files.pfad", "files")
                            .doesNotContainKey("speicher.files.passwort");
                    assertThat(r.vorgabenNachSettings()).as("nie überschreiben").isZero();
                    db.put("speicher.files.pfad", "neu");
                    db.put("speicher.extra.art", "lokal");
                    db.put("speicher.extra.pfad", "extra");
                    assertThat(r.namen()).containsExactly("extra", "files");
                    // files hat noch passwort aus der yml und wird darum abgewiesen; extra kommt nur aus settings
                    assertThat(r.ablage("files")).isEmpty();
                    r.ablage("extra").orElseThrow().schreibe("x.txt", new byte[]{1}, null);
                    assertThat(basis.resolve("extra/x.txt")).exists();
                });
        runner.withBean(ISettingsService.class, () -> settings)
                .withPropertyValues("plaintext.ablagen.lokal.basis=" + basis, "plaintext.speicher.files.art=lokal",
                        "plaintext.speicher.files.pfad=files")
                .run(ctx -> {
                    ctx.getBean(KonfigurierteAblagenRegister.class).ablage("files").orElseThrow().schreibe("y.txt", new byte[]{1}, null);
                    assertThat(basis.resolve("neu/y.txt")).as("Pfad aus settings gilt").exists();
                });
    }

    /**
     * Fund claude2 / Entscheid chef (299): ein Speicher kann über settings kein fremdes Secret an einen fremden Host
     * schicken. Die Art wird mit einem fremden Verweis gar nicht erst aufgerufen; nur speicher.<name>.* gilt.
     */
    @Test
    void keinAbflussEinesFremdenSecrets() {
        java.util.List<Map<String, String>> geoeffnet = new java.util.concurrent.CopyOnWriteArrayList<>();
        DateiAblageArt netz = new DateiAblageArt() {
            @Override
            public String art() {
                return "netz";
            }

            @Override
            public DateiAblage oeffne(String name, Map<String, String> konfig) {
                geoeffnet.add(konfig);   // hier würde eine echte Art das Secret auflösen und senden
                return benannt(name);
            }
        };
        runner.withBean("netz", DateiAblageArt.class, () -> netz)
                .withPropertyValues("plaintext.speicher.boese.art=netz", "plaintext.speicher.boese.url=https://fremd.example",
                        "plaintext.speicher.boese.passwort-secret=nextcloud-app",
                        "plaintext.speicher.quer.art=netz", "plaintext.speicher.quer.url=https://fremd.example",
                        "plaintext.speicher.quer.Passwort-Secret=speicher.gut.passwort",
                        "plaintext.speicher.trick.art=netz", "plaintext.speicher.trick.passwort-secret=speicher.trick",
                        "plaintext.speicher.gut.art=netz", "plaintext.speicher.gut.url=https://cloud.example",
                        "plaintext.speicher.gut.passwort-secret=speicher.gut.passwort")
                .run(ctx -> {
                    DateiAblagenRegister r = ctx.getBean(DateiAblagenRegister.class);
                    assertThat(r.ablage("boese")).as("beliebiges vorhandenes Secret").isEmpty();
                    assertThat(r.ablage("quer")).as("Secret eines anderen Speichers").isEmpty();
                    assertThat(r.ablage("trick")).as("nur der Präfix ohne Feld").isEmpty();
                    assertThat(geoeffnet).as("die Art sah keinen fremden Verweis").isEmpty();
                    assertThat(r.ablage("gut")).isPresent();
                    assertThat(geoeffnet).singleElement().satisfies(k ->
                            assertThat(k).containsEntry("passwort-secret", "speicher.gut.passwort"));
                });
    }

    /**
     * Karte 2544: ein Aspekt (TimingAspect) legt einen CGLIB-Proxy um das Register. Der Proxy steht dann selbst in
     * {@code weitere} und ist nie {@code this}; namen() rief sich ohne Proxy-Abgleich endlos auf (StackOverflowError
     * beim Start von app 2.2140.0/2.2142.0).
     */
    @Test
    void eigenerProxyZaehltNichtAlsWeiteresRegister(@TempDir Path basis) {
        runner.withUserConfiguration(Weitere.class, Proxy.class)
                .withPropertyValues("plaintext.speicher.doku.art=codeservice", "plaintext.speicher.doku.ordner=/a")
                .run(ctx -> {
                    DateiAblagenRegister r = ctx.getBean(KonfigurierteAblagenRegister.class);
                    assertThat(r.getClass().getName()).contains("SpringCGLIB");
                    assertThat(r.namen()).containsExactly("db", "doku", "sidecar");
                    assertThat(r.ablage("sidecar").orElseThrow().name()).isEqualTo("aus-sidecars");
                    assertThat(r.ablage("gibtsnicht")).isEmpty();
                });
    }

    /** Karte 2544: zwei konfigurierte Register sehen sich gegenseitig in {@code weitere}; sie dürfen sich nicht im Kreis rufen. */
    @Test
    void zweiKonfigurierteRegisterRufenSichNichtImKreis() {
        runner.withUserConfiguration(Weitere.class, Zweites.class)
                .withPropertyValues("plaintext.speicher.doku.art=codeservice", "plaintext.speicher.doku.ordner=/a")
                .run(ctx -> {
                    assertThat(ctx.getBeansOfType(KonfigurierteAblagenRegister.class)).hasSize(2);
                    for (KonfigurierteAblagenRegister r : ctx.getBeansOfType(KonfigurierteAblagenRegister.class).values()) {
                        assertThat(r.namen()).containsExactly("db", "doku", "sidecar");
                        assertThat(r.ablage("gibtsnicht")).isEmpty();
                    }
                });
    }

    @Configuration
    static class Zweites {
        @Bean
        KonfigurierteAblagenRegister zweitesRegister(Environment env,
                                                     ObjectProvider<DateiAblageArt> arten,
                                                     ObjectProvider<DateiAblagenRegister> weitere,
                                                     ObjectProvider<ISettingsService> settings) {
            return new AblagenAutoConfiguration().konfigurierteAblagenRegister(env, arten, weitere, settings);
        }
    }

    @Configuration
    static class Proxy {
        @Bean
        static BeanPostProcessor registerProxy() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof KonfigurierteAblagenRegister)) {
                        return bean;
                    }
                    ProxyFactory f = new ProxyFactory(bean);
                    f.setProxyTargetClass(true);
                    return f.getProxy();
                }
            };
        }
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
