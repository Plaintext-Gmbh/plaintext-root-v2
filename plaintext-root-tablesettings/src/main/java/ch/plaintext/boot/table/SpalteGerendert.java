/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import java.io.Serializable;

/**
 * Ob eine Spalte gerade ueberhaupt in der Tabelle steht - unabhaengig davon, ob der Benutzer sie
 * angekreuzt hat (Karte 2086, aus dem Inventar uebernommen).
 *
 * <p>Manche Spalten haengen an einer Bedingung der Seite und nicht an der Spaltenwahl, etwa eine
 * Aufklapper-Spalte, die erst nach einer Suche erscheint. Ohne diese Auskunft rechnet
 * {@link TableSettings#spaltenSumme()} ihre Breite mit, obwohl kein {@code th} entsteht; bei
 * {@code table-layout: fixed} verteilt der Browser den Ueberschuss dann auf alle Spalten.</p>
 *
 * <p>Eigenes Interface statt {@code BooleanSupplier}, damit ein Lambda mit der Backing-Bean in die
 * Session serialisiert werden kann: ein Lambda ist nur serialisierbar, wenn sein
 * Funktionsinterface es ist.</p>
 *
 * @author info@plaintext.ch
 * @since 2026
 */
@FunctionalInterface
public interface SpalteGerendert extends Serializable {

    /** {@code true}, solange die Seite die Spalte in die Tabelle stellt. */
    boolean trifftZu();
}
