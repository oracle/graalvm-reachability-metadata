/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.BeanNameResolver;
import jakarta.el.ELProcessor;
import jakarta.el.PropertyNotWritableException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Resolves application service names with Jakarta Expression Language's bean-name resolver
 * subsystem. A catalog service can expose tenant-selected services to configurable expressions
 * without adding each service as a fixed bean in the processor.
 */
public class CatalogBeanNameResolverExpressionTest {

    @Test
    @Timeout(60)
    public void evaluatesCatalogServiceResolvedByApplicationRegistry() {
        Catalog catalog = new Catalog("EUR");
        ServiceRegistry registry = new ServiceRegistry();
        registry.register("catalog", catalog);
        ELProcessor expressions = new ELProcessor();
        expressions.getELManager().addBeanNameResolver(registry);

        String itemLabel = expressions.eval("catalog.label('SKU-7')");
        Number bundleTotal = expressions.eval(
                "catalog.price('SKU-7') + catalog.price('SKU-8')");
        String quote = expressions.eval(
                "catalog.currency.concat(' ').concat(catalog.price('SKU-7'))");

        assertEquals("Starter plan", itemLabel);
        assertEquals(89, bundleTotal.intValue());
        assertEquals("EUR 49", quote);
    }

    /** Registry that supplies request-configured application services by EL name. */
    public static final class ServiceRegistry extends BeanNameResolver {
        private final Map<String, Object> services = new LinkedHashMap<>();

        public void register(String name, Object service) {
            services.put(name, service);
        }

        @Override
        public boolean isNameResolved(String beanName) {
            return services.containsKey(beanName);
        }

        @Override
        public Object getBean(String beanName) {
            return services.get(beanName);
        }

        @Override
        public void setBeanValue(String beanName, Object value) {
            throw new PropertyNotWritableException("Service registry is read-only");
        }

        @Override
        public boolean isReadOnly(String beanName) {
            return true;
        }

        @Override
        public boolean canCreateBean(String beanName) {
            return false;
        }
    }

    /** Catalog service selected by the application registry for the expression context. */
    public static final class Catalog {
        private final String currency;

        public Catalog(String currency) {
            this.currency = currency;
        }

        public String getCurrency() {
            return currency;
        }

        public String label(String sku) {
            return switch (sku) {
                case "SKU-7" -> "Starter plan";
                case "SKU-8" -> "Team plan";
                default -> "Unknown plan";
            };
        }

        public int price(String sku) {
            return switch (sku) {
                case "SKU-7" -> 49;
                case "SKU-8" -> 40;
                default -> 0;
            };
        }
    }
}
