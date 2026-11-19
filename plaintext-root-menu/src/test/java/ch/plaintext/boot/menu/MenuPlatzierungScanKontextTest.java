/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.menu;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Karte 2377 ROOT-2, Review fable2: Huelle und Apps scannen {@code ch.plaintext}; MenuAutoConfiguration kommt dann
 * per Component-Scan (Bean-Name {@code menuAutoConfiguration}) und der Standard-Provider darf nicht als eigener gelten.
 */
class MenuPlatzierungScanKontextTest {

    private static final String[] PLATZIERUNG = {
            "plaintext.menu.scan-package=ch.plaintext.boot.menu",
            "plaintext.menu.platzierung.test_menu.parent=Werkzeuge",
            "plaintext.menu.platzierung.test_menu.order=10",
            "plaintext.menu.gruppen.Werkzeuge.order=70"};

    @Configuration
    @ComponentScan(basePackages = "ch.plaintext.boot.menu")
    static class Huelle {
    }

    private static MenuItemImpl testMenu(org.springframework.context.ApplicationContext context) {
        return context.getBeansOfType(MenuItemImpl.class).values().stream()
                .filter(m -> "Test Menu".equals(m.getTitle())).findFirst().orElseThrow();
    }

    /** Probe-Test von fable2 (schlug auf 3d581b31 fehl: 0 umgehaengt). */
    @Test
    void platzierungWirktAuchWennMenuAutoConfigurationGescanntWird() {
        new ApplicationContextRunner().withUserConfiguration(Huelle.class).withPropertyValues(PLATZIERUNG)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals("Werkzeuge", testMenu(context).getParent(), "Platzierung aus den Properties wurde nicht angewendet");
                    assertEquals(10, testMenu(context).getOrder());
                });
    }

    /** Wie eine App: @SpringBootApplication scannt ch.plaintext UND die AutoConfiguration ist importiert. */
    @Test
    void scanUndAutoConfigurationZusammenWieInDerApp() {
        new ApplicationContextRunner().withUserConfiguration(Huelle.class)
                .withConfiguration(AutoConfigurations.of(MenuAutoConfiguration.class)).withPropertyValues(PLATZIERUNG)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals("Werkzeuge", testMenu(context).getParent());
                    assertEquals(1, context.getBeansOfType(MenuPlatzierungProvider.class).size());
                });
    }

    @Test
    void eigeneProviderBeanGewinntAuchImScanKontext() {
        // withBean statt einer verschachtelten @Configuration: die laege im gescannten Paket und fiele in jeden Test hier
        MenuPlatzierungProvider eigen = id -> "test_menu".equals(id)
                ? Optional.of(new MenuPlatzierung(null, 99, null, null, null)) : Optional.empty();
        new ApplicationContextRunner().withUserConfiguration(Huelle.class)
                .withBean("eigenerProvider", MenuPlatzierungProvider.class, () -> eigen).withPropertyValues(PLATZIERUNG)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(99, testMenu(context).getOrder());
                    assertEquals("", testMenu(context).getParent(), "eigener Provider setzt keinen parent");
                });
    }
}
