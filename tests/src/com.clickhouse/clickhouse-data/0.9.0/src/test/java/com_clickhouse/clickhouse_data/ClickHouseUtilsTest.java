/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_data;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.data.ClickHouseUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ClickHouseUtilsTest {
    @Test
    void createsPublicNoArgumentImplementationByClassName() {
        List<?> value = ClickHouseUtils.newInstance(ArrayList.class.getName(), List.class, ClickHouseUtilsTest.class);

        assertThat(value).isInstanceOf(ArrayList.class).isEmpty();
    }

    @Test
    void opensRelativeFileFromClasspathWhenItIsNotOnFileSystem() throws Exception {
        String resourceName = "com_clickhouse/clickhouse_data/clickhouse-utils-resource.txt";

        try (InputStream input = ClickHouseUtils.getFileInputStream(resourceName)) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(content).isEqualTo("loaded from test classpath\n");
        }
    }
}
