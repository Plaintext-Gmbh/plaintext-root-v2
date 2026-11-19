/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.menu;

import org.junit.jupiter.api.Test;
import org.primefaces.model.menu.MenuElement;
import org.primefaces.model.menu.MenuModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Karte 2377 ROOT-2: Platzierung anwenden, Gruppen anlegen, sichtbar=false. */
class MenuPlatzierungAnwenderTest {

    private static MenuItemImpl item(String title, String parent, int order, String link, String menuId, String moduleId) {
        MenuItemImpl item = new MenuItemImpl();
        item.setTitle(title);
        item.setParent(parent);
        item.setOrder(order);
        item.setCommand(link);
        item.setMenuId(menuId);
        item.setModuleId(moduleId);
        return item;
    }

    private static MenuPlatzierungProvider aus(Map<String, MenuPlatzierung> map) {
        return id -> Optional.ofNullable(map.get(id));
    }

    private static MenuPlatzierungAnwender.Ergebnis anwenden(List<MenuItemImpl> items, Map<String, MenuPlatzierung> platz,
                                                            Map<String, MenuGruppe> gruppen) {
        return MenuPlatzierungAnwender.anwenden(items, aus(platz), gruppen, platz.keySet());
    }

    @Test
    void haengtUmUndLegtGruppeAn() {
        MenuItemImpl terminal = item("Terminal", "Misc", 5, "terminal.html", "terminal", "terminal");
        MenuItemImpl misc = item("Misc", "", 80, "", "", "");
        List<MenuItemImpl> items = new ArrayList<>(List.of(terminal, misc));

        MenuPlatzierungAnwender.Ergebnis e = anwenden(items,
                Map.of("terminal", new MenuPlatzierung("Werkzeuge", 10, "Konsole", "pi pi-desktop", null)),
                Map.of("Werkzeuge", new MenuGruppe(70, "pi pi-wrench")));

        assertEquals(1, e.umgehaengt());
        assertEquals("Werkzeuge", terminal.getParent());
        assertEquals(10, terminal.getOrder());
        assertEquals("Konsole", terminal.getTitle());
        assertEquals("pi pi-desktop", terminal.getIcon());
        assertEquals("Misc | Terminal, order 5", terminal.getPlatzierungVorgabe());
        assertEquals(1, e.gruppen().size());
        MenuItemImpl g = e.gruppen().get(0);
        assertTrue(g.isGruppe());
        assertEquals("Werkzeuge", g.getTitle());
        assertEquals("", g.getParent());
        assertEquals("", g.getCommand());
        assertEquals(70, g.getOrder());
        assertEquals("pi pi-wrench", g.getIcon());
        assertTrue(e.erkannteMenuIds().contains("terminal"));
    }

    @Test
    void menuIdAutoRolleUndModuleIdBleibenNachDemUmhaengen() {
        // ohne explizite menuId: die aus parent+title abgeleitete Id wird vor dem Umhaengen eingefroren
        MenuItemImpl files = item("Dateien", "Misc", 5, "files.html", "", "files");
        String idVorher = files.getEffectiveMenuId();
        String rolleVorher = files.getAutoRole();

        anwenden(new ArrayList<>(List.of(files, item("Misc", "", 1, "", "", ""))),
                Map.of(idVorher, new MenuPlatzierung("", 3, "Files", null, null)), Map.of());

        assertEquals("", files.getParent());
        assertEquals("Files", files.getTitle());
        assertEquals(idVorher, files.getEffectiveMenuId());
        assertEquals(rolleVorher, files.getAutoRole());
        assertEquals("files", files.getModuleId());
    }

    @Test
    void ohnePlatzierungBleibtAllesAufDerVorgabe() {
        MenuItemImpl a = item("A", "Misc", 5, "a.html", "a", "");
        MenuPlatzierungAnwender.Ergebnis e = anwenden(new ArrayList<>(List.of(a)), Map.of(), Map.of());

        assertEquals(0, e.umgehaengt());
        assertTrue(e.gruppen().isEmpty());
        assertEquals("Misc", a.getParent());
        assertEquals(5, a.getOrder());
        assertEquals("", a.getPlatzierungVorgabe());
        assertTrue(a.isSichtbar());
    }

