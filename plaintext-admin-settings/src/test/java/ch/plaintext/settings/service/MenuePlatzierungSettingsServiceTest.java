/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.settings.service;

import ch.plaintext.boot.menu.MenuItemImpl;
import ch.plaintext.settings.ISettingsService;
import ch.plaintext.settings.SettingsKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Karte 2469: Menueplatzierung aus den Root-Settings, ohne Neustart wirksam. */
class MenuePlatzierungSettingsServiceTest {

    private final Map<String, String> global = new TreeMap<>();

    private ISettingsService settings() {
        ISettingsService s = mock(ISettingsService.class);
        when(s.getAllKeys(SettingsKeys.MANDAT_GLOBAL)).thenAnswer(a -> List.copyOf(global.keySet()));
        when(s.getString(anyString(), eq(SettingsKeys.MANDAT_GLOBAL)))
                .thenAnswer(a -> global.get(a.getArgument(0, String.class)));
        return s;
    }

    private static MenuePlatzierungSettingsService dienst(ISettingsService s, Map<String, MenuItemImpl> items) {
        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("settings", s);
        items.forEach(bf::addBean);
        org.springframework.context.ApplicationContext ctx = mock(org.springframework.context.ApplicationContext.class);
        when(ctx.getBeansOfType(MenuItemImpl.class)).thenReturn(items);
        return new MenuePlatzierungSettingsService(ctx,
                bf.getBeanProvider(ISettingsService.class));
    }

    private static MenuItemImpl item(String menuId) {
        MenuItemImpl item = new MenuItemImpl();
        item.setMenuId(menuId);
        item.setTitle("Dateien");
        item.setParent("Werkzeuge");
        item.setOrder(65);
        item.setSichtbar(true);
        return item;
    }

    @Test
    void platzierungAusSettingsWirdAufItemsAngewendet() {
        global.put("plaintext.menu.platzierung.files.parent", "Marthaler");
        global.put("plaintext.menu.platzierung.files.title", "Meine Dateien");
        global.put("plaintext.menu.platzierung.files.order", "10");
        global.put("plaintext.menu.platzierung.files.sichtbar", "false");
        global.put("plaintext.menu.platzierung.anderes.icon", "pi pi-x");
        MenuItemImpl files = item("files");
        MenuItemImpl anderes = item("anderes");
        MenuePlatzierungSettingsService d = dienst(settings(), Map.of("files", files, "anderes", anderes));

        d.anwenden();

        assertThat(files.getParent()).isEqualTo("Marthaler");
        assertThat(files.getTitle()).isEqualTo("Meine Dateien");
        assertThat(files.getOrder()).isEqualTo(10);
        assertThat(files.isSichtbar()).isFalse();
        assertThat(anderes.getIcon()).isEqualTo("pi pi-x");
        assertThat(anderes.getTitle()).isEqualTo("Dateien"); // nur icon gesetzt
    }

    @Test
    void ohneEinstellungenBleibtDieAnnotationWirksam() {
        MenuItemImpl files = item("files");
        MenuePlatzierungSettingsService d = dienst(settings(), Map.of("files", files));
        d.anwenden();
        assertThat(files.getParent()).isEqualTo("Werkzeuge");
        assertThat(files.getOrder()).isEqualTo(65);
    }

    @Test
    void ungueltigeOrderWirdIgnoriert() {
        global.put("plaintext.menu.platzierung.files.order", "keine-zahl");
        MenuItemImpl files = item("files");
        dienst(settings(), Map.of("files", files)).anwenden();
        assertThat(files.getOrder()).isEqualTo(65);
    }

    @Test
    void geloeschterSchluesselSetztUrsprungswertZurueck() {
        global.put("plaintext.menu.platzierung.files.parent", "Marthaler");
        MenuItemImpl files = item("files");
        MenuePlatzierungSettingsService d = dienst(settings(), Map.of("files", files));
        d.anwenden();
        assertThat(files.getParent()).isEqualTo("Marthaler");

        global.clear(); // Schluessel geloescht
        d.anwenden();
        assertThat(files.getParent()).isEqualTo("Werkzeuge"); // Ursprung wiederhergestellt
    }

    @Test
    void settingVorRegistrierungWirktBeiNaechsterAnwendung() {
        global.put("plaintext.menu.platzierung.files.parent", "Marthaler");
        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("settings", settings());
        org.springframework.context.ApplicationContext ctx = mock(org.springframework.context.ApplicationContext.class);
        MenuePlatzierungSettingsService d = new MenuePlatzierungSettingsService(ctx,
                bf.getBeanProvider(ISettingsService.class));
        java.util.Map<String, MenuItemImpl> items = new java.util.HashMap<>();
        when(ctx.getBeansOfType(MenuItemImpl.class)).thenAnswer(a -> items);

        d.anwenden(); // vor Registrierung: kein Item
        items.put("files", item("files")); // Laufzeit-Modul registriert sich
        d.anwendenPeriodisch(); // naechster Lauf (Cron), kein Ereignis noetig
        assertThat(items.get("files").getParent()).isEqualTo("Marthaler");
    }

    @Test
    void ereignisUnterPrefixWendetNeuAn() {
        MenuItemImpl files = item("files");
        MenuePlatzierungSettingsService d = dienst(settings(), Map.of("files", files));
        d.aufEinstellung(new EinstellungGeaendert("app.eigener.schluessel", SettingsKeys.MANDAT_GLOBAL, "a", "b"));
        assertThat(files.getParent()).isEqualTo("Werkzeuge"); // fremder Key: keine Wirkung

        global.put("plaintext.menu.platzierung.files.parent", "Marthaler");
        d.aufEinstellung(new EinstellungGeaendert(
                "plaintext.menu.platzierung.files.parent", SettingsKeys.MANDAT_GLOBAL, "Werkzeuge", "Marthaler"));
        assertThat(files.getParent()).isEqualTo("Marthaler"); // eigener Prefix: sofort wirksam
    }
}
