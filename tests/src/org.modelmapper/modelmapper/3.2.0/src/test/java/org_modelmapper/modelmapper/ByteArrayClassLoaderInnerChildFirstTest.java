/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.ByteBuddy;
import org.modelmapper.internal.bytebuddy.description.type.TypeDescription;
import org.modelmapper.internal.bytebuddy.dynamic.DynamicType;
import org.modelmapper.internal.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import org.modelmapper.internal.bytebuddy.dynamic.loading.ByteArrayClassLoader.ChildFirst;
import org.modelmapper.internal.bytebuddy.dynamic.scaffold.TypeValidation;
import org.modelmapper.internal.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;

public class ByteArrayClassLoaderInnerChildFirstTest {

    @Test
    void staticallyLoadsGeneratedTypesThroughChildFirstClassLoader() {
        DynamicType.Unloaded<?> unloadedType = makeUnloadedType(
            "org_modelmapper.modelmapper.generated.ChildFirstStaticLoadType");
        TypeDescription typeDescription = unloadedType.getTypeDescription();

        Map<TypeDescription, Class<?>> loadedTypes = ChildFirst.load(
            isolatedParent(),
            Collections.singletonMap(typeDescription, unloadedType.getBytes()));

        Class<?> loadedType = loadedTypes.get(typeDescription);
        assertThat(loadedType.getName()).isEqualTo(typeDescription.getName());
        assertThat(loadedType.getClassLoader()).isInstanceOf(ChildFirst.class);
    }

    @Test
    void loadsGeneratedTypeBeforeDelegatingToParentClassLoader() throws ClassNotFoundException {
        String typeName = "org_modelmapper.modelmapper.generated.ChildFirstDirectLoadType";
        ChildFirst classLoader = new ChildFirst(
            isolatedParent(),
            Collections.singletonMap(typeName, makeUnloadedType(typeName).getBytes()));

        Class<?> loadedType = classLoader.loadClass(typeName);
        Class<?> alreadyLoadedType = classLoader.loadClass(typeName);

        assertThat(loadedType.getName()).isEqualTo(typeName);
        assertThat(loadedType.getClassLoader()).isSameAs(classLoader);
        assertThat(alreadyLoadedType).isSameAs(loadedType);
    }

    @Test
    void exposesManifestResourcesBeforeParentResources() throws IOException, ClassNotFoundException {
        String typeName = "org_modelmapper.modelmapper.generated.ChildFirstManifestResourceType";
        String resourceName = typeName.replace('.', '/') + ".class";
        ChildFirst classLoader = manifestClassLoader(typeName);

        Enumeration<URL> generatedResources = classLoader.getResources(resourceName);
        URL generatedResource = generatedResources.nextElement();
        Enumeration<URL> missingResources = classLoader.getResources(
            "org_modelmapper/modelmapper/generated/MissingChildFirstResource.class");
        classLoader.getResource("java/lang/String.class");
        Class<?> loadedType = classLoader.loadClass(typeName);

        assertThat(generatedResource).isNotNull();
        assertThat(missingResources.hasMoreElements()).isFalse();
        assertThat(loadedType.getClassLoader()).isSameAs(classLoader);
    }

    @Test
    void treatsLoadedLatentClassWithoutDefinitionAsShadowed() throws ClassNotFoundException {
        String typeName = "org_modelmapper.modelmapper.generated.ChildFirstShadowedType";
        String resourceName = typeName.replace('.', '/') + ".class";
        ChildFirst classLoader = new ChildFirst(
            isolatedParent(),
            Collections.singletonMap(typeName, makeUnloadedType(typeName).getBytes()));

        Class<?> loadedType = classLoader.loadClass(typeName);
        URL shadowedResource = classLoader.getResource(resourceName);

        assertThat(loadedType.getClassLoader()).isSameAs(classLoader);
        assertThat(shadowedResource).isNull();
    }

    private static ChildFirst manifestClassLoader(String typeName) {
        return new ChildFirst(
            isolatedParent(),
            Collections.singletonMap(typeName, makeUnloadedType(typeName).getBytes()),
            ByteArrayClassLoader.PersistenceHandler.MANIFEST);
    }

    private static DynamicType.Unloaded<?> makeUnloadedType(String typeName) {
        return new ByteBuddy()
            .with(TypeValidation.DISABLED)
            .subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
            .name(typeName)
            .make();
    }

    private static ClassLoader isolatedParent() {
        return new IsolatedParentClassLoader(
            ByteArrayClassLoaderInnerChildFirstTest.class.getClassLoader());
    }

    private static final class IsolatedParentClassLoader extends ClassLoader {
        IsolatedParentClassLoader(ClassLoader parent) {
            super(parent);
        }
    }

}
