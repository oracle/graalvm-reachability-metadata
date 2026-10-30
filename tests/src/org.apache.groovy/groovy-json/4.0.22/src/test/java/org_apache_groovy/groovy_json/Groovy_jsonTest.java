/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_json;

import groovy.json.JsonSlurper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Groovy_jsonTest {
    @Test
    void parsesNestedValuesAndEscapedStrings() {
        String json = """
                [{"label":"line\\n\\\"quoted\\\"","values":[3,true]},
                 {"label":"\\u20ac","values":[]}]
                """;
        List<Map<String, Object>> expected = List.of(
                Map.of("label", "line\n\"quoted\"", "values", List.of(3, true)),
                Map.of("label", "\u20ac", "values", List.of()));

        Object parsed = new JsonSlurper().parseText(json);

        assertEquals(expected, parsed);
    }
}
