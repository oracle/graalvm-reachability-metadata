/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.dynamic.DynamicType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassFileVersionInnerVersionLocatorInnerForJava9CapableVmTest {
    @Test
    void locatesCurrentVmThroughFallbackAwarePublicApi() {
        ClassFileVersion fallback = ClassFileVersion.JAVA_V8;
        ClassFileVersion current = ClassFileVersion.ofThisVm(fallback);

        assertThat(current).isEqualTo(expectedCurrentVmVersionOrFallback(fallback));
    }

    @Test
    void defaultByteBuddyConfigurationUsesCurrentVmVersionLookup() {
        DynamicType.Unloaded<?> dynamicType = new ByteBuddy()
                .subclass(Object.class)
                .name("net_bytebuddy.byte_buddy.GeneratedCurrentVmVersionSample")
                .make();

        assertThat(readMajorVersion(dynamicType.getBytes()))
                .isGreaterThanOrEqualTo(ClassFileVersion.JAVA_V6.getMajorVersion());
    }

    @Test
    void strictCurrentVmLookupReturnsRepresentableVersion() {
        try {
            ClassFileVersion current = ClassFileVersion.ofThisVm();

            assertThat(current.getJavaVersion()).isEqualTo(Runtime.version().feature());
        } catch (IllegalArgumentException exception) {
            assertThat(exception).hasMessageStartingWith("Unknown Java version: ");
        }
    }

    @Test
    void protectedVersionLocatorCreationActionLocatesCurrentVm() {
        try {
            ClassFileVersion current = VersionLocatorAccessor.locateCurrentVm();

            assertThat(current.getJavaVersion()).isEqualTo(Runtime.version().feature());
        } catch (IllegalArgumentException exception) {
            assertThat(exception).hasMessageStartingWith("Unknown Java version: ");
        }
    }

    @Test
    void java9CapableLocatorInvokesSuppliedVersionMethods() throws Exception {
        ClassFileVersion current = VersionLocatorAccessor.locateSuppliedVersionMethods();

        assertThat(current).isEqualTo(ClassFileVersion.JAVA_V8);
    }

    private static ClassFileVersion expectedCurrentVmVersionOrFallback(ClassFileVersion fallback) {
        try {
            return ClassFileVersion.ofJavaVersion(Runtime.version().feature());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static int readMajorVersion(byte[] classFile) {
        return ((classFile[6] & 0xFF) << 8) | (classFile[7] & 0xFF);
    }

    private static class VersionLocatorAccessor extends ClassFileVersion {
        private VersionLocatorAccessor() {
            super(0);
        }

        private static ClassFileVersion locateCurrentVm() {
            VersionLocator versionLocator = VersionLocator.CreationAction.INSTANCE.run();
            return versionLocator.locate();
        }

        private static ClassFileVersion locateSuppliedVersionMethods() throws NoSuchMethodException {
            Method current = SyntheticRuntime.class.getMethod("version");
            Method major = SyntheticRuntimeVersion.class.getMethod("major");
            VersionLocator versionLocator = new ExposedJava9CapableLocator(current, major);
            return versionLocator.locate();
        }

        private static class ExposedJava9CapableLocator extends VersionLocator.ForJava9CapableVm {
            ExposedJava9CapableLocator(Method current, Method major) {
                super(current, major);
            }
        }
    }

    public static class SyntheticRuntime {
        public static SyntheticRuntimeVersion version() {
            return new SyntheticRuntimeVersion();
        }
    }

    public static class SyntheticRuntimeVersion {
        public Integer major() {
            return 8;
        }
    }
}
