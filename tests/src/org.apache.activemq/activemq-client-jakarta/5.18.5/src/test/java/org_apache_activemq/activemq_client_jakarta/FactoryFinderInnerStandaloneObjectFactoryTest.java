/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.apache.activemq.util.FactoryFinder;
import org.junit.jupiter.api.Test;

public class FactoryFinderInnerStandaloneObjectFactoryTest {
    private static final String FACTORY_PATH = "org_apache_activemq/activemq_client_jakarta/factoryfinder/";

    @Test
    void newInstanceUsesContextClassLoaderResourceAndConstructor() throws Exception {
        ClassLoader originalContextLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(FactoryFinderInnerStandaloneObjectFactoryTest.class.getClassLoader());

        Object factoryProduct;
        try {
            factoryProduct = new FactoryFinder(FACTORY_PATH).newInstance("context-loader.properties");
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextLoader);
        }

        assertFactoryProduct(factoryProduct);
    }

    @Test
    void newInstanceFallsBackToFactoryFinderClassLoaderForResourceAndClass() throws Exception {
        ClassLoader originalContextLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader fallbackForcingLoader = new ClassLoader(originalContextLoader) {
            @Override
            public InputStream getResourceAsStream(String name) {
                return null;
            }

            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (FactoryProduct.class.getName().equals(name)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name);
            }
        };
        Thread.currentThread().setContextClassLoader(fallbackForcingLoader);

        Object factoryProduct;
        try {
            factoryProduct = new FactoryFinder(FACTORY_PATH).newInstance("fallback-loader.properties");
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextLoader);
        }

        assertFactoryProduct(factoryProduct);
    }

    private static void assertFactoryProduct(Object factoryProduct) {
        assertThat(factoryProduct).isInstanceOf(FactoryProduct.class);
        assertThat(((FactoryProduct) factoryProduct).description()).isEqualTo("created by FactoryFinder");
    }

    public static final class FactoryProduct {
        private final String description;

        public FactoryProduct() {
            this.description = "created by FactoryFinder";
        }

        public String description() {
            return description;
        }
    }
}
