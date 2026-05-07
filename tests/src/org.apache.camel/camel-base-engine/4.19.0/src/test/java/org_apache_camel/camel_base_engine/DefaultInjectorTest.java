/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_base_engine;

import org.apache.camel.impl.engine.DefaultCamelContextNameStrategy;
import org.apache.camel.impl.engine.DefaultInjector;
import org.apache.camel.impl.engine.SimpleCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultInjectorTest {
    @Test
    void createsInstanceUsingPublicStaticFactoryMethod() throws Exception {
        try (SimpleCamelContext context = new SimpleCamelContext()) {
            DefaultInjector injector = new DefaultInjector(context);

            Integer counter = injector.newInstance(
                    Integer.class, DefaultCamelContextNameStrategy.class, "getNextCounter");

            assertThat(counter).isPositive();
        }
    }
}
