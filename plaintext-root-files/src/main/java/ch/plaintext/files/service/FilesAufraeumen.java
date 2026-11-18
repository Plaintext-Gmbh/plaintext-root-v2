/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.files.service;

import ch.plaintext.PlaintextCron;
import ch.plaintext.bus.ExecutionScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Löscht nachts abgelaufene Dateien samt Links (Karte 1559). Im Inventar tat das der Code-Service; hier nur, wenn
 * der {@link ch.plaintext.files.DateiSpeicher} es nicht selbst tut. Auch ohne diesen Lauf ist eine abgelaufene Datei
 * nicht mehr abrufbar - er räumt nur den Platz auf.
 *
 * <p><b>Prototype (Karte 2354).</b> {@code plaintext-admin-cron} verlangt das von jedem {@link PlaintextCron} und
 * bricht den Start sonst ab ({@code CronBeanPostProcessor}: ein Singleton bekäme den Mandanten des letzten Laufs).
 * Bis Karte 2354 fehlte die Angabe - eine Anwendung mit Cron-Modul startete mit diesem Modul am Klassenpfad nicht.
 * Aufgefallen ist es erst, als die Seite in der root-Anwendung geprüft wurde.</p>
 */
@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class FilesAufraeumen implements PlaintextCron {

    private final FilesService files;

    @Override
    public ExecutionScope getScope() {
        return ExecutionScope.APPLICATION;
    }

    @Override
    public String getDisplayName() {
        return "Files: abgelaufene Dateien löschen";
    }

    @Override
    public String getDefaultCronExpression() {
        return "30 3 * * *";
    }

    @Override
    public void run(String mandant) {
        int n = files.aufraeumen();
        log.info("[FilesAufraeumen] {} abgelaufene Dateien gelöscht", n);
    }
}
