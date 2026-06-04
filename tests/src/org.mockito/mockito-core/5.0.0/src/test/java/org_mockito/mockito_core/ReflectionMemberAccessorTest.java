/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionMemberAccessorTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void reflectionMemberAccessorInitializesAnnotatedFieldsAndInjectMocks() throws Exception {
        final AnnotationFixture fixture = new AnnotationFixture();

        try (AutoCloseable mocks = MockitoAnnotations.openMocks(fixture)) {
            Mockito.when(fixture.dependency().greet("Mockito")).thenReturn("mocked");

            assertThat(fixture.description("Mockito")).isEqualTo("component:mocked");
        }
    }

    @Test
    void reflectionMemberAccessorInvokesMethodsForDelegatingAnswers() {
        final GreetingService service =
                Mockito.mock(
                        GreetingService.class,
                        AdditionalAnswers.delegatesTo(new GreetingDelegate("delegate")));

        assertThat(service.greet("Mockito")).isEqualTo("delegate:Mockito");
    }

    public interface GreetingService {
        String greet(String name);
    }

    private static final class AnnotationFixture {
        @Mock private GreetingService dependency;

        @InjectMocks private Component component;

        private GreetingService dependency() {
            return dependency;
        }

        private String description(String name) {
            return component.describe(name);
        }
    }

    private static final class Component {
        private final GreetingService dependency;
        private final String prefix;

        private Component(GreetingService dependency) {
            this.dependency = dependency;
            this.prefix = "component";
        }

        private String describe(String name) {
            return prefix + ":" + dependency.greet(name);
        }
    }

    private static final class GreetingDelegate implements GreetingService {
        private final String prefix;

        private GreetingDelegate(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String greet(String name) {
            return prefix + ":" + name;
        }
    }
}
