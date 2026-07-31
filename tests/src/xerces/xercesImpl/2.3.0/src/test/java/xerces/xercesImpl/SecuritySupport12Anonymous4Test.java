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

public class SecuritySupport12Anonymous4Test {
    private static final String FACTORY_ID =
            "xerces.xercesImpl.SecuritySupport12Anonymous4Test.factory";
    private static final String FALLBACK_FACTORY =
            "org.apache.xerces.impl.dv.xs.SchemaDVFactoryImpl";

    @Test
    void locatesServiceProvidersUsingTheContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());

            Object factory = ObjectFactory.createObject(FACTORY_ID, FALLBACK_FACTORY);

            assertSchemaFactory(factory);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void locatesServiceProvidersWithoutAContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);

            Object factory = ObjectFactory.createObject(FACTORY_ID, FALLBACK_FACTORY);

            assertSchemaFactory(factory);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private void assertSchemaFactory(Object factory) {
        Assertions.assertThat(factory).isInstanceOf(SchemaDVFactory.class);
        Assertions.assertThat(((SchemaDVFactory) factory).getBuiltInType("string")).isNotNull();
    }
}
