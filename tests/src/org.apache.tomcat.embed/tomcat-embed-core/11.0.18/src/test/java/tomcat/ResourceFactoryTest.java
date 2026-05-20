/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.naming.NamingException;

import org.apache.naming.ResourceRef;
import org.apache.naming.factory.BeanFactory;
import org.apache.naming.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ResourceFactoryTest {

    @Test
    void createsConfiguredDefaultDataSourceFactory() throws Exception {
        String previous = System.getProperty("javax.sql.DataSource.Factory");
        System.setProperty("javax.sql.DataSource.Factory", BeanFactory.class.getName());
        try {
            ResourceRef reference = new ResourceRef("javax.sql.DataSource", null, null, null, true);

            assertThatThrownBy(() -> new ResourceFactory().getObjectInstance(reference, null, null, null))
                    .isInstanceOf(NamingException.class);
        } finally {
            if (previous == null) {
                System.clearProperty("javax.sql.DataSource.Factory");
            } else {
                System.setProperty("javax.sql.DataSource.Factory", previous);
            }
        }
    }
}
