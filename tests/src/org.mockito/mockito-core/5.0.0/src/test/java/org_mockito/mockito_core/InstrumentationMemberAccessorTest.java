/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.Invocation;
import org.mockito.plugins.MemberAccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InstrumentationMemberAccessorTest {
    @Mock
    private Repository repository;

    @Captor
    private ArgumentCaptor<String> savedValueCaptor;

    @InjectMocks
    private CollaboratingService service;

    @Test
    void annotationsInjectMocksCaptorAndConstructorCreatedSubject() throws Exception {
        try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
            when(repository.findGreeting(eq("Mockito"))).thenReturn("Hello");

            String greeting = service.greet("Mockito");

            assertThat(greeting).isEqualTo("Hello Mockito");
            verify(repository).save(savedValueCaptor.capture());
            assertThat(savedValueCaptor.getValue()).isEqualTo("Hello Mockito");
        }
    }

    @Test
    void delegatedInterfaceMockInvokesRealDelegateThroughDefaultAnswer() {
        Calculator delegate = new RecordingCalculator("sum");
        Calculator calculator = Mockito.mock(Calculator.class, delegatesTo(delegate));

        String result = calculator.describe("answer", 19, 23);

        assertThat(result).isEqualTo("sum:answer=42");
        verify(calculator).describe(anyString(), eq(19), eq(23));
        Invocation invocation =
                Mockito.mockingDetails(calculator).getInvocations().iterator().next();
        assertThat(invocation.getMethod().getName()).isEqualTo("describe");
        assertThat(invocation.getArguments()).containsExactly("answer", 19, 23);
    }

    @Test
    void defaultMemberAccessorConstructsInvokesReadsAndWritesMembers() throws Exception {
        MemberAccessor accessor =
                Mockito.framework().getPlugins().getDefaultPlugin(MemberAccessor.class);
        Constructor<ReflectiveSubject> constructor =
                ReflectiveSubject.class.getDeclaredConstructor(String.class);
        Method rename = ReflectiveSubject.class.getDeclaredMethod("rename", String.class);
        Field name = ReflectiveSubject.class.getDeclaredField("name");

        ReflectiveSubject subject = (ReflectiveSubject) accessor.newInstance(constructor, "initial");
        Object renamed = accessor.invoke(rename, subject, "updated");
        accessor.set(name, subject, "field-value");

        assertThat(renamed).isEqualTo("updated");
        assertThat(accessor.get(name, subject)).isEqualTo("field-value");
    }

    interface Repository {
        String findGreeting(String name);

        void save(String value);
    }

    static final class CollaboratingService {
        private final Repository repository;

        private CollaboratingService(Repository repository) {
            this.repository = repository;
        }

        String greet(String name) {
            String value = repository.findGreeting(name) + " " + name;
            repository.save(value);
            return value;
        }
    }

    interface Calculator {
        String describe(String label, int left, int right);
    }

    static final class RecordingCalculator implements Calculator {
        private final String prefix;

        RecordingCalculator(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String describe(String label, int left, int right) {
            return prefix + ":" + label + "=" + (left + right);
        }
    }

    static final class ReflectiveSubject {
        private String name;

        private ReflectiveSubject(String name) {
            this.name = name;
        }

        private String rename(String newName) {
            this.name = newName;
            return name;
        }
    }
}
