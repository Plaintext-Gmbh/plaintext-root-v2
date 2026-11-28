/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext;

import java.util.Map;

/**
 * Exports and deletes the data of one tenant in a module (Karte 2566/2571, Presenter deletion lifecycle).
 * plaintext-modul-loeschung collects every bean of this type: the exports are mailed to the customer first, then
 * {@link #loesche(String)} runs for every module.
 *
 * <p>A module that keeps data outside the database (files in a storage) implements this, otherwise those files stay
 * behind. Database rows with a column {@code mandat} are removed by the deletion without the module's help.
 */
public interface MandantDaten {

    /** Short module name for the log and the overview, for example {@code praesentation}. */
    String modul();

    /**
     * Files for the customer, file name to content (for example every slide deck as PPTX); empty when the module
     * exports nothing. Called before {@link #loesche(String)}; an exception here aborts the deletion.
     */
    default Map<String, byte[]> exportiere(String mandat) {
        return Map.of();
    }

    /** Deletes all data of the tenant in this module and returns the number of deleted entries (for the log). */
    int loesche(String mandat);
}
