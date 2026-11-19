/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.menu;

/**
 * A pure group item (no link) the framework creates when a placement names a parent that does
 * not exist ({@code plaintext.menu.gruppen.<title>}).
 *
 * @param order sort order on the top level
 * @param icon  icon class, e.g. {@code pi pi-wrench}
 * @author info@plaintext.ch
 * @since 3.59.0
 */
public record MenuGruppe(Integer order, String icon) {
}
