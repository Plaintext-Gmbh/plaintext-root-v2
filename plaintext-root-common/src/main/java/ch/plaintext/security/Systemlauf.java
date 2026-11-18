/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.security;

import ch.plaintext.arch.StabileApi;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Karte 1863: die EINE Maschinen-Identitaet fuer Laeufe ohne Benutzer (Cron, Bus, {@code @Scheduled}, {@code @Async},
 * fremde Threads wie MQTT-Callbacks).
 *
 * <p>Principal {@value #SYSTEM_USER}, Authorities {@value #ROLE_SYSTEM} und {@code PROPERTY_MANDAT_<mandat>}
 * (Entscheid Daniel: {@code ROLE_SYSTEM} ist die einzige Maschinenrolle). Vorher stand dieser Kontext fuenfmal als Kopie
 * im Code (SuperCron, PlaintextBusDispatcher, guild, schuetu, iot). Semantik wie dort: immer ein frischer Kontext, nie
 * in-place; danach kommt der Vorgaenger zurueck, oder der Kontext wird geleert. {@code MODE_THREADLOCAL} bleibt
 * (InheritableThreadLocal vererbte in Pool-Threads falsch).</p>
 */
@StabileApi("Maschinen-Identitaet fuer guild, schuetu und iot (Karte 1863 Etappe 3, Karte 2352)")
public final class Systemlauf {

    /** Principal eines Maschinenlaufs; auch der Auditing-Name ohne Benutzer. */
    public static final String SYSTEM_USER = "SYSTEM";
    /** Die einzige Maschinenrolle; kein Anmeldeweg vergibt sie (siehe {@link #istSystemrolle(String)}). */
    public static final String ROLE_SYSTEM = "ROLE_SYSTEM";
    /** Praefix der Mandats-Authority. */
    public static final String MANDAT_PREFIX = "PROPERTY_MANDAT_";

    private Systemlauf() {
    }

    /** Fuehrt {@code arbeit} als {@value #SYSTEM_USER} im Mandat aus; der vorherige Kontext kommt danach zurueck. */
    public static void als(String mandat, Runnable arbeit) {
        alsBenutzer(mandat, null, arbeit);
    }

    /**
     * Wie {@link #als(String, Runnable)}, aber mit einem Benutzer als Principal (PERSOENLICH-Crons, Bus-Events eines
     * Benutzers); {@code null}/leer heisst {@value #SYSTEM_USER}. Die Rolle bleibt {@value #ROLE_SYSTEM}.
     */
    public static void alsBenutzer(String mandat, String benutzer, Runnable arbeit) {
        SecurityContext vorher = SecurityContextHolder.getContext();
        boolean vorherAngemeldet = vorher.getAuthentication() != null;
        SecurityContextHolder.setContext(kontext(mandat, benutzer));
        try {
            arbeit.run();
        } finally {
            if (vorherAngemeldet) {
                SecurityContextHolder.setContext(vorher);
            } else {
                SecurityContextHolder.clearContext();
            }
        }
    }

    /**
     * Fachrolle zusaetzlich zu {@value #ROLE_SYSTEM}, nur INNERHALB eines Systemlaufs (guild-Regel: eine Fachrolle nie
     * ohne Maschinenlauf). {@code rolle} ohne {@code ROLE_}-Praefix, z.B. {@code "MITGLIEDER"}.
     *
     * @throws IllegalStateException ausserhalb eines Systemlaufs
     */
    public static void mitFachrolle(String rolle, Runnable arbeit) {
        if (!istSystemlauf()) {
            throw new IllegalStateException("Fachrolle " + rolle + " nur innerhalb eines Systemlaufs (Karte 1863)");
        }
        SecurityContext vorher = SecurityContextHolder.getContext();
        Authentication jetzt = vorher.getAuthentication();
        Set<GrantedAuthority> rechte = new LinkedHashSet<>(jetzt.getAuthorities());
        rechte.add(new SimpleGrantedAuthority("ROLE_" + rolle.toUpperCase(Locale.ROOT)));
        SecurityContext neu = SecurityContextHolder.createEmptyContext();
        neu.setAuthentication(new UsernamePasswordAuthenticationToken(jetzt.getPrincipal(), null, rechte));
        SecurityContextHolder.setContext(neu);
        try {
            arbeit.run();
        } finally {
            SecurityContextHolder.setContext(vorher);
        }
    }

    /** {@code true}, wenn der aktuelle Thread in einem Systemlauf ist. */
    public static boolean istSystemlauf() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(g -> ROLE_SYSTEM.equals(g.getAuthority()));
    }

    /**
     * Fuer fremde Threads (Executor, Scheduler, Paho): beim Ausfuehren ein Systemlauf im Mandat — aber nur, wenn der
     * Thread keinen Kontext hat. Ein weitergereichter Benutzerkontext bleibt unangetastet.
     */
    public static Runnable umhuellen(String mandat, Runnable arbeit) {
        return () -> {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                als(mandat, arbeit);
            } else {
                arbeit.run();
            }
        };
    }

    /**
     * Karte 1863: eine Rolle aus der Benutzerverwaltung, die nie zu einer Authority werden darf. Sonst haette ein
     * Benutzer mit DB-Rolle {@code system} die Maschinenrolle und kaeme ab Etappe 2 durch {@code hasRole('SYSTEM')}.
     */
    public static boolean istSystemrolle(String rolle) {
        if (rolle == null) {
            return false;
        }
        String r = rolle.trim().toUpperCase(Locale.ROOT);
        return r.equals("SYSTEM") || r.equals(ROLE_SYSTEM);
    }

    /** Frischer Kontext: Principal (Benutzer oder {@value #SYSTEM_USER}), {@value #ROLE_SYSTEM}, Mandat. */
    static SecurityContext kontext(String mandat, String benutzer) {
        Set<GrantedAuthority> rechte = new LinkedHashSet<>();
        rechte.add(new SimpleGrantedAuthority(ROLE_SYSTEM));
        if (mandat != null && !mandat.isBlank()) {
            rechte.add(new SimpleGrantedAuthority(MANDAT_PREFIX + mandat.toLowerCase(Locale.ROOT)));
        }
        String principal = benutzer != null && !benutzer.isBlank() ? benutzer : SYSTEM_USER;
        SecurityContext k = SecurityContextHolder.createEmptyContext();
        k.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, rechte));
        return k;
    }
}
