/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.naming.StringRefAddr;

import org.apache.naming.ResourceRef;
import org.apache.naming.factory.BeanFactory;
import org.apache.naming.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FactoryBaseTest {
    @Test
    void delegatesResourceCreationToTheConfiguredObjectFactory() throws Exception {
        ResourceRef reference = new ResourceRef(BeanFactoryTest.Service.class.getName(), null, null, null, false);
        reference.add(new StringRefAddr("factory", BeanFactory.class.getName()));
        reference.add(new StringRefAddr("name", "billing"));
        BeanFactoryTest.Service resource = (BeanFactoryTest.Service)
                new ResourceFactory().getObjectInstance(reference, null, null, null);
        assertEquals("billing", resource.getName());
    }
}
