/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.menu;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Applies {@code plaintext.menu.platzierung} to the scanned menu items before they are registered
 * (Karte 2377, ROOT-2): sets parent/order/title/icon/sichtbar, creates missing parents listed in
 * {@code plaintext.menu.gruppen} as link-less group items, and puts an item back on its own
 * placement if its new parent neither exists nor is a group, or if the move closes a cycle.
 * Problems are logged as WARN; the start never fails because of a placement.
 *
 * <p>The effective {@code menuId} is frozen before an item moves, so its placement key, its
 * auto-role {@code ROLE_MENU_<menuId>} and its {@code moduleId} stay the same.</p>
 */
@Slf4j
final class MenuPlatzierungAnwender {

    /** What {@link #anwenden} did; {@code gruppen} must be registered alongside the items. */
    record Ergebnis(int umgehaengt, List<MenuItemImpl> gruppen, Set<String> erkannteMenuIds) {
    }

    private record Vorgabe(String parent, int order, String title, String icon) {
        static Vorgabe von(MenuItemImpl item) {
            return new Vorgabe(item.getParent(), item.getOrder(), item.getTitle(), item.getIcon());
        }

        void zurueck(MenuItemImpl item) {
            item.setParent(parent);
            item.setOrder(order);
            item.setTitle(title);
            item.setIcon(icon);
            item.setPlatzierungVorgabe("");
        }

        String text() {
            String ort = parent == null || parent.isBlank() ? title : parent + " | " + title;
            return ort + ", order " + order;
        }
    }

    private MenuPlatzierungAnwender() {
    }

    /**
     * @param items           the scanned items, changed in place
     * @param provider        the placement source; {@code null} means no placement
     * @param gruppen         configured groups (title -&gt; group), may be empty
     * @param konfigurierteIds menuIds named in the configuration, for the "unknown menuId" WARN
     * @return counts, the created group items and the menuIds that were recognised
     */
    static Ergebnis anwenden(List<MenuItemImpl> items, MenuPlatzierungProvider provider,
                             Map<String, MenuGruppe> gruppen, Collection<String> konfigurierteIds) {
        Set<String> erkannt = new TreeSet<>();
        for (MenuItemImpl item : items) {
            erkannt.add(item.getEffectiveMenuId());
        }
        if (provider == null) {
            return new Ergebnis(0, List.of(), erkannt);
        }
        for (String id : konfigurierteIds) {
            if (!erkannt.contains(id)) {
                log.warn("MenuPlatzierung: unbekannte menuId '{}' in plaintext.menu.platzierung - ignoriert. "
                        + "Erkannte menuIds: {}", id, erkannt);
            }
        }

        Map<MenuItemImpl, Vorgabe> verschoben = new IdentityHashMap<>();
        for (MenuItemImpl item : items) {
            String id = item.getEffectiveMenuId();
            Optional<MenuPlatzierung> platz = provider.fuer(id);
            if (platz.isEmpty()) {
                continue;
            }
            Vorgabe vorgabe = Vorgabe.von(item);
            item.setMenuId(id);
            setze(item, platz.get());
            verschoben.put(item, vorgabe);
            item.setPlatzierungVorgabe(vorgabe.text());
        }

        List<MenuItemImpl> neueGruppen = gruppenAnlegen(items, verschoben, gruppen);
        List<MenuItemImpl> alle = new ArrayList<>(items);
        alle.addAll(neueGruppen);
        zyklenAufloesen(alle, verschoben);

        log.info("MenuPlatzierung : {} Eintraege umgehaengt, {} Gruppen angelegt - erkannte menuIds: {}",
                verschoben.size(), neueGruppen.size(), erkannt);
        return new Ergebnis(verschoben.size(), neueGruppen, erkannt);
    }

    private static void setze(MenuItemImpl item, MenuPlatzierung platz) {
        if (platz.parent() != null) {
            item.setParent(platz.parent().trim());
        }
        if (platz.order() != null) {
            item.setOrder(platz.order());
        }
        if (platz.title() != null && !platz.title().isBlank()) {
            item.setTitle(platz.title().trim());
        }
        if (platz.icon() != null) {
            item.setIcon(platz.icon().trim());
        }
        if (platz.sichtbar() != null) {
            item.setSichtbar(platz.sichtbar());
        }
    }

