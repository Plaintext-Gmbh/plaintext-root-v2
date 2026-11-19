/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.menu;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Menu placement from outside the module: per application, moves an item of a loaded module to a
 * different parent, order, title or icon, or hides it from the menu.
 *
 * <pre>
 * plaintext:
 *   menu:
 *     platzierung:
 *       terminal: { parent: Werkzeuge, order: 10, title: Terminal, icon: pi pi-desktop }
 *       drawio-bilder: { sichtbar: false }
 *     gruppen:
 *       Werkzeuge: { order: 70, icon: pi pi-wrench }
 * </pre>
 *
 * <p>Key of {@code platzierung} is the {@code menuId}; key of {@code gruppen} is the title of a group
 * the framework creates if no item with that title exists. Without configuration nothing changes.</p>
 *
 * @author info@plaintext.ch
 * @since 3.59.0
 */
@ConfigurationProperties(prefix = "plaintext.menu")
public class MenuPlatzierungProperties {

    /** menuId -&gt; placement. */
    private Map<String, MenuPlatzierung> platzierung = new LinkedHashMap<>();

    /** Group title -&gt; group. */
    private Map<String, MenuGruppe> gruppen = new LinkedHashMap<>();

    public Map<String, MenuPlatzierung> getPlatzierung() {
        return platzierung;
    }

    public void setPlatzierung(Map<String, MenuPlatzierung> platzierung) {
        this.platzierung = platzierung == null ? new LinkedHashMap<>() : platzierung;
    }

    public Map<String, MenuGruppe> getGruppen() {
        return gruppen;
    }

    public void setGruppen(Map<String, MenuGruppe> gruppen) {
        this.gruppen = gruppen == null ? new LinkedHashMap<>() : gruppen;
    }

    /**
     * @param menuId the {@code menuId}, may be {@code null}
     * @return the configured placement, or empty
     */
    public Optional<MenuPlatzierung> fuer(String menuId) {
        return menuId == null ? Optional.empty() : Optional.ofNullable(platzierung.get(menuId));
    }
}
