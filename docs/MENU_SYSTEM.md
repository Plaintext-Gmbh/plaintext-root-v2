# Menu system

> **Links end in `.html`, never `.xhtml`.** The application rewrites `.html` to
> the Facelets view, and `MenuLinkInvariantTest` fails the build for a menu entry
> that points at `.xhtml` directly. Older examples in this file used to show the
> wrong form — corrected on 30 August 2026.



Programmatic menu builder for PrimeFaces using annotations.

## Overview

This module provides a declarative way to build PrimeFaces menus using annotations. Instead of defining menus in XHTML files, you can annotate classes with `@MenuAnnotation` and they will be automatically discovered and added to the menu.

## Usage

### 1. Add Dependency

Add this module as a dependency in your `pom.xml` (the version is inherited
from the `plaintext-root-parent` BOM, so omit it when consuming the framework
that way):

```xml
<dependency>
    <groupId>ch.plaintext</groupId>
    <artifactId>plaintext-root-menu</artifactId>
</dependency>
```

### 2. Annotate Classes

Create classes and annotate them with `@MenuAnnotation`:

```java
@MenuAnnotation(
    title = "Dashboard",
    link = "dashboard.html",
    order = 10,
    icon = "pi pi-home"
)
public class DashboardMenu {
}

@MenuAnnotation(
    title = "Users",
    link = "users.html",
    parent = "Administration",
    order = 20,
    icon = "pi pi-users",
    roles = {"ADMIN", "USER_MANAGER"}
)
public class UsersMenu {
}

@MenuAnnotation(
    title = "Administration",
    order = 100,
    icon = "pi pi-cog"
)
public class AdministrationMenu {
}
```

### 3. Build Menu Model

Inject `MenuModelBuilder` in your backing bean and build the menu.

For **JSF/Jakarta EE** applications:

```java
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("menuBean")
@ApplicationScoped
public class MenuBean {

    @Inject
    private MenuModelBuilder menuModelBuilder;

    private MenuModel model;

    @PostConstruct
    public void init() {
        model = menuModelBuilder.buildMenuModel();
    }

    public MenuModel getModel() {
        return model;
    }
}
```

For **Spring Boot** applications:

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
public class MenuBean {

    @Autowired
    private MenuModelBuilder menuModelBuilder;

    private MenuModel model;

    @PostConstruct
    public void init() {
        model = menuModelBuilder.buildMenuModel();
    }

    public MenuModel getModel() {
        return model;
    }
}
```

### 4. Use in XHTML

Use the menu model in your XHTML template:

```xml
<p:menubar model="#{menuBean.model}" />
```

## Configuration

### Package Scanning

By default, the module scans the `ch.plaintext` package for annotated classes. You can change this in `application.properties`:

```properties
plaintext.menu.scan-package=ch.plaintext.myapp
```

### Security

To enable role-based menu visibility, implement the `SecurityProvider` interface and register it as a Spring bean:

```java
@Component
public class MySecurityProvider implements SecurityProvider {

    @Override
    public boolean hasRole(String role) {
        // Your security logic here
        return SecurityContextHolder.getContext()
            .getAuthentication()
            .getAuthorities()
            .stream()
            .anyMatch(a -> a.getAuthority().equals(role));
    }

    @Override
    public boolean isSecurityEnabled() {
        return true;
    }
}
```

### Placement from outside

*since root 3.59.0 (Karte 2377, ROOT-1/ROOT-2; ADR [0009](adr/0009-menueplatz-von-aussen.md))*

The `@MenuAnnotation` of a module is the **default**. An application moves an entry by its `menuId`
without touching the module:

```yaml
plaintext:
  menu:
    platzierung:              # key = menuId, every field optional
      terminal:
        parent: Werkzeuge     # title of the parent; "" = top level
        order: 10
        title: Terminal
        icon: pi pi-desktop
      drawio:
        parent: Werkzeuge
        order: 20
      drawio-bilder:
        sichtbar: false       # not in the menu, the page stays reachable by link
    gruppen:                  # parents that do not exist are created as pure groups (no link)
      Werkzeuge:
        order: 70
        icon: pi pi-wrench
