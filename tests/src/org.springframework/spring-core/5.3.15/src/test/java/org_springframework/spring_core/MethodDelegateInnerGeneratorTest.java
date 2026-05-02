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
import org.springframework.cglib.core.CodeGenerationException;
import org.springframework.cglib.reflect.MethodDelegate;

public class MethodDelegateInnerGeneratorTest {

    @Test
    void createsDelegateForNamedTargetMethodAndSingleMethodInterface() {
        MessageService service = new MessageService("Hello");

        try {
            MethodDelegate delegate = MethodDelegate.create(service, "compose", TextOperation.class);
            TextOperation operation = (TextOperation) delegate;

            assertThat(delegate.getTarget()).isSameAs(service);
            assertThat(operation.apply("Spring", 2)).isEqualTo("Hello Spring Spring");
            assertThat(delegate.newInstance(new MessageService("Hi")))
                    .isInstanceOfSatisfying(TextOperation.class, replacement ->
                            assertThat(replacement.apply("Core", 3)).isEqualTo("Hi Core Core Core"));
        }
        catch (CodeGenerationException ex) {
            ignoreUnsupportedDynamicClassLoading(ex);
        }
        catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
        }
    }

    private static void ignoreUnsupportedDynamicClassLoading(CodeGenerationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) cause)) {
            return;
        }
        throw ex;
    }

    private static void ignoreUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public interface TextOperation {

        String apply(String value, int times);
    }

    public static class MessageService {

        private final String prefix;

        public MessageService(String prefix) {
            this.prefix = prefix;
        }

        public String compose(String value, int times) {
            StringBuilder message = new StringBuilder(prefix);
            for (int index = 0; index < times; index++) {
                message.append(' ').append(value);
            }
            return message.toString();
        }
    }
}
