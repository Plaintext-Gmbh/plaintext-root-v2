/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.ablagen;

import ch.plaintext.modules.ModulApi;

import java.io.IOException;
import java.util.Map;

/**
 * A kind of file store with a name (card 2425), e.g. {@code lokal}, {@code nextcloud}, {@code git}; an application
 * may add its own (a Spring bean), for example one that stores files in a document service.
 *
 * <p>Stores are declared by name in the {@code application.yml} under {@code plaintext.speicher.<name>} with
 * {@code art: <kind>} and the settings of that kind; {@link DateiAblagenRegister} opens them through the kind.
 * Secrets never appear in the settings: a kind reads them by reference (e.g. {@code passwort-secret: <name>})
 * from the secret store.</p>
 */
@ModulApi(art = ModulApi.Art.SCHNITTSTELLE, stabilitaet = ModulApi.Stabilitaet.NEU)
public interface DateiAblageArt {

    /** @return the name of the kind, lower case, e.g. {@code lokal}; unique within the application */
    String art();

    /**
     * Opens a store of this kind.
     *
     * @param name   name of the store, as modules ask for it
     * @param konfig the settings below {@code plaintext.speicher.<name>} without {@code art}; never secrets
     * @throws IOException if the settings are incomplete or the store cannot be reached
     */
    DateiAblage oeffne(String name, Map<String, String> konfig) throws IOException;
}
