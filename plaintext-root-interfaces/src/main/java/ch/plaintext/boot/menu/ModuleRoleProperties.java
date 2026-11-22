/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.menu;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Configurable module roles: per application, maps a module key to a role that switches the entire
 * module on or off.
 *
 * <pre>
 * plaintext:
 *   menu:
 *     module-roles:
 *       wiki: wiki
 *       mailbox: mail
 *       postkonto: finanzen
 * </pre>
 *
 * <p>Without the configured role neither a menu item nor a dashboard tile of the module is
 * visible, and the {@code PageAccessGuard} refuses direct calls to the pages. {@code admin} and
 * {@code root} bypass the check, except for the modules listed in {@code strenge-module-roles} (neither) or in
 * {@code ohne-root-umgehung}/{@code ohne-admin-umgehung} (Karte 2390, one of them). Modules without an entry behave unchanged.</p>
 *
 * <p><b>Why here and not in the module code:</b> the same module artifact is bundled by several
 * applications (e.g. {@code plaintext-z-mailbox} runs in app.plaintext.ch AND in app.guild42.ch).
 * The mapping therefore belongs in the configuration of the respective application, not in the
 * module.</p>
 *
 * <p><b>Module key:</b> primarily the {@code moduleId} from
 * {@code @MenuAnnotation(moduleId = "...")} or {@code ModuleDescriptor#moduleId()} — exactly the
 * value the admin panel "Root | Module" shows. For modules without a {@code moduleId} the menu
 * root id serves as the fallback (the {@code menuId}, or the identifier derived from the title of
 * the module's topmost menu item). At startup the framework logs every module key it detected; a
 * key without a match is reported as a WARN.</p>
 *
 * @author info@plaintext.ch
 * @since 1.604.0
 */
@ConfigurationProperties(prefix = "plaintext.menu")
public class ModuleRoleProperties {

    /** Module key -&gt; role name (without the {@code ROLE_} prefix, case-insensitive). */
    // Karte 2390: volatile, weil ModulZugriff (admin-settings) die Werte zur Laufzeit ersetzt und Anfrage-Threads lesen.
    private volatile Map<String, String> moduleRoles = new LinkedHashMap<>();

    /**
     * Module keys whose role is checked WITHOUT the admin/root bypass ({@code plaintext.menu.strenge-module-roles},
     * Karte 2377 ROOT-3), e.g. a terminal that opens a shell on a VM. A strict key without an entry in
     * {@link #moduleRoles} requires the role of the same name, so it is closed by default rather than open.
     *
     * @since 3.59.0
     */
    private volatile List<String> strengeModuleRoles = new ArrayList<>();

    public Map<String, String> getModuleRoles() {
        return moduleRoles;
    }

    public void setModuleRoles(Map<String, String> moduleRoles) {
        this.moduleRoles = moduleRoles == null ? new LinkedHashMap<>() : moduleRoles;
    }

    public List<String> getStrengeModuleRoles() {
        return strengeModuleRoles;
    }

    public void setStrengeModuleRoles(List<String> strengeModuleRoles) {
        this.strengeModuleRoles = strengeModuleRoles == null ? new ArrayList<>() : strengeModuleRoles;
    }

    /**
     * Karte 2390: module keys whose role ROOT must hold itself (no root bypass); admin may still bypass unless listed in
     * {@link #getOhneAdminUmgehung()}. Filled by the root module administration (settings {@code <key>.zugriff.root=false}),
     * rarely in the yml. A key in {@code strenge-module-roles} counts as listed here.
     */
    private volatile List<String> ohneRootUmgehung = new ArrayList<>();

    /** Karte 2390: like {@link #getOhneRootUmgehung()}, for ADMIN ({@code <key>.zugriff.admin=false}). */
    private volatile List<String> ohneAdminUmgehung = new ArrayList<>();

    public List<String> getOhneRootUmgehung() {
        return ohneRootUmgehung;
    }

    public void setOhneRootUmgehung(List<String> ohneRootUmgehung) {
        this.ohneRootUmgehung = ohneRootUmgehung == null ? new ArrayList<>() : ohneRootUmgehung;
    }

    public List<String> getOhneAdminUmgehung() {
        return ohneAdminUmgehung;
    }

    public void setOhneAdminUmgehung(List<String> ohneAdminUmgehung) {
        this.ohneAdminUmgehung = ohneAdminUmgehung == null ? new ArrayList<>() : ohneAdminUmgehung;
    }

    /**
     * Karte 2390 Teil 2: module keys whose menu item and dashboard tile are hidden
     * ({@code <key>.zugriff.sichtbar=false} in the module administration), although the user may use
     * the module: the direct link and the API stay reachable. Filled by {@code ModulZugriff}, rarely
     * in the yml.
     *
     * @since 3.71.0
     */
    private volatile List<String> nichtSichtbar = new ArrayList<>();

    /**
     * Karte 2390 Teil 2: module keys that are read-only even for users who may use them
     * ({@code <key>.zugriff.bearbeiten=false}): {@code @modulRolle.darfBearbeiten} answers
     * {@code false}, also for ROOT/ADMIN. Filled by {@code ModulZugriff}, rarely in the yml.
     *
     * @since 3.71.0
     */
    private volatile List<String> ohneBearbeitung = new ArrayList<>();

    public List<String> getNichtSichtbar() {
        return nichtSichtbar;
    }

    public void setNichtSichtbar(List<String> nichtSichtbar) {
        this.nichtSichtbar = nichtSichtbar == null ? new ArrayList<>() : nichtSichtbar;
    }

    public List<String> getOhneBearbeitung() {
        return ohneBearbeitung;
    }

    public void setOhneBearbeitung(List<String> ohneBearbeitung) {
        this.ohneBearbeitung = ohneBearbeitung == null ? new ArrayList<>() : ohneBearbeitung;
    }

    /**
     * @return module keys hidden from menu and tiles, canonical
     * @since 3.71.0
     */
    public Set<String> canonicalNichtSichtbar() {
        return canonical(nichtSichtbar);
    }

    /**
     * @return module keys without editing permission, canonical
     * @since 3.71.0
     */
    public Set<String> canonicalOhneBearbeitung() {
        return canonical(ohneBearbeitung);
    }

    /**
     * @return module keys without root bypass: {@code strenge-module-roles} plus {@code ohne-root-umgehung}, canonical
     * @since 3.70.0
     */
    public Set<String> canonicalOhneRootUmgehung() {
        Set<String> ret = canonical(ohneRootUmgehung);
        ret.addAll(canonicalStrengeModuleRoles());
        return ret;
    }

    /**
     * @return module keys without admin bypass: {@code strenge-module-roles} plus {@code ohne-admin-umgehung}, canonical
     * @since 3.70.0
     */
    public Set<String> canonicalOhneAdminUmgehung() {
        Set<String> ret = canonical(ohneAdminUmgehung);
        ret.addAll(canonicalStrengeModuleRoles());
        return ret;
    }

    private static Set<String> canonical(List<String> keys) {
        Set<String> ret = new TreeSet<>();
        for (String key : keys) {
            String k = canonicalKey(key);
            if (!k.isEmpty()) {
                ret.add(k);
            }
        }
        return ret;
    }

    /**
     * @return the strict module keys in canonical form, sorted (never {@code null})
     * @since 3.59.0
     */
    public Set<String> canonicalStrengeModuleRoles() {
        return canonical(strengeModuleRoles);
    }

    /**
     * The mapping in canonical form: module key lowercased, role name UPPERCASE and without the
     * {@code ROLE_} prefix — the spelling in which {@code SecurityProvider#hasRole(String)} checks
     * it. Empty keys or roles are discarded.
     *
     * @return canonical mapping, sorted alphabetically by module key (never {@code null})
     */
    public Map<String, String> canonicalModuleRoles() {
        Map<String, String> ret = new TreeMap<>();
        for (Map.Entry<String, String> entry : moduleRoles.entrySet()) {
            String key = canonicalKey(entry.getKey());
            String role = canonicalRole(entry.getValue());
            if (!key.isEmpty() && !role.isEmpty()) {
                ret.put(key, role);
            }
        }
        // A strict module without its own mapping requires the role of the same name (closed by default).
        for (String key : canonicalStrengeModuleRoles()) {
            ret.putIfAbsent(key, canonicalRole(key));
        }
        return ret;
    }

    /** {@code true} if not a single valid mapping is configured. */
    public boolean isEmpty() {
        return canonicalModuleRoles().isEmpty();
    }

    /**
     * Canonical form of a module key: trimmed and lowercased.
     *
     * @param key raw key, may be {@code null}
     * @return canonical key, never {@code null}
     */
    public static String canonicalKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Canonical form of a role name: trimmed, without the {@code ROLE_} prefix, UPPERCASE.
     *
     * @param role raw role name, may be {@code null}
     * @return canonical role name, never {@code null}
     */
    public static String canonicalRole(String role) {
        if (role == null) {
            return "";
        }
        String value = role.trim().toUpperCase(Locale.ROOT);
        if (value.startsWith("ROLE_")) {
            value = value.substring("ROLE_".length());
        }
        return value;
    }
}
