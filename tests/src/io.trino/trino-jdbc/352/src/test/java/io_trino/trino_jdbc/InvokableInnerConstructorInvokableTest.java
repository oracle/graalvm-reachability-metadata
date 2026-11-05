/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.reflect.Invokable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InvokableInnerConstructorInvokableTest {
    @Test
    void wrapsAndInvokesPublicConstructor() throws Exception {
        Invokable<StringBuilder, StringBuilder> constructor = Invokable.from(StringBuilder.class.getConstructor(String.class));
        assertEquals("seed", constructor.invoke(null, "seed").toString());
    }
}
