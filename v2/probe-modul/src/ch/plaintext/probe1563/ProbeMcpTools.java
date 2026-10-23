/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.probe1563;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * Synthetisches Modul fuer die Lader-Probe (Karte 1563): ein Bean im Paket ch.plaintext mit einem
 * MCP-Werkzeug. Wird nur ueber loader.path geladen; die Huelle kennt die Klasse nicht.
 */
@Component
public class ProbeMcpTools {

    @McpTool(name = "probe_1563_echo", description = "Lader-Probe: gibt den Text zurueck")
    public String echo(@McpToolParam(description = "Text") String text) {
        return "probe-1563: " + text;
    }
}
