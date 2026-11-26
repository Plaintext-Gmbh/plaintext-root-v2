/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.testsupport.beispiel;

import ch.plaintext.boot.menu.MenuAnnotation;

/** Beispielmodul für den ModulManifestTest (Karte 2377 ROOT-7). */
@MenuAnnotation(title = "Terminal", link = "/misc-terminal.html", parent = "Misc", order = 10, menuId = "terminal",
        moduleId = "terminal", roles = {"terminal"})
public class BeispielMenu {
}
