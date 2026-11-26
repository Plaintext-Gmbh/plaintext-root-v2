/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.rollenzuteilung.bedarf;

import ch.plaintext.rollen.RollenBedarf;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * MCP {@code list_roles} (Karte 2377, ROOT-6): the same list as {@code GET /api/root/rollen}, for agents. Token with
 * {@code SCOPE_READ}, owner ADMIN or ROOT ({@link RollenZugriff}, checked in the body).
 */
@Component
@ConditionalOnClass(name = "org.springframework.ai.mcp.annotation.McpTool")
public class RollenMcpTools {

    private final RollenBedarfService service;

    public RollenMcpTools(RollenBedarfService service) {
        this.service = service;
    }

    @PreAuthorize("hasAuthority('SCOPE_READ')")
    @McpTool(name = "list_roles", description = "Listet alle Rollen dieser Instanz mit Quelle (deklariert, module-roles, "
            + "menu-auto, menu-annotation, bestand), Modul, Art, ohneUmgehung und der Zahl der Benutzer (keine Namen). "
            + "Nur fuer ADMIN/ROOT. OIDC-Gruppen sind nicht gezaehlt.")
    public String listRoles(@McpToolParam(required = false, description = "Nur Rollen dieses Moduls, z. B. terminal")
                            String modul) {
        if (RollenZugriff.pruefe() != 0) {
            return "FEHLER: list_roles nur fuer ADMIN oder ROOT (Token mit SCOPE_READ).";
        }
        StringBuilder sb = new StringBuilder("Hinweis: OIDC-Gruppen nicht gezaehlt.\n");
        for (RollenBedarf r : service.bedarf(modul)) {
            sb.append("- ").append(r.authority()).append(" quelle=").append(r.quelle());
            if (!r.modul().isEmpty()) {
                sb.append(" modul=").append(r.modul());
            }
            sb.append(" art=").append(r.art());
            if (r.ohneUmgehung()) {
                sb.append(" ohneUmgehung");
            }
            if (!r.menuId().isEmpty()) {
                sb.append(" menuId=").append(r.menuId());
            }
            if (!r.module().isEmpty()) {
                sb.append(" module=").append(String.join(",", r.module()));
            }
            if (r.vergeben() != null) {
                sb.append(" vergeben=").append(r.vergeben());
            }
            if (!r.beschreibung().isEmpty()) {
                sb.append(" - ").append(r.beschreibung());
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
