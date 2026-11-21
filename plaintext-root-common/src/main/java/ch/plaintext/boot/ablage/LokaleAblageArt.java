/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.ablage;

import ch.plaintext.ablagen.DateiAblage;
import ch.plaintext.ablagen.DateiAblageArt;
import ch.plaintext.modules.ModulApiUmsetzung;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/** Karte 2425: Art {@code lokal}, ein Unterordner ({@code pfad}) unter {@code plaintext.ablagen.lokal.basis}. */
@ModulApiUmsetzung(beschreibung = "Opens a folder on the server below the base folder set by the operator.",
        seiteneffekte = ModulApiUmsetzung.Seiteneffekte.INTERN,
        hinweise = {"Setting pfad is required and relative to plaintext.ablagen.lokal.basis",
                "Without plaintext.ablagen.lokal.basis the kind is off and opening fails"},
        beispiele = {"plaintext.speicher.files: {art: lokal, pfad: files}"})
public class LokaleAblageArt implements DateiAblageArt {

    public static final String ART = "lokal";

    private final Path basis;

    /** @param basis {@code null} = Art aus */
    public LokaleAblageArt(Path basis) {
        this.basis = basis;
    }

    @Override
    public String art() {
        return ART;
    }

    @Override
    public DateiAblage oeffne(String name, Map<String, String> konfig) throws IOException {
        return LokaleDateiAblage.unter(name, basis, konfig.get("pfad"));
    }
}
