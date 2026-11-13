/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import java.io.Serializable;

/**
 * Wie eine Tabelle blaettert, solange der Benutzer nichts eingestellt hat (Karte 2086, aus dem
 * Inventar uebernommen): Paginator oben, unten, beides oder aus, dazu die Zeilen pro Seite.
 *
 * <p>Die Vorgabe gehoert in den Konstruktor von {@link TableSettings} und nicht an die Tabelle:
 * der Bedienbereich wird VOR der Tabelle gerendert. Stuende die Vorgabe am Tabellen-Tag, zeigten
 * die Ankreuzfelder beim ersten Aufruf den falschen Stand. Ein gespeicherter Benutzerwert geht
 * der Vorgabe immer vor.</p>
 *
 * @param oben           Blaetterbalken ueber der Tabelle
 * @param unten          Blaetterbalken unter der Tabelle
 * @param zeilenProSeite Seitengroesse; wirkt nur, wenn mindestens eine Position gesetzt ist.
 *                       Werte unter 1 ergeben {@link TableSettings#VORGABE_ZEILEN}.
 * @author info@plaintext.ch
 * @since 2026
 */
public record PagingVorgabe(boolean oben, boolean unten, int zeilenProSeite) implements Serializable {

    /** Die uebliche Seitengroesse, wenn eine Tabelle bisher keine hatte. */
    public static final int ZEILEN_STANDARD = 50;

    /** Kein Blaetterbalken - die Tabelle zeigt alle Zeilen. */
    public static PagingVorgabe aus() {
        return new PagingVorgabe(false, false, ZEILEN_STANDARD);
    }

    /** Balken nur unter der Tabelle. */
    public static PagingVorgabe unten(int zeilen) {
        return new PagingVorgabe(false, true, zeilen);
    }

    /** Balken nur ueber der Tabelle. */
    public static PagingVorgabe oben(int zeilen) {
        return new PagingVorgabe(true, false, zeilen);
    }

    /** Balken oben und unten - was PrimeFaces ohne paginatorPosition von selbst tut. */
    public static PagingVorgabe beides(int zeilen) {
        return new PagingVorgabe(true, true, zeilen);
    }

    /** Ist ueberhaupt ein Balken vorgesehen? */
    public boolean aktiv() {
        return oben || unten;
    }
}
