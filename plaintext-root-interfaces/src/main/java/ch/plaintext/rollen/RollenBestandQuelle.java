/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.rollen;

import ch.plaintext.modules.ModulApi;

import java.util.Map;

/**
 * The roles held by users of this instance, counted — the {@value RollenBedarf#BESTAND} part of
 * {@link RollenBedarf} (Karte 2377, ROOT-5). Root implements it on its user table; groups that only come
 * from an OIDC provider are not counted.
 *
 * @author info@plaintext.ch
 * @since 3.85.0
 */
@ModulApi(art = ModulApi.Art.SCHNITTSTELLE, stabilitaet = ModulApi.Stabilitaet.NEU)
public interface RollenBestandQuelle {

    /** @return stored role name (lowercase, without {@code ROLE_}) to the number of users holding it */
    Map<String, Integer> vergebeneRollen();
}
