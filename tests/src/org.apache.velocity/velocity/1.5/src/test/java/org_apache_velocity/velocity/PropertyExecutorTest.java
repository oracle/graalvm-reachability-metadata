/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.apache.velocity.runtime.parser.node.PropertyExecutor;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;

public class PropertyExecutorTest {
    @Test
    void executeInvokesResolvedPropertyGetter() throws Exception {
        Log log = new Log(new NullLogSystem());
        PropertyExecutor executor = new PropertyExecutor(log, new Introspector(log), Person.class, "displayName");
        Person person = new Person("Ada Lovelace");

        Object result = executor.execute(person);

        assertThat(executor.isAlive()).isTrue();
        assertThat(result).isEqualTo("Ada Lovelace");
        assertThat(person.invocationCount).isEqualTo(1);
    }

    public static final class Person {
        private final String displayName;
        private int invocationCount;

        Person(final String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            invocationCount++;
            return displayName;
        }
    }
}
