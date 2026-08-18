/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xerces.xercesImpl;

import org.apache.xerces.impl.dv.SchemaDVFactory;
import org.apache.xerces.util.ObjectFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ObjectFactoryTest {
    private static final String SCHEMA_DV_FACTORY_IMPLEMENTATION =
            "org.apache.xerces.impl.dv.xs.SchemaDVFactoryImpl";

    @Test
    void createsConfiguredFactoriesWithNullAndApplicationClassLoaders() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            ClassLoader libraryClassLoader = ObjectFactory.findClassLoader();

            Assertions.assertThat(libraryClassLoader).isNotNull();
            assertSchemaFactory(ObjectFactory.newInstance(
                    SCHEMA_DV_FACTORY_IMPLEMENTATION, null, false));
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertSchemaFactory(ObjectFactory.newInstance(
                SCHEMA_DV_FACTORY_IMPLEMENTATION, ObjectFactoryTest.class.getClassLoader(), false));
    }

    private void assertSchemaFactory(Object factory) {
        Assertions.assertThat(factory).isInstanceOf(SchemaDVFactory.class);
        Assertions.assertThat(((SchemaDVFactory) factory).getBuiltInType("string")).isNotNull();
    }
}
