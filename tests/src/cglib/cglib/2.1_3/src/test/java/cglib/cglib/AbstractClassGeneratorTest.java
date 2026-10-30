/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import net.sf.cglib.core.AbstractClassGenerator;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassVisitor;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractClassGeneratorTest {
    @Test
    void reusesPrecompiledClassWithoutGeneratingBytecode() {
        PrecompiledClassResolver resolver = new PrecompiledClassResolver();
        resolver.setClassLoader(Message.class.getClassLoader());
        resolver.setAttemptLoad(true);
        resolver.setNamingPolicy((prefix, source, key, names) -> Message.class.getName());

        assertThat(resolver.resolve("message")).isSameAs(Message.class);
        assertThat(resolver.resolve("message")).isSameAs(Message.class);
        assertThat(new Message().text()).isEqualTo("precompiled");
        assertThat(AbstractClassGenerator.getCurrent()).isNull();
    }

    private static final class PrecompiledClassResolver extends AbstractClassGenerator {
        private PrecompiledClassResolver() {
            super(new Source("precompiled-messages"));
        }

        private Class<?> resolve(String key) {
            return (Class<?>) create(key);
        }

        @Override
        protected ClassLoader getDefaultClassLoader() {
            return Message.class.getClassLoader();
        }

        @Override
        protected Object firstInstance(Class type) {
            return type;
        }

        @Override
        protected Object nextInstance(Object instance) {
            return instance;
        }

        @Override
        public void generateClass(ClassVisitor visitor) {
            throw new AssertionError("The precompiled class must be reused");
        }
    }

    public static class Message {
        public String text() {
            return "precompiled";
        }
    }
}
