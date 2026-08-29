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

/** Verifies delegate transformer validation through its public constructor. */
public class AddDelegateTransformerTest {
    @Test
    void acceptsDelegateWithObjectConstructor() {
        AddDelegateTransformer transformer = new AddDelegateTransformer(
                new Class<?>[] {Greeting.class}, GreetingDelegate.class);

        assertThat(transformer).isNotNull();
    }

    public interface Greeting {
        String greet();
    }

    public static final class GreetingDelegate implements Greeting {
        public GreetingDelegate(Object target) { }

        @Override
        public String greet() {
            return "hello";
        }
    }
}
