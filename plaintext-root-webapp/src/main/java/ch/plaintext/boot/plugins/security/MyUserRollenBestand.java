/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.plugins.security;

import ch.plaintext.boot.plugins.security.model.MyUserEntity;
import ch.plaintext.boot.plugins.security.persistence.MyUserRepository;
import ch.plaintext.framework.PlaintextRole;
import ch.plaintext.modules.ModulApiUmsetzung;
import ch.plaintext.rollen.RollenBestandQuelle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.TreeMap;

/**
 * Counts the roles on {@code my_user_entity.roles} (Karte 2377, ROOT-5). The tenant markers
 * {@code PROPERTY_MANDAT_*} are not roles and are skipped; OIDC groups never reach this table and are not counted.
 */
@Component
@ModulApiUmsetzung(beschreibung = "Counts the users per stored role in my_user_entity.roles.",
        seiteneffekte = ModulApiUmsetzung.Seiteneffekte.KEINE,
        hinweise = {"Reads every user (a few hundred), only on request", "PROPERTY_MANDAT_* markers are skipped",
                "OIDC groups are not included"},
        beispiele = {"vergebeneRollen() -> {admin=2, files=5, user=17}"})
public class MyUserRollenBestand implements RollenBestandQuelle {

    private final MyUserRepository benutzer;

    public MyUserRollenBestand(MyUserRepository benutzer) {
        this.benutzer = benutzer;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> vergebeneRollen() {
        Map<String, Integer> ret = new TreeMap<>();
        for (MyUserEntity u : benutzer.findAll()) {
            if (u.getRoles() == null) {
                continue;
            }
            u.getRoles().stream()
                    .filter(r -> r != null && !r.isBlank() && !r.toUpperCase(java.util.Locale.ROOT).startsWith("PROPERTY_MANDAT_"))
                    .map(r -> PlaintextRole.of(r).normalizedName())
                    .distinct()
                    .forEach(r -> ret.merge(r, 1, Integer::sum));
        }
        return ret;
    }
}