```

* A group title with a space or a dot needs the bracket form as a map key: `"[Mein Bereich]": { order: 5 }`.
* Placement never changes the `menuId` (frozen before moving), so automatic roles `ROLE_MENU_<menuId>`, the
  page guard mapping and tenant visibility stay. The **full title** does change: tenant menu lists and tile
  `menuTitle` entries written with the old title no longer match (`TileVisibilityValidator` only warns).
* Unknown `menuId`, a missing parent that is no group, and a cycle are reported as WARN; the entry keeps its
  default. Startup log: `MenuPlatzierung : n Eintraege umgehaengt, m Gruppen angelegt`.
* `sichtbar: false` only hides the entry in the menu builder; it stays registered for the page guard (under
  `STRICT` the page would otherwise be locked, under `PERMISSIVE` unprotected).
* The page guard inherits from the **effective** parent: moving an entry under a protected branch protects it as
  well. Moving it under *Root* brings the root-branch rule (no tenant filter) — a conscious decision.
* Automatic roles `ROLE_MENU_<menuId>` only exist under `plaintext.menu.access-policy: STRICT`; the default is
  `PERMISSIVE`.
* An application that must compute placements registers its own `MenuPlatzierungProvider` bean (replaces the
  property default). It is created early inside a `BeanFactoryPostProcessor` and must not depend on beans that
  need post-processing.
* `/debug/menu-scan` and the menu diagnostics show default and effective place side by side.
* Every class with `@MenuAnnotation(moduleId = …)` needs a `menuId` (ArchUnit `menuIdPflichtBeiModuleId`, switch
  `plaintext.arch.menu-id`: `melden` by default, `enforce` in root; ROOT-4).

## Annotation Parameters

- `title`: The menu item label (default: "Dashboard")
- `link`: The navigation outcome/URL (default: "dashboard.htm")
- `parent`: The parent menu item title for hierarchical menus (default: "")
- `order`: Sort order (lower numbers appear first, default: 0)
- `icon`: PrimeFaces icon class (default: "")
- `roles`: Array of roles that can see this menu item (default: empty = visible to all)
- `moduleId`: The module this entry belongs to; the entry disappears when the module is disabled (default: empty)
- `menuId`: Stable id of the entry, used as key for placement from outside (`plaintext.menu.platzierung.<menuId>`)
  and for the auto-role `ROLE_MENU_<MENUID>` under `plaintext.menu.access-policy=STRICT`. Empty = derived from
  `parent` + `title`. **Required whenever `moduleId` is set** (ArchUnit rule `menuIdPflichtBeiModuleId` in
  plaintext-root-archtests, Karte 2377 ROOT-4): the derived id would change when the entry is moved or renamed.
  Transition for consumer repos: `menu-id <Klasse>  # <Karte, Grund>` in `plaintext-arch-allowlist.txt`. During the
  transition release the rule only reports in consumer repos; `-Dplaintext.arch.menu-id=enforce` (root does this in
  Surefire) makes it fail.

## How It Works

1. **MenuRegistryPostProcessor** scans the configured package for classes with `@MenuAnnotation`
2. Found annotations are converted to `MenuItemImpl` beans and registered in Spring context
3. **MenuModelBuilder** reads all `MenuItemImpl` beans and builds a hierarchical PrimeFaces `MenuModel`
4. Menu items are filtered based on security roles (if configured)
5. The resulting `MenuModel` can be used directly with PrimeFaces components

## Migration from XHTML Menus

If you're migrating from XHTML-defined menus:

1. For each menu item in your XHTML, create a corresponding annotated class
2. Map XHTML attributes to annotation parameters:
   - `value` → `title`
   - `outcome` → `link`
   - `rendered` → `roles` (use SecurityProvider)
3. Use the parent-child relationship via the `parent` parameter
4. Remove the menu definition from your XHTML template
5. Inject and use `MenuModelBuilder` instead

## Example

See the example classes in this package for a complete working example of a multi-level menu with security.
