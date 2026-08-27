/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jayway_jsonpath.json_path;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PathFunctionFactoryTest {
    @Test
    void evaluatesLengthFunctionForJsonArray() {
        Integer bookCount = JsonPath.read(
                """
                        {"books":[{"title":"Native Image"},{"title":"Metadata"},{"title":"Testing"}]}
                        """,
                "$.books.length()");

        assertThat(bookCount).isEqualTo(3);
    }
}
