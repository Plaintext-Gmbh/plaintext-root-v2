/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.table;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Texte des Tabellen-Bedienbereichs in der Sprache der Seite (Karte 1853, Runde 6 — Widget
 * mehrsprachig, mindestens de/en/fr/it). Bundle {@code ch.plaintext.boot.table.tablesettings}
 * (Basis = de, Varianten _en/_fr/_it). Ausgelagert aus {@link TableSettings}, damit diese
 * Klasse unter der Groessen-Leitplanke bleibt (PlaintextGroessenLeitplankeTest).
 *
 * <p>Die Locale kommt aus dem ViewRoot; ohne Faces-Kontext oder ohne Schluessel faellt die
 * Basis bzw. der Schluessel selbst zurueck — nie eine Exception mitten im Rendern.</p>
 *
 * @author info@plaintext.ch
 * @since 2026
 */
public final class TableSettingsTexte {

    private static final String BUNDLE = "ch.plaintext.boot.table.tablesettings";

    private TableSettingsTexte() {
    }

    /**
     * Das Bundle in der Sprache der Seite — mit dem UserPreferences-Vorrang aus
     * {@link #locale()} (Karte 1853 Runde 6, claude2-Befund: {@code f:loadBundle} folgt der
     * ViewRoot-Locale, die in den Apps fest auf de steht, und blieb fuer language=en-Benutzer
     * deutsch). Das Tag bindet es ueber {@code anzeige.texte} (Getter in {@link TableSettings}),
     * damit keine Bohne noetig ist (funktioniert auch im eigenstaendigen tablesettings-Artefakt
     * ohne Spring-Scan).
     */
    public static ResourceBundle bundle() {
        return ResourceBundle.getBundle(BUNDLE, locale(), TableSettingsTexte.class.getClassLoader());
    }

    /** Text zum Schluessel, mit {@link MessageFormat}-Parametern ({0}, {1}, ...). */
    public static String text(String key, Object... args) {
        String muster;
        try {
            muster = ResourceBundle.getBundle(BUNDLE, locale(), TableSettingsTexte.class.getClassLoader()).getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
        return args.length == 0 ? muster : MessageFormat.format(muster, args);
    }

    /**
     * Kurzes Growl fuer gelungene Aenderungen: globale FacesMessage, die das globalGrowl des
     * Templates per autoUpdate zeigt; ohne Faces-Kontext still. Gleiche Wirkung wie
     * {@code FacesMessages.info} aus root-common, aber ohne dessen Abhaengigkeit: dieses Paket
     * liegt seit Karte 2086 nur noch im eigenstaendigen Artefakt plaintext-root-tablesettings.
     * Die Architekturregel {@code meldungenNurUeberFacesMessages} nimmt genau diese Klasse aus.
     */
    public static void growl(String text) {
        var faces = jakarta.faces.context.FacesContext.getCurrentInstance();
        if (faces != null) {
            faces.addMessage(null, new jakarta.faces.application.FacesMessage(
                    jakarta.faces.application.FacesMessage.SEVERITY_INFO, text, null));
        }
    }

    /** Label des Dreifach-Modus-Schalters je Modus (0 anpassen, 1 Verschieben, 2 Vergroessern). */
    public static String modusLabel(int modus) {
        return switch (modus) {
            case 1 -> text("modus.verschieben");
            case 2 -> text("modus.vergroessern");
            default -> text("modus.anpassen");
        };
    }

    /** Tooltip des Dreifach-Modus-Schalters je Modus. */
    public static String modusTitel(int modus) {
        return switch (modus) {
            case 1 -> text("modus.titel.verschieben");
            case 2 -> text("modus.titel.vergroessern");
            default -> text("modus.titel.anpassen");
        };
    }

    /** Icon des Dreifach-Modus-Schalters je Modus. */
    public static String modusIcon(int modus) {
        return switch (modus) {
            case 1 -> "pi pi-arrows-h";
            case 2 -> "pi pi-arrow-h-right";
            default -> "pi pi-sliders-h";
        };
    }

    /**
     * Sprache der Texte: die Benutzer-Einstellung ({@code #{userPreferencesBackingBean.language}},
     * ohne Kompilier-Abhaengigkeit), sonst die ViewRoot-Locale, sonst de. Die ViewRoot-Locale
     * ist in den Apps fest de (faces-config) — ohne den UserPreferences-Vorrang bliebe das
     * Widget fuer language=en-Benutzer deutsch (Nachtrag claude1 zu #36, Karte 1853 Runde 6).
     */
    private static Locale locale() {
        String sprache = userPreferencesSprache();
        if (sprache != null && !sprache.isBlank()) {
            return Locale.forLanguageTag(sprache);
        }
        var faces = jakarta.faces.context.FacesContext.getCurrentInstance();
        if (faces != null && faces.getViewRoot() != null && faces.getViewRoot().getLocale() != null) {
            return faces.getViewRoot().getLocale();
        }
        return Locale.GERMAN;
    }

    private static String userPreferencesSprache() {
        var faces = jakarta.faces.context.FacesContext.getCurrentInstance();
        try {
            return faces == null ? null
                    : faces.getApplication().evaluateExpressionGet(faces,
                            "#{userPreferencesBackingBean.language}", String.class);
        } catch (RuntimeException e) { // NOSONAR - Bean fehlt oder kein Wert: ViewRoot/Deutsch
            return null;
        }
    }

    /** Label uebersetzen: per gesetztem Uebersetzer (Tests), sonst per {@code #{i18n.t(...)}}. */
    public static String uebersetzeLabel(String label, java.util.function.UnaryOperator<String> uebersetzer) {
        return label == null ? null
                : uebersetzer != null ? uebersetzer.apply(label) : perI18nBean(label);
    }

    /** Label per {@code #{i18n.t(...)}}; ohne Kompilier-Abhaengigkeit, v1-Apps ohne die Bean liefern null. */
    private static String perI18nBean(String label) {
        var faces = jakarta.faces.context.FacesContext.getCurrentInstance();
        try {
            String literal = label.replace("\\", "\\\\").replace("'", "\\'");
            return faces == null ? null
                    : faces.getApplication().evaluateExpressionGet(faces, "#{i18n.t('" + literal + "')}", String.class);
        } catch (RuntimeException e) { // NOSONAR - keine i18n-Bean oder kein Eintrag: Label bleibt unuebersetzt
            return null;
        }
    }
}
