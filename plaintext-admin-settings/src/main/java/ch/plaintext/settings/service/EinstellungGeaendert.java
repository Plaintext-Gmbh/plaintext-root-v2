/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.settings.service;

/**
 * Karte 2390: ein Wert in settings wurde gesetzt oder gelöscht ({@code neu == null}). Für Verbraucher, die eine
 * Einstellung ohne Neustart übernehmen (z. B. {@link ModulZugriff}). Werte von Geheimnissen stehen nie in settings.
 */
public record EinstellungGeaendert(String key, String mandat, String alt, String neu) {
}
