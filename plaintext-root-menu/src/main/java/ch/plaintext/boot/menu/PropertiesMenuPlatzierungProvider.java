/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.menu;

import ch.plaintext.modules.ModulApiUmsetzung;

import java.util.Optional;

/** Standard {@link MenuPlatzierungProvider}: reads {@code plaintext.menu.platzierung}. */
@ModulApiUmsetzung(beschreibung = "Returns the menu placement configured under plaintext.menu.platzierung for a menuId.",
        seiteneffekte = ModulApiUmsetzung.Seiteneffekte.KEINE,
        hinweise = {"An application bean of type MenuPlatzierungProvider replaces this one"},
        beispiele = {"fuer(\"terminal\")"})
public class PropertiesMenuPlatzierungProvider implements MenuPlatzierungProvider {

    private final MenuPlatzierungProperties properties;

    public PropertiesMenuPlatzierungProvider(MenuPlatzierungProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<MenuPlatzierung> fuer(String menuId) {
        return properties.fuer(menuId);
    }
}