    @Test
    void ohneProviderNichts() {
        MenuItemImpl a = item("A", "Misc", 5, "a.html", "a", "");
        MenuPlatzierungAnwender.Ergebnis e = MenuPlatzierungAnwender.anwenden(List.of(a), null, Map.of(), Set.of());
        assertEquals(0, e.umgehaengt());
        assertEquals("Misc", a.getParent());
    }

    @Test
    void unbekannteMenuIdBrichtNichtAb() {
        MenuItemImpl a = item("A", "", 5, "a.html", "a", "");
        MenuPlatzierungAnwender.Ergebnis e = anwenden(new ArrayList<>(List.of(a)),
                Map.of("gibtesnicht", new MenuPlatzierung("X", 1, null, null, null)), Map.of());
        assertEquals(0, e.umgehaengt());
        assertEquals("", a.getParent());
    }

    @Test
    void elterWederDaNochGruppeBleibtAufVorgabe() {
        MenuItemImpl a = item("A", "Misc", 5, "a.html", "a", "");
        MenuPlatzierungAnwender.Ergebnis e = anwenden(new ArrayList<>(List.of(a, item("Misc", "", 1, "", "", ""))),
                Map.of("a", new MenuPlatzierung("Nirgends", 9, "Neu", null, null)), Map.of());

        assertEquals(0, e.umgehaengt());
        assertTrue(e.gruppen().isEmpty());
        assertEquals("Misc", a.getParent());
        assertEquals(5, a.getOrder());
        assertEquals("A", a.getTitle());
        assertEquals("", a.getPlatzierungVorgabe());
    }

    @Test
    void zyklusUeberParentBleibtAufVorgabe() {
        MenuItemImpl x = item("X", "", 1, "", "x", "");
        MenuItemImpl y = item("Y", "X", 2, "y.html", "y", "");
        // X unter Y gehaengt: X -> Y -> X
        MenuPlatzierungAnwender.Ergebnis e = anwenden(new ArrayList<>(List.of(x, y)),
                Map.of("x", new MenuPlatzierung("Y", null, null, null, null)), Map.of());

        assertEquals(0, e.umgehaengt());
        assertEquals("", x.getParent());
        assertEquals("X", y.getParent());
    }

    @Test
    void sichtbarFalseBleibtImRegisterUndIsOn() {
        MenuItemImpl bilder = item("Bilder", "", 5, "drawio-bilder.html", "drawio-bilder", "drawio");
        anwenden(new ArrayList<>(List.of(bilder)),
                Map.of("drawio-bilder", new MenuPlatzierung(null, null, null, null, false)), Map.of());

        assertFalse(bilder.isSichtbar());
        assertTrue(bilder.isOn(), "PageGuard geht ueber isOn(): die Seite bleibt per Link erreichbar");
    }

    @Test
    void builderLaesstUnsichtbareWegUndRendertGruppeAlsSubmenu() {
        MenuItemImpl terminal = item("Terminal", "Misc", 5, "terminal.html", "terminal", "");
        MenuItemImpl bilder = item("Bilder", "Misc", 6, "bilder.html", "drawio-bilder", "");
        MenuItemImpl misc = item("Misc", "", 80, "", "", "");
        List<MenuItemImpl> items = new ArrayList<>(List.of(terminal, bilder, misc));
        Map<String, MenuPlatzierung> platz = new LinkedHashMap<>();
        platz.put("terminal", new MenuPlatzierung("Werkzeuge", 10, null, null, null));
        platz.put("drawio-bilder", new MenuPlatzierung("Werkzeuge", 20, null, null, false));
        MenuPlatzierungAnwender.Ergebnis e = anwenden(items, platz, Map.of("Werkzeuge", new MenuGruppe(70, null)));
        items.addAll(e.gruppen());

        MenuModel model = bauen(items);

        // Misc hat keine Kinder mehr und ist ein normaler Eintrag ohne Link; Werkzeuge ist Submenu mit nur Terminal
        List<String> oben = model.getElements().stream().map(MenuPlatzierungAnwenderTest::titel).toList();
        assertEquals(List.of("Werkzeuge", "Misc"), oben);
        PrimefacesSubmenu werkzeuge = (PrimefacesSubmenu) model.getElements().get(0);
        assertEquals(List.of("Terminal"), werkzeuge.getElements().stream().map(MenuPlatzierungAnwenderTest::titel).toList());
    }

