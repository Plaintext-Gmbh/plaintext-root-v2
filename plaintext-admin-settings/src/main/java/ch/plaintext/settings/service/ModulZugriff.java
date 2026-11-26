/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.settings.service;

import ch.plaintext.PlaintextSecurity;
import ch.plaintext.boot.menu.ModuleRoleProperties;
import ch.plaintext.boot.menu.ModuleRoleService;
import ch.plaintext.framework.PlaintextRoleRegistry;
import ch.plaintext.settings.ISettingsService;
import ch.plaintext.settings.SettingsKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Karte 2390 (Entscheide Daniel 21.11.2026): wer ein Modul mit Modulrolle nutzen darf, steht je App in settings
 * (Mandant {@code global}) und nicht mehr nur in der yml:
 *
 * <pre>
 * &lt;key&gt;.zugriff.rolle   die Modulrolle, z. B. files; leer = frei
 * &lt;key&gt;.zugriff.root    true/false: ROOT darf ohne die Rolle
 * &lt;key&gt;.zugriff.admin   true/false: ADMIN darf ohne die Rolle
 * &lt;key&gt;.zugriff.sichtbar   true/false: Menue und Kachel zeigen das Modul (Teil 2; «false» blendet nur
 *                            die Eintrittspunkte aus, die Seite bleibt per Link erreichbar)
 * &lt;key&gt;.zugriff.bearbeiten true/false: wer das Modul nutzen darf, darf es auch aendern (Teil 2;
 *                            «false» = nur lesen, auch fuer ROOT/ADMIN, siehe @modulRolle.darfBearbeiten)
 * </pre>
 *
 * <ul>
 *   <li><b>Vorgabe einmal</b> (Entscheid 2): Beim Start schreibt {@link #vorgabenSchreiben()} je Modul aus
 *   {@code plaintext.menu.module-roles}/{@code strenge-module-roles} die fehlenden Schlüssel (root/admin «true», bei
 *   strengen Modulen «false»), nie überschreibend. Module ohne Eintrag bekommen nichts: frei bleibt frei (1).</li>
 *   <li><b>Danach gilt settings</b>: {@link #anwenden()} setzt {@link ModuleRoleProperties} aus settings neu und löst
 *   Menü, Kacheln, Seitenzugriff und {@code @modulRolle} neu auf; nach jeder Änderung eines {@code *.zugriff.*}-Schlüssels
 *   ({@link EinstellungGeaendert}) ohne Neustart.</li>
 *   <li><b>Nie offener als die yml aus Versehen</b>: Fehlt ein yml-Modul in settings (Vorgabe nicht geschrieben),
 *   gilt für dieses Modul weiter die yml. Öffnen geht nur ausdrücklich (Rolle leer bzw. root/admin «true»).</li>
 * </ul>
 */
@Slf4j
@Component
public class ModulZugriff {

    static final String TEIL = ".zugriff.";
    static final String ROLLE = TEIL + "rolle";
    static final String ROOT = TEIL + "root";
    static final String ADMIN = TEIL + "admin";
    static final String SICHTBAR = TEIL + "sichtbar";
    static final String BEARBEITEN = TEIL + "bearbeiten";

    private final ModuleRoleProperties properties;
    private final ObjectProvider<ModuleRoleService> rollenDienst;
    private final ISettingsService settings;
    private final ObjectProvider<PlaintextSecurity> security;

    /** Die yml beim Start, bevor {@link #anwenden()} die Properties umschreibt. */
    private final Map<String, String> ymlRollen;
    private final Set<String> ymlOhneRoot;
    private final Set<String> ymlOhneAdmin;
    /** Waehrend {@link #vorgabenSchreiben()}: die eigenen Schreibvorgaenge sind keine Aenderung durch einen Benutzer. */
    private volatile boolean schreibtVorgaben;

    public ModulZugriff(ModuleRoleProperties properties, ObjectProvider<ModuleRoleService> rollenDienst,
                        ISettingsService settings, ObjectProvider<PlaintextSecurity> security) {
        this(properties, rollenDienst, settings, security, null);
    }

    /**
     * @param registry Karte 2377 ROOT-5: eine mit {@code ohneUmgehung} deklarierte Rolle macht ihr Modul in der
     *                 yml-Vorgabe streng (vor dem Kopieren der yml, damit die Vorgabe in settings «false» schreibt);
     *                 {@code null} = keine Kopplung
     */
    @Autowired
    public ModulZugriff(ModuleRoleProperties properties, ObjectProvider<ModuleRoleService> rollenDienst,
                        ISettingsService settings, ObjectProvider<PlaintextSecurity> security,
                        ObjectProvider<PlaintextRoleRegistry> registry) {
        if (registry != null) {
            registry.ifAvailable(r -> ohneUmgehungStreng(properties, r.getOhneUmgehungModule()));
        }
        this.properties = properties;
        this.rollenDienst = rollenDienst;
        this.settings = settings;
        this.security = security;
        this.ymlRollen = Map.copyOf(properties.canonicalModuleRoles());
        this.ymlOhneRoot = Set.copyOf(properties.canonicalOhneRootUmgehung());
        this.ymlOhneAdmin = Set.copyOf(properties.canonicalOhneAdminUmgehung());
    }

    /**
     * Ergänzt {@code strenge-module-roles} (und, falls das Modul keine Zuordnung hat, {@code module-roles}) um die
     * Module mit {@code ohneUmgehung}-Rolle. Eine ausdrückliche Zuordnung in der yml bleibt.
     */
    static void ohneUmgehungStreng(ModuleRoleProperties properties, Map<String, String> modulZuRolle) {
        if (modulZuRolle.isEmpty()) {
            return;
        }
        Set<String> streng = properties.canonicalStrengeModuleRoles();
        Map<String, String> zuordnung = properties.canonicalModuleRoles();
        List<String> neuStreng = new ArrayList<>(properties.getStrengeModuleRoles());
        Map<String, String> neuRollen = new LinkedHashMap<>(properties.getModuleRoles());
        modulZuRolle.forEach((modul, rolle) -> {
            String k = ModuleRoleProperties.canonicalKey(modul);
            if (!streng.contains(k)) {
                neuStreng.add(k);
            }
            if (!zuordnung.containsKey(k)) {
                neuRollen.put(k, rolle);
            }
        });
        properties.setModuleRoles(neuRollen);
        properties.setStrengeModuleRoles(neuStreng);
        log.info("Modul-Zugriff: Module mit ohneUmgehung-Rolle in der Vorgabe streng: {}", modulZuRolle);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        vorgabenSchreiben();
        anwenden();
    }

    /** @return Zahl der neu geschriebenen Schlüssel */
    int vorgabenSchreiben() {
        schreibtVorgaben = true;
        try {
            return vorgabenSchreibenIntern();
        } finally {
            schreibtVorgaben = false;
        }
    }

    private int vorgabenSchreibenIntern() {
        int neu = 0;
        for (Map.Entry<String, String> e : ymlRollen.entrySet()) {
            String k = e.getKey();
            neu += vorgabe(k + ROLLE, e.getValue().toLowerCase(java.util.Locale.ROOT), "STRING",
                    "Karte 2390: Modulrolle für " + k + " (leer = frei)");
            neu += vorgabe(k + ROOT, String.valueOf(!ymlOhneRoot.contains(k)), "BOOLEAN",
                    "Karte 2390: ROOT darf " + k + " ohne die Rolle");
            neu += vorgabe(k + ADMIN, String.valueOf(!ymlOhneAdmin.contains(k)), "BOOLEAN",
                    "Karte 2390: ADMIN darf " + k + " ohne die Rolle");
        }
        return neu;
    }

    private int vorgabe(String key, String wert, String typ, String beschreibung) {
        try {
            if (settings.exists(key, SettingsKeys.MANDAT_GLOBAL)) {
                return 0;
            }
            settings.setSetting(key, SettingsKeys.MANDAT_GLOBAL, wert, typ, beschreibung);
            log.info("Modul-Zugriff: Vorgabe {} = {} aus der yml angelegt", key, wert);
            return 1;
        } catch (RuntimeException ex) {
            log.warn("Modul-Zugriff: Vorgabe {} nicht geschrieben ({}), es gilt die yml", key, ex.getMessage());
            return 0;
        }
    }

    /** Liest das Set aus settings (mit der yml als Rückfall je Schlüssel) und löst die Modulrollen neu auf. */
    public synchronized void anwenden() {
        Map<String, String> rollen = new TreeMap<>(ymlRollen);
        Set<String> ohneRoot = new TreeSet<>(ymlOhneRoot);
        Set<String> ohneAdmin = new TreeSet<>(ymlOhneAdmin);
        Set<String> nichtSichtbar = new TreeSet<>(properties.canonicalNichtSichtbar());
        Set<String> ohneBearbeitung = new TreeSet<>(properties.canonicalOhneBearbeitung());
        Set<String> keys = new TreeSet<>(ymlRollen.keySet());
        try {
            for (String s : settings.getAllKeys(SettingsKeys.MANDAT_GLOBAL)) {
                if (s == null) {
                    continue;
                }
                // Karte 2390 Teil 2: auch Module ohne Rollen-Eintrag koennen sichtbar/bearbeiten gesetzt haben.
                if (s.endsWith(ROLLE) && s.length() > ROLLE.length()) {
                    keys.add(ModuleRoleProperties.canonicalKey(s.substring(0, s.length() - ROLLE.length())));
                } else if (s.endsWith(SICHTBAR) && s.length() > SICHTBAR.length()) {
                    keys.add(ModuleRoleProperties.canonicalKey(s.substring(0, s.length() - SICHTBAR.length())));
                } else if (s.endsWith(BEARBEITEN) && s.length() > BEARBEITEN.length()) {
                    keys.add(ModuleRoleProperties.canonicalKey(s.substring(0, s.length() - BEARBEITEN.length())));
                }
            }
            for (String k : keys) {
                if (settings.exists(k + ROLLE, SettingsKeys.MANDAT_GLOBAL)) {
                    String r = ModuleRoleProperties.canonicalRole(settings.getString(k + ROLLE, SettingsKeys.MANDAT_GLOBAL));
                    if (r.isEmpty()) {
                        rollen.remove(k);
                    } else {
                        rollen.put(k, r);
                    }
                }
                setzen(ohneRoot, k, settings.getString(k + ROOT, SettingsKeys.MANDAT_GLOBAL));
                setzen(ohneAdmin, k, settings.getString(k + ADMIN, SettingsKeys.MANDAT_GLOBAL));
                // Fehlt der Schalter, gilt der yml-Stand bzw. der Standard (sichtbar, bearbeitbar);
                // nur exakt «true» oeffnet, alles andere schliesst (fail-closed).
                setzen(nichtSichtbar, k, settings.getString(k + SICHTBAR, SettingsKeys.MANDAT_GLOBAL));
                setzen(ohneBearbeitung, k, settings.getString(k + BEARBEITEN, SettingsKeys.MANDAT_GLOBAL));
            }
        } catch (RuntimeException ex) {
            log.warn("Modul-Zugriff: settings nicht lesbar ({}), es gilt die yml", ex.getMessage());
            return;
        }
        ohneRoot.retainAll(rollen.keySet());
        ohneAdmin.retainAll(rollen.keySet());
        // Review claude2 (Fund B): erst die engere Lage setzen, «strenge» zuletzt leeren - sonst gaebe es ein kurzes
        // Fenster, in dem ModulRolle.darf (andere Threads) eine Umgehung sieht, die es nach dem Wechsel nicht gibt.
        properties.setOhneRootUmgehung(new ArrayList<>(ohneRoot));
        properties.setOhneAdminUmgehung(new ArrayList<>(ohneAdmin));
        properties.setNichtSichtbar(new ArrayList<>(nichtSichtbar));
        properties.setOhneBearbeitung(new ArrayList<>(ohneBearbeitung));
        properties.setModuleRoles(new TreeMap<>(rollen));
        properties.setStrengeModuleRoles(new ArrayList<>());
        rollenDienst.ifAvailable(ModuleRoleService::neuAufloesen);
        log.info("Modul-Zugriff aus settings: Rollen {}, ohne Root-Umgehung {}, ohne Admin-Umgehung {}, "
                + "nicht sichtbar {}, ohne Bearbeitung {}", rollen, ohneRoot, ohneAdmin, nichtSichtbar, ohneBearbeitung);
    }

    /** Fehlt der Wert, bleibt die yml; sonst «true» = Umgehung erlaubt, alles andere = nicht. */
    private static void setzen(Set<String> ohne, String key, String wert) {
        if (wert == null || wert.isBlank()) {
            return;
        }
        if (Boolean.parseBoolean(wert.strip())) {
            ohne.remove(key);
        } else {
            ohne.add(key);
        }
    }

    /**
     * Karte 2390: eine Änderung eines Zugriffs-Schlüssels gilt sofort; ins Log mit Benutzer. Review claude2 (Fund A):
     * erst nach dem Commit - bei einem Rollback gälte sonst im Speicher der (vielleicht offenere) neue Zugriff. Ohne
     * Transaktion (fallbackExecution) sofort.
     */
    @org.springframework.transaction.event.TransactionalEventListener(
            phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void geaendert(EinstellungGeaendert e) {
        if (schreibtVorgaben || e.key() == null || !e.key().contains(TEIL) || !SettingsKeys.MANDAT_GLOBAL.equals(e.mandat())) {
            return;
        }
        PlaintextSecurity s = security.getIfAvailable();
        log.info("Modul-Zugriff: {} von {} auf {} geändert durch {}", e.key(), e.alt(), e.neu(), s == null ? "?" : s.getUser());
        anwenden();
    }
}
