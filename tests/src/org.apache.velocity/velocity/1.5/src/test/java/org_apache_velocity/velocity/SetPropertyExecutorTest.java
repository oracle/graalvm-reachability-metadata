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
import org.apache.velocity.runtime.parser.node.SetPropertyExecutor;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;

public class SetPropertyExecutorTest {
    @Test
    void executeInvokesResolvedSetterMethod() throws IllegalAccessException, InvocationTargetException {
        Log log = new Log(new NullLogSystem());
        SetPropertyExecutor executor = new SetPropertyExecutor(log, new Introspector(log), Person.class, "name", "initial");
        Person person = new Person();

        Object result = executor.execute(person, "Ada Lovelace");

        assertThat(executor.isAlive()).isTrue();
        assertThat(result).isNull();
        assertThat(person.name).isEqualTo("Ada Lovelace");
        assertThat(person.invocationCount).isEqualTo(1);
    }

    public static final class Person {
        private String name;
        private int invocationCount;

        public void setName(String name) {
            invocationCount++;
            this.name = name;
        }
    }
}
