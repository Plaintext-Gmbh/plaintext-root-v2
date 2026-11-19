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
 * {@code root} bypass the check, except for the modules listed in {@code strenge-module-roles}. Modules without an entry behave unchanged.</p>
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
    private Map<String, String> moduleRoles = new LinkedHashMap<>();

    /**
     * Module keys whose role is checked WITHOUT the admin/root bypass ({@code plaintext.menu.strenge-module-roles},
     * Karte 2377 ROOT-3), e.g. a terminal that opens a shell on a VM. A strict key without an entry in
     * {@link #moduleRoles} requires the role of the same name, so it is closed by default rather than open.
     *
     * @since 3.59.0
     */
    private List<String> strengeModuleRoles = new ArrayList<>();

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
     * @return the strict module keys in canonical form, sorted (never {@code null})
     * @since 3.59.0
     */
    public Set<String> canonicalStrengeModuleRoles() {
        Set<String> ret = new TreeSet<>();
        for (String key : strengeModuleRoles) {
            String k = canonicalKey(key);
            if (!k.isEmpty()) {
                ret.add(k);
            }
        }
        return ret;
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
