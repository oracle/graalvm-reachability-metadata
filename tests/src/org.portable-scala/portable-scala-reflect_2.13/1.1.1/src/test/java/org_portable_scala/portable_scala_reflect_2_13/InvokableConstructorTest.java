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
import org_portable_scala.portable_scala_reflect_2_13.InstantiatableClassTest.Message;
import scala.jdk.javaapi.CollectionConverters;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class InvokableConstructorTest {
    @Test
    void looksUpAndInvokesAConstructorByParameterTypes() {
        InstantiatableClass descriptor = Reflect.lookupInstantiatableClass(
                Message.class.getName(), Message.class.getClassLoader()).get();
        InvokableConstructor constructor = descriptor.getConstructor(
                CollectionConverters.asScala(List.<Class<?>>of(String.class, int.class)).toList()).get();

        Message message = (Message) constructor.newInstance(
                CollectionConverters.asScala(List.<Object>of("ha", 3)).toList());

        assertThat(message.render()).isEqualTo("hahaha");
    }
}
