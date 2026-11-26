/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.testsupport;

import ch.plaintext.boot.menu.MenuAnnotationScanner;
import ch.plaintext.boot.menu.MenuItemImpl;
import ch.plaintext.framework.PlaintextRole;
import ch.plaintext.framework.PlaintextRoleProvider;
import ch.plaintext.framework.PlaintextRoleRegistry;
import ch.plaintext.lader.ModulManifest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Basisklasse für den Manifest-Test eines Moduls (Karte 2377, ROOT-7; Entscheid ENT-3 = A: Test statt Maven-Plugin).
 * Ein Modul legt eine Unterklasse an, die {@link #modul()} und {@link #paket()} nennt. Der Test liest ohne
 * Spring-Start die {@link PlaintextRoleProvider} des Pakets, die {@code @MenuAnnotation}-Einträge (Vorgabe vor jeder
 * Platzierung) und die {@code @ConfigurationProperties}-Präfixe und vergleicht das Ergebnis mit
 * {@code src/main/resources/}{@value ModulManifest#PFAD}. Abweichung = rot; neu schreiben mit
 * {@code mvn test -Dplaintext.manifest.schreiben=true}.
 *
 * <p>Ein Provider braucht einen Konstruktor ohne Argumente; sonst {@link #rollenProvider()} überschreiben.</p>
 */
public abstract class ModulManifestTest {

    /** Systemproperty: {@code true} schreibt das Manifest, statt zu vergleichen. */
    public static final String SCHREIBEN = "plaintext.manifest.schreiben";

    /** @return der Modulschlüssel, z. B. {@code files} */
    protected abstract String modul();

    /** @return das Basispaket des Moduls, z. B. {@code ch.plaintext.files} */
    protected abstract String paket();

    /** @return wohin das Manifest gehört (relativ zum Modulverzeichnis, in dem Surefire läuft) */
    protected Path ziel() {
        return Path.of("src", "main", "resources").resolve(ModulManifest.PFAD);
    }

    /** @return {@code true}: nur Klassen aus {@code target/classes}, Hilfsklassen aus {@code src/test} zählen nicht */
    protected boolean nurHauptcode() {
        return true;
    }

    /** @return die Rollen-Provider des Moduls; Vorgabe: jede Klasse im Paket mit Konstruktor ohne Argumente */
    protected List<PlaintextRoleProvider> rollenProvider() {
        List<PlaintextRoleProvider> ret = new ArrayList<>();
        for (Class<?> c : klassen(new AssignableTypeFilter(PlaintextRoleProvider.class))) {
            if (c.isInterface() || Modifier.isAbstract(c.getModifiers())) {
                continue;
            }
            try {
                ret.add((PlaintextRoleProvider) c.getDeclaredConstructor().newInstance());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(c.getName() + " hat keinen Konstruktor ohne Argumente: "
                        + "rollenProvider() überschreiben", e);
            }
        }
        return ret;
    }

    /** @return das Manifest, wie es der Code heute ergibt */
    public ModulManifest erzeugen() {
        List<ModulManifest.Rolle> rollen = new ArrayList<>();
        for (PlaintextRole r : new PlaintextRoleRegistry(rollenProvider()).getDeclaredRoles()) {
            rollen.add(new ModulManifest.Rolle(r.normalizedName(), r.authorityName(), r.description(), r.art().name(),
                    r.ohneUmgehung()));
        }
        List<ModulManifest.Menue> menue = new ArrayList<>();
        for (MenuItemImpl m : new MenuAnnotationScanner(null, null, null).mitUmgebung(new StandardEnvironment())
                .findAnnotatedClasses(paket())) {
            menue.add(new ModulManifest.Menue(m.getEffectiveMenuId(), m.getParent(), m.getOrder(), m.getTitle(),
                    m.getLink() == null ? "" : m.getLink(), m.getRoles() == null ? List.of() : m.getRoles()));
        }
        menue.sort(Comparator.comparing(ModulManifest.Menue::menuId));
        TreeSet<String> properties = new TreeSet<>();
        for (Class<?> c : klassen(new AnnotationTypeFilter(ConfigurationProperties.class))) {
            ConfigurationProperties cp = AnnotatedElementUtils.findMergedAnnotation(c, ConfigurationProperties.class);
            String praefix = cp == null ? "" : (cp.prefix().isEmpty() ? cp.value() : cp.prefix());
            if (!praefix.isEmpty()) {
                properties.add(praefix);
            }
        }
        return new ModulManifest(modul(), null, rollen, menue, List.copyOf(properties));
    }

    @Test
    void manifestStimmtMitDemCode() throws IOException {
        pruefeOderSchreibe(erzeugen().alsYaml(), ziel(), Boolean.getBoolean(SCHREIBEN));
    }

    static void pruefeOderSchreibe(String soll, Path ziel, boolean schreiben) throws IOException {
        if (schreiben) {
            Files.createDirectories(ziel.getParent());
            Files.writeString(ziel, soll);
            return;
        }
        assertTrue(Files.exists(ziel), () -> "Modul-Manifest fehlt: " + ziel.toAbsolutePath()
                + " - mit 'mvn test -D" + SCHREIBEN + "=true' erzeugen und einchecken");
        assertEquals(soll, Files.readString(ziel), () -> "Modul-Manifest " + ziel + " passt nicht mehr zum Code "
                + "(Rolle, Menü oder Properties geändert) - mit 'mvn test -D" + SCHREIBEN + "=true' neu schreiben");
    }

    private List<Class<?>> klassen(org.springframework.core.type.filter.TypeFilter filter) {
        ClassPathScanningCandidateComponentProvider scan = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition d) {
                return d.getMetadata().isIndependent();
            }
        };
        scan.addIncludeFilter(filter);
        List<Class<?>> ret = new ArrayList<>();
        for (BeanDefinition d : scan.findCandidateComponents(paket())) {
            Class<?> c = ClassUtils.resolveClassName(d.getBeanClassName(), getClass().getClassLoader());
            var quelle = c.getProtectionDomain().getCodeSource();
            if (!nurHauptcode() || quelle == null || !quelle.getLocation().getPath().contains("test-classes")) {
                ret.add(c);
            }
        }
        ret.sort(Comparator.comparing(Class::getName));
        return ret;
    }
}
