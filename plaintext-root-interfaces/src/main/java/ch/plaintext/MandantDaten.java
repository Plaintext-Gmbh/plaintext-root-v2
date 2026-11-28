/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext;

import java.util.Map;

/**
 * Karte 2566/2571 (Presenter, Lösch-Lebenszyklus): ein Fachmodul gibt die Daten eines Mandanten heraus und löscht sie.
 * plaintext-modul-loeschung sammelt alle Beans dieses Typs: vor der Löschung gehen die Exporte per Mail an den
 * Kunden, danach ruft es {@link #loesche(String)} für jedes Modul auf.
 *
 * <p>Wer Daten ausserhalb der Datenbank hat (Dateien in einer Speicher-Ablage), setzt diese Schnittstelle um, sonst
 * bleiben sie nach der Löschung liegen. Datenbankzeilen mit einer Spalte {@code mandat} räumt die Löschung auch ohne
 * Mithilfe des Moduls weg.
 */
public interface MandantDaten {

    /** Kurzname des Moduls für Protokoll und Anzeige, etwa {@code praesentation}. */
    String modul();

    /**
     * Dateien für den Kunden, Dateiname → Inhalt (etwa jeder Foliensatz als PPTX). Leer, wenn das Modul nichts
     * exportiert. Wird vor {@link #loesche(String)} aufgerufen; ein Fehler hier bricht die Löschung ab.
     */
    default Map<String, byte[]> exportiere(String mandat) {
        return Map.of();
    }

    /** Löscht alle Daten des Mandanten in diesem Modul und liefert die Zahl gelöschter Einträge (Protokoll). */
    int loesche(String mandat);
}
