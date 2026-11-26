/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.rollenzuteilung.bedarf;

import ch.plaintext.rollen.RollenBedarf;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code GET /api/root/rollen[?modul=…]}: every role of this instance with its origin and the number of users
 * holding it, never names (Karte 2377, ROOT-6; Konzept «Rollen von aussen lesen», Stufe C). Only ADMIN/ROOT,
 * see {@link RollenZugriff}; for API tokens the application adds the path to
 * {@code plaintext.mcp.bearer-filter.url-patterns}. The header {@value #HINWEIS} says that OIDC groups are not
 * counted (ENT-5 = A).
 */
@RestController
public class RollenController {

    static final String PFAD = "/api/root/rollen";
    static final String HINWEIS = "X-Plaintext-Hinweis";

    private final RollenBedarfService service;

    public RollenController(RollenBedarfService service) {
        this.service = service;
    }

    @GetMapping(value = PFAD, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<RollenBedarf>> rollen(@RequestParam(required = false) String modul) {
        int status = RollenZugriff.pruefe();
        if (status != 0) {
            return ResponseEntity.status(status).build();
        }
        return ResponseEntity.ok().header(HINWEIS, "OIDC-Gruppen nicht gezaehlt").body(service.bedarf(modul));
    }
}
