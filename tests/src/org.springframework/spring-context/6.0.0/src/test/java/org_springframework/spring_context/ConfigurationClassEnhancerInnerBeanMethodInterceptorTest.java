/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheFactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class ConfigurationClassEnhancerInnerBeanMethodInterceptorTest {

    static {
        System.setProperty("spring.objenesis.ignore", "true");
    }

    @Test
    void finalFactoryBeanWithInterfaceReturnTypeUsesInterfaceProxy() throws Exception {
        withContext(InterfaceProxyConfiguration.class, context -> {
            final InterfaceProxyConfiguration configuration = context.getBean(InterfaceProxyConfiguration.class);
            final ProductFactory targetFactory = (ProductFactory) context.getBean("&interfaceFactoryBean");

            final ProductFactory interceptedFactory = configuration.interfaceFactoryBean();
            final Product product = interceptedFactory.getObject();

            assertNotSame(targetFactory, interceptedFactory);
            assertEquals(Product.class, interceptedFactory.getObjectType());
            assertTrue(interceptedFactory.isSingleton());
            assertEquals("interface-proxy-product", product.getName());
        });
    }

    @Test
    void nonFinalFactoryBeanUsesCglibProxyInstantiatedThroughDefaultConstructorFallback() throws Exception {
        withContext(CglibProxyConfiguration.class, context -> {
            final CglibProxyConfiguration configuration = context.getBean(CglibProxyConfiguration.class);
            final ConcurrentMapCacheFactoryBean targetFactory =
                    (ConcurrentMapCacheFactoryBean) context.getBean("&cglibFactoryBean");

            final ConcurrentMapCacheFactoryBean interceptedFactory = configuration.cglibFactoryBean();
            final ConcurrentMapCache cache = interceptedFactory.getObject();

            assertNotSame(targetFactory, interceptedFactory);
            assertSame(ConcurrentMapCache.class, interceptedFactory.getObjectType());
            assertTrue(interceptedFactory.isSingleton());
            if (!CglibProxyConfiguration.class.equals(configuration.getClass())) {
                assertSame(context.getBean("cglibFactoryBean"), cache);
            }
        });
    }

    private static void withContext(Class<?> configurationClass, ContextAction action) throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(configurationClass)) {
            action.accept(context);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @FunctionalInterface
    private interface ContextAction {
        void accept(AnnotationConfigApplicationContext context) throws Exception;
    }

    @Configuration
    public static class InterfaceProxyConfiguration {

        @Bean
        public ProductFactory interfaceFactoryBean() {
            return new FinalProductFactory();
        }
    }

    @Configuration
    public static class CglibProxyConfiguration {

        @Bean
        public ConcurrentMapCacheFactoryBean cglibFactoryBean() {
            return new ConcurrentMapCacheFactoryBean();
        }
    }

    public interface ProductFactory extends FactoryBean<Product> {
    }

    public static final class FinalProductFactory implements ProductFactory {

        private final Product product = new Product("interface-proxy-product");

        @Override
        public Product getObject() {
            return product;
        }

        @Override
        public Class<?> getObjectType() {
            return Product.class;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }

    public static final class Product {

        private final String name;

        Product(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
