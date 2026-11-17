/*
 * Copyright (C) plaintext.ch, 2026.
 *
 * Verhalten des Bedienbereichs einer Tabelle (META-INF/tags/tableSettings.xhtml): Tabellen fuer
 * die Kopf-Griffe markieren, Modus-Knopf (Klick/Doppelklick), Panel-Oeffner, Spaltenschalter.
 * Runde 7 (Karte 1853): der Klick auf das Viereck im Kopf (Spalte auf die Zielbreite setzen,
 * Karte 1336) ist mit dem Reiter «Breiten» weggefallen - die Wiederholbarkeit, fuer die er da
 * war, liefert jetzt der feste Stand (Ansicht festhalten, Doppelklick auf den Modus-Knopf).
 * Karte 2160: Gesamt-/Zielbreite samt Java entfernt; der Doppelklick auf den Verbreitern-Griff
 * meldet die Spalte fuer das Feld «Feste Breite» (remoteCommand BreiteFix). Daniel
 * 15.11.: Resize haengt nicht mehr am Modus — der Griff ist auf Seiten mit Breiten immer da,
 * der Modus wird nie gespeichert und startet je Anmeldung mit «Anpassen».
 * Karte 2333 (Daniel 17.11.): der Griff haengt wieder nur im Modus «Vergroessern», der feste
 * Stand ist weg (Klick auf den Modus-Knopf wechselt sofort), der Doppelklick auf den Griff
 * fuellt das leere Feld «Feste Breite» oder setzt dessen Wert auf die Spalte, das Panel geht
 * immer am Regler-Symbol auf, die Spaltenliste merkt sich ihre gezogene Hoehe.
 *
 * WARUM ALS EIGENE DATEI (Welle 4, CSP ohne 'unsafe-inline'):
 * Solange irgendwo ein Inline-<script> steht, muss die Content-Security-Policy
 * script-src 'unsafe-inline' fuehren — und damit laeuft auch jedes eingeschleuste <script>.
 * Muster im Bestand: plaintext-layout/js/topbar.js.
 *
 * ANBINDUNG UEBER data-ATTRIBUTE STATT UEBER FESTE IDs:
 * Der Rahmen des Bedienbereichs traegt data-pt-tablesettings="<DOM-Id der Tabelle>" und
 * data-pt-breiten="1" (Seite mit Spaltenbreiten). Damit weiss dieses Skript nichts ueber
 * Formularnamen — und zwei Bedienbereiche auf einer Seite stoeren sich nicht.
 *
 * WARUM EIN MutationObserver UND KEIN EINMALIGES EINRICHTEN:
 * Nach jedem PrimeFaces-Teilupdate ist die Tabelle ein neuer DOM-Knoten; die Markierung waere
 * weg. Der Beobachter setzt sie wieder (nur Klassen - keine Kind-Aenderung, die ihn erneut
 * ausloeste).
 */
