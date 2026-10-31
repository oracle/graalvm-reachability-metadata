/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.naming.StringRefAddr;

import org.apache.naming.NamingContext;
import org.apache.naming.ResourceRef;
import org.apache.naming.factory.BeanFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class NamingContextTest {
    @Test
    void resolvesSingletonResourceReferenceDuringLookup() throws Exception {
        NamingContext context = new NamingContext(null, "application");
        try {
            ResourceRef reference = new ResourceRef(BeanFactoryTest.Service.class.getName(), null, null, null,
                    true, BeanFactory.class.getName(), null);
            reference.add(new StringRefAddr("name", "inventory"));
            context.bind("service", reference);
            BeanFactoryTest.Service service = (BeanFactoryTest.Service) context.lookup("service");
            assertEquals("inventory", service.getName());
            assertSame(service, context.lookup("service"));
        } finally {
            context.close();
        }
    }
}
