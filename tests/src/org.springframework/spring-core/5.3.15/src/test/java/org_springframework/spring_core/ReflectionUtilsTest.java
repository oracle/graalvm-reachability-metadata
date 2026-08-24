/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.CustomizableThreadCreator;
import org.springframework.util.ReflectionUtils;

/** Verifies Spring's public reflection utilities against Spring Core types. §FS-repository-functional-spec.5.2 */
public class ReflectionUtilsTest {
    @Test
    void obtainsAccessibleConstructorAndCreatesResource() throws Exception {
        byte[] content = new byte[] {1, 2, 3};
        Constructor<ByteArrayResource> constructor = ReflectionUtils.accessibleConstructor(
                ByteArrayResource.class, byte[].class, String.class);

        ByteArrayResource resource = constructor.newInstance(
                new Object[] {content, "created with an accessible constructor"});

        assertThat(constructor.getDeclaringClass()).isEqualTo(ByteArrayResource.class);
        assertThat(resource.getByteArray()).isSameAs(content);
        assertThat(resource.getDescription()).contains("accessible constructor");
    }

    @Test
    void discoversInterfaceMethodsAndInvokesDefaultMethod() {
        ReflectionUtils.clearCache();

        Method[] declaredMethods = ReflectionUtils.getDeclaredMethods(Resource.class);
        Method readableMethod = ReflectionUtils.findMethod(Resource.class, "isReadable");
        ByteArrayResource resource = new ByteArrayResource(new byte[] {4, 5, 6});

        assertThat(declaredMethods).anyMatch(method -> method.getName().equals("isReadable"));
        assertThat(readableMethod).isNotNull();
        assertThat(ReflectionUtils.invokeMethod(readableMethod, resource)).isEqualTo(true);
    }

    @Test
    void readsWritesAndCopiesMutableFieldState() {
        ReflectionUtils.clearCache();
        CustomizableThreadCreator source = new CustomizableThreadCreator("source-");
        CustomizableThreadCreator destination = new CustomizableThreadCreator("destination-");
        Field prefixField = ReflectionUtils.findField(CustomizableThreadCreator.class, "threadNamePrefix");

        assertThat(prefixField).isNotNull();
        ReflectionUtils.makeAccessible(prefixField);
        ReflectionUtils.setField(prefixField, source, "reflected-");
        assertThat(ReflectionUtils.getField(prefixField, source)).isEqualTo("reflected-");

        source.setThreadPriority(Thread.MAX_PRIORITY);
        source.setDaemon(true);
        ReflectionUtils.shallowCopyFieldState(source, destination);

        assertThat(destination.getThreadNamePrefix()).isEqualTo("reflected-");
        assertThat(destination.getThreadPriority()).isEqualTo(Thread.MAX_PRIORITY);
        assertThat(destination.isDaemon()).isTrue();
    }
}
