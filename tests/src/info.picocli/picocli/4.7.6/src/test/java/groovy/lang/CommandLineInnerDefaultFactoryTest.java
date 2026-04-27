/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package groovy.lang;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CommandLineInnerDefaultFactoryTest {

    @Test
    void createsPublicNoArgInstancesWithDefaultFactory() throws Exception {
        CommandLine.IFactory factory = CommandLine.defaultFactory();

        Object result = factory.create(PublicNoArgCommand.class);

        assertThat(result).isInstanceOf(PublicNoArgCommand.class);
    }

    @Test
    void invokesDeclaredConstructorWhenPublicInstantiationFails() {
        CommandLine.IFactory factory = CommandLine.defaultFactory();

        assertThatThrownBy(() -> factory.create(ThrowingConstructorCommand.class))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void createsInstanceWhenPublicConstructorSucceedsOnDeclaredRetry() throws Exception {
        CommandLine.IFactory factory = CommandLine.defaultFactory();
        SucceedsOnSecondConstructionCommand.attempts = 0;

        Object result = factory.create(SucceedsOnSecondConstructionCommand.class);

        assertThat(result).isInstanceOf(SucceedsOnSecondConstructionCommand.class);
        assertThat(SucceedsOnSecondConstructionCommand.attempts).isEqualTo(2);
    }

    @Test
    void createsInstancesWithPrivateNoArgConstructors() throws Exception {
        CommandLine.IFactory factory = CommandLine.defaultFactory();

        Object result = factory.create(PrivateNoArgCommand.class);

        assertThat(result).isInstanceOf(PrivateNoArgCommand.class);
    }

    @Test
    void invokesGroovyClosureConstructorAndCallThroughDefaultFactory() throws Exception {
        CommandLine.IFactory factory = CommandLine.defaultFactory();

        Object result = factory.create(CallableClosure.class);

        assertThat(result).isEqualTo("created-by-closure");
    }

    public static class PublicNoArgCommand {
    }

    public static class ThrowingConstructorCommand {
        public ThrowingConstructorCommand() {
            throw new IllegalStateException("intentional constructor failure");
        }
    }

    public static class SucceedsOnSecondConstructionCommand {
        private static int attempts;

        public SucceedsOnSecondConstructionCommand() {
            attempts++;
            if (attempts == 1) {
                throw new IllegalStateException("retry with declared constructor");
            }
        }
    }

    public static class PrivateNoArgCommand {
        private PrivateNoArgCommand() {
        }
    }

    public static class CallableClosure extends Closure {
        public CallableClosure(Object owner, Object thisObject) {
            super(owner, thisObject);
        }

        @Override
        public Object call() {
            return "created-by-closure";
        }
    }
}

abstract class Closure implements Callable<Object> {
    protected Closure(Object owner, Object thisObject) {
    }
}
