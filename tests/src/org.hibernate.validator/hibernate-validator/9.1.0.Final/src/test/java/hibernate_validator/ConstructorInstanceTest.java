/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package hibernate_validator;

import org.hibernate.validator.internal.util.actions.ConstructorInstance;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorInstanceTest {
    @Test
    void invokesPublicConstructorWithArguments() throws NoSuchMethodException {
        Constructor<MessageHolder> constructor = MessageHolder.class.getConstructor(String.class);

        MessageHolder holder = ConstructorInstance.action(constructor, "constructed by Hibernate Validator");

        assertThat(holder.message()).isEqualTo("constructed by Hibernate Validator");
    }

    public static final class MessageHolder {
        private final String message;

        public MessageHolder(String message) {
            this.message = message;
        }

        String message() {
            return message;
        }
    }
}
