/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.rollen;

import ch.plaintext.modules.ModulApi;

import java.util.List;

/**
 * One role this instance knows of, with where it comes from — the common model for the REST endpoint
 * {@code /api/root/rollen}, MCP {@code list_roles} and the module manifest (Karte 2377, ROOT-5).
 *
 * @param authority    Spring Security spelling, e.g. {@code ROLE_TERMINAL}
 * @param name         stored spelling (lowercase, without {@code ROLE_}), e.g. {@code terminal}
 * @param beschreibung description from the declaration, empty when not declared
 * @param quelle       {@value #DEKLARIERT}, {@value #MODULE_ROLES}, {@value #MENU_AUTO}, {@value #MENU_ANNOTATION}
 *                     or {@value #BESTAND}
 * @param modul        module the declaration names, empty when unknown
 * @param art          what the role grants; {@link RollenArt#ZUGANG} when not declared
 * @param ohneUmgehung ROOT and ADMIN do not get the module without this role
 * @param menuId       menu item the role belongs to ({@value #MENU_AUTO}, {@value #MENU_ANNOTATION}), else empty
 * @param module       module keys from {@code plaintext.menu.module-roles} that require this role
 * @param vergeben     number of users holding the role; {@code null} when the stock is unknown. Names are never
 *                     part of the model
 * @author info@plaintext.ch
 * @since 3.84.0
 */
@ModulApi(art = ModulApi.Art.DTO, stabilitaet = ModulApi.Stabilitaet.STABIL)
public record RollenBedarf(String authority, String name, String beschreibung, String quelle, String modul,
                           RollenArt art, boolean ohneUmgehung, String menuId, List<String> module,
                           Integer vergeben) {

    /** Declared by a {@code PlaintextRoleProvider}. */
    public static final String DEKLARIERT = "deklariert";
    /** Required by {@code plaintext.menu.module-roles} (configuration or Root › Module), not declared. */
    public static final String MODULE_ROLES = "module-roles";
    /** Automatic menu role {@code ROLE_MENU_<menuId>}, only under {@code plaintext.menu.access-policy=STRICT}. */
    public static final String MENU_AUTO = "menu-auto";
    /** Named in {@code @MenuAnnotation(roles=…)} but not declared. */
    public static final String MENU_ANNOTATION = "menu-annotation";
    /** Held by users, required by nothing loaded — candidate for cleaning up. */
    public static final String BESTAND = "bestand";

    public RollenBedarf {
        beschreibung = beschreibung == null ? "" : beschreibung;
        modul = modul == null ? "" : modul;
        art = art == null ? RollenArt.ZUGANG : art;
        menuId = menuId == null ? "" : menuId;
        module = module == null ? List.of() : List.copyOf(module);
    }
}
