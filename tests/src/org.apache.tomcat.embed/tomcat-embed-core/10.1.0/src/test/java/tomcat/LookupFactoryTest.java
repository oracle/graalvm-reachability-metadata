/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;

import org.apache.naming.LookupRef;
import org.apache.naming.factory.LookupFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LookupFactoryTest {
    @Test
    void resolvesTypedLookupUsingConfiguredFactory() throws Exception {
        LookupRef reference = new LookupRef(String.class.getName(), null);
        reference.add(new StringRefAddr("factory", GreetingFactory.class.getName()));
        assertEquals("hello", new LookupFactory().getObjectInstance(reference, null, null, null));
    }

    public static class GreetingFactory implements ObjectFactory {
        @Override
        public Object getObjectInstance(Object object, Name name, Context context, Hashtable<?, ?> environment) {
            return "hello";
        }
    }
}
