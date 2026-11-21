/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.settings.service;

import ch.plaintext.modules.ModuleDescriptor;
import ch.plaintext.settings.ISettingsService;
import ch.plaintext.settings.SettingsKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Karte 2425 (PR B): Vorgaben der Module in settings und die Gruppen der Settings-Seite.
 *
 * <ul>
 *   <li>Beim Start schreibt {@link #schreiben()} jede fehlende {@link ModuleDescriptor.Vorgabe} nach Mandant
 *       {@code global}. Ein vorhandener Wert wird nie überschrieben (Entscheid Daniel: Vorgabe nur «falls noch
 *       fehlend»).</li>
 *   <li>Gruppe = Teil des Schlüssels vor dem ersten Punkt, wenn er eine {@code moduleId} ist; sonst «root»
 *       (Entscheid Daniel (284) 2).</li>
 * </ul>
 */
@Slf4j
@Component
public class ModulVorgaben {

    public static final String GRUPPE_ROOT = "root";
    /** Wie im Ablagen-Register (Karte 2425 PR A): ein Geheimnis gehört nie in settings. */
    static final Set<String> KLARTEXT = Set.of("passwort", "password", "token", "secret", "app-passwort");

    private final ObjectProvider<ModuleDescriptor> module;
    private final ISettingsService settings;

    public ModulVorgaben(ObjectProvider<ModuleDescriptor> module, ISettingsService settings) {
        this.module = module;
        this.settings = settings;
    }

    /** @return Zahl der neu geschriebenen Vorgaben */
    @EventListener(ApplicationReadyEvent.class)
    public int schreiben() {
        int neu = 0;
        for (ModuleDescriptor m : module.orderedStream().toList()) {
            for (ModuleDescriptor.Vorgabe v : vorgaben(m)) {
                if (!zulaessig(m, v)) {
                    continue;
                }
                try {
                    if (!settings.exists(v.schluessel(), SettingsKeys.MANDAT_GLOBAL)) {
                        settings.setSetting(v.schluessel(), SettingsKeys.MANDAT_GLOBAL, v.wert(),
                                v.typ() == null || v.typ().isBlank() ? "STRING" : v.typ().strip(), v.beschreibung());
                        neu++;
                        log.info("Settings: Vorgabe {} = {} von Modul {} angelegt", v.schluessel(), v.wert(), m.moduleId());
                    }
                } catch (RuntimeException e) {
                    log.warn("Settings: Vorgabe {} von Modul {} nicht geschrieben: {}", v.schluessel(), m.moduleId(), e.getMessage());
                }
            }
        }
        return neu;
    }

    private static boolean zulaessig(ModuleDescriptor m, ModuleDescriptor.Vorgabe v) {
        String k = v.schluessel() == null ? "" : v.schluessel().strip();
        if (!k.startsWith(m.moduleId() + ".") || k.length() <= m.moduleId().length() + 1) {
            log.warn("Settings: Vorgabe «{}» von Modul {} übergangen, der Schlüssel muss mit «{}.» beginnen", k, m.moduleId(),
                    m.moduleId());
            return false;
        }
        String letzter = k.substring(k.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (KLARTEXT.contains(letzter)) {
            log.warn("Settings: Vorgabe «{}» von Modul {} übergangen, Geheimnisse gehören in den Secret-Store", k, m.moduleId());
            return false;
        }
        return true;
    }

    private static List<ModuleDescriptor.Vorgabe> vorgaben(ModuleDescriptor m) {
        try {
            List<ModuleDescriptor.Vorgabe> l = m.einstellungen();
            return l == null ? List.of() : l;
        } catch (RuntimeException | AbstractMethodError e) {   // Modul gegen ein älteres root gebaut
            log.warn("Settings: Vorgaben von Modul {} nicht lesbar: {}", m.moduleId(), e.toString());
            return List.of();
        }
    }

    /** Die Gruppen für die Auswahl: «root», dann jede moduleId (sortiert). */
    public List<String> gruppen() {
        List<String> g = new ArrayList<>();
        g.add(GRUPPE_ROOT);
        g.addAll(moduleIds());
        return g;
    }

    /** Die Gruppe eines Schlüssels. */
    public String gruppe(String schluessel) {
        String k = schluessel == null ? "" : schluessel;
        int p = k.indexOf('.');
        String praefix = p > 0 ? k.substring(0, p) : "";
        return moduleIds().contains(praefix) ? praefix : GRUPPE_ROOT;
    }

    /** Ob der Schlüssel eine Vorgabe eines Moduls ist (Markierung auf der Seite, Hinweis claude3). */
    public boolean istVorgabe(String schluessel) {
        return module.orderedStream().flatMap(m -> vorgaben(m).stream())
                .anyMatch(v -> v.schluessel() != null && v.schluessel().strip().equals(schluessel));
    }

    private TreeSet<String> moduleIds() {
        TreeSet<String> ids = new TreeSet<>();
        module.orderedStream().map(ModuleDescriptor::moduleId).filter(id -> id != null && !id.isBlank()).forEach(ids::add);
        ids.remove(GRUPPE_ROOT);
        return ids;
    }
}
