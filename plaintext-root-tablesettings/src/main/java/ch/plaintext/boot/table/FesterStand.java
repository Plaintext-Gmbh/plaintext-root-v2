/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ein festgehaltener Spaltenstand: Breiten, Gesamtbreite und Reihenfolge (Karte 1853 Runde 7,
 * Daniel 13.11.). Erfasst im Ansicht-Reiter, wandert mit ins Profil; ein Doppelklick auf den
 * Modus-Knopf stellt die Tabelle genau darauf zurueck.
 *
 * <p>Kopiert beim Erfassen und beim Anwenden: der laufende Stand aendert sich bei jedem Ziehen,
 * der feste darf das nicht.</p>
 *
 * @param columnWidths Spaltenschluessel -&gt; Breite inkl. Einheit; fehlende = Vorgabe der Seite
 * @param columnOrder  Reihenfolge der Schluessel; {@code null} = Reihenfolge der Seite
 * @param totalWidth   Gesamtbreite; {@code null} = Summe der Spalten
 * @author info@plaintext.ch
 * @since 2026
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FesterStand(Map<String, String> columnWidths, List<String> columnOrder, Integer totalWidth)
        implements Serializable {

    public FesterStand {
        columnWidths = columnWidths == null ? new LinkedHashMap<>() : new LinkedHashMap<>(columnWidths);
        columnOrder = columnOrder == null ? null : new ArrayList<>(columnOrder);
    }

    /** Haelt den laufenden Stand fest. */
    static FesterStand aus(TableState state) {
        return new FesterStand(state.getColumnWidths(), state.getColumnOrder(), state.getTotalWidth());
    }

    /** Setzt Breiten, Gesamtbreite und Reihenfolge des laufenden Stands genau auf diesen. */
    void anwenden(TableState state) {
        state.setColumnWidths(new LinkedHashMap<>(columnWidths));
        state.setColumnOrder(columnOrder == null ? null : new ArrayList<>(columnOrder));
        state.setTotalWidth(totalWidth);
    }
}
