# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
"""
Unit-Tests fuer v2/purge.py (Karte 1563). Aufruf: python3 -I v2/test_purge.py

Der HTTP-Transport wird durch einen Fake ersetzt, der beide Repos in Speicher nachbildet und jeden
DELETE/PUT aufzeichnet. So laesst sich belegen, dass ein v1-Kandidat den Lauf abbricht, BEVOR
irgendein DELETE abgesetzt wurde — der Negativtest, den die Karte verlangt.
"""
import io
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import purge  # noqa: E402


def modul_pom(parent=None, archtests=None, extra=""):
    """Veröffentlichtes Modul-POM wie plaintext-modul-running-parent 3.0.0 (Karte 1635)."""
    p = (f"<parent><groupId>ch.plaintext.v2</groupId><artifactId>plaintext-root-parent</artifactId>"
         f"<version>{parent}</version></parent>") if parent else ""
    d = ("<dependencies><dependency><groupId>ch.plaintext.v2</groupId><artifactId>plaintext-root-archtests</artifactId>"
         "<version>${plaintext-root-archtests.version}</version></dependency></dependencies>") if archtests else ""
    props = f"<properties><plaintext-root-archtests.version>{archtests}</plaintext-root-archtests.version></properties>" if archtests else ""
    return (f'<?xml version="1.0"?><project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>'
            f"{p}<groupId>ch.plaintext</groupId><artifactId>m</artifactId>{props}{d}{extra}</project>")


class FakeNetz:
    """Reposilite + GitHub im Speicher. repo: {artefakt: [versionen]} je Gruppe.
    modul_poms: {artefakt: {version: pom-text}} — veröffentlichte Nicht-root-Artefakte unter ch/plaintext/,
    aus denen purge.py die Pins liest. Standard: ein Modul, das die jüngste Testversion 2.790.1 pinnt."""

    def __init__(self, reposilite_v2=None, reposilite_v1=None, github=None, scopes="read:packages, delete:packages",
                 delete_status=204, modul_poms=None, pom_status=200):
        self.modul_poms = {"plaintext-modul-test": {"3.0.0": modul_pom("2.790.1")}} if modul_poms is None else modul_poms
        self.pom_status = pom_status
        v1 = dict(reposilite_v1 or {})
        v1.update({a: list(vs) for a, vs in self.modul_poms.items()})
        self.reposilite = {"v2": reposilite_v2 or {}, "v1": v1}
        self.github = github or {}          # paketname -> [versionen]
        self.scopes = scopes
        self.delete_status = delete_status
        self.aufrufe = []
        self.puts = {}
        self.metadata = ""

    def __call__(self, methode, url, headers, body):
        self.aufrufe.append((methode, url))
        if url.startswith("https://api.github.com"):
            return self._github(methode, url)
        return self._reposilite(methode, url, body)

    # --- Reposilite ---
    def _reposilite(self, methode, url, body):
        pfad = url.split("https://repo.test/", 1)[1]
        if methode == "GET" and pfad.endswith(".pom"):
            art, ver = pfad[len("releases/ch/plaintext/"):].split("/")[:2]
            text = self.modul_poms.get(art, {}).get(ver)
            if self.pom_status != 200:
                return purge.Antwort(self.pom_status)
            return purge.Antwort(200, text.encode()) if text is not None else purge.Antwort(404)
        if methode == "GET" and pfad.startswith("api/maven/details/"):
            rest = pfad[len("api/maven/details/"):]
            if rest == "releases/ch/plaintext":
                namen = list(self.reposilite["v1"].keys()) + (["v2"] if self.reposilite["v2"] else [])
                return self._dir(namen)
            if rest == "releases/ch/plaintext/v2":
                return self._dir(self.reposilite["v2"].keys())
            for gruppe, praefix in (("v2", "releases/ch/plaintext/v2/"), ("v1", "releases/ch/plaintext/")):
                if rest.startswith(praefix):
                    art = rest[len(praefix):]
                    if art in self.reposilite[gruppe]:
                        return self._dir(self.reposilite[gruppe][art])
            return purge.Antwort(404)
        if methode == "DELETE":
            for gruppe, praefix in (("v2", "releases/ch/plaintext/v2/"), ("v1", "releases/ch/plaintext/")):
                if pfad.startswith(praefix):
                    art, ver = pfad[len(praefix):].split("/")
                    self.reposilite[gruppe][art].remove(ver)
                    return purge.Antwort(204)
            return purge.Antwort(404)
        if methode == "PUT":
            self.puts[pfad] = body.decode()
            if pfad.endswith("maven-metadata.xml"):
                self.metadata = body.decode()
            return purge.Antwort(201)
        return purge.Antwort(500)

    @staticmethod
    def _dir(namen):
        return purge.Antwort(200, json.dumps({"type": "DIRECTORY", "files": [{"name": n, "type": "DIRECTORY"} for n in namen]}).encode())

    # --- GitHub ---
    def _github(self, methode, url):
        if url.startswith("https://api.github.com/user"):
            return purge.Antwort(200, b"{}", {"X-OAuth-Scopes": self.scopes} if self.scopes is not None else {})
        if methode == "GET" and "/packages?package_type=maven" in url:
            return purge.Antwort(200, json.dumps([{"name": n} for n in self.github]).encode())
        if methode == "GET" and "/versions" in url:
            name = url.split("/packages/maven/")[1].split("/versions")[0]
            name = purge.urllib.parse.unquote(name)
            vs = self.github.get(name, [])
            return purge.Antwort(200, json.dumps([{"name": v, "id": f"{name}:{v}"} for v in vs]).encode())
        if methode == "DELETE":
            name, ver = url.split("/packages/maven/")[1].split("/versions/")
            name = purge.urllib.parse.unquote(name)
            if self.delete_status != 204:
                return purge.Antwort(self.delete_status)
            self.github[name].remove(ver.split(":")[1])
            return purge.Antwort(204)
        return purge.Antwort(500)


