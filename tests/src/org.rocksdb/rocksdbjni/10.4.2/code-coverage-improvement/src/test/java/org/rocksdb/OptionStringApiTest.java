/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.rocksdb;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OptionStringApiTest {

    @Test
    void parserReportsMalformedPublicInput() {
        assertThatThrownBy(() -> OptionString.Parser.parse("write_buffer_size"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Expected = separating key and value");
    }

    @Test
    void nestedPublicValuesRenderTheirEntries() {
        Object entry = OptionString.Parser.parse("outer={inner=value}").get(0);
        assertThat(entry.toString()).contains("outer").contains("inner");
    }
}
