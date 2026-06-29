/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.internal.asm.ClassVisitor;
import com.google.inject.internal.cglib.core.AbstractClassGenerator;
import com.google.inject.internal.cglib.core.ClassGenerator;
import com.google.inject.internal.cglib.core.GeneratorStrategy;
import com.google.inject.internal.cglib.core.Predicate;
import org.junit.jupiter.api.Test;

public class AbstractClassGeneratorTest {
    @Test
    void loadsAlreadyAvailableGeneratedClassWhenAttemptLoadIsEnabled() {
        String generatedClassName = PreexistingGeneratedType.class.getName();
        RecordingClassLoader loader = new RecordingClassLoader(
                AbstractClassGeneratorTest.class.getClassLoader());
        AttemptLoadGenerator generator = new AttemptLoadGenerator(generatedClassName, loader);

        Class<?> generatedType = generator.createWithAttemptLoad("already-defined-key");

        assertThat(generatedType).isEqualTo(PreexistingGeneratedType.class);
        assertThat(loader.requestedName).isEqualTo(generatedClassName);
    }

    public static class PreexistingGeneratedType {
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private String requestedName;

        private RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            requestedName = name;
            return super.loadClass(name);
        }
    }

    private static final class AttemptLoadGenerator extends AbstractClassGenerator {
        private static final Source SOURCE = new Source("attempt-load-test");
        private final String generatedClassName;

        private AttemptLoadGenerator(String generatedClassName, ClassLoader loader) {
            super(SOURCE);
            this.generatedClassName = generatedClassName;
            setAttemptLoad(true);
            setClassLoader(loader);
            setNamingPolicy(this::generatedClassName);
            setStrategy(new FailingGeneratorStrategy());
        }

        private Class<?> createWithAttemptLoad(Object key) {
            return (Class<?>) create(key);
        }

        private String generatedClassName(
                String prefix, String source, Object key, Predicate names) {
            return generatedClassName;
        }

        @Override
        protected ClassLoader getDefaultClassLoader() {
            return getClass().getClassLoader();
        }

        @Override
        public void generateClass(ClassVisitor visitor) {
            throw new AssertionError("attempt-load should find the preexisting generated class");
        }

        @Override
        protected Object firstInstance(Class type) {
            return type;
        }

        @Override
        protected Object nextInstance(Object instance) {
            return instance;
        }
    }

    private static final class FailingGeneratorStrategy implements GeneratorStrategy {
        @Override
        public byte[] generate(ClassGenerator generator) {
            throw new AssertionError("attempt-load should avoid bytecode generation");
        }
    }
}
