/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files.web;

import ch.plaintext.boot.menu.MenuAnnotation;
import ch.plaintext.files.service.FilesRollen;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Das Elternmenü «Misc», unter dem {@link FilesMenu} hängt (Karte 2354).
 *
 * <p><b>Warum das Modul es mitbringt.</b> Ein Kind, dessen Elternmenü es in der Anwendung nicht gibt, hängt der
 * {@code MenuModelBuilder} nirgends ein - der Eintrag wäre für niemanden über das Menü erreichbar. root selbst hat
 * kein «Misc»; ohne diese Klasse müsste jede Anwendung erst eins anlegen, bevor die Ablage wieder im Menü steht.</p>
 *
 * <p><b>Warum abschaltbar.</b> Eine Anwendung mit eigenem «Misc» hätte den Titel doppelt; der Builder behält dann
 * den mit der kleineren {@code order} und schreibt bei jedem Menüaufbau eine Warnung. Sie setzt
 * {@code plaintext.files.misc-menue=false} und muss ihr eigenes «Misc» dann für die Rolle {@code files} öffnen,
 * sonst sieht ein Benutzer mit dieser Rolle allein den Eintrag nicht (die Seite bleibt über die Adresse offen).</p>
 *
 * <p>Dieselben Rollen und dasselbe Modul wie das Kind: wer die Ablage nicht hat, soll kein leeres «Misc» sehen.</p>
 */
@ConditionalOnProperty(prefix = "plaintext.files", name = "misc-menue", havingValue = "true", matchIfMissing = true)
@MenuAnnotation(
        title = MiscMenu.TITEL,
        link = "index.html",
        order = 60,
        icon = "pi pi-briefcase",
        moduleId = FilesMenu.MODUL,
        menuId = "misc",
        roles = {FilesRollen.FILES, FilesRollen.ADMIN, FilesRollen.ROOT}
)
public class MiscMenu {

    static final String TITEL = "Misc";
}
