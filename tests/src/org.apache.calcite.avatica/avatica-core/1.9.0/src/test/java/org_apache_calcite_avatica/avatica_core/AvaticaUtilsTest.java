/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite_avatica.avatica_core;

import org.apache.calcite.avatica.AvaticaUtils;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AvaticaUtilsTest {
    @Test
    void constructsPluginUsingItsPublicConstructor() {
        Supplier<?> plugin = AvaticaUtils.instantiatePlugin(Supplier.class, ConstructorPlugin.class.getName());
        assertEquals("constructed", plugin.get());
    }

    @Test
    void obtainsPluginFromItsConventionalSingleton() {
        Supplier<?> plugin = AvaticaUtils.instantiatePlugin(Supplier.class, SingletonPlugin.class.getName());
        assertSame(SingletonPlugin.INSTANCE, plugin);
        assertEquals("singleton", plugin.get());
    }

    @Test
    void obtainsPluginFromAnExplicitStaticField() {
        Supplier<?> plugin = AvaticaUtils.instantiatePlugin(Supplier.class, NamedPlugins.class.getName() + "#PRIMARY");
        assertSame(NamedPlugins.PRIMARY, plugin);
        assertEquals("primary", plugin.get());
    }

    public static class ConstructorPlugin implements Supplier<String> {
        public ConstructorPlugin() {
        }

        @Override
        public String get() {
            return "constructed";
        }
    }

    public static class SingletonPlugin implements Supplier<String> {
        public static final SingletonPlugin INSTANCE = new SingletonPlugin();

        private SingletonPlugin() {
        }

        @Override
        public String get() {
            return "singleton";
        }
    }

    public static class NamedPlugins {
        public static final Supplier<String> PRIMARY = () -> "primary";
    }
}
