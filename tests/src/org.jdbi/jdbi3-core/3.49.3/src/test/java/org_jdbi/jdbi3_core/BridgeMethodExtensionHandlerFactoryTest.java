/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import java.util.Locale;
import java.util.Set;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.ExtensionFactory;
import org.jdbi.v3.core.extension.Extensions;
import org.jdbi.v3.core.extension.HandleSupplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jdbi.v3.core.extension.ExtensionFactory.FactoryFlag.NON_VIRTUAL_FACTORY;

public class BridgeMethodExtensionHandlerFactoryTest {
    @Test
    void forwardsAGenericBridgeMethodToItsTypedMethod() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:bridge_extensions;DB_CLOSE_DELAY=-1");
        jdbi.getConfig(Extensions.class).register(new TextFactory());

        String result = jdbi.withExtension(StringExtension.class, extension -> {
            GenericExtension<String> genericView = extension;
            return genericView.convert("bridge");
        });

        assertThat(result).isEqualTo("BRIDGE");
    }

    public interface GenericExtension<T> {
        T convert(T value);
    }

    public interface StringExtension extends GenericExtension<String> {
        @Override
        String convert(String value);
    }

    public static final class StringImplementation implements StringExtension {
        @Override
        public String convert(String value) {
            return value.toUpperCase(Locale.ROOT);
        }
    }

    public static final class TextFactory implements ExtensionFactory {
        @Override
        public boolean accepts(Class<?> extensionType) {
            return extensionType == StringExtension.class;
        }

        @Override
        public <E> E attach(Class<E> extensionType, HandleSupplier handleSupplier) {
            return extensionType.cast(new StringImplementation());
        }

        @Override
        public Set<FactoryFlag> getFactoryFlags() {
            return Set.of(NON_VIRTUAL_FACTORY);
        }
    }
}
