/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import java.util.Set;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.ExtensionFactory;
import org.jdbi.v3.core.extension.Extensions;
import org.jdbi.v3.core.extension.HandleSupplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jdbi.v3.core.extension.ExtensionFactory.FactoryFlag.NON_VIRTUAL_FACTORY;

public class ExtensionHandlerTest {
    @Test
    void invokesImplementationAndDefaultExtensionMethods() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:extension_handlers;DB_CLOSE_DELAY=-1");
        jdbi.getConfig(Extensions.class).register(new GreetingFactory());

        String result = jdbi.withExtension(Greeting.class, extension -> extension.decorate("core"));

        assertThat(result).isEqualTo("hello core!");
    }

    public interface Greeting {
        String greet(String name);

        default String decorate(String name) {
            return greet(name) + "!";
        }
    }

    public static final class GreetingImplementation implements Greeting {
        @Override
        public String greet(String name) {
            return "hello " + name;
        }
    }

    public static final class GreetingFactory implements ExtensionFactory {
        @Override
        public boolean accepts(Class<?> extensionType) {
            return extensionType == Greeting.class;
        }

        @Override
        public <E> E attach(Class<E> extensionType, HandleSupplier handleSupplier) {
            return extensionType.cast(new GreetingImplementation());
        }

        @Override
        public Set<FactoryFlag> getFactoryFlags() {
            return Set.of(NON_VIRTUAL_FACTORY);
        }
    }
}
