/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.framework;

import ch.plaintext.arch.StabileApi;
import ch.plaintext.rollen.RollenArt;


import java.io.Serializable;
import java.util.Locale;

/**
 * A role declared by a module: technical name plus human-readable description.
 *
 * <p>Modules declare their roles through {@link PlaintextRoleProvider#getDeclaredRoles()};
 * the {@link PlaintextRoleRegistry} collects all declarations and offers them, among other
 * things, to the user administration as a selection.</p>
 *
 * <p><b>Naming convention:</b> {@link #name()} is the technical role name WITHOUT the
 * {@code ROLE_} prefix (e.g. {@code admin}). What counts for identity (dedup in the registry)
 * is {@link #normalizedName()}: lowercase and without the {@code ROLE_} prefix — the same way
 * the roles are stored on the {@code MyUserEntity}. {@link #authorityName()} returns the
 * Spring Security spelling {@code ROLE_<UPPERCASE>}, the way the
 * {@code MyUserDetailsService} assigns it at login.</p>
 *
 * @param name        technical role name (declared with or without the {@code ROLE_} prefix)
 * @param description human-readable description for selection UIs; never {@code null} (possibly empty)
 * @param modul        module key the role opens, empty when not tied to one module (since 3.84.0)
 * @param art          what the role grants, {@link RollenArt#ZUGANG} by default (since 3.84.0)
 * @param ohneUmgehung ROOT/ADMIN do not get {@code modul} without this role (since 3.84.0, Karte 2377 ROOT-5)
 * @author info@plaintext.ch
 * @since 1.600.0
 */
@StabileApi("Rollen-SPI: Modul deklariert seine Rollen")
public record PlaintextRole(String name, String description, String modul, RollenArt art, boolean ohneUmgehung)
        implements Serializable {

    /** Spring Security prefix of the authority spelling. */
    private static final String ROLE_PREFIX = "ROLE_";

    public PlaintextRole {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Rollenname darf nicht leer sein");
        }
        name = name.trim();
        description = description == null ? "" : description.trim();
        modul = modul == null ? "" : modul.trim().toLowerCase(Locale.ROOT);
        art = art == null ? RollenArt.ZUGANG : art;
    }

    /**
     * Role without module, kind {@link RollenArt#ZUGANG}, with the ROOT/ADMIN bypass — what every provider declared
     * before Karte 2377 (ROOT-5); existing providers keep compiling unchanged.
     *
     * @param name        technical role name
     * @param description description for the role selection
     */
    public PlaintextRole(String name, String description) {
        this(name, description, "", RollenArt.ZUGANG, false);
    }

    /**
     * @param modul module key the role opens ({@code plaintext.menu.module-roles} key), e.g. {@code terminal}
     * @return a copy naming the module
     * @since 3.84.0
     */
    public PlaintextRole modul(String modul) {
        return new PlaintextRole(name, description, modul, art, ohneUmgehung);
    }

    /**
     * @param art what the role grants
     * @return a copy with this kind
     * @since 3.84.0
     */
    public PlaintextRole art(RollenArt art) {
        return new PlaintextRole(name, description, modul, art, ohneUmgehung);
    }

    /**
     * {@code true}: ROOT and ADMIN do not get the {@linkplain #modul() module} without this role — root makes the
     * module strict ({@code plaintext.menu.strenge-module-roles}) as long as Root › Module does not say otherwise.
     *
     * @param ohneUmgehung whether the ROOT/ADMIN bypass is off
     * @return a copy with this setting
     * @since 3.84.0
     */
    public PlaintextRole ohneUmgehung(boolean ohneUmgehung) {
        return new PlaintextRole(name, description, modul, art, ohneUmgehung);
    }

    /**
     * Role without a description — for providers that only implement
     * {@link PlaintextRoleProvider#getRoles()} (backwards compatibility).
     *
     * @param name technical role name
     * @return role with an empty description
     */
    public static PlaintextRole of(String name) {
        return new PlaintextRole(name, "");
    }

    /**
     * Canonical identity of the role: lowercase, without the {@code ROLE_} prefix.
     * Matches the storage convention of the user administration.
     *
     * @return normalized role name, e.g. {@code admin}
     */
    public String normalizedName() {
        String n = name;
        if (n.toUpperCase(Locale.ROOT).startsWith(ROLE_PREFIX)) {
            n = n.substring(ROLE_PREFIX.length());
        }
        return n.toLowerCase(Locale.ROOT);
    }

    /**
     * Spring Security authority spelling of the role.
     *
     * @return authority name, e.g. {@code ROLE_ADMIN}
     */
    public String authorityName() {
        return ROLE_PREFIX + normalizedName().toUpperCase(Locale.ROOT);
    }
}
