/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSONPath;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JSONPathTest {
    @Test
    void arrayAddAppendsValuesToArrayProperty() {
        Map<String, Object> root = new HashMap<>();
        root.put("names", new String[] { "fast", "json" });

        JSONPath.arrayAdd(root, "$.names", "native", "image");

        assertThat((String[]) root.get("names")).containsExactly("fast", "json", "native", "image");
    }

    @Test
    void patchAddAppendsValueToArrayProperty() {
        Map<String, Object> root = new HashMap<>();
        root.put("names", new String[] { "fast", "json" });

        JSONPath.compile("$.names").patchAdd(root, "native", false);

        assertThat((String[]) root.get("names")).containsExactly("fast", "json", "native");
    }
}
