/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files.web;

import ch.plaintext.boot.menu.MenuAnnotation;
import ch.plaintext.files.service.FilesRollen;

/**
 * Persönliche Dateiablage unter «Misc» (Karte 1559, Karte 2354).
 *
 * <p><b>Rollen.</b> Sichtbar und benutzbar mit der Rolle {@code files}; Admin und Root kommen auch hinein und sehen
 * dort die Ablagen aller. Admin und Root stehen ausdrücklich in {@code roles}: unter der Menüpolitik
 * {@code STRICT} kämen sie ohnehin durch, unter {@code PERMISSIVE} zählt aber allein diese Liste - ohne die beiden
 * Einträge hinge es an der Politik der Anwendung, ob ein Admin die Ablage sieht. Bis Karte 2354 stand hier
 * {@code USER}: jeder Angemeldete hatte die Ablage.</p>
 *
 * <p><b>{@code menuId}.</b> Fest, weil die abgeleitete Kennung aus Eltern und Titel gebildet wird: mit dem Umzug
 * unter «Misc» wäre aus {@code ROLE_MENU_FILES} sonst {@code ROLE_MENU_MISC_FILES} geworden, und wer die
 * automatische Rolle schon trägt, hätte den Eintrag still verloren.</p>
 *
 * <p>Der Zugriff auf Seite, REST und MCP hängt an diesem Eintrag ({@link FilesZugriff}).</p>
 */
@MenuAnnotation(
        title = "Files",
        link = "files/ablage.html",
        parent = MiscMenu.TITEL,
        order = 65,
        icon = "pi pi-folder-open",
        moduleId = FilesMenu.MODUL,
        menuId = "files",
        roles = {FilesRollen.FILES, FilesRollen.ADMIN, FilesRollen.ROOT}
)
public class FilesMenu {

    static final String MODUL = "files";
}
