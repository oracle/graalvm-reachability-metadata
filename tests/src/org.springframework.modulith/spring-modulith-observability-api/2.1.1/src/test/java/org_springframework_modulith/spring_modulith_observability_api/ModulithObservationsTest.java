/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_observability_api;

import java.util.Arrays;

import io.micrometer.common.KeyValue;
import io.micrometer.common.docs.KeyName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.observability.ModulithContext;
import org.springframework.modulith.observability.ModulithObservations;

import static org.assertj.core.api.Assertions.assertThat;

public class ModulithObservationsTest {

    @Test
    void documentsTheTagsEmittedByModuleInvocations() {
        ModulithContext context = ObservationFixtures.context(false);
        ModulithObservations documentation = ModulithObservations.MODULE_ENTRY;

        assertThat(context.getLowCardinalityValues()).extracting(KeyValue::getKey)
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(documentation.getLowCardinalityKeyNames())
                                .map(KeyName::asString).toList());
        assertThat(context.getHighCardinalityValues()).extracting(KeyValue::getKey)
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(documentation.getHighCardinalityKeyNames())
                                .map(KeyName::asString).toList());
        assertThat(documentation.getEvents()).isEmpty();
    }
}
