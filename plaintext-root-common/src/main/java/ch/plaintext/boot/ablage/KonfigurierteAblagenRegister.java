/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.ablage;

import ch.plaintext.ablagen.DateiAblage;
import ch.plaintext.ablagen.DateiAblageArt;
import ch.plaintext.ablagen.DateiAblagenRegister;
import ch.plaintext.modules.ModulApiUmsetzung;
import ch.plaintext.settings.ISettingsService;
import ch.plaintext.settings.SettingsKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * <p>Karte 2425 PR C (Entscheid Daniel (284) 1): Die Konfiguration steht auch in settings, Mandant {@code global},
 * unter {@code speicher.<name>.<einstellung>}. Beim Start schreibt {@link #vorgabenNachSettings()} die yml-Ablagen
 * als fehlende Vorgaben dorthin; danach gilt je Einstellung der Wert aus settings (bei jedem Öffnen gelesen, kein
 * Neustart). ROOT kann dort auch eine Ablage neu anlegen. Geheimnisse nie: dieselbe Klartext-Prüfung wie für die yml.</p>
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
    private final ObjectProvider<ISettingsService> settings;
    static final String PRAEFIX = "speicher";
    /** Verweise auf den Secret-Store enden so, z. B. {@code passwort-secret}. */
    static final String SECRET_VERWEIS = "-secret";

    /** Ob {@code verweis} ein Secret dieses Speichers nennt: {@code speicher.<name>.<feld>} (Entscheid chef (299)). */
    static boolean eigenesSecret(String name, String verweis) {
        String v = verweis == null ? "" : verweis.strip();
        String vorsatz = PRAEFIX + "." + name + ".";
        return v.startsWith(vorsatz) && v.length() > vorsatz.length() && !v.substring(vorsatz.length()).contains("..");
    }
    /** Namensgleichheit nur einmal je Name melden (Hinweis claude2). */
    private final Set<String> gewarnt = ConcurrentHashMap.newKeySet();

    public KonfigurierteAblagenRegister(Map<String, Map<String, String>> eintraege, ObjectProvider<DateiAblageArt> arten,
                                        ObjectProvider<DateiAblagenRegister> weitere, ObjectProvider<ISettingsService> settings) {
        this.eintraege = new LinkedHashMap<>();
        eintraege.forEach((n, k) -> this.eintraege.put(n.strip(), Map.copyOf(k)));
        this.arten = arten;
        this.weitere = weitere;
        this.settings = settings;
    }

    /** yml, darüber je Einstellung settings ({@code speicher.<name>.<einstellung>}, Mandant global). */
    Map<String, Map<String, String>> aktuell() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        eintraege.forEach((n, k) -> out.put(n, new LinkedHashMap<>(k)));
        ISettingsService s = settings.getIfAvailable();
        if (s == null) {
            return out;
        }
        try {
            for (String key : s.getChildKeys(PRAEFIX, SettingsKeys.MANDAT_GLOBAL)) {
                String[] t = key.split("\\.", 3);
                if (t.length == 3 && PRAEFIX.equals(t[0]) && !t[1].isBlank() && !t[2].isBlank()) {
                    String wert = s.getString(key, SettingsKeys.MANDAT_GLOBAL);
                    out.computeIfAbsent(t[1], x -> new LinkedHashMap<>()).put(t[2], wert == null ? "" : wert);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Speicher: settings nicht lesbar, es gilt die application.yml: {}", e.getMessage());
        }
        return out;
    }

    /** Beim Start: die yml-Ablagen als fehlende Vorgaben nach settings; nie überschreiben, nie ein Geheimnis. */
    @EventListener(ApplicationReadyEvent.class)
    public int vorgabenNachSettings() {
        ISettingsService s = settings.getIfAvailable();
        int neu = 0;
        if (s == null) {
            return neu;
        }
        for (Map.Entry<String, Map<String, String>> e : eintraege.entrySet()) {
            for (Map.Entry<String, String> k : e.getValue().entrySet()) {
                if (KLARTEXT.contains(k.getKey().strip().toLowerCase(Locale.ROOT))) {
                    continue;   // ablage() weist den Speicher ohnehin ab und meldet es
                }
                String key = PRAEFIX + "." + e.getKey() + "." + k.getKey();
                try {
                    if (!s.exists(key, SettingsKeys.MANDAT_GLOBAL)) {
                        s.setSetting(key, SettingsKeys.MANDAT_GLOBAL, k.getValue(), "STRING",
                                "Karte 2425: Speicher «" + e.getKey() + "», Vorgabe aus der application.yml");
                        neu++;
                    }
                } catch (RuntimeException ex) {
                    log.warn("Speicher: Vorgabe {} nicht nach settings geschrieben: {}", key, ex.getMessage());
                }
            }
        }
        if (neu > 0) {
            log.info("Speicher: {} Vorgabe(n) aus der application.yml nach settings geschrieben", neu);
        }
        return neu;
    }

    @Override
    public List<String> namen() {
        TreeSet<String> n = new TreeSet<>(aktuell().keySet());
        andere().forEach(r -> n.addAll(r.namen()));
        return List.copyOf(n);
    }

    @Override
    public Optional<DateiAblage> ablage(String name) {
        String n = name == null ? "" : name.strip();
        Map<String, String> konfig = aktuell().get(n);
        if (konfig == null) {
            return andere().map(r -> r.ablage(n)).flatMap(Optional::stream).findFirst();
        }
        if (!gewarnt.contains(n) && andere().anyMatch(r -> r.namen().contains(n))) {
            gewarnt.add(n);
            log.warn("Speicher «{}» steht in der application.yml oder settings und unter Sidecars; es gelten yml und settings", n);
        }
        String art = konfig.getOrDefault(ART, "").strip();
        Optional<String> klartext = konfig.keySet().stream()
                .filter(k -> KLARTEXT.contains(k.strip().toLowerCase(Locale.ROOT))).findFirst();
        if (klartext.isPresent()) {   // Hinweis claude2: abweisen statt still uebergehen; der Wert erscheint nie im Log
            log.warn("Speicher «{}»: Einstellung «{}» ist nicht erlaubt, Geheimnisse nur über passwort-secret (Secret-Store)",
                    n, klartext.get());
            return Optional.empty();
        }
        // Fund claude2, Entscheid chef (299): ein Speicher darf nur Secrets im eigenen Namensraum speicher.<name>.*
        // referenzieren. Sonst koennte ROOT ueber settings (url + Verweis) ein beliebiges vorhandenes Secret als
        // Anmeldung an einen fremden Host schicken.
        Optional<String> fremd = konfig.entrySet().stream()
                .filter(e -> e.getKey().strip().toLowerCase(Locale.ROOT).endsWith(SECRET_VERWEIS))
                .filter(e -> !eigenesSecret(n, e.getValue()))
                .map(Map.Entry::getKey).findFirst();
        if (fremd.isPresent()) {
            log.warn("Speicher «{}»: {} darf nur auf ein Secret «{}.{}.…» zeigen; der Speicher bleibt aus", n, fremd.get(),
                    PRAEFIX, n);
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

    /**
     * Die übrigen Register (z. B. Sidecars), ohne dieses selbst. Karte 2544: auch ohne seinen Proxy und ohne ein
     * zweites konfiguriertes Register (instanceof trifft beide); sonst rufen sie sich bis zum StackOverflowError im Kreis.
     */
    private Stream<DateiAblagenRegister> andere() {
        return weitere.orderedStream().filter(r -> !(r instanceof KonfigurierteAblagenRegister));
    }
}