def args(*extra):
    return purge.argumente(list(extra))


def lauf(netz, *extra, nur=None):
    a = args(*extra)
    r = purge.Reposilite("https://repo.test", "ci", "t", netz) if nur in (None, "reposilite") else None
    g = purge.GitHubPackages("Plaintext-Gmbh", "tok", netz) if nur in (None, "github") else None
    out = io.StringIO()
    rc = purge.lauf(a, r, g, out, pin_quelle=purge.Reposilite("https://repo.test", "ci", "t", netz))
    return rc, out.getvalue()


class AufteilenTest(unittest.TestCase):
    def test_juengste_n_bleiben_numerisch_sortiert(self):
        bleibt, weg = purge.aufteilen(["2.789.1", "2.789.10", "2.789.2", "2.790.1", "2.788.9"], 2)
        self.assertEqual(bleibt, ["2.790.1", "2.789.10"])
        self.assertEqual(weg, ["2.789.2", "2.789.1", "2.788.9"])

    def test_snapshots_und_text_werden_nie_geloescht(self):
        bleibt, weg = purge.aufteilen(["1.0.0-SNAPSHOT", "x", "2.1.0"], 1)
        self.assertEqual(bleibt, ["2.1.0"])
        self.assertEqual(weg, [])

    def test_behalten_null_ist_verboten(self):
        with self.assertRaises(ValueError):
            purge.aufteilen(["1.0.0"], 0)


