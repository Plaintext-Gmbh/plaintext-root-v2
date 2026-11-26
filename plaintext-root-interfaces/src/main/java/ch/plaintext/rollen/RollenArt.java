/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.rollen;

import ch.plaintext.modules.ModulApi;

/**
 * What a role grants, so that whoever assigns roles from outside (operations, OIDC groups) can tell an
 * entry role from an administrative one (Karte 2377, ROOT-5).
 *
 * @author info@plaintext.ch
 * @since 3.85.0
 */
@ModulApi(art = ModulApi.Art.DTO, stabilitaet = ModulApi.Stabilitaet.STABIL)
public enum RollenArt {
    /** Opens a module or page. The default. */
    ZUGANG,
    /** Allows changing data in a module that is otherwise readable. */
    BEARBEITEN,
    /** Administers a module or the instance. */
    ADMIN,
    /** For services and agents, not for people. */
    TECHNISCH
}
