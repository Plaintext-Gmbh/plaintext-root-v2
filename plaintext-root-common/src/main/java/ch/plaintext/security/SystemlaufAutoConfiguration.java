/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

/**
 * Karte 1863: {@code @Scheduled}- und {@code @Async}-Laeufe (Boot 4 legt jeden {@link TaskDecorator}-Bean an seinen
 * Scheduler und Executor) laufen als {@link Systemlauf} im Mandat {@code global}, wenn der Thread keinen Kontext hat.
 * Vorher hatten sie gar keinen: {@code getMandat()} lieferte {@code NO_AUTH}, {@code @PreAuthorize} warf. Ein
 * Mandat ist bei rohem {@code @Scheduled} unbekannt; mandantengebundene Jobs gehoeren ins PlaintextCron-Framework.
 */
@AutoConfiguration
public class SystemlaufAutoConfiguration {

    @Bean
    public TaskDecorator systemlaufTaskDecorator() {
        return arbeit -> Systemlauf.umhuellen("global", arbeit);
    }
}
