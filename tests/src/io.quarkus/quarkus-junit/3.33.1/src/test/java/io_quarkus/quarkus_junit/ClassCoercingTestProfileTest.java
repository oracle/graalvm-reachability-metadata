/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.quarkus.test.junit.ClassCoercingTestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import org.junit.jupiter.api.Test;

public class ClassCoercingTestProfileTest {
    @Test
    void delegatesAllProfileMethodsWhenObjectAlreadyImplementsProfile() {
        QuarkusTestProfile delegate = new CompleteProfile();
        ClassCoercingTestProfile profile = new ClassCoercingTestProfile(delegate);

        assertThat(profile.getConfigOverrides()).containsEntry("quarkus.test.profile", "coerced");
        assertThat(profile.getEnabledAlternatives()).containsExactly(String.class);
        assertThat(profile.getConfigProfile()).isEqualTo("integration");
        assertThat(profile.testResources()).hasSize(1);
        assertThat(profile.disableGlobalTestResources()).isTrue();
        assertThat(profile.tags()).containsExactlyInAnyOrder("native", "fast");
        assertThat(profile.commandLineParameters()).containsExactly("--debug", "false");
        assertThat(profile.runMainMethod()).isTrue();
        assertThat(profile.disableApplicationLifecycleObservers()).isTrue();
    }

    private static final class CompleteProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.test.profile", "coerced");
        }

        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.<Class<?>>of(String.class);
        }

        @Override
        public String getConfigProfile() {
            return "integration";
        }

        @Override
        public List<QuarkusTestProfile.TestResourceEntry> testResources() {
            QuarkusTestProfile.TestResourceEntry entry = new QuarkusTestProfile.TestResourceEntry(
                    QuarkusTestProfileTest.SampleResource.class, Map.of("name", "resource"));
            return List.of(entry);
        }

        @Override
        public boolean disableGlobalTestResources() {
            return true;
        }

        @Override
        public Set<String> tags() {
            return Set.of("native", "fast");
        }

        @Override
        public String[] commandLineParameters() {
            return new String[] { "--debug", "false" };
        }

        @Override
        public boolean runMainMethod() {
            return true;
        }

        @Override
        public boolean disableApplicationLifecycleObservers() {
            return true;
        }
    }
}