class TrockenlaufTest(unittest.TestCase):
    def setUp(self):
        self.netz = FakeNetz(
            reposilite_v2={"plaintext-root-parent": ["2.789.1", "2.789.2", "2.789.3", "2.790.1"],
                           "plaintext-root-common": ["2.789.1", "2.789.2"]},
            reposilite_v1={"plaintext-root-parent": ["1.787.0", "1.788.0", "1.789.0"], "plaintext-root-common": ["1.789.0"]},
            github={"ch.plaintext.v2.plaintext-root-parent": ["2.789.1", "2.789.2", "2.789.3", "2.790.1"],
                    "ch.plaintext.plaintext-root-parent": ["1.788.0", "1.789.0"],
                    "ch.plaintext.v2x.fremd": ["1.0.0"]})

    def test_trockenlauf_loescht_nichts_und_listet(self):
        rc, out = lauf(self.netz, "--behalten", "2")
        self.assertEqual(rc, 0, out)
        self.assertNotIn("DELETE", [m for m, _ in self.netz.aufrufe])
        self.assertIn("behalten (2): 2.790.1, 2.789.3", out)
        self.assertIn("loeschen (2): 2.789.2, 2.789.1", out)
        self.assertIn("v1-Zaehlung vorher: 5 Versionen", out)     # Reposilite v1: 3 + 1 + Modul
        self.assertIn("v1-Zaehlung vorher: 3 Versionen", out)     # GitHub v1: 2 + 1 (v2x.fremd zaehlt als v1)
        self.assertIn("Trockenlauf: 2 Versionen wuerden geloescht", out)
        self.assertIn("v1 unveraendert.", out)
        self.assertNotIn("ch.plaintext.v2x.fremd\n", out)           # kein v2-Kandidat

    def test_scharf_loescht_nur_v2_und_schreibt_metadata(self):
        rc, out = lauf(self.netz, "--behalten", "2", "--scharf")
        self.assertEqual(rc, 0, out)
        self.assertEqual(self.netz.reposilite["v2"]["plaintext-root-parent"], ["2.789.3", "2.790.1"])
        self.assertEqual(self.netz.reposilite["v2"]["plaintext-root-common"], ["2.789.1", "2.789.2"])
        self.assertEqual(self.netz.reposilite["v1"]["plaintext-root-parent"], ["1.787.0", "1.788.0", "1.789.0"])
        self.assertEqual(self.netz.github["ch.plaintext.v2.plaintext-root-parent"], ["2.789.3", "2.790.1"])
        self.assertEqual(self.netz.github["ch.plaintext.plaintext-root-parent"], ["1.788.0", "1.789.0"])
        deletes = [u for m, u in self.netz.aufrufe if m == "DELETE"]
        self.assertTrue(all("/ch/plaintext/v2/" in u or "/maven/ch.plaintext.v2." in u for u in deletes), deletes)
        self.assertIn("<latest>2.790.1</latest>", self.netz.metadata)
        self.assertIn("<version>2.789.3</version>", self.netz.metadata)
        self.assertNotIn("2.789.2", self.netz.metadata)
        # Karte 1563: die Pruefsummen passen zur neu geschriebenen maven-metadata.xml
        import hashlib
        m = "releases/ch/plaintext/v2/plaintext-root-parent/maven-metadata.xml"
        xml = self.netz.puts[m].encode()
        self.assertEqual(self.netz.puts[m + ".sha1"], hashlib.sha1(xml).hexdigest())
        self.assertEqual(self.netz.puts[m + ".md5"], hashlib.md5(xml).hexdigest())
        self.assertIn("v1-Zaehlung nachher: 5 Versionen", out)
        self.assertIn("v1-Zaehlung nachher: 3 Versionen", out)


class SperreTest(unittest.TestCase):
    """Negativtest: ein v1-Kandidat bricht ab, und zwar vor dem ersten DELETE."""

    def test_reposilite_pfad_ausserhalb_v2_bricht_ab(self):
        for pfad in ("releases/ch/plaintext/plaintext-root-parent/1.789.0", "releases/ch/plaintext/v2",
                     "releases/ch/plaintext/v2/", "releases/ch/plaintext/v2/../x/1.0.0", "ch/plaintext/v2/a/1.0.0"):
            with self.assertRaises(purge.V1Sperre, msg=pfad):
                purge.Reposilite.pruefe_sperre(pfad)
        purge.Reposilite.pruefe_sperre("releases/ch/plaintext/v2/plaintext-root-parent/2.789.1")

    def test_github_paket_ausserhalb_v2_bricht_ab(self):
        for name in ("ch.plaintext.plaintext-root-parent", "ch.plaintext.v2", "ch.plaintext.v2x.a", "org.x.ch.plaintext.v2.a"):
            with self.assertRaises(purge.V1Sperre, msg=name):
                purge.GitHubPackages.pruefe_sperre(name)
        purge.GitHubPackages.pruefe_sperre("ch.plaintext.v2.plaintext-root-parent")

    def test_v1_kandidat_im_plan_bricht_vor_dem_loeschen_ab(self):
        netz = FakeNetz(reposilite_v2={"plaintext-root-parent": ["2.789.1", "2.789.2"]},
                        reposilite_v1={"plaintext-root-parent": ["1.789.0", "1.788.0"]})
        r = purge.Reposilite("https://repo.test", "ci", "t", netz)
        plan = r.planen(1)[0]
        # Jemand schiebt einen v1-Pfad in den Plan (Fehler im Code, manipulierte Antwort, ...):
        plan.ziel["2.789.1"] = "releases/ch/plaintext/plaintext-root-parent/1.788.0"
        with self.assertRaises(purge.V1Sperre):
            r.loeschen(plan)
        self.assertEqual([m for m, _ in netz.aufrufe if m == "DELETE"], [])
        self.assertEqual(netz.reposilite["v1"]["plaintext-root-parent"], ["1.789.0", "1.788.0"])

    def test_v1_kandidat_github_bricht_vor_dem_loeschen_ab(self):
        netz = FakeNetz(github={"ch.plaintext.v2.a": ["1.0.0", "1.0.1"], "ch.plaintext.a": ["1.0.0", "1.0.1"]})
        g = purge.GitHubPackages("Plaintext-Gmbh", "tok", netz)
        plan = g.planen(1)[0]
        plan.artefakt = "ch.plaintext.a"
        with self.assertRaises(purge.V1Sperre):
            g.loeschen(plan)
        self.assertEqual([m for m, _ in netz.aufrufe if m == "DELETE"], [])

    def test_hauptprogramm_meldet_sperre_als_abbruch(self):
        netz = FakeNetz(reposilite_v2={"plaintext-root-parent": ["2.789.1", "2.789.2"]})
        orig = purge.Reposilite.planen

        def kaputt(self, behalten, geschuetzt=()):
            raise purge.V1Sperre("Test")
        purge.Reposilite.planen = kaputt
        try:
            a = args("--nur", "reposilite")
            out = io.StringIO()
            with self.assertRaises(purge.V1Sperre):
                purge.lauf(a, purge.Reposilite("https://repo.test", "ci", "t", netz), None, out)
        finally:
            purge.Reposilite.planen = orig


