/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTestProfile;
import org.junit.jupiter.api.Test;

public class QuarkusTestProfileTest {
    @Test
    void defaultProfileMethodsReturnEmptyOrDisabledValues() {
        QuarkusTestProfile profile = new QuarkusTestProfile() {
        };

        assertThat(profile.getConfigOverrides()).isEmpty();
        assertThat(profile.getEnabledAlternatives()).isEmpty();
        assertThat(profile.getConfigProfile()).isNull();
        assertThat(profile.testResources()).isEmpty();
        assertThat(profile.disableGlobalTestResources()).isFalse();
        assertThat(profile.tags()).isEmpty();
        assertThat(profile.commandLineParameters()).isEmpty();
        assertThat(profile.runMainMethod()).isFalse();
        assertThat(profile.disableApplicationLifecycleObservers()).isFalse();
    }

    @Test
    void testResourceEntryPreservesManagerArgumentsAndParallelFlag() {
        Map<String, String> arguments = Map.of("host", "localhost", "port", "0");
        QuarkusTestProfile.TestResourceEntry entry = new QuarkusTestProfile.TestResourceEntry(
                SampleResource.class, arguments, true);

        assertThat(entry.getClazz()).isEqualTo(SampleResource.class);
        assertThat(entry.getArgs()).containsExactlyInAnyOrderEntriesOf(arguments);
        assertThat(entry.isParallel()).isTrue();
    }

    public static final class SampleResource implements QuarkusTestResourceLifecycleManager {
        @Override
        public Map<String, String> start() {
            return Map.of("sample.started", "true");
        }

        @Override
        public void stop() {
        }
    }
}
