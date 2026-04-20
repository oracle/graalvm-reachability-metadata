/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_cache.cache_api;

import java.util.ArrayList;
import java.util.List;
import javax.cache.configuration.Factory;
import javax.cache.configuration.FactoryBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryBuilder_ClassFactoryTest {

    @Test
    void createsNewInstancesUsingTheThreadContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        TrackingClassLoader trackingClassLoader = new TrackingClassLoader(getClass().getClassLoader());
        Thread.currentThread().setContextClassLoader(trackingClassLoader);

        try {
            Factory<FactoryProduct> factory = FactoryBuilder.factoryOf(FactoryProduct.class.getName());

            FactoryProduct firstInstance = factory.create();
            FactoryProduct secondInstance = factory.create();

            assertThat(trackingClassLoader.loadedClassNames)
                .contains(FactoryProduct.class.getName());
            assertThat(firstInstance).isInstanceOf(FactoryProduct.class);
            assertThat(secondInstance).isInstanceOf(FactoryProduct.class);
            assertThat(firstInstance).isNotSameAs(secondInstance);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    public static final class TrackingClassLoader extends ClassLoader {

        private final List<String> loadedClassNames = new ArrayList<>();

        public TrackingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadedClassNames.add(name);
            return super.loadClass(name);
        }
    }

    public static final class FactoryProduct {

        public FactoryProduct() {
        }
    }
}
