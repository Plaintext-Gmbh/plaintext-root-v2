/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import lombok.Data;

import java.io.Serializable;

/**
 * Eine ein- und ausblendbare Spalte einer Tabelle mit Anzeige-Einstellungen.
 *
 * <p>Der {@code key} ist der Vertrag zwischen drei Stellen, die sich sonst nicht kennen: der Seite
 * (welche Spalte gerendert wird), dem gespeicherten Benutzerstand (welche Breite und Sichtbarkeit
 * dazugehoert) und dem Resize-Ereignis von PrimeFaces. Letzteres liefert <b>keine</b>
 * Spaltenkennung mit, sondern nur den Kopftext — deshalb traegt jede Spalte ihr {@code label}
 * mit, ueber das {@link TableSettings#keyFromHeader(String)} zurueckrechnet.</p>
 *
 * <p>Daraus folgt eine Regel, die man beim Lesen nicht sieht: <b>zwei Spalten derselben Tabelle
 * duerfen nicht denselben Kopftext haben.</b> Sonst landet eine gezogene Breite bei der falschen
 * Spalte. Ein Kopftext, der doppelt vorkommt, ist ohnehin fuer den Benutzer mehrdeutig.</p>
 *
 * @author info@plaintext.ch
 * @since 2026
 */
@Data
public class TableColumn implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Stabiler Schluessel, z.B. {@code "owner"}. Aendert sich nie — er steht in gespeicherten Staenden. */
    private String key;

    /** Kopftext der Spalte, wie er in der Tabelle steht, z.B. {@code "Owner"}. */
    private String label;

    /** Vorgabebreite in Pixeln; {@code 0} heisst "keine feste Breite" (Auto-Layout). */
    private int defaultWidth;

    /** Startet die Spalte sichtbar, solange der Benutzer nichts verstellt hat? */
    private boolean defaultVisible;

    /**
     * Pflichtspalte (Karte 1853): bleibt auch bei «Alle aus» sichtbar und laesst sich im
     * Spalten-Panel nicht abschalten. Der Griff zum Verschieben bleibt — ihre Position
     * darf der Benutzer weiterhin waehlen.
     */
    private boolean required;

    /**
     * Name in Spaltenliste und Reihenfolge, wenn er vom Kopftext abweichen soll (Karte 2086) - vor
     * allem fuer kopflose Knopf-, Symbol- und Auswahlspalten. {@code null}: der Kopftext, bei
     * kopflosen Spalten eine laufende Nummer (siehe {@link TableSettings#columnLabel(String)}).
     */
    private String listenLabel;

    /**
     * Steht die Spalte mit eigenem Schalter in der Spaltenliste (Karte 2086)? {@code false} fuer
     * Platzhalter und Spalten, hinter denen der einzige Weg zu einer Funktion steht: sie bleiben
     * sichtbar, auch bei «Alle aus». Strenger als {@link #required}, das einen gesperrten Schalter
     * zeigt.
     */
    private boolean waehlbar = true;

    /**
     * Laesst sich die Spalte einzeln in der Reihenfolge-Liste verschieben (Karte 2086)? {@code false}
     * fuer Spalten aus einem {@code p:columns}-Block: PrimeFaces sortiert den Block als eine Spalte.
     */
    private boolean verschiebbar = true;

    public TableColumn(String key, String label, int defaultWidth) {
        this(key, label, defaultWidth, true);
    }

    public TableColumn(String key, String label, int defaultWidth, boolean defaultVisible) {
        this(key, label, defaultWidth, defaultVisible, false);
    }

    /** Mit Pflichtspalte (Karte 1853): der fuenfte Wert ist {@link #required}. */
    public TableColumn(String key, String label, int defaultWidth, boolean defaultVisible, boolean required) {
        this.key = key;
        this.label = label;
        this.defaultWidth = defaultWidth;
        this.defaultVisible = defaultVisible;
        this.required = required;
    }

    /**
     * Wie im Inventar (Karte 2086): der fuenfte und sechste Wert sind {@link #waehlbar} und
     * {@link #verschiebbar} - nicht zu verwechseln mit dem Fuenfer-Konstruktor ({@code required}).
     */
    public TableColumn(String key, String label, int defaultWidth, boolean defaultVisible,
                       boolean waehlbar, boolean verschiebbar) {
        this(key, label, defaultWidth, defaultVisible, false);
        this.waehlbar = waehlbar;
        this.verschiebbar = verschiebbar;
    }

    /** Spalte aus einem {@code p:columns}-Block: ankreuzbar, aber nicht einzeln verschiebbar. */
    public static TableColumn ausBlock(String key, String label, int defaultWidth) {
        return new TableColumn(key, label, defaultWidth, true, true, false);
    }

    /** Platzhalter eines solchen Blocks in der Reihenfolge: verschiebbar, nicht ankreuzbar, ohne Breite. */
    public static TableColumn blockPlatzhalter(String key, String label) {
        return new TableColumn(key, label, 0, true, false, true);
    }

    /** Kopflose Knopf-/Symbolspalte: verschiebbar, nicht ankreuzbar (sie traegt eine Funktion). */
    public static TableColumn knopfSpalte(String key, int defaultWidth) {
        return new TableColumn(key, "", defaultWidth, true, false, true);
    }

    /** Dieselbe kopflose Spalte mit Namen fuer die Spaltenliste und frei waehlbarer Ankreuzbarkeit. */
    public static TableColumn knopfSpalte(String key, int defaultWidth, String listenLabel, boolean waehlbar) {
        TableColumn spalte = new TableColumn(key, "", defaultWidth, true, waehlbar, true);
        spalte.setListenLabel(listenLabel);
        return spalte;
    }
}
