/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.reflect.Invokable;
import org.testcontainers.shaded.com.google.common.reflect.TypeToken;

import static org.assertj.core.api.Assertions.assertThat;

public class InvokableInnerConstructorInvokableTest {
    @Test
    void invokesAConstructorRepresentedByAnInvokable() throws Exception {
        Invokable<Value, Value> constructor = TypeToken.of(Value.class)
            .constructor(Value.class.getConstructor(String.class));

        assertThat(constructor.invoke(null, "constructed").text).isEqualTo("constructed");
    }

    public static class Value {
        private final String text;

        public Value(String text) {
            this.text = text;
        }
    }
}
