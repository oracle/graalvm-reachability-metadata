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
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StopWatch;

public class ReflectionUtilsTest {

    @Test
    void obtainsAccessibleLibraryConstructor() throws Exception {
        Constructor<?> constructor = ReflectionUtils.accessibleConstructor(
                NamedThreadLocal.class,
                String.class
        );

        assertThat(constructor.getDeclaringClass()).isEqualTo(NamedThreadLocal.class);
        assertThat(constructor.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void findsLibraryMethodsOnClassesAndInterfaces() {
        ReflectionUtils.clearCache();

        Method currentTaskNameMethod = ReflectionUtils.findMethod(StopWatch.class, "currentTaskName");
        Method interfaceMethod = ReflectionUtils.findMethod(Resource.class, "isReadable");
        Method[] resourceMethods = ReflectionUtils.getDeclaredMethods(AbstractResource.class);

        assertThat(currentTaskNameMethod).isNotNull();
        assertThat(currentTaskNameMethod.getDeclaringClass()).isEqualTo(StopWatch.class);
        assertThat(interfaceMethod).isNotNull();
        assertThat(interfaceMethod.getDeclaringClass()).isEqualTo(Resource.class);
        assertThat(resourceMethods)
                .anySatisfy(method -> assertThat(method.getName()).isEqualTo("exists"))
                .anySatisfy(method -> {
                    assertThat(method.getName()).isEqualTo("readableChannel");
                    assertThat(method.getDeclaringClass()).isEqualTo(Resource.class);
                });
    }

    @Test
    void invokesMethodThroughReflectionUtils() {
        StopWatch stopWatch = new StopWatch("reflection-utils-invoke");
        Method getIdMethod = ReflectionUtils.findMethod(StopWatch.class, "getId");

        Object id = ReflectionUtils.invokeMethod(getIdMethod, stopWatch);

        assertThat(id).isEqualTo("reflection-utils-invoke");
    }

    @Test
    void readsAndWritesLibraryFieldsThroughReflectionUtils() {
        ReflectionUtils.clearCache();
        StopWatch stopWatch = new StopWatch();
        Field keepTaskListField = ReflectionUtils.findField(StopWatch.class, "keepTaskList");

        assertThat(keepTaskListField).isNotNull();
        ReflectionUtils.makeAccessible(keepTaskListField);
        assertThat(ReflectionUtils.getField(keepTaskListField, stopWatch)).isEqualTo(true);

        ReflectionUtils.setField(keepTaskListField, stopWatch, false);

        assertThat(ReflectionUtils.getField(keepTaskListField, stopWatch)).isEqualTo(false);
    }

    @Test
    void shallowCopiesLibraryFieldState() {
        ReflectionUtils.clearCache();
        StopWatch source = new StopWatch("source");
        StopWatch destination = new StopWatch("destination");
        source.setKeepTaskList(false);
        source.start("copied-task");

        ReflectionUtils.shallowCopyFieldState(source, destination);

        assertThat(destination.getId()).isEqualTo("destination");
        assertThat(destination.isRunning()).isTrue();
        assertThat(destination.currentTaskName()).isEqualTo("copied-task");
    }
}
