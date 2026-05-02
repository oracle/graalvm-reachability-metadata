/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.cglib.core.NamingPolicy;
import org.springframework.cglib.core.Predicate;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.NoOp;

public class AbstractClassGeneratorTest {

    @Test
    void attemptLoadResolvesExistingGeneratedNameBeforeBytecodeGeneration() {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(LoadAttemptBase.class);
        enhancer.setCallbackType(NoOp.class);
        enhancer.setUseCache(false);
        enhancer.setAttemptLoad(true);
        enhancer.setNamingPolicy(new ExistingClassNamingPolicy(PreexistingGeneratedType.class));

        try {
            Class<?> loadedClass = enhancer.createClass();

            assertThat(loadedClass).isSameAs(PreexistingGeneratedType.class);
        }
        catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
        }
    }

    private static void ignoreUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static final class ExistingClassNamingPolicy implements NamingPolicy {

        private final Class<?> targetClass;

        private ExistingClassNamingPolicy(Class<?> targetClass) {
            this.targetClass = targetClass;
        }

        @Override
        public String getClassName(String prefix, String source, Object key, Predicate names) {
            return targetClass.getName();
        }
    }

    public static class LoadAttemptBase {

        public String describe() {
            return "base";
        }
    }

    public static class PreexistingGeneratedType extends LoadAttemptBase {
    }
}
