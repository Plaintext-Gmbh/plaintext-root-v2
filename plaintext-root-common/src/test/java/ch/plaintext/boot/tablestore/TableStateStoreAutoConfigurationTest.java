/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.tablestore;

import ch.plaintext.PlaintextSecurity;
import ch.plaintext.boot.plugins.jsf.userprofile.UserPreferencesBackingBean;
import ch.plaintext.boot.table.TableState;
import ch.plaintext.boot.table.TableStateOwner;
import ch.plaintext.boot.table.TableStateStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Karte 2086, Teil B: die mitgelieferte Ablage kommt per AutoConfiguration und nur, solange die App
 * keine eigene {@link TableStateStore}-Bean hat.
 *
 * @author info@plaintext.ch
 * @since 2026
 */
class TableStateStoreAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withBean(UserPreferencesBackingBean.class, () -> mock(UserPreferencesBackingBean.class))
            .withConfiguration(AutoConfigurations.of(TableStateStoreAutoConfiguration.class));

    @Test
    @DisplayName("Ohne eigene Bean greift der Default: UserPreferenceTableStateStore und TableStateOwner")
    void ohneEigeneBeanGreiftDerDefault() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(TableStateStore.class);
            assertThat(ctx.getBean(TableStateStore.class)).isInstanceOf(UserPreferenceTableStateStore.class);
            assertThat(ctx).hasSingleBean(TableStateOwner.class);
        });
    }

    @Test
    @DisplayName("Eigene TableStateStore-Bean ersetzt den Default - der wird gar nicht erst angelegt")
    void eigeneBeanErsetztDenDefault() {
        runner.withUserConfiguration(EigeneAblage.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(TableStateStore.class);
            assertThat(ctx.getBean(TableStateStore.class)).isInstanceOf(InventarStore.class);
            assertThat(ctx).doesNotHaveBean(UserPreferenceTableStateStore.class);
            assertThat(ctx).hasSingleBean(TableStateOwner.class);
        });
    }

    @Test
    @DisplayName("Wie in app/guild: ein @ComponentScan ueber das Paket holt die AutoConfiguration NICHT als Konfiguration herein")
    void scanWieAppUndGuildFindetKeinenZweitenStore() {
        new WebApplicationContextRunner()
                .withBean(UserPreferencesBackingBean.class, () -> mock(UserPreferencesBackingBean.class))
                .withUserConfiguration(ScanWieGuild.class)
                .withConfiguration(AutoConfigurations.of(TableStateStoreAutoConfiguration.class))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(TableStateStore.class);
                    assertThat(ctx.getBean(TableStateStore.class)).isInstanceOf(InventarStore.class);
                });
    }

    @Test
    @DisplayName("TableStateOwner liest Benutzer und Mandant beim Aufruf aus PlaintextSecurity")
    void ownerAusDerSecurity() {
        PlaintextSecurity security = mock(PlaintextSecurity.class);
        when(security.getUser()).thenReturn("anna");
        when(security.getMandat()).thenReturn("guild42");
        runner.withBean(PlaintextSecurity.class, () -> security).run(ctx -> {
            TableStateOwner owner = ctx.getBean(TableStateOwner.class);
            assertThat(owner.getUser()).isEqualTo("anna");
            assertThat(owner.getMandat()).isEqualTo("guild42");
            when(security.getMandat()).thenReturn("plaintext");
            assertThat(owner.getMandat()).isEqualTo("plaintext");
        });
    }

    @Test
    @DisplayName("Ohne PlaintextSecurity startet der Kontext trotzdem; der Owner liefert null")
    void ownerOhneSecurity() {
        runner.run(ctx -> {
            TableStateOwner owner = ctx.getBean(TableStateOwner.class);
            assertThat(owner.getUser()).isNull();
            assertThat(owner.getMandat()).isNull();
        });
    }

    /** Eine App-eigene Ablage, wie das Inventar sie mit seinem TableUserStateService anhaengen wird. */
    static class InventarStore implements TableStateStore {
        @Override
        public TableState load(String page) {
            return new TableState();
        }

        @Override
        public void save(String page, TableState state) {
            // Test: nichts zu sichern
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EigeneAblage {
        @Bean
        TableStateStore inventarStore() {
            return new InventarStore();
        }
    }

    /** Scannt das Paket der AutoConfiguration wie app/guild ({@code @ComponentScan("ch.plaintext")}). */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = TableStateStoreAutoConfiguration.class,
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Test.*"))
    static class ScanWieGuild {
        @Bean
        TableStateStore inventarStore() {
            return new InventarStore();
        }
    }
}
