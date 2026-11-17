/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot.integration;

import ch.plaintext.boot.plugins.jsf.userprofile.UserPreference;
import ch.plaintext.boot.plugins.jsf.userprofile.UserPrefsSimpleStorage;
import ch.plaintext.boot.plugins.objstore.SimpleStorableEntityRepository;
import ch.plaintext.boot.plugins.security.model.MyUserEntity;
import ch.plaintext.boot.plugins.security.persistence.MyUserRepository;
import ch.plaintext.boot.table.TableState;
import ch.plaintext.testsupport.EmbeddedPg;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Karte 1336, Pruefung 6: der Tabellenstand ({@code TableState}) gehoert der Person — im
 * Browser, mit zwei Benutzern und zwei Sitzungen fuer denselben Benutzer.
 *
 * <p><b>Warum die Benutzerverwaltung.</b> Die einzige Seite in root, die ihren Tabellenstand
 * ueber {@code TableSettings} und die mitgelieferte Ablage ({@code UserPreferenceTableStateStore})
 * haelt, ist {@code useradmin.xhtml} — seit Karte 1904 ueber das Tag {@code pt:tableSettings}
 * (vorher ein eigenes Spaltenmenue). Die Ablage ist fuer beide dieselbe: der ganze Stand als JSON in {@code UserPreference}, je
 * Benutzer und Mandant. Was hier fuer die Spaltenwahl gilt, gilt deshalb fuer jedes Feld des
 * Stands; dass Paginator und Seitengroesse (Karte 1336) den Weg durch das JSON ueberstehen, zeigt
 * {@code TabellenStandZweiBenutzerTest} in plaintext-root-common.</p>
 *
 * <p><b>Ablauf.</b> A blendet die Spalte "ID" aus. B meldet sich an und sieht sie. A meldet sich in
 * einem neuen Browser-Kontext (neue Session, neues Session-Bean) wieder an und sieht sie nicht —
 * dazwischen der Blick in die Datenbank, dass der Stand wirklich bei A liegt und nicht nur in der
 * Session.</p>
 *
 * <p>Aufgebaut wie {@link RootPagesPlaywrightIT}: eingebettetes PostgreSQL, Chromium headless;
 * ohne installiertes Chromium ueberspringt sich die Klasse.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.docker.compose.enabled=false",
                "plaintext.cron.default-enabled=false",
                "plaintext.cron.default-startup=false"
        }
)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TabellenstandZweiBenutzerPlaywrightIT {

    private static final String ANNA = "pw-tabelle-anna";
    private static final String BRUNO = "pw-tabelle-bruno";
    private static final String CARLA = "pw-tabelle-carla";   // Runde 7, eigener Stand
    private static final String DORA = "pw-tabelle-dora";     // Karte 2160, Breite fix
    private static final String PASSWORT = "Playwright-2026!";

    /** Ablageschluessel: Mandant + "/" + Seitenschluessel von MyUserBackingBean. */
    private static final String SCHLUESSEL = "default/useradmin";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        EmbeddedPg.registrieren(registry, "tabellenstandzweibenutzerplaywrightit");
    }

    @LocalServerPort int port;
    @Autowired MyUserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserPrefsSimpleStorage userPrefs;
    @Autowired SimpleStorableEntityRepository ablage;

    private Playwright playwright;
    private Browser browser;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @BeforeAll
    void launchBrowserUndBenutzer() {
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        } catch (RuntimeException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Chromium not installed: " + e.getMessage());
        }
        // Rollen OHNE "ROLE_"-Praefix: MyUserDetailsService setzt es selbst davor.
        benutzerAnlegen(ANNA);
        benutzerAnlegen(BRUNO);
        benutzerAnlegen(CARLA);
        benutzerAnlegen(DORA);
    }

    private void benutzerAnlegen(String name) {
        if (userRepository.findByUsername(name) != null) {
            return;
        }
        MyUserEntity user = new MyUserEntity();
        user.setUsername(name);
        user.setPassword(passwordEncoder.encode(PASSWORT));
        user.addRole("ADMIN");
        user.addRole("USER");
        user.setMandat("default");
        userRepository.save(user);
    }

    @AfterAll
    void closeBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test
    @DisplayName("A blendet eine Spalte aus: B sieht sie weiter, A nach neuer Anmeldung nicht")
    void spaltenwahlGehoertDerPerson() {
        // ── A: Vorgabe sehen (Positivkontrolle), dann "ID" ausblenden ──────
        try (BrowserContext a = neuerKontext()) {
            Page seite = benutzerverwaltung(a, ANNA);
            int vpBreite = Integer.parseInt(System.getProperty("diag.vp.breite", "1600"));
            int vpHoehe = Integer.parseInt(System.getProperty("diag.vp.hoehe", "900"));
            seite.setViewportSize(vpBreite, vpHoehe);   // genug Platz, damit das Panel nicht ragt (claude2-Befund)
            assertTrue(idKopf(seite).isVisible(), "Vorgabe: die Spalte ID muss zu Beginn sichtbar sein");

            // Karte 1904: pt:tableSettings - Oeffner, Reiter "Spalten", Schalter der Zeile "ID".
            // Seit Karte 1853 Runde 5 (root 3.19.0) ist der Oeffner wieder ein sichtbarer Knopf ueber der
            // Tabelle (<id>-oeffnen); den Regler-Klon im Spaltenkopf (.pt-ts-regler) gibt es nicht mehr.
            seite.click("#fm\\:anz-oeffnen");
            seite.locator(".pt-ts-panel").waitFor();
            seite.evaluate("""
                    () => {
                      window.__hidelog = [];
                      const k = Object.keys(PrimeFaces.widgets).find(k => /PanelWv$/.test(k));
                      const w = PF(k);
                      const h = w.hide.bind(w);
                      w.hide = function () { window.__hidelog.push(new Error().stack); return h(); };
                    }
                    """);
            seite.locator(".pt-ts-panel .ui-tabs-nav li").filter(new Locator.FilterOptions().setHasText("Spalten")).click();
            Locator zeileId = spaltenZeile(seite, "id");
            zeileId.locator(".pt-ts-schalter-sichtbar").click();

            TableState gespeichert = warteAufGespeichertenStand(seite, ANNA);
            assertEquals(Boolean.FALSE, gespeichert.getColumnVisible().get("id"),
                    "In der Datenbank steht fuer A nicht 'id=false': " + gespeichert.getColumnVisible());
            idKopf(seite).waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));

            // Reihenfolge per Griff (Runde 6, Punkt 9 — die Pfeil-Buttons sind entfernt):
            // "Vorname" eine Stelle nach oben, vor "Benutzername".
            // Regressionsschutz (Daniel Punkt 3, Karte 1853): nach jedem Toggle bleibt das
            // Panel offen und der Reiter «Spalten» aktiv — sonst verdeckt der Griff-Klick
            // unten einen echten Fehler statt ihn zu zeigen.
            Object hideLog = seite.evaluate("""
                    () => (window.__hidelog || []).join(' ||| ')
                    """);
            try { java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/diag-hide.txt"),
                    "hideLog=" + hideLog); } catch (java.io.IOException ignored) { }
            // PF 16 schliesst ein OverlayPanel bei jedem window-scroll/resize
            // (handleViewportChange, im hideLog als Ausloeser belegt) — fuer .pt-ts-panel
            // neutralisiert (Prototyp-Patch in table-settings.js). Playwright scrollt fuer
            // einen Klick die Seite; das Panel muss trotzdem offen bleiben.
            assertTrue(seite.locator(".pt-ts-panel").isVisible(),
                    "Panel muss nach dem Sichtbarkeits-Toggle offen bleiben (hideLog=" + hideLog + ")");
            assertTrue(seite.locator(".pt-ts-panel .ui-tabs-header[aria-selected='true'], "
                            + ".pt-ts-panel li.ui-state-active").innerText().contains("Spalten"),
                    "Reiter «Spalten» muss nach dem Toggle aktiv bleiben");
            griffZiehen(seite, "vorname", "username");

            // Diagnose: DOM-Reihenfolge nach Drag + Server-Stand
            Object dom = seite.evaluate("""
                    () => Array.from(document.querySelectorAll('.pt-ts-reihenfolge li.ui-orderlist-item'))
                        .map(li => li.getAttribute('data-item-value')).join(',')
                    """);
            try { java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/diag-reihenfolge.txt"), "dom=" + dom + "\n"); } catch (java.io.IOException ignored) { }

TableState mitReihenfolge = warteAufStand(seite, ANNA, st -> st.getColumnOrder() != null
                    && st.getColumnOrder().indexOf("vorname") >= 0
                    && st.getColumnOrder().indexOf("vorname") < st.getColumnOrder().indexOf("username"));
            assertEquals(Boolean.FALSE, mitReihenfolge.getColumnVisible().get("id"),
                    "Die Reihenfolge hat die Abwahl von ID ueberschrieben: " + mitReihenfolge);
        }
        // Der Befund vom 29.09.2026: das Session-Bean entstand auf der Anmeldeseite und schrieb
        // danach fuer jeden Benutzer in den gemeinsamen Datensatz "anonymousUser".
        assertFalse(eintraegeInDerAblage().contains("anonymousUser"),
                "Es gibt einen gemeinsamen Datensatz anonymousUser: " + eintraegeInDerAblage());

        // ── B: eigene Sitzung, eigener Stand — die Spalte ist da ───────────
        try (BrowserContext b = neuerKontext()) {
            Page seite = benutzerverwaltung(b, BRUNO);
            assertTrue(idKopf(seite).isVisible(), "B sieht die Einstellung von A: Spalte ID fehlt");
        }
        UserPreference beiB = userPrefs.findByUniqueId(BRUNO);
        assertNotNull(beiB, "B hat nach dem Seitenaufruf gar keine Einstellungen");
        TableState standB = beiB.getTabellenStaende().get(SCHLUESSEL);
        assertTrue(standB == null || !Boolean.FALSE.equals(standB.getColumnVisible().get("id")),
                "Der Stand von B enthaelt die Abwahl von A: " + standB);

        // ── A: neue Sitzung (Abmelden/Neuladen) — die Abwahl ist geblieben ─
        try (BrowserContext aNeu = neuerKontext()) {
            Page seite = benutzerverwaltung(aNeu, ANNA);
            // Gegenkontrolle, dass die Tabelle ueberhaupt da ist: die Spalte Benutzername.
            assertTrue(seite.locator("#fm\\:tbl thead th .ui-column-title:text-is('Benutzername')").isVisible(),
                    "Tabelle der Benutzerverwaltung nicht gerendert");
            assertFalse(idKopf(seite).isVisible(), "A hat nach neuer Anmeldung die Spalte ID wieder");
            java.util.List<String> koepfe = seite.locator("#fm\\:tbl thead th .ui-column-title").allInnerTexts();
            assertTrue(koepfe.indexOf("Vorname") >= 0 && koepfe.indexOf("Vorname") < koepfe.indexOf("Benutzername"),
                    "A hat nach neuer Anmeldung die alte Reihenfolge: " + koepfe);
        }
    }

    @Test
    @DisplayName("Karte 2333: Adjust ohne Griffe, Reorder nur Verschieben, Resize nur Verbreitern; Modus nie gespeichert; Kopf-Zug speichert sofort")
    void griffeJeModus() {
        try (BrowserContext c = neuerKontext()) {
            Page seite = benutzerverwaltung(c, CARLA);
            seite.click("#fm\\:anz-oeffnen");
            seite.locator(".pt-ts-panel").waitFor();

            // Start = «Anpassen» (outline, Daniel 15.11.): KEIN Griff im Kopf (Daniel 17.11.).
            seite.locator("#fm\\:anz-modus.ui-button-outlined").waitFor();
            assertEquals(0, seite.locator("#fm\\:tbl thead th.ui-draggable-column").count(), "Adjust: kein Verschiebe-Griff");
            assertEquals(0, seite.locator("#fm\\:tbl thead th .ui-column-resizer").count(), "Adjust: kein Verbreitern-Griff");
            assertEquals(0, seite.locator(".pt-ts-breite-fix-wert").count(), "Adjust: kein Feld Breite fix");

            // Klick -> Verschieben (1): gefuellt, Verschiebe-Viereck links am Titel, KEIN Resize-Griff.
            seite.click("#fm\\:anz-modus");
            seite.locator("#fm\\:tbl thead th.ui-draggable-column").first().waitFor();
            assertEquals(0, seite.locator("#fm\\:tbl thead th .ui-column-resizer").count(), "Reorder: kein Verbreitern-Griff");
            assertFalse(seite.locator("#fm\\:anz-modus").getAttribute("class").contains("ui-button-outlined"),
                    "Verschieben muss gefuellt sein");
            Object griff = seite.evaluate("""
                    () => getComputedStyle(document.querySelector('#fm\\\\:tbl thead th.ui-draggable-column .ui-column-title'), '::before').content
                    """);
            assertTrue(griff != null && !"none".equals(griff) && !"normal".equals(griff), "Verschieben: kein Griff im Kopf, content=" + griff);

            // Im Kopf ziehen: die Reihenfolge ist sofort gespeichert (Daniel 17.11.: jede Aenderung).
            // Panel zu, sonst liegt es ueber den Koepfen und der Zug trifft das Panel.
            seite.click("#fm\\:anz-oeffnen");
            seite.locator(".pt-ts-panel").waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
            kopfZiehen(seite, "Vorname", "Benutzername");
            warteAufStand(seite, CARLA, st -> st.getColumnOrder() != null
                    && st.getColumnOrder().indexOf("vorname") < st.getColumnOrder().indexOf("username"));

            // Klick -> Vergroessern (2): nur der Resize-Griff, dazu das Feld Breite fix (leer).
            seite.click("#fm\\:anz-oeffnen");
            seite.locator(".pt-ts-panel").waitFor();
            seite.click("#fm\\:anz-modus");
            seite.locator("#fm\\:tbl thead th .ui-column-resizer").first().waitFor();
            assertEquals(0, seite.locator("#fm\\:tbl thead th.ui-draggable-column").count(), "Resize: kein Verschiebe-Griff");
            assertEquals("\"\u2194\"", seite.evaluate("""
                    () => getComputedStyle(document.querySelector('#fm\\\\:tbl thead th .ui-column-resizer'), '::after').content
                    """), "Resize-Viereck ohne Zeichen");
            Locator feld = seite.locator(".pt-ts-breite-fix-wert");
            feld.waitFor();
            assertEquals("", feld.inputValue(), "Breite fix startet leer");
            assertEquals(0, seite.locator("[id$='anz-festhalten']").count(), "Fester Stand muss weg sein");

            // Daniel 15.11.: der Modus wird nicht gespeichert — nach einer NEUEN ANMELDUNG
            // (neue Session, neues Bean) startet er wieder neutral mit «Anpassen» (outline).
        }
        try (BrowserContext c2 = neuerKontext()) {
            Page frisch = benutzerverwaltung(c2, CARLA);
            frisch.locator("#fm\\:tbl").waitFor();
            assertEquals(0, frisch.locator("#fm\\:tbl thead th .ui-column-resizer").count(), "neue Anmeldung: Adjust, kein Griff");
            frisch.click("#fm\\:anz-oeffnen");
            frisch.locator("#fm\\:anz-modus.ui-button-outlined").waitFor();
        }
    }

    /**
     * Karte 2160/2333 (Daniel 17.11.): «Breite fix» ist ein Sitzungswert im Modus Resize. Leer:
     * der Doppelklick auf den Griff uebernimmt die Breite der Spalte ins Feld und das Panel geht
     * am Oeffner auf. Gefuellt: der Doppelklick setzt die jeweilige Spalte auf den Wert, gespeichert
     * wie ein Zug. Der Modus-Wechsel blendet das Feld aus und vergisst den Wert.
     */
    @Test
    @DisplayName("Karte 2333: Breite fix - leer uebernimmt per Doppelklick, gefuellt stempelt per Doppelklick, Wert nur in der Sitzung")
    void breiteFixPerDoppelklick() {
        try (BrowserContext d = neuerKontext()) {
            Page seite = benutzerverwaltung(d, DORA);
            seite.click("#fm\\:anz-oeffnen");
            seite.locator(".pt-ts-panel").waitFor();
            seite.click("#fm\\:anz-modus");   // Reorder
            seite.locator("#fm\\:tbl thead th.ui-draggable-column").first().waitFor();
            seite.click("#fm\\:anz-modus");   // Resize
            seite.locator("#fm\\:tbl thead th .ui-column-resizer").first().waitFor();
            seite.click("#fm\\:anz-oeffnen");   // Panel zu, die Koepfe muessen frei liegen
            seite.locator(".pt-ts-panel").waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));

            // Spalte «Vorname» ziehen: gespeichert (jede Aenderung sofort).
            resizerZiehen(seite, "Vorname", 40);
            TableState gezogen = warteAufStand(seite, DORA,
                    st -> st.getColumnWidths().get("vorname") != null);
            int gezogeneBreite = Math.round(Float.parseFloat(
                    gezogen.getColumnWidths().get("vorname").replace("px", "").trim()));

            // Leeres Feld + Doppelklick auf den Griff von «Vorname»: Panel auf, Ansicht-Reiter,
            // Feld = Breite der Spalte. Gespeichert wird dabei nichts.
            seite.locator("#fm\\:tbl thead th:has(.ui-column-title:text-is('Vorname')) .ui-column-resizer").dblclick();
            seite.locator(".pt-ts-panel").waitFor();
            Locator feld = seite.locator(".pt-ts-breite-fix-wert");
            feld.waitFor();
            // Der Wert kommt mit dem Ajax-Update, Reiter und Fokus danach (pfAjaxComplete).
            seite.waitForFunction("() => { const f = document.querySelector('.pt-ts-breite-fix-wert'); return !!f && f.value.trim() !== ''; }");
            assertTrue(seite.locator(".pt-ts-panel .ui-tabs-header[aria-selected='true'], "
                            + ".pt-ts-panel li.ui-state-active").innerText().contains("Ansicht"),
                    "Der 1. Reiter (Ansicht) muss nach dem Doppelklick aktiv sein");
            assertEquals(gezogeneBreite, Integer.parseInt(feld.inputValue()),
                    "Das Feld muss die Breite der doppelt geklickten Spalte tragen");
            assertEquals(0, seite.locator(".pt-ts-breite-fix-spalte").count(), "kein Spaltenname am Feld");

            // Wert eintragen (Tab loest change aus), Panel zu, Doppelklick auf «Nachname»: die
            // Spalte bekommt 222px, Stand und Profil.
            feld.fill("222");
            feld.press("Tab");
            seite.waitForTimeout(400);   // der change-Ajax traegt den Wert ein
            seite.click("#fm\\:anz-oeffnen");
            seite.locator(".pt-ts-panel").waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
            seite.locator("#fm\\:tbl thead th:has(.ui-column-title:text-is('Nachname')) .ui-column-resizer").dblclick();
            TableState fix = warteAufStand(seite, DORA, st -> "222px".equals(st.getColumnWidths().get("nachname")));
            assertEquals("222px", fix.getProfiles().get(fix.getActiveProfile()).getColumnWidths().get("nachname"),
                    "Der Wert muss im aktiven Profil landen");
            assertEquals(222, thBreite(seite, "Nachname"), "Tabellenkopf zeigt die Breite nicht direkt");
            assertEquals(gezogeneBreite, thBreite(seite, "Vorname"), "Vorname bleibt bei der gezogenen Breite");

            // Breite wirkt nach Reload; Modus-Wechsel (Resize -> Adjust) blendet das Feld aus.
            seite.reload();
            seite.locator("#fm\\:tbl").waitFor();
            assertEquals(222, thBreite(seite, "Nachname"), "Breite wirkt nach dem Reload nicht");
            seite.click("#fm\\:anz-oeffnen");
            seite.locator(".pt-ts-panel").waitFor();
            seite.locator(".pt-ts-breite-fix-wert").waitFor();
            seite.click("#fm\\:anz-modus");   // -> Adjust
            seite.locator("#fm\\:anz-modus.ui-button-outlined").waitFor();
            assertEquals(0, seite.locator(".pt-ts-breite-fix-wert").count(), "ausserhalb Resize kein Feld");
        }
    }

    @Test
    @DisplayName("Karte 2333: Panel bleibt nach «Neues Profil» offen; Spaltenliste ohne Knopfspalte, gezogene Hoehe gespeichert")
    void neuesProfilUndSpaltenliste() {
        try (BrowserContext e = neuerKontext()) {
            Page seite = benutzerverwaltung(e, CARLA);
            seite.click("#fm\\:anz-oeffnen");
            seite.locator(".pt-ts-panel").waitFor();

            seite.locator(".pt-ts-profilzeile .pi-plus").first().click();
            Locator name = seite.locator("[id$='anz-newProfileName']");
            name.waitFor();
            name.fill("Kurz");
            seite.locator("[id$='anz-profileDialog'] .pi-check").first().click();
            warteAufStand(seite, CARLA, st -> st.getProfiles().containsKey("Kurz"));
            seite.waitForTimeout(500);   // oncomplete: Dialog zu, Panel wieder am Oeffner
            assertTrue(seite.locator(".pt-ts-panel").isVisible(), "Panel muss nach Neues Profil offen sein");

            // Spalten-Reiter: die Knopfspalte des OrderList ist weg, die Liste nimmt die ganze Breite.
            seite.locator(".pt-ts-panel .ui-tabs-nav li").nth(1).click();
            Locator liste = seite.locator(".pt-ts-reihenfolge .ui-orderlist-list");
            liste.waitFor();
            assertFalse(seite.locator(".pt-ts-reihenfolge .ui-orderlist-controls").isVisible(), "Knopfspalte sichtbar");
            double listeBreite = liste.boundingBox().width;
            double panelBreite = seite.locator(".pt-ts-reihenfolge").boundingBox().width;
            assertTrue(listeBreite > panelBreite * 0.95, "Liste nutzt nicht die ganze Breite: " + listeBreite + " von " + panelBreite);

            // Gezogene Hoehe (resize-Griff der Liste): wie der Browser sie setzt, dann mouseup.
            seite.evaluate("""
                    () => { const ul = document.querySelector('.pt-ts-reihenfolge .ui-orderlist-list');
                            ul.style.height = '333px'; document.dispatchEvent(new MouseEvent('mouseup', {bubbles: true})); }
                    """);
            warteAufStand(seite, CARLA, st -> Integer.valueOf(333).equals(st.getSpaltenListeHoehe()));
        }
        try (BrowserContext e2 = neuerKontext()) {
            Page frisch = benutzerverwaltung(e2, CARLA);
            frisch.click("#fm\\:anz-oeffnen");
            frisch.locator(".pt-ts-panel").waitFor();
            frisch.locator(".pt-ts-panel .ui-tabs-nav li").nth(1).click();
            Locator liste = frisch.locator(".pt-ts-reihenfolge .ui-orderlist-list");
            liste.waitFor();
            assertEquals(333, Math.round(liste.boundingBox().height), "gezogene Hoehe nach neuer Anmeldung");
        }
    }

    /** Die am Tabellenkopf gerenderte Breite der Spalte, in px gerundet (fehlt sie, -1). */
    private static int thBreite(Page seite, String spalte) {
        Locator th = seite.locator("#fm\\:tbl thead th:has(.ui-column-title:text-is('" + spalte + "'))");
        String width = th.getAttribute("width");
        if (width == null || width.isBlank()) {
            width = th.getAttribute("style");   // PF 16 schreibt die Breite teils ins style-Attribut
        }
        if (width == null) {
            return -1;
        }
        var m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*px").matcher(width);
        return m.find() ? Math.round(Float.parseFloat(m.group(1))) : -1;
    }

    /** Zieht den Resize-Griff der Spalte um {@code px} nach rechts und loest damit den colResize-Ajax aus. */
    private static void resizerZiehen(Page seite, String spalte, int px) {
        Locator resizer = seite.locator("#fm\\:tbl thead th:has(.ui-column-title:text-is('" + spalte + "')) .ui-column-resizer");
        resizer.waitFor();
        var box = resizer.boundingBox();
        assertNotNull(box, "Resize-Griff der Spalte " + spalte + " sichtbar");
        double x = box.x + box.width / 2;
        double y = box.y + box.height / 2;
        seite.mouse().move(x, y);
        seite.mouse().down();
        seite.mouse().move(x + px, y, new com.microsoft.playwright.Mouse.MoveOptions().setSteps(10));
        seite.waitForTimeout(300);
        seite.mouse().up();
    }

    /**
     * Zieht im Tabellenkopf (Modus Reorder, jQuery-UI-draggable der PF-DataTable) die Spalte
     * {@code von} auf die linke Haelfte von {@code vor} und wartet den colReorder-Ajax ab.
     */
    private static void kopfZiehen(Page seite, String von, String vor) {
        var start = seite.locator("#fm\\:tbl thead th:has(.ui-column-title:text-is('" + von + "'))").boundingBox();
        var ziel = seite.locator("#fm\\:tbl thead th:has(.ui-column-title:text-is('" + vor + "'))").boundingBox();
        assertNotNull(start, "Kopf " + von + " sichtbar");
        assertNotNull(ziel, "Kopf " + vor + " sichtbar");
        double sx = start.x + start.width / 2;
        double sy = start.y + 12;
        double zx = ziel.x + 6;
        double zy = ziel.y + 12;
        seite.mouse().move(sx, sy);
        seite.mouse().down();
        for (int i = 1; i <= 15; i++) {
            seite.mouse().move(sx + (zx - sx) * i / 15.0, sy + (zy - sy) * i / 15.0);
            seite.waitForTimeout(40);
        }
        seite.waitForTimeout(300);
        seite.mouse().up();
        seite.waitForTimeout(2500);   // colReorder-Ajax abwarten
    }

    /**
     * Zieht im Reiter «Spalten» die Zeile {@code von} am Griff vor die Zeile {@code vor}
     * (Runde 6, Punkt 9 - die Pfeil-Buttons sind entfernt) und wartet den reorder-Ajax ab.
     */
    private static void griffZiehen(Page seite, String von, String vor) {
        Locator griff = spaltenZeile(seite, von).locator(".pt-ts-griff");
        griff.waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
        var griffBox = griff.boundingBox();
        assertNotNull(griffBox, "Griff der Zeile " + von + " sichtbar");
        var zielBox = spaltenZeile(seite, vor).boundingBox();
        assertNotNull(zielBox, "Zeile " + vor + " sichtbar");
        double startX = griffBox.x + griffBox.width / 2;
        double startY = griffBox.y + griffBox.height / 2;
        double zielX = zielBox.x + 20;
        double zielY = zielBox.y + 4;   // Oberkante der Zielzeile + 4 px (claude2-Messung)
        seite.mouse().move(startX, startY);
        seite.mouse().down();
        for (int i = 1; i <= 12; i++) {
            seite.mouse().move(startX + (zielX - startX) * i / 12.0,
                startY + (zielY - startY) * i / 12.0);
            seite.waitForTimeout(50);
        }
        seite.waitForTimeout(300);
        seite.mouse().up();
        seite.waitForTimeout(2500);   // reorder-Ajax abwarten
    }

    /**
     * Karte 1904: ein Fenster, in dem Tabelle und Panel ohne Scrollen Platz haben. Playwright
     * scrollt vor jedem Klick das Ziel ins Bild, und PrimeFaces schliesst ein offenes
     * OverlayPanel bei jedem Scrollen (verbundenes Overlay) - sonst geht das Spalten-Panel je
     * nach Lage vor dem Klick auf den Schalter wieder zu.
     */
    private BrowserContext neuerKontext() {
        return browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1400));
    }

    private Page benutzerverwaltung(BrowserContext kontext, String benutzer) {
        Page seite = kontext.newPage();
        seite.navigate(url("/login.html"));
        seite.fill("#username", benutzer);
        seite.fill("#password", PASSWORT);
        seite.locator("#password").press("Enter");
        seite.waitForURL(u -> !u.contains("login"), new Page.WaitForURLOptions().setTimeout(90_000));
        seite.navigate(url("/useradmin.html"));
        seite.waitForLoadState();
        assertTrue(seite.url().contains("useradmin"), "umgeleitet nach " + seite.url());
        seite.locator("#fm\\:tbl").waitFor();
        return seite;
    }

    /**
     * Zeile einer Spalte in der Liste des Reiters "Spalten", ueber ihren Schluessel. Die Liste
     * liegt im TabView (NamingContainer) - ihre Client-Id traegt eine generierte j_idt-Stufe.
     */
    private static Locator spaltenZeile(Page seite, String schluessel) {
        return seite.locator(".pt-ts-reihenfolge li.ui-orderlist-item[data-item-value='" + schluessel + "']");
    }

    /** Kopfzelle der Spalte "ID" — nur im Tabellenkopf, reflow wiederholt die Titel in jeder Zeile. */
    private static Locator idKopf(Page seite) {
        return seite.locator("#fm\\:tbl thead th:has(.ui-column-title:text-is('ID'))");
    }

    /**
     * Das Speichern laeuft per Ajax; bis zu 15 s warten, bis der Stand in der Datenbank steht.
     *
     * <p>Die Pause zwischen zwei Blicken in die Datenbank macht Playwright ({@code waitForTimeout}),
     * nicht {@code Thread.sleep} (Karte 1410, Sonar java:S2925): Die Seite verarbeitet waehrenddessen
     * ihre Ereignisse weiter, und es gibt keine {@code InterruptedException} zu behandeln.</p>
     */
    private TableState warteAufGespeichertenStand(Page seite, String benutzer) {
        return warteAufStand(seite, benutzer, stand -> Boolean.FALSE.equals(stand.getColumnVisible().get("id")));
    }

    private TableState warteAufStand(Page seite, String benutzer, java.util.function.Predicate<TableState> erfuellt) {
        long bis = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < bis) {
            UserPreference prefs = userPrefs.findByUniqueId(benutzer);
            TableState stand = prefs == null ? null : prefs.getTabellenStaende().get(SCHLUESSEL);
            if (stand != null && erfuellt.test(stand)) {
                return stand;
            }
            seite.waitForTimeout(200);
        }
        UserPreference prefs = userPrefs.findByUniqueId(benutzer);
        String vorhanden = eintraegeInDerAblage().toString();
        throw new AssertionError("Kein passender Stand fuer " + benutzer + " unter " + SCHLUESSEL
                + " — gefunden: " + (prefs == null ? "keine Einstellungen" : prefs.getTabellenStaende())
                + " — Eintraege in der Ablage: " + vorhanden);
    }

    /** Die Benutzer, fuer die Einstellungen in der Ablage liegen. */
    private java.util.List<String> eintraegeInDerAblage() {
        return ablage.findAll().stream()
                .map(e -> e.getMyObject() == null ? "(leer)" : e.getMyObject().getUniqueId())
                .toList();
    }
}