class PinSchutzTest(unittest.TestCase):
    """Karte 1635: benutzte v2-Versionen bleiben; ohne ermittelte Pins wird nichts geloescht."""

    V2 = ["2.805.1", "2.805.2", "2.806.1", "2.809.1", "2.814.1", "2.815.1"]

    def netz(self, **kw):
        kw.setdefault("modul_poms", {"plaintext-modul-running-parent": {"3.0.0": modul_pom("2.805.2", archtests="2.809.1")},
                                     "plaintext-modul-running": {"3.0.0": modul_pom()}})
        return FakeNetz(reposilite_v2={"plaintext-root-common": list(self.V2)},
                        github={"ch.plaintext.v2.plaintext-root-common": list(self.V2)}, **kw)

    def test_pom_parser(self):
        self.assertEqual(purge.v2_pins_aus_pom(modul_pom("2.805.2", archtests="2.809.1")), {"2.805.2", "2.809.1"})
        self.assertEqual(purge.v2_pins_aus_pom(modul_pom()), set())
        verschachtelt = modul_pom(extra="<properties><a>${b}</a><b>2.812.1</b></properties><build><plugins><plugin>"
                                        "<groupId>ch.plaintext.v2</groupId><version>${a}</version></plugin></plugins></build>")
        self.assertEqual(purge.v2_pins_aus_pom(verschachtelt), {"2.812.1"})
        # exclusion ohne Version, unaufloesbare Property, fremde Gruppe: kein Pin
        nichts = modul_pom(extra="<dependencies><dependency><groupId>ch.plaintext</groupId><version>1.805.0</version></dependency>"
                                 "<dependency><groupId>ch.plaintext.v2</groupId><version>${fehlt}</version><exclusions><exclusion>"
                                 "<groupId>ch.plaintext.v2</groupId><artifactId>*</artifactId></exclusion></exclusions>"
                                 "</dependency></dependencies>")
        self.assertEqual(purge.v2_pins_aus_pom(nichts), set())

    def test_positiv_gepinnte_alte_version_bleibt_negativ_ungepinnte_wird_geloescht(self):
        netz = self.netz()
        rc, out = lauf(netz, "--behalten", "2", "--scharf")
        self.assertEqual(rc, 0, out)
        self.assertIn("Pin-Schutz: 2 benutzte v2-Versionen aus 2 POMs bleiben: 2.809.1, 2.805.2", out)
        # Positiv: 2.805.2 (Parent) und 2.809.1 (archtests ueber ${property}) sind aelter als die 2 juengsten und bleiben
        self.assertEqual(netz.reposilite["v2"]["plaintext-root-common"], ["2.805.2", "2.809.1", "2.814.1", "2.815.1"])
        self.assertEqual(netz.github["ch.plaintext.v2.plaintext-root-common"], ["2.805.2", "2.809.1", "2.814.1", "2.815.1"])
        self.assertIn("<version>2.805.2</version>", netz.metadata)
        self.assertIn("<version>2.809.1</version>", netz.metadata)
        # Negativ: die ungepinnten alten 2.806.1 und 2.805.1 sind weg
        self.assertNotIn("2.806.1", netz.metadata)
        deletes = [u for m, u in netz.aufrufe if m == "DELETE"]
        self.assertEqual(sum("2.806.1" in u for u in deletes), 2, deletes)      # Reposilite + GitHub
        self.assertEqual(sum("2.805.1" in u for u in deletes), 2, deletes)
        self.assertFalse(any("2.805.2" in u or "2.809.1" in u for u in deletes), deletes)

    def test_trockenlauf_listet_gepinnte_unter_behalten(self):
        rc, out = lauf(self.netz(), "--behalten", "2", nur="reposilite")
        self.assertEqual(rc, 0, out)
        self.assertIn("behalten (4): 2.815.1, 2.814.1, 2.809.1, 2.805.2", out)
        self.assertIn("loeschen (2): 2.806.1, 2.805.1", out)

    def assert_abbruch_ohne_delete(self, netz, text):
        with self.assertRaises(purge.PinsFehlen) as cm:
            lauf(netz, "--behalten", "1", "--scharf")
        self.assertIn(text, str(cm.exception))
        self.assertEqual([m for m, _ in netz.aufrufe if m in ("DELETE", "PUT")], [])
        self.assertEqual(netz.reposilite["v2"]["plaintext-root-common"], self.V2)

    def test_null_treffer_bricht_ab_ohne_zu_loeschen(self):
        self.assert_abbruch_ohne_delete(self.netz(modul_poms={"plaintext-modul-x": {"1.0.0": modul_pom()}}),
                                        "keine benutzte v2-Version")

    def test_keine_modul_artefakte_bricht_ab(self):
        self.assert_abbruch_ohne_delete(self.netz(modul_poms={}), "in 0 POMs keine benutzte v2-Version")

    def test_pom_lesefehler_bricht_ab_ohne_zu_loeschen(self):
        self.assert_abbruch_ohne_delete(self.netz(pom_status=500), "Pin-Ermittlung gescheitert")

    def test_kaputtes_pom_bricht_ab_ohne_zu_loeschen(self):
        self.assert_abbruch_ohne_delete(self.netz(modul_poms={"plaintext-modul-x": {"1.0.0": "<project>ch.plaintext.v2"}}),
                                        "ParseError")

    def test_root_artefakte_werden_nicht_gelesen(self):
        netz = self.netz()
        netz.reposilite["v1"]["plaintext-root-common"] = ["1.805.0"]
        lauf(netz, "--behalten", "2", nur="reposilite")
        self.assertFalse(any(u.endswith(".pom") and "plaintext-root-" in u for _, u in netz.aufrufe))

    def test_hauptprogramm_gibt_rc_5(self):
        orig = purge.lauf
        purge.lauf = lambda *a, **k: (_ for _ in ()).throw(purge.PinsFehlen("Test"))
        try:
            self.assertEqual(purge.main(["--nur", "reposilite"]), 5)
        finally:
            purge.lauf = orig


