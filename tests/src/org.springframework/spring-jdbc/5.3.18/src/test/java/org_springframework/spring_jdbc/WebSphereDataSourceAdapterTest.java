/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_jdbc;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.WebSphereDataSourceAdapter;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class WebSphereDataSourceAdapterTest {
    @Test
    void rejectsTargetThatDoesNotImplementWebSphereDataSource() {
        DataSource target = JdbcTestSupport.dataSource();
        WebSphereDataSourceAdapter adapter = new WebSphereDataSourceAdapter();
        adapter.setTargetDataSource(target);

        assertThatIllegalStateException().isThrownBy(adapter::afterPropertiesSet)
                .withMessage("Specified 'targetDataSource' is not a WebSphere WSDataSource: " + target);
    }
}
