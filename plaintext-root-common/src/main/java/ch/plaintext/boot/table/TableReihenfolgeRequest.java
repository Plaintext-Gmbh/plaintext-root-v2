/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import jakarta.faces.component.UIComponent;
import jakarta.faces.component.UIData;
import jakarta.faces.context.FacesContext;
import org.primefaces.component.api.UIColumn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Liest die neue Spaltenreihenfolge aus dem Faces-Request (Karte 1853, Fix #32/#34: die
 * Ereignis-Differenzierung, damit weder der Kopf-Zug noch der Panel-Zug verloren gehen).
 * Ausgelagert aus {@link TableSettings}, damit diese Klasse unter der Groessen-Leitplanke
 * bleibt (PlaintextGroessenLeitplankeTest).
 *
 * <p>PF schickt beide Reihenfolge-Felder mit jedem Submit mit — die orderList liegt im selben
 * Formular wie die Tabelle. Deshalb entscheidet nur das Behavior-Event, welches Feld zaehlt:
 * {@code reorder} → {@code _values} der Liste, {@code colReorder} → {@code columnOrder} der
 * Tabelle (gemessen: claude1, Inventar-Pilot 3.19/3.20).</p>
 *
 * @author info@plaintext.ch
 * @since 2026
 */
public final class TableReihenfolgeRequest {

    private TableReihenfolgeRequest() {
    }

    /** Das Behavior-Event des Requests ({@code jakarta.faces.behavior.event}), leer wenn keins. */
    public static String event() {
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces == null || faces.getExternalContext() == null) {
            return "";
        }
        String event = faces.getExternalContext().getRequestParameterMap()
                .get("jakarta.faces.behavior.event");
        return event != null ? event : "";
    }

    /**
     * Reihenfolge aus dem versteckten {@code <id>_values}-Select der Reihenfolge-Liste
     * (p:orderList im Panel). Der Browser sendet alle Eintraege in ihrer neuen Ordnung als
     * Mehrfach-Parameter. Erkannte wird das Feld daran, dass alle Werte bekannte Spalten-Keys sind.
     */
    public static List<String> ausOrderListValuesFeld(List<String> bekannteKeys) {
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces == null || faces.getExternalContext() == null) {
            return List.of();
        }
        Set<String> keys = new HashSet<>(bekannteKeys);
        for (Map.Entry<String, String[]> eintrag
                : faces.getExternalContext().getRequestParameterValuesMap().entrySet()) {
            if (!eintrag.getKey().endsWith("_values")) {
                continue;
            }
            String[] werte = eintrag.getValue();
            if (werte == null || werte.length == 0 || !keys.containsAll(java.util.Arrays.asList(werte))) {
                continue;
            }
            return new ArrayList<>(List.of(werte));
        }
        return List.of();
    }

    /**
     * Neue Spaltenreihenfolge aus dem versteckten {@code <clientId>_columnOrder}-Feld des
     * DataTable-Requests (kommagetrennte Client-Ids der gerenderten Spaltenköpfe). Die Ids
     * sind die Client-Ids der UIColumn-Kinder — bei Spalten ohne explizite Id generische
     * j_idt-Ids, die sich NICHT über SearchExpressions auflösen lassen. Der Abgleich laeuft
     * deshalb direkt ueber die Kinder der Tabelle; {@code keyMapper} bildet den Kopftext
     * auf den Spalten-Schluessel ab ({@link TableSettings#keyFromHeader}).
     */
    public static List<String> ausColumnOrderFeld(UIData data, Function<String, String> keyMapper) {
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces == null || faces.getExternalContext() == null) {
            return List.of();
        }
        String wert = faces.getExternalContext().getRequestParameterMap()
                .get(data.getClientId(faces) + "_columnOrder");
        if (wert == null || wert.isBlank()) {
            return List.of();
        }
        Map<String, String> clientIdZuKey = new HashMap<>();
        for (UIComponent kind : data.getChildren()) {
            if (kind instanceof UIColumn col) {
                String key = keyMapper.apply(col.getHeaderText());
                if (key != null && !key.isBlank()) {
                    clientIdZuKey.put(col.getClientId(faces), key);
                }
            }
        }
        List<String> keys = new ArrayList<>();
        for (String thId : wert.split(",")) {
            String key = clientIdZuKey.get(thId.trim());
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }
}
