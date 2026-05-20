/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.naming.StringRefAddr;

import org.apache.catalina.connector.Connector;
import org.apache.naming.ResourceRef;
import org.apache.naming.factory.BeanFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanFactoryTest {

    @Test
    void createsBeanAndAppliesStringProperties() throws Exception {
        ResourceRef reference = new ResourceRef(Connector.class.getName(), null, null, null, true);
        reference.add(new StringRefAddr("port", "0"));
        reference.add(new StringRefAddr("allowTrace", "true"));

        Object bean = new BeanFactory().getObjectInstance(reference, null, null, null);

        assertThat(bean).isInstanceOf(Connector.class);
        Connector connector = (Connector) bean;
        assertThat(connector.getPort()).isZero();
        assertThat(connector.getAllowTrace()).isTrue();
    }
}
