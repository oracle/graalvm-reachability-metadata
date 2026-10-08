/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jayway_jsonpath.json_path;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PathFunctionFactoryTest {
    @Test
    void evaluatesNumericPathFunctionsThroughThePublicApi() {
        String json = """
                {
                  "numbers": [2, 4, 6]
                }
                """;
        DocumentContext document = JsonPath.parse(json);

        Double average = document.read("$.numbers.avg()");
        Double sum = document.read("$.numbers.sum()");

        assertThat(average).isEqualTo(4.0d);
        assertThat(sum).isEqualTo(12.0d);
    }
}
