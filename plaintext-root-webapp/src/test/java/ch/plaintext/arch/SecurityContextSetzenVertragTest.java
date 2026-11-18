/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Karte 1863: {@code SecurityContextHolder.setContext} nur in {@code Systemlauf} (Maschinenlaeufe) und den Klassen, die
 * eine echte Anmeldung herstellen. Vorher stand der Systemkontext fuenfmal als Kopie im Code; die Regel verhindert die
 * sechste.
 *
 * <p>Bewusst nur ueber root, nicht im geteilten {@code PlaintextArchitectureTest}: schuetu und iot setzen den Kontext
 * heute selbst und werden erst in Etappe 3 umgestellt — dort wandert die Regel in den geteilten Satz.</p>
 */
class SecurityContextSetzenVertragTest {

    /** Gemessen wie in Jackson3VertragTest: mehrere hundert root-Klassen auf dem Klassenpfad von root-webapp. */
    private static final int MINDESTENS_KLASSEN = 500;

    /** Systemlauf plus die Anmelde-/Filterklassen, die einen Benutzerkontext herstellen. */
    private static final List<String> FREIGEGEBEN = List.of(
            "ch.plaintext.security.Systemlauf",
            "ch.plaintext.apitoken.McpBearerTokenFilter",
            "ch.plaintext.freigabe.link.service.FreigabeLinkService",
            "ch.plaintext.boot.plugins.security.PlaintextAuthenticationSuccessHandler",
            "ch.plaintext.boot.plugins.security.PlaintextSecurityImpl",
            "ch.plaintext.boot.plugins.security.SessionLoginFinalizer",
            "ch.plaintext.boot.plugins.security.totp.TotpVerificationController");

    static final ArchRule NUR_SYSTEMLAUF_SETZT = noClasses()
            .that(DescribedPredicate.describe("nicht freigegeben", (JavaClass c) -> FREIGEGEBEN.stream()
                    .noneMatch(f -> c.getName().equals(f) || c.getName().startsWith(f + "$"))))
            .should().callMethod(SecurityContextHolder.class, "setContext", SecurityContext.class)
            .because("Maschinenlaeufe gehen ueber ch.plaintext.security.Systemlauf (Karte 1863)");

    @Test
    @DisplayName("Karte 1863: setContext nur in Systemlauf und den Anmeldeklassen")
    void nurSystemlaufSetztDenKontext() {
        JavaClasses root = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("ch.plaintext");
        assertTrue(root.size() >= MINDESTENS_KLASSEN,
                "Nur " + root.size() + " root-Klassen gelesen — der Test sucht am falschen Ort.");
        NUR_SYSTEMLAUF_SETZT.check(root);
    }

    @Test
    @DisplayName("Positivkontrolle: eine eigene Kopie des Systemkontexts verletzt die Regel")
    void regelSchlaegtBeiEigenerKopieAn() {
        EvaluationResult ergebnis = NUR_SYSTEMLAUF_SETZT.evaluate(
                new ClassFileImporter().importClasses(KontextKopie.class));
        assertTrue(ergebnis.hasViolation(), "Die Regel muss eine eigene setContext-Kopie melden");
    }

    /** Attrappe: setzt den Kontext am Systemlauf vorbei. */
    static class KontextKopie {
        void setzen() {
            SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
        }
    }
}
