/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files;

import ch.plaintext.testsupport.ModulManifestTest;

/** Karte 2377 ROOT-7: das Manifest von plaintext-root-files (Akzeptanzkriterium des Plans). */
class FilesModulManifestTest extends ModulManifestTest {

    @Override
    protected String modul() {
        return "files";
    }

    @Override
    protected String paket() {
        return "ch.plaintext.files";
    }
}
