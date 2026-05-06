/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_junit_jupiter;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class MockitoJunitJupiterTest {
    @Mock
    private GreetingRepository repository;

    @Captor
    private ArgumentCaptor<String> greetingCaptor;

    @Spy
    private AuditLog audit = new AuditLog();

    @InjectMocks
    private GreetingService service;

    private GreetingRepository lifecycleRepository;

    @BeforeEach
    void initializeLifecycleMock(@Mock GreetingRepository lifecycleRepository) {
        this.lifecycleRepository = lifecycleRepository;
    }

    @Test
    void extensionInitializesAnnotatedFieldsAndInjectsMocks() {
        when(repository.findGreeting("Ada")).thenReturn("Hello Ada");

        String greeting = service.greet("Ada");

        verify(repository).saveGreeting(greetingCaptor.capture());
        verify(audit).record("Hello Ada");
        assertThat(greeting).isEqualTo("Hello Ada");
        assertThat(greetingCaptor.getValue()).isEqualTo("Hello Ada");
        assertThat(audit.entries()).containsExactly("Hello Ada");
        assertThat(mockingDetails(repository).isMock()).isTrue();
        assertThat(mockingDetails(audit).isSpy()).isTrue();
    }

    @Test
    void extensionResolvesMockParametersForTestMethods(@Mock GreetingRepository parameterRepository) {
        when(parameterRepository.findGreeting("Grace")).thenReturn("Hi Grace");

        assertThat(parameterRepository.findGreeting("Grace")).isEqualTo("Hi Grace");
        assertThat(mockingDetails(parameterRepository).isMock()).isTrue();
        assertThat(parameterRepository).isNotSameAs(repository);
    }

    @Test
    void extensionResolvesMockParametersForLifecycleMethods() {
        when(lifecycleRepository.findGreeting("Katherine")).thenReturn("Welcome Katherine");

        assertThat(lifecycleRepository.findGreeting("Katherine")).isEqualTo("Welcome Katherine");
        assertThat(mockingDetails(lifecycleRepository).isMock()).isTrue();
        assertThat(lifecycleRepository).isNotSameAs(repository);
    }

    @Test
    void mockitoSettingsCanMakeUnusedStubbingLenient() {
        when(repository.findGreeting("unused")).thenReturn("unused value");

        assertThat(mockingDetails(repository).isMock()).isTrue();
    }

    public interface GreetingRepository {
        String findGreeting(String name);

        void saveGreeting(String greeting);
    }

    public static class GreetingService {
        private final GreetingRepository repository;
        private final AuditLog audit;

        public GreetingService(GreetingRepository repository, AuditLog audit) {
            this.repository = repository;
            this.audit = audit;
        }

        public String greet(String name) {
            String greeting = repository.findGreeting(name);
            repository.saveGreeting(greeting);
            audit.record(greeting);
            return greeting;
        }
    }

    public static class AuditLog {
        private final List<String> entries = new ArrayList<>();

        public void record(String entry) {
            entries.add(entry);
        }

        public List<String> entries() {
            return List.copyOf(entries);
        }
    }
}
