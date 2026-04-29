/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.ByteBuddy;
import org.modelmapper.internal.bytebuddy.dynamic.DynamicType;
import org.modelmapper.internal.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import org.modelmapper.internal.bytebuddy.dynamic.loading.PackageDefinitionStrategy;
import org.modelmapper.internal.bytebuddy.dynamic.scaffold.TypeValidation;
import org.modelmapper.internal.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;

public class ClassInjectorInnerUsingReflectionInnerDispatcherInnerUsingUnsafeInjectionTest {

    @Test
    void injectsGeneratedTypeAndDefinesItsPackageThroughUnsafeReflectionDispatcher() {
        ClassLoader targetClassLoader = new IsolatedClassLoader(getClass().getClassLoader());
        String typeName = "org_modelmapper.modelmapper.generated."
            + "UnsafeReflectionDispatcherInjectedType";

        Class<?> loadedType = loadInjectedType(typeName, targetClassLoader);

        if (loadedType != null) {
            assertThat(loadedType.getName()).isEqualTo(typeName);
            assertThat(loadedType.getClassLoader()).isSameAs(targetClassLoader);
            assertThat(targetClassLoader.getDefinedPackage("org_modelmapper.modelmapper.generated"))
                .isNotNull()
                .extracting(Package::getSpecificationTitle)
                .isEqualTo(DefinedPackageStrategy.SPECIFICATION_TITLE);
        }
    }

    @Test
    void fallsBackToLegacyPackageLookupWhenPackageIsDefinedDuringInjection() {
        ClassLoader targetClassLoader = new PackageRaceClassLoader(getClass().getClassLoader());
        String typeName = "org_modelmapper.modelmapper.race.UnsafeReflectionDispatcherRaceType";

        Class<?> loadedType = loadInjectedType(typeName, targetClassLoader);

        if (loadedType != null) {
            assertThat(loadedType.getName()).isEqualTo(typeName);
            assertThat(loadedType.getPackage().getName())
                .isEqualTo("org_modelmapper.modelmapper.race");
            assertThat(loadedType.getPackage().getSpecificationTitle())
                .isEqualTo(DefinedPackageStrategy.SPECIFICATION_TITLE);
        }
    }

    private static Class<?> loadInjectedType(String typeName, ClassLoader targetClassLoader) {
        try {
            return makeUnloadedType(typeName)
                .load(targetClassLoader, injectionStrategy())
                .getLoaded();
        } catch (UnsupportedOperationException e) {
            assertThat(e).hasMessageContaining("Cannot get defined package using reflection");
            return null;
        }
    }

    private static DynamicType.Unloaded<?> makeUnloadedType(String typeName) {
        return new ByteBuddy()
            .with(TypeValidation.DISABLED)
            .subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
            .name(typeName)
            .make();
    }

    private static ClassLoadingStrategy<ClassLoader> injectionStrategy() {
        return ClassLoadingStrategy.Default.INJECTION.with(new DefinedPackageStrategy());
    }

    private static class IsolatedClassLoader extends ClassLoader {
        IsolatedClassLoader(ClassLoader parent) {
            super(parent);
        }
    }

    private static final class PackageRaceClassLoader extends ClassLoader {
        PackageRaceClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Package definePackage(
            String name,
            String specificationTitle,
            String specificationVersion,
            String specificationVendor,
            String implementationTitle,
            String implementationVersion,
            String implementationVendor,
            URL sealBase) {
            Package definedPackage = super.definePackage(
                name,
                specificationTitle,
                specificationVersion,
                specificationVendor,
                implementationTitle,
                implementationVersion,
                implementationVendor,
                sealBase);
            throw new IllegalStateException(
                "Package was defined concurrently: " + definedPackage.getName());
        }
    }

    private static final class DefinedPackageStrategy implements PackageDefinitionStrategy {
        static final String SPECIFICATION_TITLE = "ModelMapper Byte Buddy injection coverage";

        @Override
        public Definition define(ClassLoader classLoader, String packageName, String typeName) {
            return new Definition.Simple(
                SPECIFICATION_TITLE,
                "1",
                "modelmapper-test",
                "modelmapper-test",
                "1",
                "modelmapper-test",
                null);
        }
    }
}
