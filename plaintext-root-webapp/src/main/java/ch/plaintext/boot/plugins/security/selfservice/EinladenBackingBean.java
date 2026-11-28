/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.plugins.security.selfservice;

import ch.plaintext.PlaintextSecurity;
import ch.plaintext.boot.plugins.jsf.FacesMessages;
import ch.plaintext.framework.EigeneAdresse;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Karte 2560: Seite «Personen einladen» (einladen.xhtml). Wer die Rolle {@code einladen} hat (in der SaaS-Instanz jeder
 * Neukunde), laedt eine Mail-Adresse in den EIGENEN Mandanten ein, z.B. den Vorstand eines Vereins.
 */
@Component
@Scope("session")
@SuppressWarnings("java:S6813")
public class EinladenBackingBean implements Serializable {

    static final String ROLLE = "einladen";

    @org.springframework.beans.factory.annotation.Autowired
    private transient RegistrationService registrationService;
    @org.springframework.beans.factory.annotation.Autowired
    private transient PlaintextSecurity security;
    @org.springframework.beans.factory.annotation.Autowired
    private transient EigeneAdresse eigeneAdresse;
    @Value("${plaintext.baseurl:}")
    private String plaintextBaseurl = "";

    @Getter
    @Setter
    private String email;

    public void einladen() {
        if (!(security.ifGranted(ROLLE) || security.ifGranted("ADMIN") || security.ifGranted("ROOT"))) {
            FacesMessages.error("Einladen ist für dieses Konto nicht freigeschaltet.");
            return;
        }
        String basis = eigeneAdresse.basis(plaintextBaseurl);
        switch (registrationService.einladen(email, security.getMandat(), security.getUser(), basis == null ? "" : basis)) {
            case GESENDET -> {
                FacesMessages.info("Einladung an " + email.trim() + " geschickt. Der Link gilt 7 Tage.");
                email = null;
            }
            case HAT_KONTO -> FacesMessages.warn("Diese Adresse hat schon ein Konto.");
            case ZU_VIELE -> FacesMessages.warn("Es sind schon viele Einladungen offen. Bitte warten, bis sie angenommen sind.");
            case UNGUELTIG -> FacesMessages.error("Bitte eine gültige E-Mail-Adresse eingeben.");
            case VERSAND_FEHLER -> FacesMessages.error("Die Einladung konnte nicht gesendet werden. Bitte später erneut versuchen.");
        }
    }
}
