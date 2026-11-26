/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.rollenzuteilung.bedarf;

import ch.plaintext.boot.menu.MenuAccessPolicy;
import ch.plaintext.boot.menu.MenuItemImpl;
import ch.plaintext.boot.menu.ModuleRoleProperties;
import ch.plaintext.framework.ModuleRoleDeclarationProvider;
import ch.plaintext.framework.PlaintextRole;
import ch.plaintext.framework.PlaintextRoleProvider;
import ch.plaintext.framework.PlaintextRoleRegistry;
import ch.plaintext.rollen.RollenArt;
import ch.plaintext.rollen.RollenBedarf;
import ch.plaintext.rollen.RollenBestandQuelle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Every role this instance knows of, with its origin (Karte 2377, ROOT-5; Konzept «Rollen von aussen lesen»,
 * Stufe C): declared by a {@link PlaintextRoleProvider}, required by {@code plaintext.menu.module-roles}, automatic
 * menu role under {@code STRICT}, named in {@code @MenuAnnotation(roles)}, or only held by users ({@code bestand}).
 *
 * <p>Lives here and not in the {@link PlaintextRoleRegistry} because the registry ({@code plaintext-root-common})
 * cannot see menu items. Groups that only come from an OIDC provider are not counted (Entscheid ENT-5 = A).</p>
 */
@Slf4j
@Service
public class RollenBedarfService {

    /** Hint for every reader of the list (ENT-5 = A). */
    public static final String HINWEIS_OIDC = "OIDC-Gruppen nicht gezählt";

    private static final String ROLE = "ROLE_";
    private static final String PROPERTY_MANDAT = "PROPERTY_MANDAT_";

    private final ApplicationContext ctx;

    public RollenBedarfService(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    /** @return all roles, sorted by authority */
    public List<RollenBedarf> bedarf() {
        List<PlaintextRole> deklariert = new ArrayList<>();
        PlaintextRoleRegistry registry = new PlaintextRoleRegistry(ctx.getBeansOfType(PlaintextRoleProvider.class)
                .values().stream().filter(p -> !(p instanceof ModuleRoleDeclarationProvider)).toList());
        deklariert.addAll(registry.getDeclaredRoles());
        ModuleRoleProperties props = ctx.getBeanProvider(ModuleRoleProperties.class).getIfAvailable();
        Map<String, Integer> bestand = null;
        RollenBestandQuelle quelle = ctx.getBeanProvider(RollenBestandQuelle.class).getIfAvailable();
        if (quelle != null) {
            try {
                bestand = quelle.vergebeneRollen();
            } catch (RuntimeException e) {
                log.warn("Rollenbedarf: Bestand nicht lesbar ({}), «vergeben» bleibt leer", e.getMessage());
            }
        }
        return sammle(deklariert, props == null ? Map.of() : props.canonicalModuleRoles(),
                ctx.getBeansOfType(MenuItemImpl.class).values(), bestand);
    }

    /**
     * @param modul module key or name; empty = all
     * @return the roles naming this module (declaration or module-roles key)
     */
    public List<RollenBedarf> bedarf(String modul) {
        if (modul == null || modul.isBlank()) {
            return bedarf();
        }
        String m = modul.strip().toLowerCase(Locale.ROOT);
        return bedarf().stream().filter(r -> m.equals(r.modul()) || r.module().contains(m)).toList();
    }

    /**
     * @param deklariert  declared roles without those that only mirror module-roles
     * @param moduleRoles canonical module key to canonical role (uppercase, without {@code ROLE_})
     * @param menues      registered menu items
     * @param bestand     stored role name to number of users, {@code null} = unknown
     */
    static List<RollenBedarf> sammle(List<PlaintextRole> deklariert, Map<String, String> moduleRoles,
                                     Collection<MenuItemImpl> menues, Map<String, Integer> bestand) {
        Map<String, List<String>> moduleJeRolle = new TreeMap<>();
        moduleRoles.forEach((k, r) -> moduleJeRolle.computeIfAbsent(ROLE + r, x -> new ArrayList<>()).add(k));
        Map<String, Integer> vergeben = new TreeMap<>();
        if (bestand != null) {
            bestand.forEach((n, z) -> vergeben.merge(authority(n), z == null ? 0 : z, Integer::sum));
        }
        Map<String, RollenBedarf> ret = new TreeMap<>();
        for (PlaintextRole r : deklariert) {
            String a = r.authorityName();
            ret.putIfAbsent(a, new RollenBedarf(a, r.normalizedName(), r.description(), RollenBedarf.DEKLARIERT,
                    r.modul(), r.art(), r.ohneUmgehung(), "", moduleJeRolle.get(a), zahl(vergeben, a, bestand)));
        }
        moduleJeRolle.forEach((a, keys) -> ret.putIfAbsent(a, neu(a, RollenBedarf.MODULE_ROLES, "", keys, vergeben,
                bestand)));
        for (MenuItemImpl m : menues) {
            if (m.isGruppe()) {
                continue;
            }
            for (String rolle : m.getRoles() == null ? List.<String>of() : m.getRoles()) {
                String a = authority(rolle);
                if (!a.equals(ROLE)) {
                    ret.putIfAbsent(a, neu(a, RollenBedarf.MENU_ANNOTATION, m.getEffectiveMenuId(), null, vergeben,
                            bestand));
                }
            }
            if (m.getAccessPolicy() == MenuAccessPolicy.STRICT) {
                String a = m.getAutoRole();
                ret.putIfAbsent(a, neu(a, RollenBedarf.MENU_AUTO, m.getEffectiveMenuId(), null, vergeben, bestand));
            }
        }
        vergeben.forEach((a, z) -> {
            if (!a.startsWith(ROLE + PROPERTY_MANDAT)) {
                ret.putIfAbsent(a, neu(a, RollenBedarf.BESTAND, "", null, vergeben, bestand));
            }
        });
        return List.copyOf(ret.values());
    }

    private static RollenBedarf neu(String a, String quelle, String menuId, List<String> module,
                                    Map<String, Integer> vergeben, Map<String, Integer> bestand) {
        return new RollenBedarf(a, a.substring(ROLE.length()).toLowerCase(Locale.ROOT), "", quelle, "",
                RollenArt.ZUGANG, false, menuId, module, zahl(vergeben, a, bestand));
    }

    private static Integer zahl(Map<String, Integer> vergeben, String a, Map<String, Integer> bestand) {
        return bestand == null ? null : vergeben.getOrDefault(a, 0);
    }

    static String authority(String rolle) {
        return ROLE + ModuleRoleProperties.canonicalRole(rolle);
    }
}
