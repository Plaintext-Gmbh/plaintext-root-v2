/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.menu;

/**
 * Placement of one menu item, set by the application from outside the module
 * ({@code plaintext.menu.platzierung.<menuId>}). Every field is optional; {@code null} keeps the
 * value the module declares in {@code @MenuAnnotation}.
 *
 * @param parent  title of the parent item; {@code ""} means top level
 * @param order   sort order within the parent
 * @param title   displayed title
 * @param icon    icon class, e.g. {@code pi pi-desktop}
 * @param sichtbar {@code false} keeps the item out of the menu while its page stays reachable by link
 * @author info@plaintext.ch
 * @since 3.59.0
 */
public record MenuPlatzierung(String parent, Integer order, String title, String icon, Boolean sichtbar) {
}
