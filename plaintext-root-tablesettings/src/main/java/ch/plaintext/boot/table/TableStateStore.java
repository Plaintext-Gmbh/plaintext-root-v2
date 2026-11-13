/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import java.util.List;

/**
 * Woher der Tabellenstand eines Benutzers kommt und wohin er geht.
 *
 * <p><b>Stabile API (Karte 2086).</b> Diese Schnittstelle liegt im eigenstaendigen Artefakt
 * {@code plaintext-root-tablesettings} und ist der Vertrag fuer jede Ablage. Sie traegt kein
 * {@code @StabileApi}, weil die Annotation in plaintext-root-common liegt und dieses Artefakt
 * ohne root auskommen muss; der Vertrag gilt trotzdem: Methoden kommen dazu (mit Default), keine
 * verschwindet oder aendert ihre Bedeutung ohne vorherige Abkuendigung.</p>
 *
 * <p><b>Die mitgelieferte Ablage.</b> In root-v2-Apps registriert plaintext-root-common
 * {@code UserPreferenceTableStateStore} (Stand je Benutzer <i>und Mandant</i> als JSON in
 * {@code UserPreference}) per AutoConfiguration - aber nur, solange die App <b>keine eigene</b>
 * {@code TableStateStore}-Bean hat ({@code @ConditionalOnMissingBean}). Eine App ersetzt die Ablage
 * also, indem sie eine Bean dieses Typs anbietet: ohne Property, ohne Exclude, ohne
 * {@code @Primary}. Die Backing-Bean laesst sich den Store injizieren und reicht ihn an
 * {@link TableSettings#init} weiter.</p>
 *
 * <p><b>Pro Person, ohne Security-Abfrage.</b> {@link TableSettings} kennt nur den
 * Seitenschluessel. Wem der Stand gehoert, liefert {@link TableStateOwner} (Benutzer, Mandant):
 * root stellt eine Umsetzung bereit, ein eigener Store laesst sie sich injizieren, statt selbst
 * die Security zu fragen.</p>
 *
 * <p><b>Format.</b> {@link TableState} ist ein reines Datenobjekt mit
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}: eine Ablage, die als JSON speichert, liest
 * aeltere und neuere Staende (neue Felder bleiben auf ihrem Default, unbekannte werden
 * ueberlesen). Wer einen eigenen Mapper nutzt, stellt dort ebenso
 * {@code FAIL_ON_UNKNOWN_PROPERTIES=false} ein.</p>
 *
 * <p><b>Aufrufhaeufigkeit.</b> {@code save} laeuft nach jeder Aenderung an der Anzeige, also
 * durchaus mehrmals je Sekunde, wenn jemand Haken setzt. Eine Umsetzung, die dabei synchron ins
 * Netz greift, macht die Spaltenwahl zaeh; die Sicherung nach aussen gehoert hinter eine
 * Verzoegerung.</p>
 *
 * @author info@plaintext.ch
 * @since 2026
 */
public interface TableStateStore {

    /**
     * Gespeicherter Stand der Seite fuer den aktuellen Benutzer.
     *
     * @param page Seitenschluessel, z.B. {@code "projects"}
     * @return der Stand; nie {@code null} — ohne gespeicherten Stand ein frischer
     */
    TableState load(String page);

    /**
     * Wie {@link #load(String)}, aber mit den Spaltenschluesseln der Seite.
     *
     * <p>Die Vorgabe reicht an {@link #load(String)} durch. Eine Ablage, die einen <b>Altbestand
     * in anderem Format</b> uebernimmt, braucht die Schluessel: eine gespeicherte Liste
     * <i>sichtbarer</i> Spalten sagt nur mit dem vollstaendigen Spaltensatz, welche Spalten
     * <i>ausgeblendet</i> waren. Genau das tut {@code UserPreferenceTableStateStore} mit
     * {@code UserPreference.tabellenSpalten}.</p>
     *
     * @param page       Seitenschluessel
     * @param columnKeys alle Spaltenschluessel der Seite, in Tabellenreihenfolge
     * @return der Stand; nie {@code null}
     */
    default TableState load(String page, List<String> columnKeys) {
        return load(page);
    }

    /**
     * Legt den Stand der Seite fuer den aktuellen Benutzer ab.
     *
     * @param page  Seitenschluessel, z.B. {@code "projects"}
     * @param state der zu sichernde Stand
     */
    void save(String page, TableState state);
}
