/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.plugins.security.selfservice;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Tunables for the self-registration and password-reset-via-link flows.
 *
 * <p>The per-mandant master toggles live in {@code SetupConfig}; this class
 * only carries operational knobs that are the same across all mandants.
 */
@Component
@ConfigurationProperties(prefix = "plaintext.selfservice")
@Data
public class SelfServiceProperties {

    /**
     * Public base URL embedded into outgoing verification / reset links.
     * Defaults to empty, in which case the controller falls back to the
     * incoming request's base URL — fine for single-host deployments.
     */
    private String publicBaseUrl = "";

    /**
     * Mandant assigned to users created through self-registration. The mandant
     * is encoded as a {@code PROPERTY_MANDAT_<value>} role.
     */
    private String defaultMandat = "default";

    /**
     * Lifetime of a registration verification token.
     */
    private Duration registrationTokenTtl = Duration.ofHours(24);

    /**
     * Lifetime of a password-reset token. Kept short on purpose — links sit
     * in mailboxes long after they have served their purpose.
     */
    private Duration passwordResetTokenTtl = Duration.ofHours(1);

    /**
     * Karte 2554 (SaaS): each completed self-registration gets a mandant of its own instead of
     * {@link #defaultMandat}. Off by default — the existing instances keep their behaviour.
     */
    private boolean mandantJeRegistrierung = false;

    /**
     * Karte 2554: bare role names (without {@code ROLE_}) every self-registered user receives in
     * addition to {@code user}, e.g. {@code praesentation}.
     */
    private List<String> standardRollen = new ArrayList<>();
}
