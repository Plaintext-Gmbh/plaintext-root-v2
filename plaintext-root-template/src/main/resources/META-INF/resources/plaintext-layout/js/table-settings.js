/*
 * Copyright (C) plaintext.ch, 2026.
 *
 * Verhalten des Bedienbereichs einer Tabelle (META-INF/tags/tableSettings.xhtml): Tabellen fuer
 * die Kopf-Griffe markieren, Modus-Knopf (Klick/Doppelklick), Panel-Oeffner, Spaltenschalter.
 * Runde 7 (Karte 1853): der Klick auf das Viereck im Kopf (Spalte auf die Zielbreite setzen,
 * Karte 1336) ist mit dem Reiter «Breiten» weggefallen - die Wiederholbarkeit, fuer die er da
 * war, liefert jetzt der feste Stand (Ansicht festhalten, Doppelklick auf den Modus-Knopf).
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
     * Modus-Knopf (Runde 7, Punkt 3): Klick wechselt den Modus, Doppelklick stellt den festen
     * Stand her. Der Klick wartet kurz, ob ein zweiter folgt - sonst wechselte ein Doppelklick
     * zuerst zweimal den Modus. Beide laufen ueber p:remoteCommand des Tags
     * (<settingsId>ModusWechseln / <settingsId>FesterStand); die settingsId steht in der Id des
     * Knopfs (<settingsId>-modus). Delegation am document: der Knopf wird bei jedem Wechsel neu
     * gerendert.
     */
    var modusWartet = null;
    document.addEventListener('click', function (e) {
        var knopf = e.target && e.target.closest ? e.target.closest('.pt-ts-modus') : null;
        if (!knopf || !knopf.id) {
            return;
        }
        e.preventDefault();
        var settingsId = knopf.id.split(':').pop().replace(/-modus$/, '');
        if (modusWartet) {
            clearTimeout(modusWartet);
            modusWartet = null;
            aufrufen(settingsId + 'FesterStand');
            return;
        }
        modusWartet = setTimeout(function () {
            modusWartet = null;
            aufrufen(settingsId + 'ModusWechseln');
        }, 300);
    });

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
