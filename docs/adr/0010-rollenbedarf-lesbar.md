# The roles a module needs are readable from outside

* **Status:** accepted
* **Date:** 2026-11-26
* **Deciders:** Daniel (ENT-3, ENT-4, ENT-5 = A, 20.11.2026; Karte 2377)
* **Informed:** operations, consumers of plaintext-root

## Context

Roles of a module were only visible inside the running process. Roles that nobody declares (menu `roles`,
`ROLE_MENU_*` under `STRICT`, `module-roles`, literals in code) were known to nobody. Operations, OIDC group
maintenance, agents and the loader before the start had to read source code to know what a jar requires.

## Decision

One model, `ch.plaintext.rollen.RollenBedarf`, from three places:

1. **Declaration** — `PlaintextRole` gets `modul`, `art`, `ohneUmgehung`; `ohneUmgehung` makes the module strict
   by default.
2. **Manifest** `META-INF/plaintext/modul.yaml`, written and checked by a test base class
   (`ModulManifestTest`, ENT-3 = A instead of a Maven plugin), read without Spring by `ModulManifest` in the loader
   (`--rollen`, startup log).
3. **Runtime** — `GET /api/root/rollen` and MCP `list_roles`: session ADMIN/ROOT, token `SCOPE_READ` of an
   ADMIN/ROOT owner, no new scope (ENT-4 = A); counts per role, never names; OIDC groups are not counted and the
   response says so (ENT-5 = A).

## Consequences

* Positive: whoever assigns roles sees what is required and what is stale (`bestand`).
* Negative: `PlaintextRole` equality now includes three more components.
* Negative: the manifest is only as current as the last test run; a build without tests does not refresh it (the
  test fails on drift, so CI catches it).
* Neutral: the version is taken from the jar's `pom.properties`, not from the file, so release bumps do not make
  the manifest test red.

## Alternatives considered

| Option | Why not? |
| ------ | -------- |
| Maven plugin `plaintext-modul-manifest` (ENT-3 B) | New artifact to maintain and to ship in the BIT `lib/`; the test runs everywhere already. |
| New token scope `ROLLEN` (ENT-4 B) | Scope change in UI, contract test and every consumer for one read-only list. |
| Counting OIDC groups (ENT-5 B) | Extra dependency on the OIDC module, and the claim mapping makes the count fuzzy. |
| `getRollenBedarf()` in `PlaintextRoleRegistry` | `plaintext-root-common` cannot see menu items; the service lives in role-assignment. |

## References

* `docs/ROLE_REGISTRY.md` «Reading roles from outside», «Manifest»
* Konzept «zu vergebende Rollen eines Moduls von aussen lesen» (backport, 17.11.2026); plan cards ROOT-5 to ROOT-8