    @Test
    void gruppeOhneSichtbaresKindFaelltWeg() {
        MenuItemImpl bilder = item("Bilder", "", 6, "bilder.html", "drawio-bilder", "");
        List<MenuItemImpl> items = new ArrayList<>(List.of(bilder));
        MenuPlatzierungAnwender.Ergebnis e = anwenden(items,
                Map.of("drawio-bilder", new MenuPlatzierung("Werkzeuge", 20, null, null, false)),
                Map.of("Werkzeuge", new MenuGruppe(70, null)));
        items.addAll(e.gruppen());

        assertTrue(bauen(items).getElements().isEmpty());
    }

    private static MenuModel bauen(List<MenuItemImpl> items) {
        ApplicationContext ctx = mock(ApplicationContext.class);
        Map<String, MenuItemImpl> beans = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            beans.put("m" + i, items.get(i));
        }
        when(ctx.getBeansOfType(MenuItemImpl.class)).thenReturn(beans);
        MenuModelBuilder builder = new MenuModelBuilder();
        ReflectionTestUtils.setField(builder, "applicationContext", ctx);
        return builder.buildMenuModel();
    }

    private static String titel(MenuElement element) {
        return element instanceof PrimefacesSubmenu s ? s.getLabel() : ((PrimefacesMenuItem) element).getValue().toString();
    }

    /** Im echten Kontext: Platzierung wirkt vor der Registrierung, Gruppe wird Bean, eigene Provider-Bean gewinnt. */
    @Test
    void imSpringKontextVorDerRegistrierung() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MenuAutoConfiguration.class))
                .withPropertyValues("plaintext.menu.scan-package=ch.plaintext.boot.menu",
                        "plaintext.menu.platzierung.test_menu.parent=Werkzeuge",
                        "plaintext.menu.platzierung.test_menu.order=10",
                        "plaintext.menu.gruppen.Werkzeuge.order=70",
                        "plaintext.menu.gruppen.Werkzeuge.icon=pi pi-wrench")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    Map<String, MenuItemImpl> beans = context.getBeansOfType(MenuItemImpl.class);
                    MenuItemImpl test = beans.values().stream().filter(m -> "Test Menu".equals(m.getTitle())).findFirst().orElseThrow();
                    assertEquals("Werkzeuge", test.getParent());
                    assertEquals(10, test.getOrder());
                    assertEquals("test_menu", test.getMenuId());
                    assertEquals("testmodule", test.getModuleId());
                    assertTrue(beans.values().stream().anyMatch(m -> m.isGruppe() && "Werkzeuge".equals(m.getTitle())));
                });
    }

    @Test
    void eigeneProviderBeanWirktImPostProcessor() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MenuAutoConfiguration.class))
                .withPropertyValues("plaintext.menu.scan-package=ch.plaintext.boot.menu",
                        "plaintext.menu.platzierung.test_menu.order=10")
                .withBean(MenuPlatzierungProvider.class,
                        () -> id -> "test_menu".equals(id) ? Optional.of(new MenuPlatzierung(null, 99, null, null, null)) : Optional.empty())
                .run(context -> {
                    MenuItemImpl test = context.getBeansOfType(MenuItemImpl.class).values().stream()
                            .filter(m -> "Test Menu".equals(m.getTitle())).findFirst().orElseThrow();
                    assertEquals(99, test.getOrder());
                });
    }
}
