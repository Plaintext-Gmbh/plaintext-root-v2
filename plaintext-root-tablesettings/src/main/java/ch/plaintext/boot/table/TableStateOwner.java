/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

/**
 * Wem ein Tabellenstand gehoert: Benutzer und Mandant des laufenden Requests (Karte 2086).
 *
 * <p><b>Stabile API.</b> Gegenstueck zu {@link TableStateStore}: ein Store ist pro Person, aber er
 * soll die Person nicht selbst aus der Security ziehen muessen. root stellt eine Umsetzung als
 * Bean bereit (aus {@code PlaintextSecurity}, AutoConfiguration in plaintext-root-common, nur
 * solange die App keine eigene {@code TableStateOwner}-Bean hat). Ein eigener Store laesst sich
 * diese Bean injizieren und fragt sie bei jedem {@code load}/{@code save}.</p>
 *
 * <p>Die Werte gelten fuer den Request, in dem gefragt wird - die Umsetzung darf sie nicht
 * zwischenspeichern.</p>
 *
 * @author info@plaintext.ch
 * @since 2026
 */
public interface TableStateOwner {

    /** Anmeldename des Benutzers; {@code null} ohne Anmeldung. */
    String getUser();

    /** Mandant des Benutzers; {@code null} ohne Mandant. */
    String getMandat();
}
