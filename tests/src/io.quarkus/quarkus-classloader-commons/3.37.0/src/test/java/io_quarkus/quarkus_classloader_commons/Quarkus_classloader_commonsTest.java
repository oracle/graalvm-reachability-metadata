/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_classloader_commons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.quarkus.commons.classloading.ClassLoaderHelper;
import org.junit.jupiter.api.Test;

public class Quarkus_classloader_commonsTest {
    @Test
    void fromClassNameToResourceNameConvertsJavaNamesToClassResources() {
        assertThat(ClassLoaderHelper.fromClassNameToResourceName("java.lang.String"))
                .isEqualTo("java/lang/String.class");
        assertThat(ClassLoaderHelper.fromClassNameToResourceName("io.quarkus.commons.classloading.ClassLoaderHelper"))
                .isEqualTo("io/quarkus/commons/classloading/ClassLoaderHelper.class");
        assertThat(ClassLoaderHelper.fromClassNameToResourceName("com.example.Outer$Inner"))
                .isEqualTo("com/example/Outer$Inner.class");
    }

    @Test
    void fromClassNameToResourceNamePreservesNamesWithoutPackagesAndSpecialClassNames() {
        assertThat(ClassLoaderHelper.fromClassNameToResourceName("DefaultPackageClass"))
                .isEqualTo("DefaultPackageClass.class");
        assertThat(ClassLoaderHelper.fromClassNameToResourceName("package-info"))
                .isEqualTo("package-info.class");
        assertThat(ClassLoaderHelper.fromClassNameToResourceName("module-info"))
                .isEqualTo("module-info.class");
    }

    @Test
    void fromResourceNameToClassNameConvertsClassResourcesToJavaNames() {
        assertThat(ClassLoaderHelper.fromResourceNameToClassName("java/lang/String.class"))
                .isEqualTo("java.lang.String");
        assertThat(ClassLoaderHelper.fromResourceNameToClassName(
                "io/quarkus/commons/classloading/ClassLoaderHelper.class"))
                .isEqualTo("io.quarkus.commons.classloading.ClassLoaderHelper");
        assertThat(ClassLoaderHelper.fromResourceNameToClassName("com/example/Outer$Inner.class"))
                .isEqualTo("com.example.Outer$Inner");
    }

    @Test
    void fromResourceNameToClassNamePreservesResourceNamesWithoutPackageDirectories() {
        assertThat(ClassLoaderHelper.fromResourceNameToClassName("DefaultPackageClass.class"))
                .isEqualTo("DefaultPackageClass");
        assertThat(ClassLoaderHelper.fromResourceNameToClassName("package-info.class"))
                .isEqualTo("package-info");
        assertThat(ClassLoaderHelper.fromResourceNameToClassName("module-info.class"))
                .isEqualTo("module-info");
    }

    @Test
    void conversionMethodsRoundTripPublicClassNameForms() {
        final String[] classNames = {
                "java.util.Map$Entry",
                "io.quarkus.commons.classloading.ClassLoaderHelper",
                "org.example.deeply.nested.Service$Provider"
        };

        for (final String className : classNames) {
            final String resourceName = ClassLoaderHelper.fromClassNameToResourceName(className);

            assertThat(ClassLoaderHelper.fromResourceNameToClassName(resourceName))
                    .isEqualTo(className);
        }
    }

    @Test
    void conversionMethodsSupportBinaryNamesReturnedByClassObjects() {
        final String className = Fixture.NestedService.class.getName();
        final String resourceName = ClassLoaderHelper.fromClassNameToResourceName(className);

        assertThat(resourceName)
                .endsWith("/Quarkus_classloader_commonsTest$Fixture$NestedService.class");
        assertThat(ClassLoaderHelper.fromResourceNameToClassName(resourceName))
                .isEqualTo(className);
    }

    @Test
    void fromResourceNameToClassNameRejectsResourceNamesThatDoNotEndWithClassSuffix() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClassLoaderHelper.fromResourceNameToClassName("java/lang/String.txt"))
                .withMessageContaining("java/lang/String.txt")
                .withMessageContaining("doesn't end with .class");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClassLoaderHelper.fromResourceNameToClassName("java/lang/String.CLASS"))
                .withMessageContaining("java/lang/String.CLASS")
                .withMessageContaining("doesn't end with .class");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClassLoaderHelper.fromResourceNameToClassName("java/lang/String.class/"))
                .withMessageContaining("java/lang/String.class/")
                .withMessageContaining("doesn't end with .class");
    }

    @Test
    void isInJdkPackageRecognizesSupportedJdkPackagePrefixes() {
        assertThat(ClassLoaderHelper.isInJdkPackage("java.lang.String")).isTrue();
        assertThat(ClassLoaderHelper.isInJdkPackage("java.")).isTrue();
        assertThat(ClassLoaderHelper.isInJdkPackage("jdk.internal.misc.Unsafe")).isTrue();
        assertThat(ClassLoaderHelper.isInJdkPackage("sun.misc.Unsafe")).isTrue();
    }

    @Test
    void isInJdkPackageRejectsNamesOutsideSupportedJdkPackagePrefixes() {
        assertThat(ClassLoaderHelper.isInJdkPackage("javax.sql.DataSource")).isFalse();
        assertThat(ClassLoaderHelper.isInJdkPackage("com.sun.net.httpserver.HttpServer")).isFalse();
        assertThat(ClassLoaderHelper.isInJdkPackage("sun.reflect.ReflectionFactory")).isFalse();
        assertThat(ClassLoaderHelper.isInJdkPackage("java")).isFalse();
        assertThat(ClassLoaderHelper.isInJdkPackage("")).isFalse();
    }

    @Test
    void isInJdkPackageDoesNotMatchLookalikePackagePrefixes() {
        assertThat(ClassLoaderHelper.isInJdkPackage("javassist.ClassPool")).isFalse();
        assertThat(ClassLoaderHelper.isInJdkPackage("jdk.internalized.Helper")).isFalse();
        assertThat(ClassLoaderHelper.isInJdkPackage("sun.miscellaneous.Tool")).isFalse();
    }

    @Test
    void publicMethodsRejectNullInputs() {
        assertThatNullPointerException()
                .isThrownBy(() -> ClassLoaderHelper.fromClassNameToResourceName(null));
        assertThatNullPointerException()
                .isThrownBy(() -> ClassLoaderHelper.fromResourceNameToClassName(null));
        assertThatNullPointerException()
                .isThrownBy(() -> ClassLoaderHelper.isInJdkPackage(null));
    }

    private static final class Fixture {
        private static final class NestedService {
        }
    }
}
