/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.tablestore;

import ch.plaintext.PlaintextSecurity;
import ch.plaintext.boot.plugins.jsf.userprofile.UserPreferencesBackingBean;
import ch.plaintext.boot.table.TableStateOwner;
import ch.plaintext.boot.table.TableStateStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Die mitgelieferte Ablage fuer {@code pt:tableSettings} (Karte 2086): {@link TableStateOwner} aus
 * {@code PlaintextSecurity} und {@link UserPreferenceTableStateStore} - beide nur, solange die App
 * keine eigene Bean dieses Typs anbietet. Eine App ersetzt die Ablage also mit einer eigenen
 * {@link TableStateStore}-Bean, ohne Property und ohne Exclude.
 *
 * <p><b>Bewusst ohne {@code @Configuration}/{@code @AutoConfiguration}.</b> app und guild scannen
 * zusaetzlich zu {@code @SpringBootApplication} mit einem eigenen
 * {@code @ComponentScan("ch.plaintext")} - ohne den Filter, der AutoConfigurations aus dem Scan
 * nimmt. Mit Stereotyp landete diese Klasse dort als gewoehnliche Konfiguration, und
 * {@code @ConditionalOnMissingBean} wuerde ausgewertet, bevor die Konfiguration der App ihre Beans
 * angemeldet hat: Default und App-Store stuenden nebeneinander. So kommt die Klasse nur ueber
 * {@code META-INF/spring/...AutoConfiguration.imports} herein, also verzoegert nach allen
 * App-Beans; ihre {@code @Bean}-Methoden gelten im Lite-Modus (sie rufen sich nicht gegenseitig
 * auf). Abgesichert durch {@code TableStateStoreAutoConfigurationTest}.</p>
 *
 * @author info@plaintext.ch
 * @since 2026
 */
@ConditionalOnWebApplication
public class TableStateStoreAutoConfiguration {

    /** Benutzer und Mandant aus der Security, beim Aufruf gelesen (die Werte gelten je Request). */
    @Bean
    @ConditionalOnMissingBean(TableStateOwner.class)
    public TableStateOwner tableStateOwner(ObjectProvider<PlaintextSecurity> security) {
        return new TableStateOwner() {
            @Override
            public String getUser() {
                PlaintextSecurity s = security.getIfAvailable();
                return s == null ? null : s.getUser();
            }

            @Override
            public String getMandat() {
                PlaintextSecurity s = security.getIfAvailable();
                return s == null ? null : s.getMandat();
            }
        };
    }

    /** Stand je Benutzer und Mandant in {@code UserPreference} - nur ohne eigene Ablage der App. */
    @Bean
    @ConditionalOnMissingBean(TableStateStore.class)
    public UserPreferenceTableStateStore userPreferenceTableStateStore(UserPreferencesBackingBean preferences,
                                                                       TableStateOwner owner) {
        return new UserPreferenceTableStateStore(preferences, owner);
    }
}
