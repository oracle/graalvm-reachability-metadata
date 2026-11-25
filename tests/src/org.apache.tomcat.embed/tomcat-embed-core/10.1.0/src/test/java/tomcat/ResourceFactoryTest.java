/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.sql.Connection;
import javax.sql.DataSource;

import org.apache.naming.ResourceRef;
import org.apache.naming.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceFactoryTest {
    @Test
    void selectsConfiguredDefaultDataSourceFactory() throws Exception {
        String property = "javax.sql.DataSource.Factory";
        String previous = System.getProperty(property);
        System.setProperty(property, DataSourceSupport.Factory.class.getName());
        try {
            ResourceRef reference = new ResourceRef(DataSource.class.getName(), null, null, null, true);
            DataSource source = (DataSource) new ResourceFactory().getObjectInstance(reference, null, null, null);
            try (Connection connection = source.getConnection()) {
                assertThat(connection.isValid(10)).isTrue();
                assertThat(connection.getMetaData().getUserName()).isEqualTo("READER");
            }
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }
}
