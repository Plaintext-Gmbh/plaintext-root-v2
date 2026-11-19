/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.menu;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Karte 2377 ROOT-1: Bindung von plaintext.menu.platzierung/gruppen und Vorrang einer eigenen Provider-Bean. */
class MenuPlatzierungPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MenuAutoConfiguration.class))
            .withPropertyValues("plaintext.menu.scan-package=ch.plaintext.boot.menu");

    private static ApplicationContextRunner mitKonzeptYaml(ApplicationContextRunner runner) throws IOException {
        List<PropertySource<?>> quellen = new YamlPropertySourceLoader()
                .load("konzept", new ClassPathResource("menu-platzierung-konzept.yaml"));
        return runner.withInitializer(ctx -> quellen.forEach(q -> ctx.getEnvironment().getPropertySources().addFirst(q)));
    }

    @Test
    void konzeptYamlBindetVollstaendig() throws IOException {
        mitKonzeptYaml(runner).run(context -> {
            assertNull(context.getStartupFailure());
            MenuPlatzierungProperties p = context.getBean(MenuPlatzierungProperties.class);
            assertEquals(new MenuPlatzierung("Werkzeuge", 10, "Terminal", "pi pi-desktop", null), p.getPlatzierung().get("terminal"));
            assertEquals(new MenuPlatzierung("Werkzeuge", 30, null, null, null), p.getPlatzierung().get("files"));
            assertEquals(new MenuPlatzierung(null, null, null, null, false), p.getPlatzierung().get("drawio-bilder"));
            assertEquals(Map.of("Werkzeuge", new MenuGruppe(70, "pi pi-wrench")), p.getGruppen());
            // module-roles unter demselben Präfix bleibt unberührt
            assertEquals("TERMINAL", context.getBean(ModuleRoleProperties.class).canonicalModuleRoles().get("terminal"));

            MenuPlatzierungProvider provider = context.getBean(MenuPlatzierungProvider.class);
            assertInstanceOf(PropertiesMenuPlatzierungProvider.class, provider);
            assertEquals(Optional.of(new MenuPlatzierung("Werkzeuge", 20, null, null, null)), provider.fuer("drawio"));
            assertTrue(provider.fuer("gibtesnicht").isEmpty());
            assertTrue(provider.fuer(null).isEmpty());
        });
    }

    @Test
    void ohneKonfigurationLeer() {
        runner.run(context -> {
            MenuPlatzierungProperties p = context.getBean(MenuPlatzierungProperties.class);
            assertTrue(p.getPlatzierung().isEmpty());
            assertTrue(p.getGruppen().isEmpty());
            assertTrue(context.getBean(MenuPlatzierungProvider.class).fuer("terminal").isEmpty());
        });
    }

    @Test
    void eigeneProviderBeanGewinnt() throws IOException {
        MenuPlatzierungProvider eigen = menuId -> Optional.of(new MenuPlatzierung("Misc", 1, null, null, null));
        mitKonzeptYaml(runner).withBean(MenuPlatzierungProvider.class, () -> eigen).run(context -> {
            assertEquals(1, context.getBeansOfType(MenuPlatzierungProvider.class).size());
            assertEquals("Misc", context.getBean(MenuPlatzierungProvider.class).fuer("terminal").orElseThrow().parent());
        });
    }
}
