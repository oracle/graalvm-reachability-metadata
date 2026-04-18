/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.util.List;

import org.apache.seata.sqlparser.SQLRecognizer;
import org.apache.seata.sqlparser.SQLType;
import org.apache.seata.sqlparser.druid.DruidDelegatingSQLRecognizerFactory;
import org.apache.seata.sqlparser.util.JdbcConstants;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DruidDelegatingSQLRecognizerFactoryTest {
    @Test
    void createLoadsTheIsolatedRecognizerFactoryImplementation() {
        Assumptions.assumeFalse(isNativeImageRuntime());
        DruidDelegatingSQLRecognizerFactory factory = new DruidDelegatingSQLRecognizerFactory();

        List<SQLRecognizer> recognizers = factory.create(
                "delete from account where id = 1",
                JdbcConstants.MYSQL);

        assertThat(recognizers).hasSize(1);
        assertThat(recognizers.get(0).getSQLType()).isEqualTo(SQLType.DELETE);
        assertThat(recognizers.get(0).getTableName()).isEqualTo("account");
        assertThat(recognizers.get(0).getOriginalSQL()).isEqualTo("delete from account where id = 1");
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
