/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.seata.sqlparser.druid.DruidDelegatingDbTypeParser;
import org.junit.jupiter.api.Test;

public class DruidDelegatingDbTypeParserTest {
    @Test
    void loadsDruidParserImplementationAndParsesJdbcUrl() {
        DruidDelegatingDbTypeParser parser = new DruidDelegatingDbTypeParser();

        String dbType = parser.parseFromJdbcUrl("jdbc:mysql://localhost:3306/seata");

        assertThat(dbType).isEqualTo("mysql");
    }
}
