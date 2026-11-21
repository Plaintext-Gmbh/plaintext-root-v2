/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.ablage;

import ch.plaintext.ablagen.DateiAblage;
import ch.plaintext.ablagen.DateiAblageArt;
import ch.plaintext.ablagen.DateiAblagenRegister;
import ch.plaintext.modules.ModulApiUmsetzung;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Karte 2425: das Register, das Module bekommen ({@code @Primary}). Es führt die Ablagen aus der
 * {@code application.yml} ({@code plaintext.speicher.<name>.art} und Einstellungen der Art) mit denen weiterer
 * Register zusammen (Root &gt; Sidecars, DB). Bei gleichem Namen gilt die yml (WARN im Log).
 *
 * <p>Eine unbekannte Art oder eine Ablage, die sich nicht öffnen lässt, ergibt ein leeres {@link Optional} mit einer
 * Log-Zeile; die Anwendung startet trotzdem, wie bei einer kaputten Ablage unter Sidecars.</p>
 */
@Slf4j
@ModulApiUmsetzung(beschreibung = "Hands modules the stores declared in the application.yml (plaintext.speicher) and those of "
        + "other registers such as Root > Sidecars.",
        seiteneffekte = ModulApiUmsetzung.Seiteneffekte.KEINE,
        hinweise = {"On equal names the store from the application.yml wins",
                "An unknown kind or a store that cannot be opened yields an empty Optional and a log line"},
        beispiele = {"ablage(\"files\")"})
public class KonfigurierteAblagenRegister implements DateiAblagenRegister {

    static final String ART = "art";
    /** Entscheid Daniel (284): Geheimnisse nie im Klartext, nur als Verweis ({@code passwort-secret}) in den Secret-Store. */
    static final Set<String> KLARTEXT = Set.of("passwort", "password", "token", "secret", "app-passwort");

    private final Map<String, Map<String, String>> eintraege;
    private final ObjectProvider<DateiAblageArt> arten;
    private final ObjectProvider<DateiAblagenRegister> weitere;
    /** Namensgleichheit nur einmal je Name melden (Hinweis claude2). */
    private final Set<String> gewarnt = ConcurrentHashMap.newKeySet();

    public KonfigurierteAblagenRegister(Map<String, Map<String, String>> eintraege, ObjectProvider<DateiAblageArt> arten,
                                        ObjectProvider<DateiAblagenRegister> weitere) {
        this.eintraege = new LinkedHashMap<>();
        eintraege.forEach((n, k) -> this.eintraege.put(n.strip(), Map.copyOf(k)));
        this.arten = arten;
        this.weitere = weitere;
    }

    @Override
    public List<String> namen() {
        TreeSet<String> n = new TreeSet<>(eintraege.keySet());
        andere().forEach(r -> n.addAll(r.namen()));
        return List.copyOf(n);
    }

    @Override
    public Optional<DateiAblage> ablage(String name) {
        String n = name == null ? "" : name.strip();
        Map<String, String> konfig = eintraege.get(n);
        if (konfig == null) {
            return andere().map(r -> r.ablage(n)).flatMap(Optional::stream).findFirst();
        }
        if (!gewarnt.contains(n) && andere().anyMatch(r -> r.namen().contains(n))) {
            gewarnt.add(n);
            log.warn("Speicher «{}» steht in der application.yml und unter Sidecars; es gilt die application.yml", n);
        }
        String art = konfig.getOrDefault(ART, "").strip();
        Optional<String> klartext = konfig.keySet().stream().filter(KLARTEXT::contains).findFirst();
        if (klartext.isPresent()) {   // Hinweis claude2: abweisen statt still uebergehen; der Wert erscheint nie im Log
            log.warn("Speicher «{}»: Einstellung «{}» ist nicht erlaubt, Geheimnisse nur über passwort-secret (Secret-Store)",
                    n, klartext.get());
            return Optional.empty();
        }
        Optional<DateiAblageArt> a = arten.orderedStream().filter(x -> x.art().equals(art)).findFirst();
        if (a.isEmpty()) {
            log.warn("Speicher «{}»: Art «{}» unbekannt (vorhanden: {})", n, art,
                    arten.orderedStream().map(DateiAblageArt::art).toList());
            return Optional.empty();
        }
        Map<String, String> ohneArt = new LinkedHashMap<>(konfig);
        ohneArt.remove(ART);
        try {
            return Optional.of(a.get().oeffne(n, Map.copyOf(ohneArt)));
        } catch (IOException | RuntimeException e) {
            log.warn("Speicher «{}» (Art {}) nicht nutzbar: {}", n, art, e.getMessage());
            return Optional.empty();
        }
    }

    /** Die übrigen Register (z. B. Sidecars), ohne dieses selbst. */
    private Stream<DateiAblagenRegister> andere() {
        return weitere.orderedStream().filter(r -> r != this);
    }
}
