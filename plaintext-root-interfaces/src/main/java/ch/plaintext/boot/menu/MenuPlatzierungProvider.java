/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.menu;

import ch.plaintext.modules.ModulApi;

import java.util.Optional;

/**
 * Supplies the placement of a menu item by its {@code menuId}. The standard implementation reads
 * {@link MenuPlatzierungProperties}; an application that has to compute the placement registers its
 * own bean, which replaces the standard one.
 *
 * @author info@plaintext.ch
 * @since 3.59.0
 */
@ModulApi(art = ModulApi.Art.SCHNITTSTELLE, stabilitaet = ModulApi.Stabilitaet.NEU)
public interface MenuPlatzierungProvider {

    /**
     * @param menuId the stable {@code menuId} of the item
     * @return the placement, or empty if the item keeps the module's own values
     */
    Optional<MenuPlatzierung> fuer(String menuId);
}
