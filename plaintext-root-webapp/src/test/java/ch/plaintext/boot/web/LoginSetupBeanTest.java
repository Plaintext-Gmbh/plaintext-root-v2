/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.web;

import ch.plaintext.oidc.entity.OidcConfig;
import ch.plaintext.oidc.service.OidcConfigService;
import ch.plaintext.settings.entity.SetupConfig;
import ch.plaintext.settings.service.SetupConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Card 2042: login form switch and auto-redirect to the single active identity provider. */
@ExtendWith(MockitoExtension.class)
class LoginSetupBeanTest {

    @Mock
    private SetupConfigService setupConfigService;

    @Mock
    private OidcConfigService oidcConfigService;

    @InjectMocks
    private LoginSetupBean bean;

    @BeforeEach
    void ohneGewaehltenIdp() {
        lenient().when(setupConfigService.findFirstWithOidcAutoRedirect()).thenReturn(Optional.empty());
        lenient().when(setupConfigService.isPasswordManagementDisabledAnywhere()).thenReturn(false);
        ReflectionTestUtils.setField(bean, "formular", true);
        ReflectionTestUtils.setField(bean, "autoRedirectEinzigerIdp", false);
    }

    private static OidcConfig idp(long id, String registrationId) {
        OidcConfig c = new OidcConfig();
        c.setId(id);
        c.setName(registrationId); // registrationId = name, kleingeschrieben
        c.setEnabled(true);
        return c;
    }

    @Test
    @DisplayName("Default: form hidden in the setup and one provider - no redirect, as before")
    void defaultBleibtWieBisher() {
        when(setupConfigService.isPasswordManagementDisabledAnywhere()).thenReturn(true);
        lenient().when(oidcConfigService.getActiveConfigs()).thenReturn(List.of(idp(1, "mock")));

        assertFalse(bean.isPasswordManagementActive());
        assertFalse(bean.isOidcAutoRedirectActive());
        assertNull(bean.getOidcAutoRedirectRegistrationId());
    }

    @Test
    @DisplayName("Property on, form hidden by property, exactly one provider - redirect to it")
    void einzigerIdpMitAusgeblendetemFormular() {
        ReflectionTestUtils.setField(bean, "formular", false);
        ReflectionTestUtils.setField(bean, "autoRedirectEinzigerIdp", true);
        when(oidcConfigService.getActiveConfigs()).thenReturn(List.of(idp(1, "mock")));

        assertFalse(bean.isPasswordManagementActive(), "plaintext.login.form=false hides the form");
        assertTrue(bean.isOidcAutoRedirectActive());
        assertEquals("mock", bean.getOidcAutoRedirectRegistrationId());
    }

    @Test
    @DisplayName("Property on, form hidden in the setup, exactly one provider - redirect to it")
    void einzigerIdpMitSetupFlag() {
        ReflectionTestUtils.setField(bean, "autoRedirectEinzigerIdp", true);
        when(setupConfigService.isPasswordManagementDisabledAnywhere()).thenReturn(true);
        when(oidcConfigService.getActiveConfigs()).thenReturn(List.of(idp(1, "keycloak")));

        assertEquals("keycloak", bean.getOidcAutoRedirectRegistrationId());
    }

    @Test
    @DisplayName("Property on, form hidden, two providers - no redirect")
    void zweiIdpsKeinRedirect() {
        ReflectionTestUtils.setField(bean, "formular", false);
        ReflectionTestUtils.setField(bean, "autoRedirectEinzigerIdp", true);
        when(oidcConfigService.getActiveConfigs()).thenReturn(List.of(idp(1, "mock"), idp(2, "google")));

        assertFalse(bean.isOidcAutoRedirectActive());
    }

    @Test
    @DisplayName("Property on, form visible, one provider - no redirect (the user can still choose the form)")
    void formularSichtbarKeinRedirect() {
        ReflectionTestUtils.setField(bean, "autoRedirectEinzigerIdp", true);
        lenient().when(oidcConfigService.getActiveConfigs()).thenReturn(List.of(idp(1, "mock")));

        assertTrue(bean.isPasswordManagementActive());
        assertFalse(bean.isOidcAutoRedirectActive());
    }

    @Test
    @DisplayName("Provider chosen in the setup wins, independent of the new properties")
    void gewaehlterIdpGewinnt() {
        SetupConfig setup = new SetupConfig();
        setup.setOidcAutoRedirectConfigId(2L);
        when(setupConfigService.findFirstWithOidcAutoRedirect()).thenReturn(Optional.of(setup));
        when(oidcConfigService.findAll()).thenReturn(List.of(idp(1, "mock"), idp(2, "google")));

        assertEquals("google", bean.getOidcAutoRedirectRegistrationId());
        assertTrue(bean.isOidcAutoRedirectActive());
    }
}
