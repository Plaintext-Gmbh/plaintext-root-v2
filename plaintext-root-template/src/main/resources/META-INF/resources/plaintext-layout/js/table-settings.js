/*
 * Copyright (C) plaintext.ch, 2026.
 *
 * Verhalten des Bedienbereichs einer Tabelle (META-INF/tags/tableSettings.xhtml): Tabellen fuer
 * die Kopf-Griffe markieren, Modus-Knopf (Klick/Doppelklick), Panel-Oeffner, Spaltenschalter.
 * Runde 7 (Karte 1853): der Klick auf das Viereck im Kopf (Spalte auf die Zielbreite setzen,
 * Karte 1336) ist mit dem Reiter «Breiten» weggefallen - die Wiederholbarkeit, fuer die er da
 * war, liefert jetzt der feste Stand (Ansicht festhalten, Doppelklick auf den Modus-Knopf).
 * Karte 2160: Gesamt-/Zielbreite samt Java entfernt; der Doppelklick auf den Verbreitern-Griff
 * bedient das Feld «Breite fix» (remoteCommand BreiteFix). Karte 2333 (Daniel 17.11.): der feste
 * Stand ist weg (Klick = Moduswechsel, kein Doppelklick mehr), das Feld ist ein Sitzungswert nur
 * im Modus Resize — leer: Doppelklick uebernimmt die Spaltenbreite und das Panel oeffnet sich am
 * Oeffner; gefuellt: Doppelklick setzt die Spalte auf den Wert. Dazu meldet das Skript die am
 * Griff gezogene Hoehe der Spaltenliste (remoteCommand ListeHoehe).
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
        // Karte 2333: Ausgangshoehe jeder Spaltenliste merken, damit der mouseup unten nur echte
        // Zuege am resize-Griff meldet (nach jedem Teilupdate ist die Liste ein neuer Knoten).
        var listen = document.querySelectorAll('.pt-ts-reihenfolge .ui-orderlist-list');
        for (var j = 0; j < listen.length; j++) {
            if (listenHoehe(listen[j]) && !listen[j].getAttribute('data-pt-hoehe')) {
                listen[j].setAttribute('data-pt-hoehe', String(listenHoehe(listen[j])));
            }
        }
    }

    /* CSS-Hoehe (content-box, ohne Rand) - genau das, was das Tag spaeter als height rendert;
       offsetHeight traegt den Rand mit und wuechse bei jedem Speichern um 2 px. 0 im zuen Reiter. */
    function listenHoehe(el) {
        return Math.round(parseFloat(getComputedStyle(el).height)) || 0;
    }

    /* Karte 2333: Hoehe der Spaltenliste nach einem Zug am resize-Griff speichern. Die Liste
       liegt im TabView (NamingContainer) - die settingsId steckt am Ende ihrer Id
       (<settingsId>-reihenfolge), wie beim Modus-Knopf. */
    document.addEventListener('mouseup', function () {
        var listen = document.querySelectorAll('.pt-ts-reihenfolge .ui-orderlist-list');
        for (var i = 0; i < listen.length; i++) {
            var hoehe = listenHoehe(listen[i]);
            var alt = listen[i].getAttribute('data-pt-hoehe');
            if (!hoehe || String(hoehe) === alt) {
                continue;   // Reiter zu (0) oder nichts gezogen
            }
            listen[i].setAttribute('data-pt-hoehe', String(hoehe));
            if (!alt) {
                continue;   // erstes Sichtbarwerden (Reiterwechsel), kein Zug
            }
            var orderList = listen[i].closest('.pt-ts-reihenfolge');
            if (!orderList || !orderList.id) {
                continue;
            }
            var settingsId = orderList.id.split(':').pop().replace(/-reihenfolge$/, '');
            var melde = window[settingsId + 'ListeHoehe'];
            if (typeof melde === 'function') {
                melde([{name: 'hoehe', value: String(hoehe)}]);
            }
        }
    });

    /*
     * Modus-Knopf: Klick wechselt den Modus ueber p:remoteCommand <settingsId>ModusWechseln; die
     * settingsId steht in der Id des Knopfs (<settingsId>-modus). Delegation am document: der
     * Knopf wird bei jedem Wechsel neu gerendert. (Der Doppelklick fuer den festen Stand ist mit
     * Karte 2333 weg - der Klick wartet auf nichts mehr.)
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
     * Karte 2160/2333: Doppelklick auf den Verbreitern-Griff (↔-Viereck im Spaltenkopf, nur im
     * Modus Resize). Das Skript meldet die Kopf-Position ('kopf' - die Seiten vergeben den Spalten
     * keine Ids) und die gerenderte Breite ('breite') an den remoteCommand <id>BreiteFix. Ist das
     * Feld «Breite fix» leer, uebernimmt der Server die Breite ins Feld und das Panel oeffnet sich
     * am Oeffner (nie am Griff, Daniel 17.11.); sonst stempelt der Server den Wert auf die Spalte.
     * Ob das Feld leer war, weiss der Browser schon vor dem Aufruf. Die von PF erzeugte
     * remoteCommand-Funktion nimmt KEIN zweites Argument (kein oncomplete, nur Behaviors haben
     * «ext») - Reiter und Fokus kommen deshalb ueber das globale pfAjaxComplete, sobald das neu
     * gerenderte Feld seinen Wert traegt.
     *
     * DER FANGE: PrimeFaces 16 haengt dem Griff SEINEN eigenen dblclick-Handler an —
     * «Spalte automatisch auf den Inhalt einpassen» (autosizeColumnWidth), gemessen als
     * verkuemmerte colResize-Breite nach Zug + Doppelklick (183 -> 89/84). Der Griff-Handler
     * laeuft in der Target-Phase, ein document-Listener in der Bubble-Phase kaeme zu spaet —
     * deshalb CAPTURE: unser Listener verbraucht den Doppelklick, bevor PrimeFaces ihn sieht.
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
        var feld = breiteFeld(settingsId);
        var leer = !feld || !feld.value.trim();
        // Inhaltsbreite wie die gespeicherten Werte (content-box im Theme), nicht offsetWidth.
        var breite = Math.round(parseFloat(getComputedStyle(th).width)) || 0;
        if (typeof window[settingsId + 'BreiteFix'] !== 'function') {
            return;
        }
        if (leer) {
            panelOeffnen(settingsId);
            nachAjax(function () {
                var neu = breiteFeld(settingsId);
                if (!neu || !neu.value.trim()) {
                    return false;
                }
                breiteFeldZeigen(settingsId);
                return true;
            });
        }
        window[settingsId + 'BreiteFix'](
            [{name: 'kopf', value: String(index)}, {name: 'breite', value: String(breite)}]);
    }, true);

    /* Ruft pruefen() nach jedem abgeschlossenen PF-Ajax, bis es true liefert (hoechstens drei
       Antworten: ein noch laufender Zug-Ajax kommt vor unserem). jQuery ist mit PF da. */
    function nachAjax(pruefen) {
        var rest = 3;
        var handler = function () {
            if (pruefen() || --rest <= 0) {
                $(document).off('pfAjaxComplete', handler);
            }
        };
        $(document).on('pfAjaxComplete', handler);
    }

    /** Das Eingabefeld «Breite fix» des Bedienbereichs, null solange es nicht gerendert ist. */
    function breiteFeld(settingsId) {
        var panel = window.PF ? PF(settingsId + 'PanelWv') : null;
        var panelEl = panel && panel.jq ? panel.jq[0] : null;
        return panelEl ? panelEl.querySelector('.pt-ts-breite-fix-wert') : null;
    }

    /** 1. Reiter (Ansicht) und Fokus ins Feld «Breite fix» - nach dem Ajax-Update. */
    function breiteFeldZeigen(settingsId) {
        if (window.PF) {
            var tabs = PF(settingsId + 'TabsWv');
            if (tabs && tabs.select) {
                tabs.select(0);
            }
        }
        var feld = breiteFeld(settingsId);
        if (feld) {
            feld.focus();
            feld.select();
        }
    }

    /*
     * Karte 2333: das Panel am Oeffner (Regler-Symbol ueber der Tabelle) zeigen, falls es zu ist -
     * fuer den Doppelklick oben und fuer «Neues Profil» (der Klick in den Dialog klappt das
     * dismissable Panel zu; das Tag ruft nach dem Anlegen ptTableSettings.panelOeffnen).
     */
    function panelOeffnen(settingsId) {
        var wrap = document.querySelector('[data-pt-settings-id="' + settingsId + '"]');
        var oeffner = wrap ? wrap.querySelector('.pt-ts-oeffner') : null;
        var panel = window.PF ? PF(settingsId + 'PanelWv') : null;
        if (!oeffner || !panel || typeof panel.show !== 'function') {
            return;
        }
        if (typeof panel.isVisible === 'function' && panel.isVisible()) {
            return;
        }
        panelZeigen(panel, oeffner);
    }

    function panelZeigen(panel, oeffner) {
        panel.show(oeffner.id);
        var panelEl = panel.jq ? panel.jq[0] : null;
        if (panelEl && window.requestAnimationFrame) {
            requestAnimationFrame(function () {
                panelEl.style.setProperty('--pt-ts-panel-oben',
                    Math.round(panelEl.getBoundingClientRect().top) + 'px');
            });
        }
    }

    window.ptTableSettings = {panelOeffnen: panelOeffnen};

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
                panelZeigen(panel, oeffner);
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
