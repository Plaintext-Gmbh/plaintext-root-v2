# Menu placement is set from outside, keyed by menuId

* **Status:** accepted
* **Date:** 2026-11-26
* **Deciders:** Daniel (plan backport phase A, Karte 2377)
* **Informed:** consumers of plaintext-root (app, guild, schuetu, iot, module repos)

## Context

A module jar brings its menu entry fixed in `@MenuAnnotation` (`parent`, `order`, `title`, `icon`). The same
module runs in several applications (inventory, demo, carrier), each wanting it in a different place. Changing the
module per application is not possible for a shared module; the only knobs were on/off, a module role and tenant
hiding. Some modules (the terminal) must also not be open to ROOT/ADMIN by default.

## Decision

The annotation is the default; `plaintext.menu.platzierung.<menuId>` (or an own `MenuPlatzierungProvider` bean)
overrides parent/order/title/icon/sichtbar before `MenuRegistryPostProcessor` registers the entries. Missing
parents come from `plaintext.menu.gruppen`. The key is the `menuId`, which every entry with a `moduleId` must set
(ArchUnit rule), and which placement never changes. Strictness is configuration as well
(`strenge-module-roles`, since 3.70.0 split into ROOT and ADMIN bypass and kept in settings).

## Consequences

* Positive: one module jar, many menu layouts; no application-wide `ModuleEnablementProvider` hacks.
* Positive: `menuId` stays stable, so automatic roles and page guard mapping survive a move.
* Negative: the full title changes on a move; tenant menu lists and tile `menuTitle` with the old title no longer
  match (only a warning).
* Neutral: moving under *Root* brings the root-branch rule; the page guard inherits from the effective parent.

## Alternatives considered

| Option | Why not? |
| ------ | -------- |
| Admin page to move entries at runtime | Menu structure is delivery, not operations; the provider interface allows it later. |
| Key from parent + title | A move would change the key and the automatic role — users would lose rights. |
| Changing `link`/`roles` from outside | The link belongs to the pages in the jar; roles go through `module-roles`. |

## References

* `docs/MENU_SYSTEM.md` «Placement from outside», `docs/ROLE_REGISTRY.md` «Strict module roles»
* Konzept «Menüplatz eines Moduls von aussen setzen» (backport, 17.11.2026); plan cards ROOT-1 to ROOT-4
