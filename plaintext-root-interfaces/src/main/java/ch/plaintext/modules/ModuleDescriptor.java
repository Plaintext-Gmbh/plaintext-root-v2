/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.modules;

/**
 * Discovery interface for feature modules (Task #016, module management). Every module that should
 * appear in the central management page ("Root | Module") provides a Spring bean implementing this
 * interface — analogous to the {@code PlaintextCron} discovery (Spring collects
 * {@code List<ModuleDescriptor>} automatically, no central registry). Without such a bean a module
 * does not show up (opt-in). The on/off state is persisted separately (table {@code module_config}).
 */
@ModulApi(art = ModulApi.Art.ERWEITERUNG, stabilitaet = ModulApi.Stabilitaet.STABIL)
public interface ModuleDescriptor {

    /** Unique, stable module id (e.g. "secrets", "member", "buchhaltung"). */
    String moduleId();

    /** Display name for the UI (default: {@link #moduleId()}). */
    default String displayName() {
        return moduleId();
    }

    /**
     * Module version — by default from the jar manifest ({@code Implementation-Version} of the
     * implementing class); if it is missing (IDE/dev run), {@code "dev"}. Prerequisite for the
     * manifest: {@code maven-jar-plugin} with {@code addDefaultImplementationEntries}.
     */
    default String version() {
        Package p = getClass().getPackage();
        String v = p != null ? p.getImplementationVersion() : null;
        return v != null && !v.isBlank() ? v : "dev";
    }

    /**
     * JPA entity classes of this module that are included in export/import (Task #016 phase 2).
     * Default: empty (opt-in) — modules without data of their own (e.g. pure UI modules) do not
     * need to override anything.
     */
    default java.util.List<Class<?>> entities() {
        return java.util.List.of();
    }

    /**
     * Default settings of the module (card 2425). On start the application writes every missing one to the
     * settings (tenant {@code global}); an existing value is never overwritten, so an operator's change stays.
     * Keys start with {@code <moduleId>.}, so the settings page lists them in the module's group; other keys are
     * skipped with a log line. Never put a password or token here: those live in the secret store.
     * Default: none.
     */
    default java.util.List<Vorgabe> einstellungen() {
        return java.util.List.of();
    }

    /**
     * A default setting.
     *
     * @param schluessel   key, starting with {@code <moduleId>.}
     * @param wert         default value as text
     * @param typ          {@code STRING}, {@code INTEGER}, {@code BOOLEAN}, {@code DATE} or {@code LIST}
     * @param beschreibung what the setting does, shown on the settings page
     */
    record Vorgabe(String schluessel, String wert, String typ, String beschreibung) {
    }
}
