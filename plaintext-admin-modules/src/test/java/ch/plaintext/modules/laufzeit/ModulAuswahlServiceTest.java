/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.modules.laufzeit;

import ch.plaintext.lader.ModulLader;
import ch.plaintext.secrets.SecretResolver;
import ch.plaintext.secrets.SecretWriter;
import ch.plaintext.PlaintextSecurity;
import ch.plaintext.audit.DestructiveActionAuditService;
import ch.plaintext.lader.ModulAuswahl;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationContext;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Karte 1608/1612: nur ROOT sieht und schaltet; Auswahl nur aus dem Angebot; Neustart per Exit-Code. */
class ModulAuswahlServiceTest {

    @TempDir
    Path module;
    @TempDir
    Path konfig;

    private final PlaintextSecurity security = mock(PlaintextSecurity.class);
    private final DestructiveActionAuditService audit = mock(DestructiveActionAuditService.class);
    private final AtomicInteger beendet = new AtomicInteger();
    private ModulAuswahlService service;
    private HttpServer server;
    private final List<String> index = new ArrayList<>();
    private KeyPair schluessel;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", ex -> {
            byte[] b = String.join("\n", index).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream o = ex.getResponseBody()) {
                o.write(b);
            }
            ex.close();
        });
        server.start();
        schluessel = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        index.add("plaintext-modul-index 1");
        zeile("plaintext-z-a", "1.0.0", schluessel);
        zeile("plaintext-z-a", "1.10.0", schluessel);
        zeile("plaintext-z-b", "2.0.0", KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        Files.writeString(konfig.resolve(ModulAuswahl.QUELLEN), "schluessel ed25519 "
                + Base64.getEncoder().encodeToString(schluessel.getPublic().getEncoded()) + "\nregistry test http://127.0.0.1:"
                + server.getAddress().getPort() + "/index.txt\n");
        when(security.getUser()).thenReturn("root@plaintext.ch");
        service = new ModulAuswahlService(security, audit, mock(ApplicationContext.class), module.toString(), konfig.toString(), "") {
            @Override
            void beenden() {
                beendet.incrementAndGet();
            }
        };
    }

    @AfterEach
    void stoppen() {
        server.stop(0);
    }

    private void zeile(String name, String version, KeyPair k) throws Exception {
        ModulAuswahl.Zeile z = new ModulAuswahl.Zeile("modul", name, version, "1.770.0", "a".repeat(64), null, List.of(), List.of(),
                "x.jar", "x", "test", null);
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(k.getPrivate());
        s.update(z.nachricht().getBytes(StandardCharsets.UTF_8));
        index.add(z.alsIndexZeile().replace("signatur=x", "signatur=" + Base64.getEncoder().encodeToString(s.sign())));
    }

    private void als(String rolle) {
        when(security.ifGranted("ROLE_ROOT")).thenReturn("ROOT".equals(rolle));
        when(security.ifGranted("ROLE_ADMIN")).thenReturn("ADMIN".equals(rolle));
    }

    @Test
    void rootSiehtAlleModuleNeuesteZuerstMitSignaturstatus() throws Exception {
        als("ROOT");
        ModulAuswahlService.Ansicht a = service.ansicht();

        assertThat(a.darfSchalten()).isTrue();
        assertThat(a.module()).extracting(ModulAuswahlService.Modul::name).containsExactly("plaintext-z-a", "plaintext-z-b");
        assertThat(a.module().get(0).versionen()).extracting(ModulAuswahlService.Version::version)
                .containsExactly("1.10.0", "1.0.0");
        assertThat(a.module().get(0).versionen().get(0).signiert()).isTrue();
        assertThat(a.module().get(1).versionen().get(0).signiert()).isFalse();
        assertThat(a.registryFehler()).isEmpty();
    }

    @Test
    void adminSiehtNichts() {
        als("ADMIN");
        assertThatThrownBy(service::ansicht).isInstanceOf(SecurityException.class).hasMessageContaining("nur ROOT");
    }

    @Test
    void userSiehtNichts() {
        als("USER");
        assertThatThrownBy(service::ansicht).isInstanceOf(SecurityException.class);
    }

    @Test
    void rootSpeichertAuswahlMitVorherUndAudit() throws Exception {
        als("ROOT");
        service.speichern(List.of(new ModulAuswahl.Wahl("plaintext-z-a", "latest")));
        service.speichern(List.of(new ModulAuswahl.Wahl("plaintext-z-a", "1.0.0")));

        assertThat(ModulAuswahl.auswahlParsen(Files.readString(module.resolve(ModulAuswahl.AUSWAHL)), "t"))
                .containsExactly(new ModulAuswahl.Wahl("plaintext-z-a", "1.0.0"));
        assertThat(Files.readString(module.resolve(ModulAuswahl.AUSWAHL_VORHER))).contains("version: latest");
        verify(audit).logDestructiveAction(eq("UI"), eq("MODUL_AUSWAHL_SPEICHERN"), eq("auswahl.yaml"),
                eq("plaintext-z-a:1.0.0"), anyString());
        assertThat(service.ansicht().module().get(0).gewaehlt()).isEqualTo("1.0.0");
    }

    @Test
    void gemeinsameAuswahlLiegtImEigenenVerzeichnisNichtImSlot(@TempDir Path gemeinsam) throws Exception {
        // Karte 1618: blue/green - die Seite schreibt die Auswahl beider Slots, nicht die Kopie im Slot
        ModulAuswahlService g = new ModulAuswahlService(security, audit, mock(ApplicationContext.class), module.toString(),
                konfig.toString(), gemeinsam.toString());
        als("ROOT");
        g.speichern(List.of(new ModulAuswahl.Wahl("plaintext-z-a", "latest")));
        g.speichern(List.of());

        assertThat(ModulAuswahl.auswahlParsen(Files.readString(gemeinsam.resolve(ModulAuswahl.AUSWAHL)), "t")).isEmpty();
        assertThat(Files.readString(gemeinsam.resolve(ModulAuswahl.AUSWAHL_VORHER))).contains("plaintext-z-a");
        assertThat(module.resolve(ModulAuswahl.AUSWAHL)).doesNotExist();
        assertThat(g.ansicht().module()).allSatisfy(m -> assertThat(m.gewaehlt()).isNull());
    }

    @Test
    void adminDarfNichtSpeichernUndNichtNeuStarten() {
        als("ADMIN");
        assertThatThrownBy(() -> service.speichern(List.of(new ModulAuswahl.Wahl("plaintext-z-a", "latest"))))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(service::neustart).isInstanceOf(SecurityException.class);

        assertThat(module.resolve(ModulAuswahl.AUSWAHL)).doesNotExist();
        assertThat(beendet).hasValue(0);
        verify(audit, never()).logDestructiveAction(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void nurAngeboteneModuleUndVersionen() {
        als("ROOT");
        assertThatThrownBy(() -> service.speichern(List.of(new ModulAuswahl.Wahl("plaintext-z-gibtsnicht", "latest"))))
                .hasMessageContaining("steht in keiner erreichbaren Registry");
        assertThatThrownBy(() -> service.speichern(List.of(new ModulAuswahl.Wahl("plaintext-z-a", "9.9.9"))))
                .hasMessageContaining("9.9.9 steht in keiner Registry");
        assertThat(module.resolve(ModulAuswahl.AUSWAHL)).doesNotExist();
    }

    @Test
    void rootStartetNeuMitAudit() {
        als("ROOT");
        service.neustart();

        assertThat(beendet).hasValue(1);
        verify(audit).logDestructiveAction(eq("UI"), eq("MODUL_AUSWAHL_NEUSTART"), eq("auswahl.yaml"),
                eq("Neustart mit Exit 75"), anyString());
    }

    @Test
    void beanVerweigertOhneRootOhneDenDienstZuRufen() throws Exception {
        ModulAuswahlService s = mock(ModulAuswahlService.class);
        when(s.darfSchalten()).thenReturn(false);
        ModulAuswahlBackingBean bean = new ModulAuswahlBackingBean();
        bean.setService(s);

        bean.speichern();
        bean.neustart();

        verify(s, never()).speichern(org.mockito.ArgumentMatchers.any());
        verify(s, never()).neustart();
        assertThat(bean.verweigert()).isTrue();
    }

    @Test
    void beanBautZeilenAusDerAnsicht() throws Exception {
        als("ROOT");
        service.speichern(List.of(new ModulAuswahl.Wahl("plaintext-z-a", "1.0.0")));
        ModulAuswahlBackingBean bean = new ModulAuswahlBackingBean();
        bean.setService(service);

        bean.load();

        assertThat(bean.getZeilen()).extracting(ModulAuswahlBackingBean.Zeile::getName).containsExactly("plaintext-z-a", "plaintext-z-b");
        assertThat(bean.getZeilen().get(0).isAktiv()).isTrue();
        assertThat(bean.getZeilen().get(0).getOptionen()).extracting(jakarta.faces.model.SelectItem::getValue)
                .containsExactly("latest", "1.10.0", "1.0.0");
        assertThat(bean.getZeilen().get(1).getHinweis()).contains("Signatur ungültig");
    }

    /** Karte 2377 (ROOT-9): registry in der Wahl muss das Modul dort anbieten; die Ansicht nennt je Registry den Status. */
    @Test
    void registryInDerWahlUndRegistryStatus() throws Exception {
        als("ROOT");
        assertThatThrownBy(() -> service.speichern(List.of(new ModulAuswahl.Wahl("plaintext-z-a", "1.0.0", "fremd"))))
                .hasMessageContaining("nicht in Registry 'fremd'");
        assertThatThrownBy(() -> service.speichern(List.of(new ModulAuswahl.Wahl("plaintext-z-a", "9.9.9", "test"))))
                .hasMessageContaining("9.9.9 steht nicht in Registry 'test'");
        service.speichern(List.of(new ModulAuswahl.Wahl("plaintext-z-a", "1.0.0", "test")));
        assertThat(Files.readString(module.resolve(ModulAuswahl.AUSWAHL))).contains("registry: test");

        ModulAuswahlService.Ansicht a = service.ansicht();
        assertThat(a.module().get(0).gewaehlt()).isEqualTo("1.0.0");
        assertThat(a.module().get(0).gewaehltRegistry()).isEqualTo("test");
        assertThat(a.registries()).extracting(ModulAuswahlService.Registry::name).containsExactly("test");
        assertThat(a.registries().get(0).status()).containsPattern("^\\d+ Modul(e)?, \\d+ Version(en)?, ").contains("ungueltiger Signatur");
    }

    /** Karte 2456: «oeffentlich 4 Modul(e)» war ein Modul mit vier Versionen; gezaehlt werden Module und Versionen. */
    @Test
    void registryStatusZaehltModuleUndVersionen() {
        java.util.function.BiFunction<String, String, ModulAuswahl.Zeile> z = (name, version) -> new ModulAuswahl.Zeile("modul", name,
                version, "3.58.0", "abc", null, List.of(), List.of(), name + "-" + version + ".jar", "sig", "oeffentlich", null);
        ModulAuswahl.Angebot angebot = new ModulAuswahl.Angebot(List.of(z.apply("plaintext-modul-files", "1.0.1"),
                z.apply("plaintext-modul-files", "1.1.0"), z.apply("plaintext-modul-files", "1.2.0"), z.apply("plaintext-modul-files", "1.3.0")),
                java.util.Map.of("kunde", "antwortet 503"));
        assertThat(ModulAuswahlService.registries(List.of("oeffentlich", "kunde", "leer"), angebot, List.of()))
                .extracting(ModulAuswahlService.Registry::status)
                .containsExactly("1 Modul, 4 Versionen, 4 mit ungueltiger Signatur", "Fehler: antwortet 503", "leer");
    }

    // ------------------------------------------------------------------ Karte 2408: Quellen aus der Oberflaeche

    private static final byte[] JAR_C = "jar-c".getBytes(StandardCharsets.UTF_8);

    /** Zweite Registry unter /ui/, nur mit HTTP Basic k-1:pw (wie /nosec/k/ auf modules). */
    private String uiRegistry() throws Exception {
        String sha = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(JAR_C));
        ModulAuswahl.Zeile z = new ModulAuswahl.Zeile("modul", "plaintext-z-c", "1.0.0", "1.770.0", sha, null, List.of(), List.of(),
                "jars/plaintext-z-c-1.0.0.jar", "x", "kunde", null);
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(schluessel.getPrivate());
        s.update(z.nachricht().getBytes(StandardCharsets.UTF_8));
        String idx = "plaintext-modul-index 1\n" + z.alsIndexZeile().replace("signatur=x", "signatur=" + Base64.getEncoder().encodeToString(s.sign()));
        String kopf = "Basic " + Base64.getEncoder().encodeToString("k-1:pw".getBytes(StandardCharsets.UTF_8));
        server.createContext("/ui/", ex -> {
            byte[] b = !kopf.equals(ex.getRequestHeaders().getFirst("Authorization")) ? null
                    : ex.getRequestURI().getPath().equals("/ui/index.txt") ? idx.getBytes(StandardCharsets.UTF_8)
                    : ex.getRequestURI().getPath().equals("/ui/jars/plaintext-z-c-1.0.0.jar") ? JAR_C : null;
            ex.sendResponseHeaders(b == null ? 401 : 200, b == null ? -1 : b.length);
            if (b != null) {
                try (OutputStream o = ex.getResponseBody()) {
                    o.write(b);
                }
            }
            ex.close();
        });
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/ui/index.txt";
    }

    private ModulAuswahlService mitHuelle() {
        return new ModulAuswahlService(security, audit, mock(ApplicationContext.class), module.toString(), konfig.toString(), "") {
            @Override
            ModulLader.Huelle huelle() {
                return new ModulLader.Huelle("1.780.0", "1.767.0", null);
            }
        };
    }

    private UiQuellen.Eingabe eingabe(String index) {
        return new UiQuellen.Eingabe(UiQuellen.FORMAT, "kunde", null, index,
                "ed25519 " + Base64.getEncoder().encodeToString(schluessel.getPublic().getEncoded()), "k-1", "pw");
    }

    @Test
    void uiQuelleUebernehmenAnsichtUndVorladenBeimSpeichern() throws Exception {
        als("ROOT");
        String index = uiRegistry();
        java.util.Map<String, String> store = new java.util.HashMap<>();
        ModulAuswahlService s = mitHuelle();
        s.setSecretSchreiber(new SecretWriter() {
            public void store(String name, String value, String note) { store.put(name, value); }
            public void remove(String name) { store.remove(name); }
        });
        s.setSecretLeser(name -> java.util.Optional.ofNullable(store.get(name)));

        s.uebernehmen(eingabe(index));
        assertThat(store).containsEntry("modul-quelle.kunde", "k-1:pw");
        String datei = Files.readString(module.resolve(ModulAuswahl.QUELLEN_UI));
        assertThat(datei).contains("registry kunde " + index).doesNotContain("pw");
        org.mockito.ArgumentCaptor<String> texte = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(audit).logDestructiveAction(eq("UI"), eq("MODUL_QUELLE_ANLEGEN"), any(), texte.capture(), texte.capture());
        assertThat(texte.getAllValues()).noneMatch(t -> t.contains("k-1:pw"));

        assertThat(s.gesamt().toString()).doesNotContain("k-1:pw");
        assertThat(s.ansicht().module()).extracting(ModulAuswahlService.Modul::name).contains("plaintext-z-c");
        s.speichern(List.of(new ModulAuswahl.Wahl("plaintext-z-c", "1.0.0")));
        assertThat(module.resolve("vorbereitet/kunde/jars/plaintext-z-c-1.0.0.jar")).hasBinaryContent(JAR_C);
        assertThat(Files.readString(module.resolve("vorbereitet/kunde/index.txt"))).contains("modul plaintext-z-c 1.0.0 ");

        s.quelleEntfernen("kunde");
        assertThat(store).isEmpty();
        assertThat(module.resolve("vorbereitet/kunde")).doesNotExist();
        assertThat(s.uiQuellen()).isEmpty();
    }

    @Test
    void uiQuelleOhneSecretStoreOderFalschemZugangOderFremdemSchluessel() throws Exception {
        als("ROOT");
        String index = uiRegistry();
        ModulAuswahlService s = mitHuelle();
        assertThatThrownBy(() -> s.uebernehmen(eingabe(index))).hasMessageContaining("kein Secret-Store");
        s.setSecretSchreiber(mock(SecretWriter.class));
        UiQuellen.Eingabe falsch = new UiQuellen.Eingabe(UiQuellen.FORMAT, "kunde", null, index, eingabe(index).schluessel(), "k-1", "nein");
        assertThatThrownBy(() -> s.uebernehmen(falsch)).hasMessageContaining("401").hasMessageNotContaining("nein");
        String fremd = "ed25519 " + Base64.getEncoder().encodeToString(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
        assertThatThrownBy(() -> s.uebernehmen(new UiQuellen.Eingabe(UiQuellen.FORMAT, "kunde", null, index, fremd, "k-1", "pw")))
                .hasMessageContaining("gueltiger Signatur");
        assertThatThrownBy(() -> s.uebernehmen(new UiQuellen.Eingabe(UiQuellen.FORMAT, "test", null, index, fremd, "k-1", "pw")))
                .hasMessageContaining("schon in");
        assertThat(module.resolve(ModulAuswahl.QUELLEN_UI)).doesNotExist();
        als("ADMIN");
        assertThatThrownBy(() -> s.quelleAnlegen("{}")).isInstanceOf(SecurityException.class);
    }

    @Test
    void uiQuelleOhneZugangImStoreStehtAlsFehler() throws Exception {
        als("ROOT");
        Files.writeString(module.resolve(ModulAuswahl.QUELLEN_UI), UiQuellen.text(List.of(new UiQuellen.Quelle("kunde",
                uiRegistry(), Base64.getEncoder().encodeToString(schluessel.getPublic().getEncoded()))), "t"));
        ModulAuswahlService s = mitHuelle();
        s.setSecretLeser(name -> java.util.Optional.empty());
        assertThat(s.ansicht().registryFehler()).containsKey("kunde");
        assertThat(s.ansicht().registryFehler().get("kunde")).contains("Zugang fehlt");
    }
}
