/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.seata.sqlparser.SQLRecognizer;
import org.apache.seata.sqlparser.SQLType;
import org.apache.seata.sqlparser.SQLUpdateRecognizer;
import org.apache.seata.sqlparser.druid.DruidDelegatingSQLRecognizerFactory;
import org.junit.jupiter.api.Test;

public class DruidDelegatingSQLRecognizerFactoryTest {
    @Test
    void loadsDruidRecognizerFactoryImplementationAndCreatesRecognizers() {
        String sql = "UPDATE account_tbl SET balance = 100 WHERE id = 1";
        DruidDelegatingSQLRecognizerFactory factory = new DruidDelegatingSQLRecognizerFactory();

        List<SQLRecognizer> recognizers = factory.create(sql, "mysql");

        assertThat(recognizers).hasSize(1);
        SQLRecognizer recognizer = recognizers.get(0);
        assertThat(recognizer.getSQLType()).isEqualTo(SQLType.UPDATE);
        assertThat(recognizer.getTableName()).isEqualTo("account_tbl");
        assertThat(recognizer.getOriginalSQL()).isEqualTo(sql);
        assertThat(recognizer).isInstanceOf(SQLUpdateRecognizer.class);
        assertThat(((SQLUpdateRecognizer) recognizer).getUpdateColumns()).containsExactly("balance");
    }
}
