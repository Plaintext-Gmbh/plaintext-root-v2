/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import java.io.Serializable;

/**
 * Was eine Seite nach dem Anwenden eines Profils zusaetzlich zu tun hat (Karte 2086, aus dem
 * Inventar uebernommen).
 *
 * <p>Ein Profil traegt auch den seitenspezifischen Stand ({@link TableState#getPageState()}):
 * Filter, Schalter. Der Bedienbereich zeichnet danach die Tabelle neu, weiss aber nicht, dass eine
 * Seite ihre Zeilen erst aus diesen Werten aufbaut (z.B. einen Baum). Ohne diesen Einhaenger
 * stuende dort der alte Inhalt unter den neuen Filtern.</p>
 *
 * <p>Eigenes Interface statt {@link Runnable}, damit ein Lambda mit der Backing-Bean in die
 * Session serialisiert werden kann.</p>
 *
 * @author info@plaintext.ch
 * @since 2026
 */
@FunctionalInterface
public interface NachProfilwechsel extends Serializable {

    /** Laeuft am Ende von {@link TableSettings#onProfileSelected()}, auch ohne gefundenes Profil. */
    void ausfuehren();
}
