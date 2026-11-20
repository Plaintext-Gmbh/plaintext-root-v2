/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files;

import ch.plaintext.modules.ModulApi;

import java.util.List;

/**
 * Where the personal file store of {@code plaintext-root-files} keeps its files (card 1559).
 *
 * <p>Paths are relative and separated by {@code /}, e.g. {@code files/anna/bericht.pdf}; the module only passes
 * cleaned names (no {@code ..}, no control characters). An implementation must still refuse to leave its root.</p>
 *
 * <p><b>Default:</b> {@code plaintext-root-files} stores in the file system under {@code plaintext.files.verzeichnis}
 * and steps back as soon as the application defines its own bean. An application with another store (e.g. a
 * persistent volume behind a service) implements this interface.</p>
 */
@ModulApi(art = ModulApi.Art.SCHNITTSTELLE, stabilitaet = ModulApi.Stabilitaet.NEU)
public interface DateiSpeicher {

    /** Stores the file; an existing file is replaced and counts as changed now. Missing folders are created. */
    void upload(String pfad, byte[] inhalt);

    /** @return the content, {@code null} if the file does not exist */
    byte[] download(String pfad);

    /** @return {@code true} if a file exists at the path */
    boolean exists(String pfad);

    /**
     * @param ordner folder, e.g. {@code files/anna}; a missing folder is an empty list
     * @return files and sub folders directly in the folder, without the folder itself
     */
    List<DateiEintrag> list(String ordner);

    /** @return {@code true} if a file was deleted */
    boolean delete(String pfad);

    // ── Folders and moving (card 2377, backport ROOT-12). Defaults work on the five primitives above, so an
    //    implementation may still provide only those; a native store overrides them (DateisystemDateiSpeicher).

    /** @return {@code true} if a folder exists at the path (default: its parent lists it as a folder) */
    default boolean istOrdner(String pfad) {
        int i = pfad.lastIndexOf('/');
        String eltern = i < 0 ? "" : pfad.substring(0, i);
        String name = pfad.substring(i + 1);
        return list(eltern).stream().anyMatch(e -> e.ordner() && e.name().equals(name));
    }

    /**
     * Creates the folder (and missing parents). Default: nothing, because folders of a store without native
     * folders appear with their first upload; a native store creates it.
     */
    default void legeOrdnerAn(String pfad) {
    }

    /**
     * Moves a file or a whole folder; {@code nach} must not exist yet.
     * Default: copy and delete over the primitives, folder by folder.
     *
     * @throws IllegalArgumentException if {@code von} does not exist or {@code nach} exists
     */
    default void verschiebe(String von, String nach) {
        if (exists(nach) || istOrdner(nach)) {
            throw new IllegalArgumentException("Ziel existiert schon: " + nach);
        }
        if (istOrdner(von)) {
            for (DateiEintrag e : list(von)) {
                verschiebe(von + "/" + e.name(), nach + "/" + e.name());
            }
            return;
        }
        byte[] inhalt = download(von);
        if (inhalt == null) {
            throw new IllegalArgumentException("Quelle fehlt: " + von);
        }
        upload(nach, inhalt);
        delete(von);
    }

    /**
     * Deletes a file, or a folder with everything in it. Default: over {@link #list} and {@link #delete}.
     *
     * @return {@code true} if something was deleted
     */
    default boolean loescheRekursiv(String pfad) {
        if (!istOrdner(pfad)) {
            return delete(pfad);
        }
        boolean etwas = false;
        for (DateiEintrag e : list(pfad)) {
            etwas |= loescheRekursiv(pfad + "/" + e.name());
        }
        return etwas;
    }

    /**
     * Does the store delete expired files itself (retention rule outside the application)? Then the module does
     * not clean up; otherwise it deletes files {@code plaintext.files.frist} after their last change.
     */
    default boolean raeumtSelbstAuf() {
        return false;
    }
}