class RechteTest(unittest.TestCase):
    def test_ohne_delete_packages_bricht_scharf_klar_ab(self):
        netz = FakeNetz(github={"ch.plaintext.v2.a": ["1.0.0", "1.0.1"]}, scopes="read:packages, write:packages")
        with self.assertRaises(purge.RechteFehlen) as cm:
            lauf(netz, "--behalten", "1", "--scharf", nur="github")
        self.assertIn("delete:packages", str(cm.exception))
        self.assertEqual([m for m, _ in netz.aufrufe if m == "DELETE"], [])

    def test_ohne_delete_packages_ist_trockenlauf_erlaubt(self):
        netz = FakeNetz(github={"ch.plaintext.v2.a": ["1.0.0", "1.0.1"]}, scopes="read:packages")
        rc, out = lauf(netz, "--behalten", "1", nur="github")
        self.assertEqual(rc, 0, out)
        self.assertIn("Token-Scopes: read:packages", out)

    def test_403_beim_loeschen_wird_als_rechtefehler_gemeldet(self):
        netz = FakeNetz(github={"ch.plaintext.v2.a": ["1.0.0", "1.0.1"]}, scopes=None, delete_status=403)
        with self.assertRaises(purge.RechteFehlen):
            lauf(netz, "--behalten", "1", "--scharf", nur="github")

    def test_ohne_token_klare_meldung(self):
        g = purge.GitHubPackages("Plaintext-Gmbh", None, FakeNetz())
        with self.assertRaises(purge.RechteFehlen):
            g.rechte_pruefen(False)


class ArgumenteTest(unittest.TestCase):
    def test_standard_ist_trockenlauf_mit_fuenf(self):
        a = args()
        self.assertFalse(a.scharf)
        self.assertEqual(a.behalten, 5)

    def test_keine_option_fuer_die_gruppe(self):
        with self.assertRaises(SystemExit):
            args("--gruppe", "ch.plaintext")
        with self.assertRaises(SystemExit):
            args("--behalten", "0")


if __name__ == "__main__":
    unittest.main()
