/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.rollenzuteilung.bedarf;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

/**
 * Who may read the role list (Entscheid ENT-4 = A): a session with ADMIN or ROOT; a token with {@code SCOPE_READ}
 * whose owner is ADMIN or ROOT. The bearer filter gives every token {@code SCOPE_READ} and the owner's real roles,
 * so one check covers both. Checked in the body as well as by the security chain, because an MCP call does not
 * take the web path (Karte 557).
 */
final class RollenZugriff {

    private static final Set<String> ROLLEN = Set.of("ROLE_ADMIN", "ROLE_ROOT");

    private RollenZugriff() {
    }

    /** @return 0 = allowed, 401 = not signed in, 403 = signed in without ADMIN/ROOT (or a token without SCOPE_READ) */
    static int pruefe() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || "anonymousUser".equals(a.getPrincipal())) {
            return 401;
        }
        boolean rolle = false;
        boolean token = false;
        boolean lesen = false;
        for (GrantedAuthority g : a.getAuthorities()) {
            String n = g.getAuthority();
            rolle |= ROLLEN.contains(n);
            token |= n != null && n.startsWith("SCOPE_");
            lesen |= "SCOPE_READ".equals(n);
        }
        return rolle && (!token || lesen) ? 0 : 403;
    }
}
