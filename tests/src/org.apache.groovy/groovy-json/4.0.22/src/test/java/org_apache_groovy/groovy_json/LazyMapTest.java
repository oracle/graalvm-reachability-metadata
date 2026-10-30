/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_json;

import groovy.json.JsonParserType;
import groovy.json.JsonSlurper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LazyMapTest {
    @Test
    void parsesObjectPreservingEntriesAcrossRepeatedGrowth() {
        Map<String, Object> expected = new LinkedHashMap<>();
        StringJoiner json = new StringJoiner(",", "{", "}");
        for (int index = 0; index < 24; index++) {
            String key = "reading" + index;
            int value = index * 10;
            expected.put(key, value);
            json.add("\"" + key + "\":" + value);
        }

        Object parsed = new JsonSlurper()
                .setType(JsonParserType.CHAR_BUFFER)
                .parseText(json.toString());

        assertEquals(expected, parsed);
    }
}
