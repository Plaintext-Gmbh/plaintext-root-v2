#!/usr/bin/env python3
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
"""
scripts/purge.py — alte v2-Releases aus beiden Paket-Repos entfernen (Karte 1563).

Behält je v2-Artefakt die jüngsten N Releases (Standard 5) und löscht die älteren, in
  * Reposilite   https://maven.plaintext.ch/releases  — NUR unter releases/ch/plaintext/v2/
  * GitHub Packages (Org Plaintext-Gmbh, Maven)        — NUR Pakete mit Namen ch.plaintext.v2.*

HARTE SPERRE GEGEN v1. Die beiden Filter oben sind Konstanten, keine Optionen: dieses Skript kennt
`ch.plaintext` allein nicht als Filter und hat keinen Schalter, der die Gruppe ändert. Jeder
Löschkandidat wird vor dem Löschen noch einmal gegen die Sperre geprüft (Pfad bzw. Paketname);
ein Kandidat, der sie nicht erfüllt, bricht den Lauf ab (V1Sperre), auch im Trockenlauf.
Die Zählung der v1-Versionen vor und nach dem Lauf steht in der Ausgabe und muss gleich sein.

PIN-SCHUTZ (Karte 1635). Vor dem Planen liest das Skript alle veröffentlichten POMs der
Nicht-root-Artefakte unter releases/ch/plaintext/ (Module, Apps) und sammelt jede Version, mit der
sie ch.plaintext.v2 benutzen: Parent, Abhängigkeit, Plugin, auch über eine ${property} des POMs.
Diese Versionen werden in keinem Artefakt gelöscht, auch wenn sie älter als die jüngsten N sind.
Ein Modul mit Parent ch.plaintext.v2:plaintext-root-parent:2.805.2 braucht beim Bau die ganze
2.805.2-Linie. Scheitert die Ermittlung oder findet sie keinen Pin, bricht der Lauf ab, bevor
irgendetwas gelöscht wird (rc 5). Nicht gesehen werden Pins außerhalb veröffentlichter POMs,
etwa LADER_VERSION in ci/registry.sh der Modul-Repos.

TROCKENLAUF IST DER STANDARD. Gelöscht wird nur mit --scharf.

Aufruf (Jumphost oder Woodpecker-Schritt, nur Python 3 Standardbibliothek):
    scripts/purge.py                               Trockenlauf, beide Repos, N=5
    scripts/purge.py --behalten 3                  Trockenlauf, N=3
    scripts/purge.py --scharf                      löscht
    scripts/purge.py --nur reposilite|github       nur ein Repo
Umgebung:
    REPOSILITE_BASIS   Standard https://maven.plaintext.ch   (ohne /releases)
    REPOSILITE_TOKEN   Reposilite-Token (Secret maven_nas_token), Benutzer REPOSILITE_USER (Standard ci)
    GITHUB_TOKEN       PAT mit read:packages (+ delete:packages für --scharf). Fehlt delete:packages,
                       meldet das Skript es VOR dem ersten Löschversuch klar und bricht ab.
    GITHUB_ORG         Standard Plaintext-Gmbh

Reposilite: Versionen = Unterverzeichnisse von releases/ch/plaintext/v2/<artefakt>/ (API
/api/maven/details). Löschen = DELETE auf das Versionsverzeichnis; danach wird die
maven-metadata.xml des Artefakts ohne die gelöschten Versionen neu geschrieben (PUT), weil ein
Eintrag in der Metadatei sonst weiter als "neueste Version" vorgeschlagen würde (Karte 1281).
GitHub Packages: Versionen = /orgs/{org}/packages/maven/{name}/versions; Löschen = DELETE je
Version. Die letzte Version eines Pakets lässt GitHub nicht einzeln löschen — die bleibt ohnehin,
weil N >= 1.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from typing import Callable, Iterable

# ── Die Sperre: Konstanten, keine Optionen ────────────────────────────────────
REPOSILITE_V2_PFAD = "releases/ch/plaintext/v2/"
GITHUB_V2_PRAEFIX = "ch.plaintext.v2."
# Für die v1-Zählung: alles unter ch/plaintext/, was NICHT die Gruppe v2 ist.
REPOSILITE_V1_PFAD = "releases/ch/plaintext/"
GITHUB_V1_PRAEFIX = "ch.plaintext."

VERSION_RE = re.compile(r"^\d+(\.\d+)*$")
# Pin-Schutz: die v1-root-Linie selbst benutzt v2 nie; ihre ~9000 POMs zu lesen kostet nur Zeit.
PIN_SCAN_OHNE = re.compile(r"^plaintext-(root|admin)-")


class V1Sperre(Exception):
    """Ein Löschkandidat liegt nicht im v2-Raum. Der Lauf bricht ab, nichts wird gelöscht."""


class PinsFehlen(Exception):
    """Die benutzten v2-Versionen liessen sich nicht ermitteln (Fehler oder 0 Treffer). Nichts wird geloescht."""


class RechteFehlen(Exception):
    """Das Token kann nicht löschen (kein delete:packages). Klar gemeldet statt still nichts getan."""


# ── HTTP: ein kleiner, austauschbarer Transport (Tests schieben einen Fake unter) ─
class Antwort:
    def __init__(self, status: int, body: bytes = b"", headers: dict | None = None):
        self.status = status
        self.body = body
        self.headers = {k.lower(): v for k, v in (headers or {}).items()}

    def json(self):
        return json.loads(self.body.decode("utf-8") or "null")

    def text(self) -> str:
        return self.body.decode("utf-8", errors="replace")


Transport = Callable[[str, str, dict, bytes | None], Antwort]


def urllib_transport(methode: str, url: str, headers: dict, body: bytes | None) -> Antwort:
    anfrage = urllib.request.Request(url, data=body, method=methode, headers=headers)
    try:
        with urllib.request.urlopen(anfrage, timeout=60) as r:
            return Antwort(r.status, r.read(), dict(r.headers))
    except urllib.error.HTTPError as e:
        return Antwort(e.code, e.read() if e.fp else b"", dict(e.headers or {}))


# ── Versionen sortieren: numerisch je Stelle, jüngste zuerst ──────────────────
def versions_schluessel(v: str):
    return tuple(int(t) for t in v.split("."))


def nur_release_versionen(versionen: Iterable[str]) -> list[str]:
    return sorted((v for v in versionen if VERSION_RE.match(v)), key=versions_schluessel, reverse=True)


def aufteilen(versionen: Iterable[str], behalten: int, geschuetzt: Iterable[str] = ()) -> tuple[list[str], list[str]]:
    """(behalten, löschen) — jüngste N bleiben, dazu jede gepinnte Version. Unsortierbare Einträge
    (SNAPSHOT, Text) werden nie gelöscht: sie sind keine Releases dieses Schemas."""
    if behalten < 1:
        raise ValueError("--behalten muss mindestens 1 sein")
    sortiert = nur_release_versionen(versionen)
    geschuetzt = set(geschuetzt)
    aelter = sortiert[behalten:]
    return sortiert[:behalten] + [v for v in aelter if v in geschuetzt], [v for v in aelter if v not in geschuetzt]


def v2_pins_aus_pom(text: str) -> set[str]:
    """Versionen, mit denen ein POM ch.plaintext.v2 benutzt (Parent, Abhängigkeit, Plugin; ${property}
    aus dem eigenen <properties> aufgelöst). Nicht auflösbare Platzhalter zählen nicht: das POM, das
    die Property definiert, benutzt sie selbst und wird ebenfalls gelesen."""
    wurzel = ET.fromstring(text)
    for e in wurzel.iter():
        e.tag = e.tag.rsplit("}", 1)[-1]
    props = {p.tag: (p.text or "").strip() for p in wurzel.findall("properties/*")}
    funde = set()
    for e in wurzel.iter():
        if (e.findtext("groupId") or "").strip() != "ch.plaintext.v2":
            continue
        v = (e.findtext("version") or "").strip()
        for _ in range(5):                       # ${a} -> ${b} -> 2.805.2
            m = re.fullmatch(r"\$\{([^}]+)\}", v)
            if not m:
                break
            v = props.get(m.group(1), "")
        if VERSION_RE.match(v):
            funde.add(v)
    return funde


@dataclass
class Plan:
    repo: str
    artefakt: str
    behalten: list[str]
    loeschen: list[str]
    # Reposilite: Pfade je zu löschender Version; GitHub: Versions-IDs je Version
    ziel: dict[str, str] = field(default_factory=dict)


# ── Reposilite ────────────────────────────────────────────────────────────────
class Reposilite:
    def __init__(self, basis: str, user: str | None, token: str | None, transport: Transport = urllib_transport):
        self.basis = basis.rstrip("/")
        self.transport = transport
        self.headers = {"Accept": "application/json"}
        if user and token:
            auth = base64.b64encode(f"{user}:{token}".encode()).decode()
            self.headers["Authorization"] = f"Basic {auth}"

    def _details(self, pfad: str) -> list[dict]:
        a = self.transport("GET", f"{self.basis}/api/maven/details/{pfad}", self.headers, None)
        if a.status == 404:
            return []
        if a.status != 200:
            raise RuntimeError(f"Reposilite {a.status} bei details/{pfad}: {a.text()[:200]}")
        d = a.json()
        return d.get("files", []) if isinstance(d, dict) else []

    def verzeichnisse(self, pfad: str) -> list[str]:
        return [f["name"] for f in self._details(pfad) if f.get("type") == "DIRECTORY"]

    def artefakte_v2(self) -> list[str]:
        return sorted(self.verzeichnisse(REPOSILITE_V2_PFAD.rstrip("/")))

    def versionen(self, artefakt: str) -> list[str]:
        return self.verzeichnisse(f"{REPOSILITE_V2_PFAD}{artefakt}")

    def v1_zaehlung(self) -> int:
        """Anzahl Versionen aller v1-Artefakte direkt unter ch/plaintext/ (ohne das Verzeichnis v2)."""
        anzahl = 0
        for a in self.verzeichnisse(REPOSILITE_V1_PFAD.rstrip("/")):
            if a == "v2":
                continue
            anzahl += len(self.verzeichnisse(f"{REPOSILITE_V1_PFAD}{a}"))
        return anzahl

    @staticmethod
    def pruefe_sperre(pfad: str) -> None:
        if not pfad.startswith(REPOSILITE_V2_PFAD) or pfad == REPOSILITE_V2_PFAD:
            raise V1Sperre(f"Reposilite-Pfad ausserhalb von {REPOSILITE_V2_PFAD}: {pfad}")
        rest = pfad[len(REPOSILITE_V2_PFAD):]
        if ".." in rest or rest.startswith("/"):
            raise V1Sperre(f"Reposilite-Pfad unsauber: {pfad}")

    def _pom_pins(self, artefakt: str, version: str) -> set[str]:
        a = self.transport("GET", f"{self.basis}/{REPOSILITE_V1_PFAD}{artefakt}/{version}/{artefakt}-{version}.pom",
                           self.headers, None)
        if a.status == 404:                      # Versionsverzeichnis ohne POM: benutzt nichts
            return set()
        if a.status != 200:
            raise RuntimeError(f"Reposilite {a.status} beim POM {artefakt}:{version}")
        text = a.text()
        return v2_pins_aus_pom(text) if "ch.plaintext.v2" in text else set()

    def v2_pins(self) -> tuple[set[str], int]:
        """(benutzte v2-Versionen, Anzahl gelesener POMs) über alle Nicht-root-Artefakte unter ch/plaintext/."""
        artefakte = [a for a in self.verzeichnisse(REPOSILITE_V1_PFAD.rstrip("/"))
                     if a != "v2" and not PIN_SCAN_OHNE.match(a)]
        jobs = [(a, v) for a in artefakte for v in self.verzeichnisse(f"{REPOSILITE_V1_PFAD}{a}")]
        with ThreadPoolExecutor(16) as ex:
            funde = list(ex.map(lambda j: self._pom_pins(*j), jobs))
        return set().union(*funde), len(jobs)

    def planen(self, behalten: int, geschuetzt: Iterable[str] = ()) -> list[Plan]:
        plaene = []
        for a in self.artefakte_v2():
            bleibt, weg = aufteilen(self.versionen(a), behalten, geschuetzt)
            p = Plan("reposilite", a, bleibt, weg)
            for v in weg:
                pfad = f"{REPOSILITE_V2_PFAD}{a}/{v}"
                self.pruefe_sperre(pfad)
                p.ziel[v] = pfad
            plaene.append(p)
        return plaene

    def loeschen(self, plan: Plan) -> list[str]:
        meldungen = []
        for v in plan.loeschen:
            pfad = plan.ziel[v]
            self.pruefe_sperre(pfad)           # zweite Prüfung unmittelbar vor dem DELETE
            a = self.transport("DELETE", f"{self.basis}/{pfad}", self.headers, None)
            if a.status not in (200, 204):
                raise RuntimeError(f"Reposilite DELETE {pfad}: {a.status} {a.text()[:200]}")
            meldungen.append(f"geloescht {pfad}")
        if plan.loeschen:
            meldungen.append(self.metadata_neu_schreiben(plan))
        return meldungen

    def metadata_neu_schreiben(self, plan: Plan) -> str:
        pfad = f"{REPOSILITE_V2_PFAD}{plan.artefakt}/maven-metadata.xml"
        self.pruefe_sperre(pfad)
        xml = maven_metadata(plan.artefakt, plan.behalten)
        a = self.transport("PUT", f"{self.basis}/{pfad}", {**self.headers, "Content-Type": "application/xml"}, xml.encode())
        if a.status not in (200, 201, 204):
            raise RuntimeError(f"Reposilite PUT {pfad}: {a.status} {a.text()[:200]}")
        # Karte 1563: Reposilite erzeugt die Pruefsummen nicht selbst (Reparatur 22.10. lud sie von Hand);
        # ohne sie passt .sha1/.md5 nach dem Purge nicht mehr zur neuen maven-metadata.xml.
        for endung, algo in (("sha1", hashlib.sha1), ("md5", hashlib.md5)):
            a = self.transport("PUT", f"{self.basis}/{pfad}.{endung}", {**self.headers, "Content-Type": "text/plain"},
                               algo(xml.encode()).hexdigest().encode())
            if a.status not in (200, 201, 204):
                raise RuntimeError(f"Reposilite PUT {pfad}.{endung}: {a.status} {a.text()[:200]}")
        return f"maven-metadata.xml neu geschrieben (mit .sha1/.md5): {pfad} ({len(plan.behalten)} Versionen)"


def maven_metadata(artefakt: str, versionen: list[str]) -> str:
    import datetime
    sortiert = nur_release_versionen(versionen)
    neueste = sortiert[0] if sortiert else ""
    stand = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d%H%M%S")
    zeilen = "".join(f"      <version>{v}</version>\n" for v in reversed(sortiert))
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        "<metadata>\n"
        "  <groupId>ch.plaintext.v2</groupId>\n"
        f"  <artifactId>{artefakt}</artifactId>\n"
        "  <versioning>\n"
        f"    <latest>{neueste}</latest>\n"
        f"    <release>{neueste}</release>\n"
        "    <versions>\n"
        f"{zeilen}"
        "    </versions>\n"
        f"    <lastUpdated>{stand}</lastUpdated>\n"
        "  </versioning>\n"
        "</metadata>\n"
    )


# ── GitHub Packages ───────────────────────────────────────────────────────────
class GitHubPackages:
    API = "https://api.github.com"

    def __init__(self, org: str, token: str | None, transport: Transport = urllib_transport):
        self.org = org
        self.transport = transport
        self.headers = {"Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}
        if token:
            self.headers["Authorization"] = f"Bearer {token}"
        self.token = token

    def _get_alle(self, url: str) -> list:
        ergebnis: list = []
        seite = 1
        while True:
            trenner = "&" if "?" in url else "?"
            a = self.transport("GET", f"{url}{trenner}per_page=100&page={seite}", self.headers, None)
            if a.status == 404:
                return ergebnis
            if a.status != 200:
                raise RuntimeError(f"GitHub {a.status} bei {url}: {a.text()[:200]}")
            teil = a.json() or []
            ergebnis.extend(teil)
            if len(teil) < 100:
                return ergebnis
            seite += 1

    def pakete(self) -> list[str]:
        return [p["name"] for p in self._get_alle(f"{self.API}/orgs/{self.org}/packages?package_type=maven")]

    def versionen(self, paket: str) -> dict[str, str]:
        """Versionsname -> Versions-ID."""
        name = urllib.parse.quote(paket, safe="")
        return {v["name"]: str(v["id"]) for v in self._get_alle(f"{self.API}/orgs/{self.org}/packages/maven/{name}/versions")}

    def v1_zaehlung(self) -> int:
        anzahl = 0
        for p in self.pakete():
            if p.startswith(GITHUB_V1_PRAEFIX) and not p.startswith(GITHUB_V2_PRAEFIX):
                anzahl += len(self.versionen(p))
        return anzahl

    @staticmethod
    def pruefe_sperre(paket: str) -> None:
        if not paket.startswith(GITHUB_V2_PRAEFIX) or paket == GITHUB_V2_PRAEFIX:
            raise V1Sperre(f"GitHub-Paket ausserhalb von {GITHUB_V2_PRAEFIX}*: {paket}")

    def rechte_pruefen(self, scharf: bool) -> str:
        """Liest die Scopes des Tokens. Klassische PATs melden sie im Header X-OAuth-Scopes;
        ohne Header (fein-granulares Token, GitHub-App) kann nur der Löschversuch selbst antworten."""
        if not self.token:
            raise RechteFehlen("GITHUB_TOKEN fehlt — ohne Token weder Liste noch Löschung")
        a = self.transport("GET", f"{self.API}/user", self.headers, None)
        if a.status != 200:
            raise RuntimeError(f"GitHub /user antwortet {a.status}: Token ungueltig?")
        scopes = a.headers.get("x-oauth-scopes")
        if scopes is None:
            return "Token-Scopes nicht lesbar (kein X-OAuth-Scopes-Header) — Rechte zeigen sich erst beim Loeschen"
        liste = [s.strip() for s in scopes.split(",") if s.strip()]
        if "read:packages" not in liste and "write:packages" not in liste and "delete:packages" not in liste:
            raise RechteFehlen(f"Token hat kein read:packages (Scopes: {scopes or '<keine>'})")
        if scharf and "delete:packages" not in liste:
            raise RechteFehlen(f"Token hat kein delete:packages (Scopes: {scopes}) — --scharf kann in GitHub Packages"
                               " nichts loeschen. Vault-Notiz: github.mvn-deploy-pat ist bewusst OHNE delete:packages;"
                               " fuer den Purge braucht es ein eigenes Token.")
        return f"Token-Scopes: {scopes}"

    def planen(self, behalten: int, geschuetzt: Iterable[str] = ()) -> list[Plan]:
        plaene = []
        for paket in sorted(self.pakete()):
            if not paket.startswith(GITHUB_V2_PRAEFIX):
                continue
            ids = self.versionen(paket)
            bleibt, weg = aufteilen(ids.keys(), behalten, geschuetzt)
            p = Plan("github", paket, bleibt, weg)
            for v in weg:
                self.pruefe_sperre(paket)
                p.ziel[v] = ids[v]
            plaene.append(p)
        return plaene

    def loeschen(self, plan: Plan) -> list[str]:
        meldungen = []
        name = urllib.parse.quote(plan.artefakt, safe="")
        for v in plan.loeschen:
            self.pruefe_sperre(plan.artefakt)  # zweite Prüfung unmittelbar vor dem DELETE
            a = self.transport("DELETE", f"{self.API}/orgs/{self.org}/packages/maven/{name}/versions/{plan.ziel[v]}",
                               self.headers, None)
            if a.status in (401, 403):
                raise RechteFehlen(f"GitHub verweigert DELETE {plan.artefakt} {v}: {a.status} — Token ohne delete:packages?")
            if a.status != 204:
                raise RuntimeError(f"GitHub DELETE {plan.artefakt} {v}: {a.status} {a.text()[:200]}")
            meldungen.append(f"geloescht {plan.artefakt} {v} (id {plan.ziel[v]})")
        return meldungen


# ── Ablauf ────────────────────────────────────────────────────────────────────
def plan_ausgeben(plaene: list[Plan], out) -> int:
    weg = 0
    for p in plaene:
        print(f"  {p.artefakt}", file=out)
        print(f"      behalten ({len(p.behalten)}): {', '.join(p.behalten) or '-'}", file=out)
        print(f"      loeschen ({len(p.loeschen)}): {', '.join(p.loeschen) or '-'}", file=out)
        weg += len(p.loeschen)
    return weg


def pins_ermitteln(quelle: Reposilite | None) -> tuple[list[str], int]:
    """Benutzte v2-Versionen oder PinsFehlen. Kein Treffer zählt als Fehler: dann ist eher die
    Ermittlung kaputt, als dass niemand v2 benutzt."""
    if quelle is None:
        raise PinsFehlen("keine Reposilite-Quelle fuer die Pin-Ermittlung")
    try:
        pins, poms = quelle.v2_pins()
    except Exception as e:  # noqa: BLE001 — jeder Fehler heisst: Pins unbekannt, also nicht loeschen
        raise PinsFehlen(f"Pin-Ermittlung gescheitert: {type(e).__name__}: {e}") from e
    if not pins:
        raise PinsFehlen(f"Pin-Ermittlung fand in {poms} POMs keine benutzte v2-Version")
    return nur_release_versionen(pins), poms


def lauf(args, reposilite: Reposilite | None, github: GitHubPackages | None, out=sys.stdout,
         pin_quelle: Reposilite | None = None) -> int:
    modus = "SCHARF" if args.scharf else "TROCKENLAUF"
    print(f"v2-Purge {modus}: behalte je Artefakt die juengsten {args.behalten} Releases", file=out)
    print(f"Sperre: Reposilite nur {REPOSILITE_V2_PFAD}*, GitHub Packages nur {GITHUB_V2_PRAEFIX}*", file=out)
    pins, poms = pins_ermitteln(pin_quelle or reposilite)   # vor jedem Planen und Loeschen
    print(f"Pin-Schutz: {len(pins)} benutzte v2-Versionen aus {poms} POMs bleiben: {', '.join(pins)}", file=out)
    rc = 0
    for name, repo in (("Reposilite", reposilite), ("GitHub Packages", github)):
        if repo is None:
            continue
        print(f"\n== {name} ==", file=out)
        if isinstance(repo, GitHubPackages):
            print("  " + repo.rechte_pruefen(args.scharf), file=out)
        v1_vorher = repo.v1_zaehlung()
        print(f"  v1-Zaehlung vorher: {v1_vorher} Versionen", file=out)
        plaene = repo.planen(args.behalten, pins)  # wirft V1Sperre, bevor irgendetwas geloescht wird
        anzahl = plan_ausgeben(plaene, out)
        if not args.scharf:
            print(f"  Trockenlauf: {anzahl} Versionen wuerden geloescht. Nichts veraendert.", file=out)
        else:
            geloescht = 0
            for p in plaene:
                for m in repo.loeschen(p):
                    print("  " + m, file=out)
                geloescht += len(p.loeschen)
            print(f"  geloescht: {geloescht} Versionen", file=out)
        v1_nachher = repo.v1_zaehlung()
        print(f"  v1-Zaehlung nachher: {v1_nachher} Versionen", file=out)
        if v1_nachher != v1_vorher:
            print(f"  FEHLER: v1-Zaehlung hat sich veraendert ({v1_vorher} -> {v1_nachher})!", file=out)
            rc = 1
        else:
            print("  v1 unveraendert.", file=out)
    return rc


def argumente(argv: list[str]):
    p = argparse.ArgumentParser(description="Alte v2-Releases entfernen (Trockenlauf ist Standard).")
    p.add_argument("--behalten", type=int, default=5, help="juengste N Releases je Artefakt behalten (Standard 5)")
    p.add_argument("--scharf", action="store_true", help="wirklich loeschen (ohne: Trockenlauf)")
    p.add_argument("--nur", choices=["reposilite", "github"], help="nur ein Repo bearbeiten")
    args = p.parse_args(argv)
    if args.behalten < 1:
        p.error("--behalten muss mindestens 1 sein")
    return args


def main(argv: list[str]) -> int:
    args = argumente(argv)
    reposilite = github = None
    # Die Pins kommen immer aus Reposilite, auch bei --nur github.
    pin_quelle = Reposilite(os.environ.get("REPOSILITE_BASIS", "https://maven.plaintext.ch"),
                            os.environ.get("REPOSILITE_USER", "ci"), os.environ.get("REPOSILITE_TOKEN"))
    if args.nur in (None, "reposilite"):
        reposilite = pin_quelle
    if args.nur in (None, "github"):
        github = GitHubPackages(os.environ.get("GITHUB_ORG", "Plaintext-Gmbh"), os.environ.get("GITHUB_TOKEN"))
    try:
        return lauf(args, reposilite, github, pin_quelle=pin_quelle)
    except V1Sperre as e:
        print(f"\nABBRUCH (V1-SPERRE): {e}\nNichts wurde geloescht.", file=sys.stderr)
        return 3
    except PinsFehlen as e:
        print(f"\nABBRUCH (PIN-SCHUTZ): {e}\nNichts wurde geloescht.", file=sys.stderr)
        return 5
    except RechteFehlen as e:
        print(f"\nABBRUCH (RECHTE): {e}", file=sys.stderr)
        return 4


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
