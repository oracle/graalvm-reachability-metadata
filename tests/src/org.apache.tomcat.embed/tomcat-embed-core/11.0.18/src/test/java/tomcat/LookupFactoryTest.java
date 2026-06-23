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
import javax.naming.RefAddr;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;

import org.apache.naming.LookupRef;
import org.apache.naming.factory.Constants;
import org.apache.naming.factory.LookupFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LookupFactoryTest {

    @Test
    void createsObjectWithFactoryLoadedFromContextClassLoader() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(LookupFactoryTest.class.getClassLoader());
        try {
            LookupTarget target = createTarget();

            assertThat(target.value()).isEqualTo("created-by-lookup-factory");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void createsObjectWithFactoryLoadedByClassForNameWhenContextClassLoaderIsUnavailable() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            LookupTarget target = createTarget();

            assertThat(target.value()).isEqualTo("created-by-lookup-factory");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static LookupTarget createTarget() throws Exception {
        LookupRef reference = new LookupRef(LookupTarget.class.getName(), null);
        reference.add(new StringRefAddr(Constants.FACTORY, LookupObjectFactory.class.getName()));

        Object instance = new LookupFactory().getObjectInstance(reference, null, null, null);

        assertThat(instance).isInstanceOf(LookupTarget.class);
        return (LookupTarget) instance;
    }

    public record LookupTarget(String value) {
    }

    public static class LookupObjectFactory implements ObjectFactory {

        @Override
        public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) {
            RefAddr lookupName = ((LookupRef) obj).get(LookupRef.LOOKUP_NAME);
            assertThat(lookupName).isNull();
            return new LookupTarget("created-by-lookup-factory");
        }
    }
}
