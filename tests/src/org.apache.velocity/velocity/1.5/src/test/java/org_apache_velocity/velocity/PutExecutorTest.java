/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.apache.velocity.runtime.parser.node.PutExecutor;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;

public class PutExecutorTest {
    @Test
    void invokesDiscoveredPutMethodWithConfiguredPropertyName() throws IllegalAccessException, InvocationTargetException {
        Log log = new Log(new NullLogSystem());
        PutExecutor executor = new PutExecutor(log, new Introspector(log), PropertySink.class, "Velocity", "name");
        PropertySink sink = new PropertySink();

        Object result = executor.execute(sink, "Velocity");

        assertThat(executor.isAlive()).isTrue();
        assertThat(result).isEqualTo("stored name=Velocity");
        assertThat(sink.propertyName).isEqualTo("name");
        assertThat(sink.propertyValue).isEqualTo("Velocity");
    }

    public static final class PropertySink {
        private String propertyName;
        private Object propertyValue;

        public String put(String name, Object value) {
            propertyName = name;
            propertyValue = value;
            return "stored " + name + "=" + value;
        }
    }
}
