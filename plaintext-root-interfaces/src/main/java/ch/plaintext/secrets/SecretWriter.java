/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.secrets;

import ch.plaintext.modules.ModulApi;

/**
 * Karte 2408: stores a secret of the current tenant in the active backend of <i>Root → Secrets</i>, for technical
 * producers that receive a credential and must never put it into a file or the log (e.g. the access of a module
 * source pasted on «Laufzeit-Module»). Counterpart of {@link SecretResolver}; implemented by the {@code SecretService}
 * in {@code plaintext-admin-secrets}. Keep the dependency optional, like the resolver.
 */
@ModulApi(art = ModulApi.Art.SCHNITTSTELLE, stabilitaet = ModulApi.Stabilitaet.NEU)
public interface SecretWriter {

    /** Creates or replaces the secret {@code name}; a soft-deleted entry of that name comes back. */
    void store(String name, String value, String note);

    /** Soft-deletes the secret {@code name}; unknown names are ignored. */
    void remove(String name);
}