(function () {
    'use strict';

    /**
     * Markiert die Tabellen der Bedienbereiche fuer table-settings.css (Karte 1853 Runde 7):
     * pt-ts-tabelle immer (Verschiebe-Griff an th.ui-draggable-column), pt-tablesettings-breiten
     * bei Seiten mit Spaltenbreiten (Resize-Viereck, Mobil-Regel aus Karte 1077). Bis Runde 6
     * hing das an einer Schalterzeile, die seit Runde 5 im OverlayPanel ausserhalb des Wraps
     * liegt - gefunden wurde sie nie, die Griffe fehlten (Befund claude1/claude2, gemessen).
     * Den Modus liest das CSS direkt an der Tabelle (ui-datatable-resizable, ui-draggable-column).
     * Idempotent: nach jedem Teilupdate ist der Tabellenknoten neu, der Beobachter setzt nach.
     */
    function einrichten() {
        listenHoeheAnwenden();
        var rahmen = document.querySelectorAll('[data-pt-tablesettings]');
        for (var i = 0; i < rahmen.length; i++) {
            var tabelle = document.getElementById(rahmen[i].getAttribute('data-pt-tablesettings'));
            if (!tabelle) {
                continue;   // Tabelle (noch) nicht im DOM, z.B. ein Reiter, der zu ist.
            }
            tabelle.classList.add('pt-ts-tabelle');
            if (rahmen[i].getAttribute('data-pt-breiten') === '1') {
                tabelle.classList.add('pt-tablesettings-breiten');
            }
        }
    }

    /*
     * Modus-Knopf: ein Klick wechselt den Modus ueber p:remoteCommand <settingsId>ModusWechseln;
     * die settingsId steht in der Id des Knopfs (<settingsId>-modus). Delegation am document:
     * der Knopf wird bei jedem Wechsel neu gerendert. Der Doppelklick (fester Stand) ist mit
     * Karte 2333 weggefallen, damit auch die Wartezeit auf einen zweiten Klick.
     */
    document.addEventListener('click', function (e) {
        var knopf = e.target && e.target.closest ? e.target.closest('.pt-ts-modus') : null;
        if (!knopf || !knopf.id) {
            return;
        }
        e.preventDefault();
        aufrufen(knopf.id.split(':').pop().replace(/-modus$/, '') + 'ModusWechseln');
    });

    /*
     * Hoehe der Spaltenliste (Karte 2333): die Liste laesst sich per CSS-resize von Hand ziehen.
     * Der Rahmen (.pt-ts-listenrahmen) traegt die gespeicherte Hoehe in data-pt-listenhoehe und
     * den Namen des remoteCommand in data-pt-remote. Nach jedem Teilupdate ist die Liste neu,
     * einrichten() setzt die Hoehe wieder. Der Browser schreibt beim Ziehen style.height an die
     * Liste; weicht sie beim Loslassen vom gemerkten Wert ab, wird sie gemeldet.
     */
    function listenHoeheAnwenden() {
        var rahmen = document.querySelectorAll('.pt-ts-listenrahmen[data-pt-listenhoehe]');
        for (var i = 0; i < rahmen.length; i++) {
            var liste = rahmen[i].querySelector('.ui-orderlist-list');
            var hoehe = rahmen[i].getAttribute('data-pt-listenhoehe');
            if (liste && hoehe && !liste.style.height) {
                liste.style.height = hoehe + 'px';
            }
        }
    }

    document.addEventListener('mouseup', function (e) {
        var rahmen = e.target && e.target.closest ? e.target.closest('.pt-ts-listenrahmen') : null;
        var liste = rahmen ? rahmen.querySelector('.ui-orderlist-list') : null;
        if (!liste || !liste.style.height) {
            return;
        }
        var hoehe = String(Math.round(liste.offsetHeight));
        if (hoehe !== rahmen.getAttribute('data-pt-listenhoehe')) {
            rahmen.setAttribute('data-pt-listenhoehe', hoehe);
            var melde = window[rahmen.getAttribute('data-pt-remote')];
            if (typeof melde === 'function') {
                melde([{name: 'hoehe', value: hoehe}]);
            }
        }
    });

    /*
     * Karte 2160 (Daniel 15.11.): Doppelklick auf den Verbreitern-Griff (↔-Viereck im
     * Spaltenkopf) oeffnet das Breite-Feld fuer genau diese Spalte (remoteCommand <id>BreiteFix
     * mit der Kopf-Position als Parameter 'kopf' — die Seiten vergeben den Spalten keine Ids).
     * Das Panel oeffnet sich am Griff, das Eingabefeld im Ansicht-Reiter wird nach dem
     * Ajax-Update fokussiert (oncomplete des remoteCommand). Annahme wie beim colReorder: EINE
     * Kopfzeile ohne verbundene Zellen; nur Tabellen eines Bedienbereichs mit Breiten tragen
     * den Griff (resizableColumns = anzeige.groesseAendernAktiv).
     *
     * DER FANGE: PrimeFaces 16 haengt dem Griff SEINEN eigenen dblclick-Handler an —
     * «Spalte automatisch auf den Inhalt einpassen» (autosizeColumnWidth), gemessen als
     * verkuemmerte colResize-Breite nach Zug + Doppelklick (183 -> 89/84). Der Griff-Handler
     * laeuft in der Target-Phase, ein document-Listener in der Bubble-Phase kaeme zu spaet —
     * deshalb CAPTURE: unser Listener verbraucht den Doppelklick, bevor PrimeFaces ihn sieht,
     * und oeffnet stattdessen das Feld fuer eine exakte Breite.
     */
    document.addEventListener('dblclick', function (e) {
        var griff = e.target && e.target.closest ? e.target.closest('.ui-column-resizer') : null;
        if (!griff) {
            return;
        }
        var th = griff.closest('th');
        var tabelleEl = griff.closest('.ui-datatable');
        var zeile = th ? th.parentNode : null;
        if (!zeile || !tabelleEl || !tabelleEl.id) {
            return;
        }
        var wrap = document.querySelector('[data-pt-tablesettings="' + tabelleEl.id + '"]');
        if (!wrap || wrap.getAttribute('data-pt-breiten') !== '1') {
            return;
        }
        var index = Array.prototype.indexOf.call(zeile.children, th);
        if (index < 0) {
            return;
        }
        e.preventDefault();
        e.stopPropagation();   // PrimeFaces' Autosize am Griff (siehe oben) nicht ausloesen
        var settingsId = wrap.getAttribute('data-pt-settings-id');
        if (typeof window[settingsId + 'BreiteFix'] === 'function') {
            window[settingsId + 'BreiteFix']([{name: 'kopf', value: String(index)}]);
        }
        // Karte 2333: das Panel geht nur auf, solange das Feld leer ist (der Doppelklick fuellt
        // es dann), und immer am Regler-Symbol, nie am Griff der Spalte.
        var panel = window.PF ? PF(settingsId + 'PanelWv') : null;
        var feld = panel && panel.jq && panel.jq[0] ? panel.jq[0].querySelector('.pt-ts-breite-fix-wert') : null;
        var oeffner = wrap.querySelector('.pt-ts-oeffner');
        if (panel && oeffner && !(feld && feld.value) && typeof panel.show === 'function'
                && !(typeof panel.isVisible === 'function' && panel.isVisible())) {
            panel.show(oeffner.id);
        }
    }, true);

    function aufrufen(name) {
        if (typeof window[name] === 'function') {
            window[name]();
        }
    }

    function sicherEinrichten() {
        try {
            einrichten();
        } catch (e) {
            // Der Beobachter laeuft bei jeder DOM-Aenderung; eine Ausnahme hier schluege sonst
            // mitten in ein PrimeFaces-Teilupdate hinein.
            if (window.console) {
                console.warn('[pt:tableSettings]', e);
            }
        }
    }

    /*
     * Karte 1853/1904: Schalter je Spalte im Spalten-Panel. Die Checkbox traegt data-spalte und
     * data-pt-remote (Name des p:remoteCommand). EIN Listener am document erreicht auch die
     * Schalter, die jedes Teilupdate neu rendert.
     */
    document.addEventListener('change', function (e) {
        var schalter = e.target;
        if (!schalter || !schalter.classList || !schalter.classList.contains('pt-ts-schalter-sichtbar')) {
            return;
        }
        var melde = window[schalter.getAttribute('data-pt-remote')];
        if (typeof melde === 'function') {
            melde([{name: 'spalte', value: schalter.getAttribute('data-spalte')},
                {name: 'sichtbar', value: String(schalter.checked)}]);
        }
    });

    /*
     * Karte 1853 (claude2-Befund): Panel-Oeffner. EIN Listener am document statt PF-AutoBinding
     * am Element: der colReorder-Re-Render des Wraps ersetzt den Oeffner, PF('...PanelWv').target
     * zeigt danach auf das alte Element (Hoehe 0, oeffnet nicht). Die Delegation ueberlebt
     * Teil-Updates und ist CSP-konform (kein on...-Attribut).
     */
    document.addEventListener('click', function (e) {
        var oeffner = e.target && e.target.closest ? e.target.closest('.pt-ts-oeffner') : null;
        if (!oeffner) {
            return;
        }
        var wrap = oeffner.closest('[data-pt-settings-id]');
        if (!wrap || !window.PF) {
            return;
        }
        var panel = PF(wrap.getAttribute('data-pt-settings-id') + 'PanelWv');
        if (panel) {
            e.preventDefault();
            if (typeof panel.isVisible === 'function' && panel.isVisible()) {
                panel.hide();
            } else if (typeof panel.show === 'function') {
                panel.show(oeffner.id);
                var panelEl = panel.jq ? panel.jq[0] : null;
                if (panelEl && window.requestAnimationFrame) {
                    requestAnimationFrame(function () {
                        panelEl.style.setProperty('--pt-ts-panel-oben',
                            Math.round(panelEl.getBoundingClientRect().top) + 'px');
                    });
                }
            }
        }
    });

    /* Karte 1853 (claude2, gemessen an 1280x720): PF 16 schliesst ein OverlayPanel bei
       JEDEM Fenster-Scroll/Resize (handleViewportChange). Das Panel der Tabelleinstellungen
       soll nur per Toggle (Oeffner) oder dismissable (Klick draussen) schliessen — ragt der
       Inhalt, scrollt der eigene Container (max-height), nicht die Seite. PF bindet den
       Handler beim Widget-Init an window, ein Eigenschafts-Override am Objekt greift nicht:
       Deshalb der Prototyp, nur fuer .pt-ts-panel. Laeuft in sicherEinrichten (nach PF). */
    function patchViewportSchliessen() {
        var proto = PrimeFaces.widget.OverlayPanel && PrimeFaces.widget.OverlayPanel.prototype;
        if (proto && !proto.__ptTsKeinViewportSchliessen) {
            proto.__ptTsKeinViewportSchliessen = true;
            var orig = proto.handleViewportChange;
            proto.handleViewportChange = function () {
                if (this.jq && this.jq[0]
                        && this.jq[0].classList.contains('pt-ts-panel')) {
                    return;
                }
                if (typeof orig === 'function') {
                    return orig.apply(this, arguments);
                }
            };
        }
    }

    function einrichtenMitPatch() {
        patchViewportSchliessen();
        sicherEinrichten();
    }
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', einrichtenMitPatch);
    } else {
        einrichtenMitPatch();
    }
    new MutationObserver(einrichtenMitPatch).observe(document.body, {childList: true, subtree: true});
})();
