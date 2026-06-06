/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_integrations_graal.helidon_graal_native_image_extension;

import java.util.Set;
import java.util.function.Function;

import io.helidon.integrations.graal.nativeimage.extension.HelidonReflectionConfiguration;
import io.helidon.integrations.graal.nativeimage.extension.NativeConfig;
import io.helidon.integrations.graal.nativeimage.extension.NativeTrace;
import io.helidon.integrations.graal.nativeimage.extension.NativeUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Helidon_graal_native_image_extensionTest {
    private static final String TEST_OPTION = "test.option";
    private static final String TEST_OPTION_PROPERTY = "helidon.native." + TEST_OPTION;

    @Test
    void nativeConfigUsesDefaultWhenOptionPropertyIsAbsent() {
        String previous = System.clearProperty(TEST_OPTION_PROPERTY);
        try {
            assertThat(NativeConfig.option(TEST_OPTION, true)).isTrue();
            assertThat(NativeConfig.option(TEST_OPTION, false)).isFalse();
        } finally {
            restoreProperty(previous);
        }
    }

    @Test
    void nativeConfigParsesBooleanOptionProperty() {
        String previous = System.getProperty(TEST_OPTION_PROPERTY);
        try {
            System.setProperty(TEST_OPTION_PROPERTY, "true");
            assertThat(NativeConfig.option(TEST_OPTION, false)).isTrue();

            System.setProperty(TEST_OPTION_PROPERTY, "false");
            assertThat(NativeConfig.option(TEST_OPTION, true)).isFalse();
        } finally {
            restoreProperty(previous);
        }
    }

    @Test
    void reflectionConfigurationStartsWithoutExclusions() {
        HelidonReflectionConfiguration configuration = new HelidonReflectionConfiguration();

        assertThat(configuration.excluded()).isEmpty();
    }

    @Test
    void nativeTraceAcceptsDeferredMessages() {
        NativeTrace trace = new NativeTrace();

        trace.parsing(() -> "parsing native-image metadata");
        trace.trace(() -> "native-image registration");
        trace.section(() -> "metadata section");
    }

    @Test
    void nativeUtilClassMapperDelegatesToProvidedClassResolver() {
        NativeUtil util = nativeUtil(typeName -> false);

        assertThat(util.classMapper("test resolver").apply(Leaf.class.getName()))
                .isEqualTo(Leaf.class);
        assertThat(util.classMapper("test resolver").apply("example.MissingType"))
                .isNull();
    }

    @Test
    void nativeUtilInclusionFilterRejectsNullAndExcludedClasses() {
        NativeUtil util = nativeUtil(type -> type == IgnoredInterface.class);

        assertThat(util.inclusionFilter("test inclusion").test(null)).isFalse();
        assertThat(util.inclusionFilter("test inclusion").test(IgnoredInterface.class)).isFalse();
        assertThat(util.inclusionFilter("test inclusion").test(MarkerInterface.class)).isTrue();
    }

    @Test
    void nativeUtilFindsSuperclassChainInOrder() {
        NativeUtil util = nativeUtil(typeName -> false);

        Set<Class<?>> superclasses = util.findSuperclasses(Leaf.class);

        assertThat(superclasses).containsExactly(Middle.class, Base.class, Object.class);
    }

    @Test
    void nativeUtilFindsDirectInterfacesAndAppliesExclusions() {
        NativeUtil util = nativeUtil(type -> type == IgnoredInterface.class);

        Set<Class<?>> interfaces = util.findInterfaces(DirectInterfaces.class);

        assertThat(interfaces).containsExactly(MarkerInterface.class);
    }

    private static NativeUtil nativeUtil(Function<Class<?>, Boolean> exclusion) {
        return NativeUtil.create(new NativeTrace(),
                null,
                Helidon_graal_native_image_extensionTest::resolveClass,
                exclusion);
    }

    private static Class<?> resolveClass(String className) {
        if (Leaf.class.getName().equals(className)) {
            return Leaf.class;
        }
        if (Middle.class.getName().equals(className)) {
            return Middle.class;
        }
        if (Base.class.getName().equals(className)) {
            return Base.class;
        }
        if (MarkerInterface.class.getName().equals(className)) {
            return MarkerInterface.class;
        }
        if (IgnoredInterface.class.getName().equals(className)) {
            return IgnoredInterface.class;
        }
        if (DirectInterfaces.class.getName().equals(className)) {
            return DirectInterfaces.class;
        }
        return null;
    }

    private static void restoreProperty(String value) {
        if (value == null) {
            System.clearProperty(TEST_OPTION_PROPERTY);
        } else {
            System.setProperty(TEST_OPTION_PROPERTY, value);
        }
    }

    private interface MarkerInterface {
    }

    private interface IgnoredInterface {
    }

    private static class Base {
    }

    private static class Middle extends Base {
    }

    private static class Leaf extends Middle {
    }

    private static class DirectInterfaces implements MarkerInterface, IgnoredInterface {
    }
}
