/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.mockito.mock.SerializableMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class ModuleHandlerInnerModuleSystemFoundTest {
    @Test
    void subclassMockMakerCreatesConcreteMockWithAdditionalInterface() {
        ClassBackedGreeter greeter =
                Mockito.mock(
                        ClassBackedGreeter.class,
                        withSettings()
                                .mockMaker(MockMakers.SUBCLASS)
                                .defaultAnswer(CALLS_REAL_METHODS)
                                .extraInterfaces(Tagged.class));

        when(greeter.prefix()).thenReturn("Hi");
        String greeting = greeter.greet("Ada");
        ((Tagged) greeter).tag("module-handler");

        assertThat(greeting).isEqualTo("Hi Ada");
        verify(greeter, atLeastOnce()).prefix();
        verify(greeter).greet("Ada");
        verify((Tagged) greeter).tag("module-handler");
        assertThat(invocationNames(greeter)).contains("prefix", "greet", "tag");
        assertThat(Mockito.mockingDetails(greeter).getMockCreationSettings().getMockMaker())
                .isEqualTo(MockMakers.SUBCLASS);
    }

    @Test
    void subclassMockMakerCreatesCrossClassLoaderSerializableClassMock() {
        ClassBackedGreeter greeter =
                Mockito.mock(
                        ClassBackedGreeter.class,
                        withSettings()
                                .mockMaker(MockMakers.SUBCLASS)
                                .serializable(SerializableMode.ACROSS_CLASSLOADERS));

        when(greeter.greet("Grace")).thenReturn("Hello Grace");

        assertThat(greeter.greet("Grace")).isEqualTo("Hello Grace");
        verify(greeter, atLeastOnce()).greet("Grace");
        assertThat(invocationNames(greeter)).contains("greet");
        assertThat(Mockito.mockingDetails(greeter).getMockCreationSettings().getMockMaker())
                .isEqualTo(MockMakers.SUBCLASS);
    }

    private static Collection<String> invocationNames(Object mock) {
        return Mockito.mockingDetails(mock).getInvocations().stream()
                .map(Invocation::getMethod)
                .map(method -> method.getName())
                .toList();
    }

    public static class ClassBackedGreeter {
        public String prefix() {
            return "Hello";
        }

        public String greet(String name) {
            return prefix() + " " + name;
        }
    }

    public interface Tagged {
        void tag(String value);
    }
}
