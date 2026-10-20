/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.CXFBusImpl;
import org.apache.cxf.bus.extension.Extension;
import org.junit.jupiter.api.Test;

public class ExtensionTest {
    @Test
    void createsExtensionsUsingTheirSupportedConstructorShapes() {
        Bus bus = new CXFBusImpl();

        Extension busExtension = new Extension(BusConstructed.class);
        assertThat(busExtension.load(getClass().getClassLoader(), bus))
                .isInstanceOf(BusConstructed.class);
        assertThat(((BusConstructed) busExtension.getLoadedObject()).bus).isSameAs(bus);

        Extension busArgsExtension = new Extension(BusAndArgsConstructed.class);
        busArgsExtension.setArgs(new Object[] {"alpha", "beta"});
        BusAndArgsConstructed busAndArgs =
                (BusAndArgsConstructed) busArgsExtension.load(getClass().getClassLoader(), bus);
        assertThat(busAndArgs.bus).isSameAs(bus);
        assertThat(busAndArgs.args).containsExactly("alpha", "beta");

        Extension fallbackArgsExtension = new Extension(ArgsConstructed.class);
        fallbackArgsExtension.setArgs(new Object[] {new Object[] {"fallback"}});
        ArgsConstructed fallbackArgs =
                (ArgsConstructed)
                        fallbackArgsExtension.load(getClass().getClassLoader(), bus);
        assertThat(fallbackArgs.args).containsExactly("fallback");

        Extension argsExtension = new Extension(ArgsConstructed.class);
        argsExtension.setArgs(new Object[] {new Object[] {"payload"}});
        ArgsConstructed args =
                (ArgsConstructed) argsExtension.load(getClass().getClassLoader(), null);
        assertThat(args.args).containsExactly("payload");

        Extension defaultExtension = new Extension();
        defaultExtension.setClassname(DefaultConstructed.class.getName());
        assertThat(defaultExtension.load(getClass().getClassLoader(), null))
                .isInstanceOf(DefaultConstructed.class);

        Extension retainedLoaderExtension = new Extension(DefaultConstructed.class).cloneNoObject();
        assertThat(retainedLoaderExtension.load(getClass().getClassLoader(), null))
                .isInstanceOf(DefaultConstructed.class);

        Extension fallbackLoaderExtension = new Extension();
        fallbackLoaderExtension.setClassname(DefaultConstructed.class.getName());
        assertThat(fallbackLoaderExtension.load(null, null)).isInstanceOf(DefaultConstructed.class);
    }

    public static final class BusConstructed {
        private final Bus bus;

        public BusConstructed(Bus bus) {
            this.bus = bus;
        }
    }

    public static final class BusAndArgsConstructed {
        private final Bus bus;
        private final Object[] args;

        public BusAndArgsConstructed(Bus bus, Object[] args) {
            this.bus = bus;
            this.args = args;
        }
    }

    public static final class ArgsConstructed {
        private final Object[] args;

        public ArgsConstructed(Object[] args) {
            this.args = args;
        }
    }

    public static final class DefaultConstructed {
        public DefaultConstructed() {}
    }
}
