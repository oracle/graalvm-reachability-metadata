/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.util.ReflectUtil;
import org.apache.calcite.util.ReflectiveVisitor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectUtilAnonymous2Test {
    @Test
    void invokesMethodThroughReflectiveDispatcher() {
        SoundVisitor visitor = new SoundVisitor();
        ReflectUtil.MethodDispatcher<String> dispatcher = ReflectUtil.createMethodDispatcher(
                String.class, visitor, "sound", Animal.class);

        assertThat(dispatcher.invoke(new Dog())).isEqualTo("woof");
    }

    public static class Animal {
    }

    public static class Dog extends Animal {
    }

    public static class SoundVisitor implements ReflectiveVisitor {
        public String sound(Animal animal) {
            return "unknown";
        }

        public String sound(Dog dog) {
            return "woof";
        }
    }
}
