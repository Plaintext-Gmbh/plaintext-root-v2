/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.settings.service;

import ch.plaintext.boot.menu.MenuItemImpl;
import ch.plaintext.settings.ISettingsService;
import ch.plaintext.settings.SettingsKeys;
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
public class MenuePlatzierungSettingsService implements SmartInitializingSingleton {

    static final String PREFIX = "plaintext.menu.platzierung.";
    private static final Pattern SCHLUESSEL = Pattern.compile(
            "^" + Pattern.quote(PREFIX) + "([^.]+)\\.(parent|title|order|icon|sichtbar)$");

    private final ApplicationContext context;
    private final ObjectProvider<ISettingsService> settings;

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
    public void anwenden() {
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
            return;
        }
        context.getBeansOfType(MenuItemImpl.class).values().forEach(item -> {
            Map<String, String> eintraege = werte.get(item.getMenuId());
            if (eintraege == null) {
                return;
            }
            if (eintraege.containsKey("parent")) {
                item.setParent(eintraege.get("parent"));
            }
            if (eintraege.containsKey("title")) {
                item.setTitle(eintraege.get("title"));
            }
            if (eintraege.containsKey("order")) {
                try {
                    item.setOrder(Integer.parseInt(eintraege.get("order").trim()));
                } catch (NumberFormatException e) {
                    // ungueltige Zahl: Wert ignorieren, Annotation bleibt
                }
            }
            if (eintraege.containsKey("icon")) {
                item.setIcon(eintraege.get("icon"));
            }
            if (eintraege.containsKey("sichtbar")) {
                item.setSichtbar(Boolean.parseBoolean(eintraege.get("sichtbar").trim()));
            }
        });
    }
}
