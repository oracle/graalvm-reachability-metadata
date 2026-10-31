/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_portable_scala.portable_scala_reflect_2_13;

import org.junit.jupiter.api.Test;
import org.portablescala.reflect.InstantiatableClass;
import org.portablescala.reflect.InvokableConstructor;
import org.portablescala.reflect.Reflect;
import org.portablescala.reflect.annotation.EnableReflectiveInstantiation;
import scala.jdk.javaapi.CollectionConverters;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class InstantiatableClassTest {
    @Test
    void discoversPublicConstructors() {
        InstantiatableClass descriptor = lookupMessage();
        List<InvokableConstructor> constructors = CollectionConverters.asJava(descriptor.declaredConstructors());

        assertThat(descriptor.runtimeClass()).isEqualTo(Message.class);
        assertThat(constructors).extracting(constructor -> constructor.parameterTypes().size())
                .containsExactlyInAnyOrder(0, 2);
        InvokableConstructor parameterized = constructors.stream()
                .filter(constructor -> constructor.parameterTypes().size() == 2)
                .findFirst().orElseThrow();
        assertThat(CollectionConverters.asJava(parameterized.parameterTypes()))
                .containsExactly(String.class, int.class);
    }

    @Test
    void createsAnInstanceWithTheDefaultConstructor() {
        InstantiatableClass descriptor = lookupMessage();

        Message message = (Message) descriptor.newInstance();

        assertThat(message.render()).isEqualTo("ready");
    }

    private static InstantiatableClass lookupMessage() {
        return Reflect.lookupInstantiatableClass(Message.class.getName(), Message.class.getClassLoader()).get();
    }

    @EnableReflectiveInstantiation
    public static class Message {
        private final String text;
        private final int repetitions;

        public Message() {
            this("ready", 1);
        }

        public Message(String text, int repetitions) {
            this.text = text;
            this.repetitions = repetitions;
        }

        public String render() {
            return text.repeat(repetitions);
        }
    }
}
