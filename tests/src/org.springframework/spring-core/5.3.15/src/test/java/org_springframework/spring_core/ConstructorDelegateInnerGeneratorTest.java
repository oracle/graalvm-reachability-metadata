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
import org.springframework.cglib.reflect.ConstructorDelegate;

public class ConstructorDelegateInnerGeneratorTest {

    @Test
    void createsDelegateForInterfaceMatchingTargetConstructor() {
        try {
            ConstructorDelegate delegate = ConstructorDelegate.create(Message.class, MessageFactory.class);
            MessageFactory factory = (MessageFactory) delegate;

            Message message = factory.newInstance("Spring", 3);

            assertThat(delegate).isInstanceOf(MessageFactory.class);
            assertThat(message.getText()).isEqualTo("Spring Spring Spring");
            assertThat(message.getCount()).isEqualTo(3);
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

    public interface MessageFactory {

        Message newInstance(String word, int count);
    }

    public static class Message {

        private final String word;

        private final int count;

        public Message(String word, int count) {
            this.word = word;
            this.count = count;
        }

        public String getText() {
            StringBuilder text = new StringBuilder();
            for (int index = 0; index < count; index++) {
                if (index > 0) {
                    text.append(' ');
                }
                text.append(word);
            }
            return text.toString();
        }

        public int getCount() {
            return count;
        }
    }
}
