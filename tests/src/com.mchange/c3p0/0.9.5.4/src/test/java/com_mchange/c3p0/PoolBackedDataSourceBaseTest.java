/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.PoolBackedDataSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PoolBackedDataSourceBaseTest {
    @Test
    void exposesConfiguredPoolBackedProperties() throws Exception {
        PoolBackedDataSource dataSource = C3p0TestSupport.newPoolBackedDataSource("pool-base", false, 0);
        Map<String, String> extensions = new HashMap<>();
        extensions.put("role", "test");
        dataSource.setExtensions(extensions);
        dataSource.setFactoryClassLocation("factory-location");
        dataSource.setNumHelperThreads(2);

        try {
            assertThat(dataSource.getDataSourceName()).isEqualTo("pool-base");
            assertThat(dataSource.getExtensions()).containsEntry("role", "test");
            assertThat(dataSource.getNumHelperThreads()).isEqualTo(2);
        } finally {
            dataSource.close();
        }
    }
}
