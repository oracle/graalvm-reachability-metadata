/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.cglib.reflect.ConstructorDelegate;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ConstructorDelegateTest {
    @Test
    void createsDelegateBackedByMatchingConstructor() {
        try {
            MessageFactory factory = (MessageFactory) ConstructorDelegate.create(
                    Message.class,
                    MessageFactory.class);

            Message message = factory.newInstance("Ada", 3);

            assertThat(factory).isInstanceOf(ConstructorDelegate.class);
            assertThat(message.format()).isEqualTo("Ada #3");
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    private static boolean isUnsupportedNativeImageDynamicClassLoading(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error
                    && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public interface MessageFactory {
        Message newInstance(String name, int sequence);
    }

    public static class Message {
        private final String name;
        private final int sequence;

        public Message(String name, int sequence) {
            this.name = name;
            this.sequence = sequence;
        }

        public String format() {
            return name + " #" + sequence;
        }
    }
}
