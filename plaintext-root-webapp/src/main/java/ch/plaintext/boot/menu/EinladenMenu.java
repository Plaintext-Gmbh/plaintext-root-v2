/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.menu;


/** Karte 2560: «Personen einladen», nur mit der Rolle einladen (SaaS-Instanz vergibt sie Neukunden). */
@MenuAnnotation(
    title = "Personen einladen",
    link = "einladen.html",
    order = 95,
    icon = "pi pi-user-plus",
    roles = {"einladen"}
)
public class EinladenMenu {
}
