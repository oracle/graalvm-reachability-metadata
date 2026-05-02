/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_api;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.maven.surefire.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {
    @Test
    void resolvesLoadsInstantiatesAndInvokesPublicMembers() throws InvocationTargetException {
        ClassLoader classLoader = ReflectionUtilsTest.class.getClassLoader();
        String targetClassName = ReflectionTarget.class.getName();

        Class<?> loadedType = ReflectionUtils.loadClass(classLoader, targetClassName);
        Class<?> tryLoadedType = ReflectionUtils.tryLoadClass(classLoader, targetClassName);
        Object defaultTarget = ReflectionUtils.instantiate(classLoader, targetClassName, ReflectionTarget.class);
        Object oneArgTarget = ReflectionUtils.instantiateOneArg(classLoader, targetClassName, String.class, "one");
        Object twoArgTarget = ReflectionUtils.instantiateTwoArgs(
                classLoader,
                targetClassName,
                String.class,
                "two",
                Integer.class,
                2
        );
        Object objectTarget = ReflectionUtils.instantiateObject(
                targetClassName,
                new Class<?>[] {String.class, Integer.class},
                new Object[] {"object", 4},
                classLoader
        );
        Constructor<?> noArgConstructor = ReflectionUtils.getConstructor(ReflectionTarget.class);
        Object constructedTarget = ReflectionUtils.newInstance(noArgConstructor);
        Method describe = ReflectionUtils.getMethod(ReflectionTarget.class, "describe", String.class);
        Method tryDescribe = ReflectionUtils.tryGetMethod(ReflectionTarget.class, "describe", String.class);
        Method missing = ReflectionUtils.tryGetMethod(ReflectionTarget.class, "missing");

        assertThat(loadedType).isEqualTo(ReflectionTarget.class);
        assertThat(tryLoadedType).isEqualTo(ReflectionTarget.class);
        assertThat(ReflectionUtils.invokeGetter(defaultTarget, "getValue")).isEqualTo("default:0");
        assertThat(ReflectionUtils.invokeGetter(oneArgTarget, "getValue")).isEqualTo("one:1");
        assertThat(ReflectionUtils.invokeGetter(twoArgTarget, "getValue")).isEqualTo("two:2");
        assertThat(ReflectionUtils.invokeGetter(objectTarget, "getValue")).isEqualTo("object:4");
        assertThat(ReflectionUtils.invokeGetter(constructedTarget, "getValue")).isEqualTo("default:0");
        assertThat(tryDescribe).isEqualTo(describe);
        assertThat(missing).isNull();
        assertThat(ReflectionUtils.invokeMethodWithArray(defaultTarget, describe, "suffix"))
                .isEqualTo("default:0:suffix");
        assertThat(ReflectionUtils.invokeMethodWithArray2(oneArgTarget, describe, "again"))
                .isEqualTo("one:1:again");
    }

    public static final class ReflectionTarget {
        private final String value;
        private final Integer count;

        public ReflectionTarget() {
            this("default", 0);
        }

        public ReflectionTarget(String value) {
            this(value, 1);
        }

        public ReflectionTarget(String value, Integer count) {
            this.value = value;
            this.count = count;
        }

        public String getValue() {
            return value + ":" + count;
        }

        public String describe(String suffix) {
            return getValue() + ":" + suffix;
        }
    }
}
