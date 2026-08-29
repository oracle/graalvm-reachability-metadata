/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cglib.transform.impl.AddDelegateTransformer;

/** Verifies validation of a delegate implementation supplied to a CGLIB transformer. */
public class AddDelegateTransformerTest {
    @Test
    void acceptsDelegateWithObjectConstructor() {
        AddDelegateTransformer transformer = new AddDelegateTransformer(
                new Class<?>[] {GreetingOperations.class}, GreetingDelegate.class);

        assertThat(transformer).isNotNull();
    }

    public interface GreetingOperations {
        String greet(String name);
    }

    public static final class GreetingDelegate {
        public GreetingDelegate(Object target) {
        }

        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
