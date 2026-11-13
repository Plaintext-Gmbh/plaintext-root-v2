/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import jakarta.faces.component.UIComponent;
import jakarta.faces.component.UIData;
import jakarta.faces.context.FacesContext;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.component.api.UIColumn;
import org.primefaces.component.api.UITable;
import org.primefaces.expression.SearchExpressionUtils;
import org.primefaces.model.SortMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Zustandslose Hilfen von {@link TableSettings} rund um die Tabellenkomponente: Tabelle finden,
 * Sortierfelder lesen, Spalten-Kinder umordnen, Breiten-Meldungen parsen. Ausgelagert mit Karte
 * 2086, damit {@link TableSettings} unter der Groessen-Leitplanke bleibt; inhaltlich unveraendert.
 *
 * @author info@plaintext.ch
 * @since 2026
 */
@Slf4j
final class TableKomponenten {

    private TableKomponenten() {
    }

    /** Die Tabelle zum Suchausdruck; {@code null}, wenn unbekannt oder ohne Faces-Kontext. */
    static UIData tabelleFinden(String tabelle, String page) {
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces == null || faces.getViewRoot() == null || tabelle == null || tabelle.isBlank()) {
            return null;
        }
        UIComponent gefunden;
        try {
            gefunden = SearchExpressionUtils.contextlessOptionalResolveComponent(faces, faces.getViewRoot(), tabelle);
        } catch (RuntimeException e) { // NOSONAR - eine Einstellung darf den Ajax-Aufruf nicht abbrechen
            log.debug("[TableSettings:{}] tabelleAngleichen | nicht aufloesbar | tabelle={} | {}", page, tabelle, e.getMessage());
            return null;
        }
        if (gefunden instanceof UIData data) {
            return data;
        }
        log.debug("[TableSettings:{}] tabelleAngleichen | keine Tabelle | tabelle={}", page, tabelle);
        return null;
    }

    /**
     * Die Sortierfelder der gerenderten Spalten der Tabelle, die gerade ausgewertet wird; {@code null},
     * wenn keine Tabelle greifbar ist (Unit-Test, Aufruf ausserhalb des Aufbaus). Dieselbe
     * Rechnung wie in {@code UITable.initSortBy}, damit die Filterung genau das durchlaesst, was
     * PrimeFaces danach auch findet.
     */
    static Set<String> sortierbareFelder(String page) {
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces == null) {
            return null; // NOSONAR - null = "nicht pruefbar", nicht "keine Felder"
        }
        try {
            if (!(UIComponent.getCurrentComponent(faces) instanceof UITable<?> tabelle)) {
                return null; // NOSONAR - siehe oben
            }
            Set<String> felder = new HashSet<>();
            tabelle.forEachColumn(spalte -> {
                SortMeta meta = SortMeta.of(faces, tabelle.getVar(), spalte);
                if (meta != null && meta.getField() != null) {
                    felder.add(meta.getField());
                }
                return true;
            });
            return felder;
        } catch (RuntimeException e) { // NOSONAR - eine Einstellung darf den Seitenaufbau nicht abbrechen
            log.debug("[TableSettings:{}] sortierbareFelderDerTabelle | nicht ermittelbar | {}", page, e.getMessage());
            return null; // NOSONAR - siehe oben
        }
    }

    /**
     * Ordnet die Spalten-Kinder der Tabelle nach {@code order} an. Idempotent, schluesselbasiert
     * (ueber den Kopftext); Kinder ohne zuordenbaren Kopftext bleiben an ihrem Platz.
     */
    static void kinderOrdnen(UIData data, List<String> order, UnaryOperator<String> keyFromHeader) {
        if (order == null || order.isEmpty()) {
            return;
        }
        List<UIComponent> kinder = data.getChildren();
        Map<String, UIComponent> spalten = new HashMap<>();
        Set<String> zielKeys = new LinkedHashSet<>(order);
        for (UIComponent kind : kinder) {
            if (kind instanceof UIColumn) {
                String key = keyFromHeader.apply(((UIColumn) kind).getHeaderText());
                if (key != null && zielKeys.contains(key)) {
                    spalten.put(key, kind);
                }
            }
        }
        if (spalten.size() < 2) {
            return;
        }
        List<String> ziel = order.stream().filter(spalten::containsKey).toList();
        List<UIComponent> gemischt = new ArrayList<>(kinder);
        java.util.Iterator<String> it = ziel.iterator();
        for (int i = 0; i < gemischt.size() && it.hasNext(); i++) {
            UIComponent kind = gemischt.get(i);
            if (kind instanceof UIColumn) {
                String key = keyFromHeader.apply(((UIColumn) kind).getHeaderText());
                if (key != null && zielKeys.contains(key)) {
                    gemischt.set(i, spalten.get(it.next()));
                }
            }
        }
        kinder.clear();
        kinder.addAll(gemischt);
    }

    /** Zahl aus einem Meldeparameter, oder {@code null} wenn unbrauchbar oder unter {@code minimum}. */
    static Integer zuBreite(String roh, int minimum) {
        if (roh == null || roh.isBlank()) {
            return null;
        }
        try {
            int wert = Math.round(Float.parseFloat(roh.trim()));
            return wert >= minimum ? wert : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
