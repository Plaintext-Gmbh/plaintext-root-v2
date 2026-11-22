/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.settings.service;

import ch.plaintext.boot.menu.MenuItemImpl;
import ch.plaintext.settings.ISettingsService;
import ch.plaintext.settings.SettingsKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Karte 2469: Menueplatzierung aus den Root-Settings. Wie ModulZugriff/ModuleRoleService wird die
 * Platzierung ({@code plaintext.menu.platzierung.<menuId>.parent|title|order|icon|sichtbar}, Mandant
 * global) NACH dem Start auf die registrierten Menue-Items angewendet und bei
 * {@code EinstellungGeaendert} unter dem Prefix erneut — ohne Neustart. Ohne admin-settings in der
 * App ist der Dienst ein No-Op; ohne Eintraege bleibt die Annotation des Moduls wirksam.
 *
 * @since 3.76.0
 */
@Component
@Slf4j
public class MenuePlatzierungSettingsService implements SmartInitializingSingleton {

    static final String PREFIX = "plaintext.menu.platzierung.";
    private static final Pattern SCHLUESSEL = Pattern.compile(
            "^" + Pattern.quote(PREFIX) + "([^.]+)\\.(parent|title|order|icon|sichtbar)$");

    private final ApplicationContext context;
    private final ObjectProvider<ISettingsService> settings;
    /** Gemerkte Ursprungswerte je menuId und Feld, um gelöschte Schlüssel zurücksetzen zu können. */
    private final Map<String, Map<String, Object>> urspruenge = new HashMap<>();

    public MenuePlatzierungSettingsService(ApplicationContext context, ObjectProvider<ISettingsService> settings) {
        this.context = context;
        this.settings = settings;
    }

    @Override
    public void afterSingletonsInstantiated() {
        anwenden();
    }

    /** Reagiert auf geänderte Platzierungs-Schluessel (Ereignis aus admin-settings). */
    @EventListener
    public void aufEinstellung(EinstellungGeaendert ereignis) {
        if (ereignis.key() != null && ereignis.key().startsWith(PREFIX)) {
            anwenden();
        }
    }

    /** Liest die Platzierung aus den Settings und wendet sie auf alle Menue-Items an. */
    public synchronized void anwenden() {
        ISettingsService s = settings.getIfAvailable();
        if (s == null) {
            return;
        }
        Map<String, Map<String, String>> werte = new HashMap<>();
        for (String key : s.getAllKeys(SettingsKeys.MANDAT_GLOBAL)) {
            Matcher m = SCHLUESSEL.matcher(key == null ? "" : key);
            if (m.matches()) {
                werte.computeIfAbsent(m.group(1), id -> new HashMap<>())
                        .put(m.group(2), s.getString(key, SettingsKeys.MANDAT_GLOBAL));
            }
        }
        if (werte.isEmpty()) {
            zuruecksetzen();
            return;
        }
        zuruecksetzen(werte);
        context.getBeansOfType(MenuItemImpl.class).values().forEach(item -> {
            Map<String, String> eintraege = werte.get(item.getMenuId());
            if (eintraege == null) {
                return;
            }
            Map<String, Object> merken = urspruenge.computeIfAbsent(item.getMenuId(), id -> new HashMap<>());
            if (eintraege.containsKey("parent")) {
                merken.putIfAbsent("parent", item.getParent());
                item.setParent(eintraege.get("parent"));
            }
            if (eintraege.containsKey("title")) {
                merken.putIfAbsent("title", item.getTitle());
                item.setTitle(eintraege.get("title"));
            }
            if (eintraege.containsKey("order")) {
                try {
                    merken.putIfAbsent("order", item.getOrder());
                    item.setOrder(Integer.parseInt(eintraege.get("order").trim()));
                } catch (NumberFormatException e) {
                    log.warn("Menueplatzierung: order '{}' fuer menuId '{}' keine Zahl, ignoriert",
                            eintraege.get("order"), item.getMenuId());
                }
            }
            if (eintraege.containsKey("icon")) {
                merken.putIfAbsent("icon", item.getIcon());
                item.setIcon(eintraege.get("icon"));
            }
            if (eintraege.containsKey("sichtbar")) {
                merken.putIfAbsent("sichtbar", item.isSichtbar());
                item.setSichtbar(Boolean.parseBoolean(eintraege.get("sichtbar").trim()));
            }
        });
    }

    /** Setzt Felder zurueck, deren Schluessel es nicht mehr gibt (alle, wenn keine Settings mehr da sind). */
    private void zuruecksetzen() {
        zuruecksetzen(Map.of());
    }

    private void zuruecksetzen(Map<String, Map<String, String>> aktuelle) {
        if (urspruenge.isEmpty()) {
            return;
        }
        context.getBeansOfType(MenuItemImpl.class).values().forEach(item -> {
            Map<String, Object> merken = urspruenge.get(item.getMenuId());
            if (merken == null) {
                return;
            }
            Map<String, String> eintraege = aktuelle.getOrDefault(item.getMenuId(), Map.of());
            merken.forEach((feld, wert) -> {
                if (eintraege.containsKey(feld)) {
                    return; // wird gleich neu gesetzt
                }
                switch (feld) {
                    case "parent" -> item.setParent((String) wert);
                    case "title" -> item.setTitle((String) wert);
                    case "order" -> item.setOrder((Integer) wert);
                    case "icon" -> item.setIcon((String) wert);
                    case "sichtbar" -> item.setSichtbar((Boolean) wert);
                    default -> { }
                }
            });
            merken.keySet().removeIf(feld -> !eintraege.containsKey(feld));
        });
        urspruenge.values().removeIf(Map::isEmpty);
    }
}
