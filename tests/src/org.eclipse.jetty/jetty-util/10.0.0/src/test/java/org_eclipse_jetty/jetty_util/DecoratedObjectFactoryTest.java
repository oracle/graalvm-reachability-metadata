/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.util.DecoratedObjectFactory;
import org.eclipse.jetty.util.Decorator;
import org.eclipse.jetty.util.Utf8StringBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DecoratedObjectFactoryTest {
    @Test
    void constructsDecoratesAndDestroysAnInstance() throws Exception {
        DecoratedObjectFactory factory = new DecoratedObjectFactory();
        List<Object> destroyed = new ArrayList<>();
        factory.addDecorator(new Decorator() {
            @Override
            public <T> T decorate(T object) {
                if (object instanceof Utf8StringBuilder builder) {
                    builder.append("decorated");
                }
                return object;
            }

            @Override
            public void destroy(Object object) {
                destroyed.add(object);
            }
        });

        Utf8StringBuilder builder = factory.createInstance(Utf8StringBuilder.class);
        assertEquals("decorated", builder.toString());
        builder.append(" payload");
        assertEquals("decorated payload", builder.toString());
        factory.destroy(builder);
        assertEquals(1, destroyed.size());
        assertSame(builder, destroyed.get(0));
    }
}
