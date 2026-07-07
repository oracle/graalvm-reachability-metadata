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
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.apache.naming.ResourceRef;
import org.apache.naming.factory.ResourceFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceFactoryTest implements ObjectFactory {

    @ParameterizedTest
    @CsvSource({
            "javax.sql.DataSource, javax.sql.DataSource.Factory",
            "jakarta.mail.Session, jakarta.mail.Session.Factory"
    })
    void createsDefaultResourceFactory(String resourceClassName, String factoryPropertyName) throws Exception {
        String previousFactoryClassName = System.getProperty(factoryPropertyName);
        System.setProperty(factoryPropertyName, ResourceFactoryTest.class.getName());
        try {
            ResourceRef reference = new ResourceRef(resourceClassName, null, null, null, true);

            Object resource = new ResourceFactory().getObjectInstance(reference, null, null, null);

            assertThat(resource).isEqualTo("created " + resourceClassName);
        } finally {
            restoreProperty(factoryPropertyName, previousFactoryClassName);
        }
    }

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) {
        assertThat(obj).isInstanceOf(Reference.class);
        return "created " + ((Reference) obj).getClassName();
    }

    private static void restoreProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }
}