    private static List<MenuItemImpl> gruppenAnlegen(List<MenuItemImpl> items, Map<MenuItemImpl, Vorgabe> verschoben,
                                                     Map<String, MenuGruppe> gruppen) {
        Set<String> titel = new HashSet<>();
        for (MenuItemImpl item : items) {
            titel.add(item.getTitle());
        }
        Map<String, MenuItemImpl> neu = new LinkedHashMap<>();
        List<MenuItemImpl> zurueckgesetzt = new ArrayList<>();
        for (Map.Entry<MenuItemImpl, Vorgabe> e : verschoben.entrySet()) {
            MenuItemImpl item = e.getKey();
            String parent = item.getParent();
            if (parent == null || parent.isBlank() || titel.contains(parent) || neu.containsKey(parent)) {
                continue;
            }
            MenuGruppe gruppe = gruppen.get(parent);
            if (gruppe == null) {
                log.warn("MenuPlatzierung: Elter '{}' fuer menuId '{}' ist weder ein Menueeintrag noch in "
                        + "plaintext.menu.gruppen - Eintrag bleibt auf der Vorgabe ({})",
                        parent, item.getMenuId(), e.getValue().text());
                e.getValue().zurueck(item);
                zurueckgesetzt.add(item);
                continue;
            }
            neu.put(parent, gruppeAus(parent, gruppe, item));
        }
        zurueckgesetzt.forEach(verschoben::remove);
        return new ArrayList<>(neu.values());
    }

    private static MenuItemImpl gruppeAus(String titel, MenuGruppe gruppe, MenuItemImpl vorlage) {
        MenuItemImpl g = new MenuItemImpl();
        g.setGruppe(true);
        g.setTitle(titel);
        g.setParent("");
        g.setCommand("");
        g.setOrder(gruppe.order() == null ? 0 : gruppe.order());
        g.setIcon(gruppe.icon() == null ? "" : gruppe.icon());
        g.setAccessPolicy(vorlage.getAccessPolicy());
        g.setSecurityProvider(vorlage.getSecurityProvider());
        g.setMenuVisibilityProvider(vorlage.getMenuVisibilityProvider());
        g.setBeanFactory(vorlage.getBeanFactory());
        return g;
    }

    /** An item whose parent chain leads back to itself goes back to its own placement. */
    private static void zyklenAufloesen(List<MenuItemImpl> alle, Map<MenuItemImpl, Vorgabe> verschoben) {
        List<MenuItemImpl> zurueckgesetzt = new ArrayList<>();
        for (Map.Entry<MenuItemImpl, Vorgabe> e : verschoben.entrySet()) {
            if (imZyklus(e.getKey(), alle)) {
                log.warn("MenuPlatzierung: Zyklus ueber parent bei menuId '{}' - Eintrag bleibt auf der Vorgabe ({})",
                        e.getKey().getMenuId(), e.getValue().text());
                e.getValue().zurueck(e.getKey());
                zurueckgesetzt.add(e.getKey());
            }
        }
        zurueckgesetzt.forEach(verschoben::remove);
    }

    private static boolean imZyklus(MenuItemImpl start, List<MenuItemImpl> alle) {
        Map<String, MenuItemImpl> nachTitel = new HashMap<>();
        for (MenuItemImpl item : alle) {
            nachTitel.putIfAbsent(item.getTitle(), item);
        }
        Set<MenuItemImpl> besucht = Collections.newSetFromMap(new IdentityHashMap<>());
        MenuItemImpl aktuell = start;
        while (aktuell != null && besucht.add(aktuell)) {
            String parent = aktuell.getParent();
            if (parent == null || parent.isBlank()) {
                return false;
            }
            aktuell = nachTitel.get(parent);
            if (aktuell == start) {
                return true;
            }
        }
        return false;
    }
}
