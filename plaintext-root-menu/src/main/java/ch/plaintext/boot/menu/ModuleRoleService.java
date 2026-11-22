/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.menu;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Enforces the configurable module roles ({@link ModuleRoleProperties}): a module that has a role
 * assigned to it in the application configuration disappears completely for users without that
 * role — menu items, dashboard tiles and (through {@link MenuItemImpl#isOn()}) direct page access
 * via the {@code PageAccessGuard} as well.
 *
 * <p><b>One switch, three effects.</b> The guard and the menu renderer both ask
 * {@link MenuItemImpl#isOn()}; the tiles ask {@link #isAllowedForLink(String, String,
 * SecurityProvider)}. The role check therefore has to sit in one place only, so that menu, tile
 * and page access disappear consistently.</p>
 *
 * <p><b>Module membership</b> is derived from existing metadata, the module itself is left
 * untouched: for every menu item the candidate keys are collected (its own {@code moduleId}, the
 * {@code moduleId} of every parent menu, as well as the
 * {@linkplain MenuItemImpl#getEffectiveMenuId() Menu-Root-Id} of the topmost parent menu). If one
 * of them matches a configured key, that key's role applies.</p>
 *
 * <p>{@code ROLE_ADMIN} and {@code ROLE_ROOT} always bypass the check — unchanged behaviour.
 * Modules without an entry behave exactly as before.</p>
 *
 * @author info@plaintext.ch
 * @since 1.604.0
 */
@Slf4j
public class ModuleRoleService implements SmartInitializingSingleton {

    /** Protection against cycles in the menu hierarchy. */
    private static final int MAX_PARENT_TIEFE = 10;

    private final ApplicationContext applicationContext;
    private final ModuleRoleProperties properties;

    /**
     * Canonical menu link -&gt; required roles; for the tiles.
     *
     * <p>The three lookup tables are built in {@link #resolve(Collection)} as a finished,
     * immutable snapshot and are then published in one go. A {@code volatile} field would publish
     * the reference safely, but would say nothing about whether the object behind it is still
     * being written to — hence {@link AtomicReference} as an explicitly thread-safe holder for the
     * snapshot.</p>
     */
    private final AtomicReference<Map<String, List<String>>> rolesByLink = new AtomicReference<>(Map.of());

    /** Menu title -&gt; required roles; fallback for tiles without a matching link. */
    private final AtomicReference<Map<String, List<String>>> rolesByMenuTitle = new AtomicReference<>(Map.of());

    /** All module keys detected on the classpath (for the startup report and doc hint). */
    private final AtomicReference<Set<String>> knownModuleKeys = new AtomicReference<>(Set.of());

    /**
     * Links and menu titles of modules without root resp. admin bypass (Karte 2377 ROOT-3 strict = both,
     * Karte 2390 one of them), for the tile check.
     */
    private final AtomicReference<Set<String>> ohneRootLinks = new AtomicReference<>(Set.of());
    private final AtomicReference<Set<String>> ohneRootMenuTitel = new AtomicReference<>(Set.of());
    private final AtomicReference<Set<String>> ohneAdminLinks = new AtomicReference<>(Set.of());
    private final AtomicReference<Set<String>> ohneAdminMenuTitel = new AtomicReference<>(Set.of());

    /**
     * Links and menu titles of modules hidden from menu and tiles (Karte 2390 Teil 2,
     * {@code <key>.zugriff.sichtbar=false}); the pages stay reachable by link.
     */
    private final AtomicReference<Set<String>> unsichtbarLinks = new AtomicReference<>(Set.of());
    private final AtomicReference<Set<String>> unsichtbarTitel = new AtomicReference<>(Set.of());

    private volatile boolean resolved;

    public ModuleRoleService(ApplicationContext applicationContext, ModuleRoleProperties properties) {
        this.applicationContext = applicationContext;
        this.properties = properties == null ? new ModuleRoleProperties() : properties;
    }

    /**
     * Resolves the module membership once and reports the detected module keys. Deliberately
     * runs only after all singletons have been instantiated: the {@link MenuItemImpl} beans are
     * contributed by the {@link MenuRegistryPostProcessor} via {@code registerSingleton}.
     */
    @Override
    public void afterSingletonsInstantiated() {
        try {
            ensureResolved();
            reportAtStartup();
        } catch (Exception e) {
            log.warn("Modul-Rollen konnten beim Start nicht aufgeloest werden: {}", e.getMessage());
        }
    }

    private void reportAtStartup() {
        Map<String, String> configured = properties.canonicalModuleRoles();
        Set<String> bekannteKeys = knownModuleKeys.get();
        if (configured.isEmpty()) {
            log.info("Keine Modul-Rollen konfiguriert (plaintext.menu.module-roles) — "
                    + "erkannte Modul-Keys: {}", bekannteKeys);
            return;
        }
        log.info("Modul-Rollen aktiv: {} — erkannte Modul-Keys: {}", configured, bekannteKeys);
        Set<String> streng = properties.canonicalStrengeModuleRoles();
        if (!streng.isEmpty()) {
            log.info("Strenge Modul-Rollen (ohne ADMIN/ROOT-Umgehung, plaintext.menu.strenge-module-roles): {}", streng);
        }
        for (Map.Entry<String, String> eintrag : configured.entrySet()) {
            String key = eintrag.getKey();
            if (!bekannteKeys.contains(key)) {
                log.warn("Modul-Rolle konfiguriert fuer unbekannten Modul-Key '{}' "
                                + "(plaintext.menu.module-roles.{}={}) — die Zuordnung greift nirgends. "
                                + "Bekannte Keys: {}",
                        key, key, eintrag.getValue(), bekannteKeys);
            }
        }
    }

    /**
     * May the current user see a dashboard tile, as far as the module roles are concerned? The
     * tile is assigned to the menu via its link, alternatively via its {@code menuTitle} — the
     * same coupling that the tenant-specific visibility uses.
     *
     * @param link             link of the tile (e.g. {@code wiki.html})
     * @param menuTitle        menu title of the tile, may be empty
     * @param securityProvider role lookup, may be {@code null}
     * @return {@code true} if no module role is required or the user holds it
     */
    public boolean isAllowedForLink(String link, String menuTitle, SecurityProvider securityProvider) {
        if (properties.isEmpty()) {
            return true;
        }
        ensureResolved();
        String key = canonicalLink(link);
        List<String> required = rolesByLink.get().get(key);
        boolean ohneRoot = ohneRootLinks.get().contains(key);
        boolean ohneAdmin = ohneAdminLinks.get().contains(key);
        if (required == null && menuTitle != null && !menuTitle.trim().isEmpty()) {
            required = rolesByMenuTitle.get().get(menuTitle.trim());
            ohneRoot = ohneRootMenuTitel.get().contains(menuTitle.trim());
            ohneAdmin = ohneAdminMenuTitel.get().contains(menuTitle.trim());
        }
        return holdsAny(required, securityProvider, ohneRoot, ohneAdmin);
    }

    /**
     * Karte 2390 Teil 2: is the module of this tile/menu entry hidden from menu and tiles
     * ({@code <key>.zugriff.sichtbar=false})? Unlike {@link #isAllowedForLink}, this is not a role
     * check and has no bypass: it applies to ROOT and ADMIN as well, because it only removes the
     * entry points — the page itself stays reachable by link.
     *
     * @param link      canonicalised by this method (e.g. {@code wiki.html})
     * @param menuTitle menu title, may be empty
     * @return {@code false} only if the module is explicitly hidden
     * @since 3.71.0
     */
    public boolean isModuleSichtbarForLink(String link, String menuTitle) {
        ensureResolved();
        String key = canonicalLink(link);
        if (unsichtbarLinks.get().contains(key)) {
            return false;
        }
        return menuTitle == null || menuTitle.trim().isEmpty()
                || !unsichtbarTitel.get().contains(menuTitle.trim());
    }

    /**
     * All module keys detected on the classpath — the valid choice for
     * {@code plaintext.menu.module-roles.<key>}.
     *
     * @return the detected module keys, alphabetically (never {@code null})
     */
    public Set<String> getKnownModuleKeys() {
        ensureResolved();
        return knownModuleKeys.get();
    }

    /**
     * Role check including the admin/root bypass. Static, so that {@link MenuItemImpl#isOn()} can
     * apply it with already resolved roles even without a Spring context (unit tests).
     *
     * @param required         required roles (UPPERCASE, without prefix), may be empty/{@code null}
     * @param securityProvider role lookup, may be {@code null}
     * @return {@code true} if access is allowed
     */
    public static boolean holdsAny(List<String> required, SecurityProvider securityProvider) {
        return holdsAny(required, securityProvider, false);
    }

    /**
     * Like {@link #holdsAny(List, SecurityProvider)}; with {@code streng} admin and root need the role
     * themselves ({@code plaintext.menu.strenge-module-roles}, Karte 2377 ROOT-3).
     *
     * @param required         required roles (UPPERCASE, without prefix), may be empty/{@code null}
     * @param securityProvider role lookup, may be {@code null}
     * @param streng           {@code true} for a strict module: no admin/root bypass
     * @return {@code true} if access is allowed
     * @since 3.59.0
     */
    public static boolean holdsAny(List<String> required, SecurityProvider securityProvider, boolean streng) {
        return holdsAny(required, securityProvider, streng, streng);
    }

    /**
     * Karte 2390: the bypass separately for root and admin ({@code <key>.zugriff.root} / {@code .admin} in the module
     * administration). Note: a root user usually also holds ADMIN, so "root no, admin yes" still lets them in through
     * the admin bypass.
     *
     * @param required         required roles (UPPERCASE, without prefix), may be empty/{@code null}
     * @param securityProvider role lookup, may be {@code null}
     * @param ohneRoot         {@code true}: ROOT does not bypass, it needs the role
     * @param ohneAdmin        {@code true}: ADMIN does not bypass, it needs the role
     * @return {@code true} if access is allowed
     * @since 3.70.0
     */
    public static boolean holdsAny(List<String> required, SecurityProvider securityProvider, boolean ohneRoot,
                                   boolean ohneAdmin) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        if (securityProvider == null) {
            return true;
        }
        if (!ohneRoot && hasRole(securityProvider, "ROOT") || !ohneAdmin && hasRole(securityProvider, "ADMIN")) {
            return true;
        }
        for (String role : required) {
            if (hasRole(securityProvider, role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Role lookup with and without the {@code ROLE_} prefix. {@code SpringSecurityProvider} knows
     * both spellings; an application's own {@link SecurityProvider} implementation need not — so
     * both are asked here instead of relying on a convention.
     *
     * @param securityProvider role lookup, not {@code null}
     * @param role             role name in UPPERCASE and without the {@code ROLE_} prefix
     * @return {@code true} if the user holds the role in either spelling
     */
    /**
     * Asks for a role — <b>regardless of how it is spelled in the configuration</b>.
     *
     * <p><b>Why this is necessary (report from Daniel, 26.08.2026: "Auszahlungen for Jasmin in
     * tenant trimstein no longer works").</b> Roles are stored in lower case in the database
     * ({@code auszahlungen}, {@code wiki}); at login {@code MyUserDetailsService} turns them into
     * {@code "ROLE_" + role.toUpperCase()}, that is {@code ROLE_AUSZAHLUNGEN}. The key values from
     * {@code plaintext.menu.module-roles}, in contrast, are written in lower case — and
     * {@code SpringSecurityProvider.hasRole} compares case-sensitively on purpose (see
     * {@code SpringSecurityProviderTest.hasRole_shouldBeCaseSensitive}).
     *
     * <p>The consequence was worse than "a module is missing": a module behind a role was
     * <b>permanently</b> invisible to everyone except ROOT/ADMIN, including the very person the
     * role had explicitly been assigned to. The gate could be closed, but no longer opened. That
     * nobody noticed for a long time is down to the ROOT/ADMIN exemption in
     * {@link #holdsAny(List, SecurityProvider)} — whoever sets up the gate is an admin and keeps
     * seeing the module.
     *
     * <p><b>Why here and not in {@code SpringSecurityProvider}.</b> Its behaviour is explicitly
     * tested, and the existing menu annotations write their roles in upper case
     * ({@code "POSTKONTO"}, {@code "ROOT"}) — so the convention of the system is upper case.
     * Centrally softening the role comparison would be a far bigger change to a security building
     * block, just to cure a configuration case. Normalisation therefore happens exactly where the
     * freely written configuration comes in.
     */
    private static boolean hasRole(SecurityProvider securityProvider, String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String gross = role.toUpperCase(java.util.Locale.ROOT);
        return securityProvider.hasRole(role)
                || securityProvider.hasRole("ROLE_" + role)
                || securityProvider.hasRole(gross)
                || securityProvider.hasRole("ROLE_" + gross);
    }

    /**
     * Resolves the module membership of all registered menu items once and writes the required
     * roles onto the menu items. Idempotent and thread-safe.
     */
    public void ensureResolved() {
        if (resolved) {
            return;
        }
        synchronized (this) {
            if (resolved) {
                return;
            }
            resolve(loadMenuItems());
        }
    }

    private Collection<MenuItemImpl> loadMenuItems() {
        if (applicationContext == null) {
            return List.of();
        }
        try {
            return applicationContext.getBeansOfType(MenuItemImpl.class).values();
        } catch (Exception e) {
            log.warn("Menuepunkte fuer die Modul-Rollen-Aufloesung nicht ermittelbar: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Resolves the module membership for all menu items, writes the required roles onto the menu
     * items and builds the lookup tables for the tiles.
     *
     * <p>In normal operation {@link #ensureResolved()} calls this method with the registered menu
     * item beans; public so that tests and applications can use it without a Spring context, with
     * a menu structure they build themselves.</p>
     *
     * @param items all registered menu items
     */
    public void resolve(Collection<MenuItemImpl> items) {
        Map<String, String> configured = properties.canonicalModuleRoles();
        Set<String> ohneRootKeys = properties.canonicalOhneRootUmgehung();
        Set<String> ohneAdminKeys = properties.canonicalOhneAdminUmgehung();
        Set<String> nichtSichtbarKeys = properties.canonicalNichtSichtbar();
        Map<String, List<MenuItemImpl>> byTitle = indexByTitle(items);
        Set<String> rootLinks = new HashSet<>();
        Set<String> rootTitel = new HashSet<>();
        Set<String> adminLinks = new HashSet<>();
        Set<String> adminTitel = new HashSet<>();
        Set<String> unsichtbarLinkSet = new HashSet<>();
        Set<String> unsichtbarTitelSet = new HashSet<>();

        Set<String> keys = new TreeSet<>();
        Map<String, List<String>> byLink = new HashMap<>();
        Map<String, List<String>> byMenuTitle = new HashMap<>();

        for (MenuItemImpl item : items) {
            Set<String> candidates = moduleKeysOf(item, byTitle);
            keys.addAll(candidates);

            // The same derivation serves two purposes: through the module roles admin controls
            // WHO may use a module; through the tenant lists root controls WHICH modules belong to
            // a tenant. Both therefore speak the same module vocabulary.
            item.setModuleKeys(List.copyOf(candidates));

            List<String> required = requiredRolesOf(candidates, configured);
            item.setModuleRoles(required);
            indexRequiredRoles(item, required, byLink, byMenuTitle);

            boolean ohneRoot = candidates.stream().anyMatch(ohneRootKeys::contains);
            boolean ohneAdmin = candidates.stream().anyMatch(ohneAdminKeys::contains);
            item.setModuleRoleOhneRoot(ohneRoot);
            item.setModuleRoleOhneAdmin(ohneAdmin);
            merken(item, ohneRoot, rootLinks, rootTitel);
            merken(item, ohneAdmin, adminLinks, adminTitel);

            // Karte 2390 Teil 2: hidden from menu and tiles only, the page stays reachable (like
            // plaintext.menu.platzierung.<menuId>.sichtbar, but per module through settings).
            boolean sichtbar = candidates.stream().noneMatch(nichtSichtbarKeys::contains);
            item.setModuleRoleSichtbar(sichtbar);
            if (!sichtbar) {
                merken(item, true, unsichtbarLinkSet, unsichtbarTitelSet);
            }
        }
        this.ohneRootLinks.set(Set.copyOf(rootLinks));
        this.ohneRootMenuTitel.set(Set.copyOf(rootTitel));
        this.ohneAdminLinks.set(Set.copyOf(adminLinks));
        this.ohneAdminMenuTitel.set(Set.copyOf(adminTitel));
        this.unsichtbarLinks.set(Set.copyOf(unsichtbarLinkSet));
        this.unsichtbarTitel.set(Set.copyOf(unsichtbarTitelSet));

        this.knownModuleKeys.set(Collections.unmodifiableSet(new TreeSet<>(keys)));
        this.rolesByLink.set(Map.copyOf(byLink));
        this.rolesByMenuTitle.set(Map.copyOf(byMenuTitle));
        this.resolved = true;
    }

    private static void merken(MenuItemImpl item, boolean an, Set<String> links, Set<String> titel) {
        if (!an) {
            return;
        }
        String link = canonicalLink(item.getCommand());
        if (!link.isEmpty()) {
            links.add(link);
        }
        if (item.getTitle() != null && !item.getTitle().isBlank()) {
            titel.add(item.getTitle().trim());
        }
    }

    /**
     * Karte 2390: resolves again after the module roles changed at runtime (root module administration via settings),
     * so that menu, tiles and page access follow without a restart.
     *
     * @since 3.70.0
     */
    public void neuAufloesen() {
        synchronized (this) {
            resolve(loadMenuItems());
        }
    }

    /**
     * The required roles of a menu item: for every candidate key the configured role, in the
     * order they were found and without duplicates.
     *
     * @param candidates candidate keys of the menu item
     * @param configured configured mapping module key -&gt; role
     * @return the required roles, possibly empty (never {@code null})
     */
    private static List<String> requiredRolesOf(Set<String> candidates, Map<String, String> configured) {
        List<String> required = new ArrayList<>();
        for (String candidate : candidates) {
            String role = configured.get(candidate);
            if (role != null && !required.contains(role)) {
                required.add(role);
            }
        }
        return required;
    }

    /**
     * Enters a role-requiring menu item into the lookup tables of the tiles — via its canonical
     * link and, failing that, via its title.
     *
     * @param item        the menu item
     * @param required    its required roles; if the list is empty nothing happens
     * @param byLink      table link -&gt; roles, is extended
     * @param byMenuTitle table title -&gt; roles, is extended
     */
    private static void indexRequiredRoles(MenuItemImpl item, List<String> required,
                                           Map<String, List<String>> byLink,
                                           Map<String, List<String>> byMenuTitle) {
        if (required.isEmpty()) {
            return;
        }
        String link = canonicalLink(item.getCommand());
        if (!link.isEmpty()) {
            byLink.merge(link, required, ModuleRoleService::union);
        }
        String title = item.getTitle();
        if (title != null && !title.trim().isEmpty()) {
            byMenuTitle.merge(title.trim(), required, ModuleRoleService::union);
        }
    }

    private static List<String> union(List<String> a, List<String> b) {
        Set<String> merged = new LinkedHashSet<>(a);
        merged.addAll(b);
        return List.copyOf(merged);
    }

    private static Map<String, List<MenuItemImpl>> indexByTitle(Collection<MenuItemImpl> items) {
        Map<String, List<MenuItemImpl>> ret = new LinkedHashMap<>();
        for (MenuItemImpl item : items) {
            String title = item.getTitle();
            if (title != null && !title.trim().isEmpty()) {
                ret.computeIfAbsent(title.trim(), k -> new ArrayList<>()).add(item);
            }
        }
        return ret;
    }

    /**
     * The module keys under which a menu item can be addressed: its own {@code moduleId}, the
     * {@code moduleId} of every parent menu and the menu root id of the topmost parent menu.
     *
     * @param item    the menu item
     * @param byTitle index title -&gt; menu items, for climbing up to the parent menu
     * @return the candidate keys in canonical form (never {@code null})
     */
    static Set<String> moduleKeysOf(MenuItemImpl item, Map<String, List<MenuItemImpl>> byTitle) {
        Set<String> keys = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();

        MenuItemImpl current = item;
        MenuItemImpl topmost = item;
        int depth = 0;
        while (current != null && depth < MAX_PARENT_TIEFE) {
            topmost = current;
            addKey(keys, current.getModuleId());

            String parentTitle = current.getParent();
            if (parentTitle == null || parentTitle.trim().isEmpty() || !visited.add(parentTitle.trim())) {
                break;
            }
            List<MenuItemImpl> parents = byTitle.get(parentTitle.trim());
            current = (parents == null || parents.isEmpty()) ? null : parents.get(0);
            depth++;
        }

        // Fallback for modules without a moduleId: the menu root id of the topmost menu item.
        addKey(keys, topmost.getEffectiveMenuId());
        return keys;
    }

    private static void addKey(Set<String> keys, String raw) {
        String key = ModuleRoleProperties.canonicalKey(raw);
        if (!key.isEmpty()) {
            keys.add(key);
        }
    }

    /**
     * Canonical form of a menu link: lower-cased, without a leading slash, without the query and
     * without the extensions {@code .xhtml}, {@code .jsf}, {@code .html}, {@code .htm} — the same
     * normalisation as in the {@code PageAccessGuardService}.
     *
     * @param link the raw link, may be {@code null}
     * @return the canonical link, never {@code null}
     */
    static String canonicalLink(String link) {
        if (link == null) {
            return "";
        }
        String value = link.trim().toLowerCase(Locale.ROOT);
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int hash = value.indexOf('#');
        if (hash >= 0) {
            value = value.substring(0, hash);
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        for (String suffix : new String[]{".xhtml", ".jsf", ".html", ".htm"}) {
            if (value.endsWith(suffix)) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }
}
