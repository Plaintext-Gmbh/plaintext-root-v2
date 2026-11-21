/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ch.plaintext.boot;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Karte 2411: Mojarra behaelt je Namensraum nur die ERSTE Taglib aus META-INF/*.taglib.xml (es fuehrt nicht
 * zusammen). Liegen zwei Taglibs mit demselben Namensraum im Klassenpfad der Huelle, verdeckt die erste die
 * Tags der zweiten - so verschwanden pt:dateiAblage, pt:freigabeLinks usw. hinter dem Taglib aus
 * plaintext-root-tablesettings (500 auf ablage-ausprobieren). Der Klassenpfad dieses Tests ist der der Huelle.
 */
class TaglibNamensraumEindeutigTest {

    private static final Pattern NAMENSRAUM = Pattern.compile("<namespace>\\s*([^<\\s]+)\\s*</namespace>");

    @Test
    void jederNamensraumHatGenauEineTaglib() throws IOException {
        Map<String, List<String>> jeNamensraum = new TreeMap<>();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:META-INF/*.taglib.xml")) {
            Matcher m = NAMENSRAUM.matcher(r.getContentAsString(StandardCharsets.UTF_8));
            if (m.find()) {
                jeNamensraum.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(r.getURL().toString());
            }
        }
        assertThat(jeNamensraum).as("pt:-Taglib des Templates gefunden").containsKey("http://plaintext.ch/ui");
        jeNamensraum.forEach((ns, quellen) ->
                assertThat(quellen).as("Namensraum %s mehrfach (Mojarra nimmt nur die erste)", ns).hasSize(1));
    }
}
