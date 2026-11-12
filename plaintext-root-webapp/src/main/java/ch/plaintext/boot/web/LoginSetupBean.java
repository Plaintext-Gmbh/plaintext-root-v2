/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.web;

import ch.plaintext.oidc.entity.OidcConfig;
import ch.plaintext.oidc.service.OidcConfigService;
import ch.plaintext.settings.entity.SetupConfig;
import ch.plaintext.settings.service.SetupConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Component("loginSetupBean")
@Slf4j
public class LoginSetupBean implements Serializable {

    @Autowired
    private SetupConfigService setupConfigService;

    @Autowired(required = false)
    private OidcConfigService oidcConfigService;

    /** Card 2042: false hides the built-in username/password form (default true = status quo). */
    @Value("${plaintext.login.form:true}")
    private boolean formular = true;

    /**
     * Card 2042: with the form hidden and exactly ONE active OIDC provider, the login page goes straight to
     * it (default false = status quo). Two or more providers: no redirect. The page never redirects after
     * ?error or ?logout (login.xhtml), so a failed or ended sign-in does not loop.
     */
    @Value("${plaintext.login.auto-redirect-single-idp:false}")
    private boolean autoRedirectEinzigerIdp;

    public boolean isOidcAutoRedirectActive() {
        return getOidcAutoRedirectRegistrationId() != null;
    }

    public boolean isPasswordManagementActive() {
        if (!formular) {
            return false;
        }
        try {
            return !setupConfigService.isPasswordManagementDisabledAnywhere();
        } catch (Exception e) {
            log.debug("Error checking password management: {}", e.getMessage());
            return true;
        }
    }

    public boolean isSelfRegistrationActive() {
        try {
            return setupConfigService.isSelfRegistrationEnabledAnywhere();
        } catch (Exception e) {
            log.debug("Error checking self-registration: {}", e.getMessage());
            return false;
        }
    }

    public boolean isPasswordResetLinkActive() {
        try {
            return setupConfigService.isPasswordResetLinkEnabledAnywhere();
        } catch (Exception e) {
            log.debug("Error checking password-reset link: {}", e.getMessage());
            return false;
        }
    }

    public boolean isMagicLinkActive() {
        try {
            return setupConfigService.isMagicLinkEnabledAnywhere();
        } catch (Exception e) {
            log.debug("Error checking magic-link: {}", e.getMessage());
            return false;
        }
    }

    /** The provider the login page redirects to: the one chosen in the setup, else the single one (card 2042). */
    public String getOidcAutoRedirectRegistrationId() {
        try {
            String gewaehlt = gewaehlterIdp();
            return gewaehlt != null ? gewaehlt : einzigerIdp();
        } catch (Exception e) {
            log.debug("Error getting OIDC redirect registration ID: {}", e.getMessage());
            return null;
        }
    }

    private String gewaehlterIdp() {
        Optional<SetupConfig> config = setupConfigService.findFirstWithOidcAutoRedirect();
        if (config.isEmpty()) {
            return null;
        }
        Long configId = config.get().getOidcAutoRedirectConfigId();
        if (configId == null || oidcConfigService == null) {
            return null;
        }
        return oidcConfigService.findAll().stream()
                .filter(c -> c.getId().equals(configId) && c.isEnabled())
                .map(OidcConfig::getRegistrationId)
                .findFirst()
                .orElse(null);
    }

    private String einzigerIdp() {
        if (!autoRedirectEinzigerIdp || oidcConfigService == null || isPasswordManagementActive()) {
            return null;
        }
        List<OidcConfig> aktiv = oidcConfigService.getActiveConfigs();
        return aktiv.size() == 1 ? aktiv.get(0).getRegistrationId() : null;
    }
}
