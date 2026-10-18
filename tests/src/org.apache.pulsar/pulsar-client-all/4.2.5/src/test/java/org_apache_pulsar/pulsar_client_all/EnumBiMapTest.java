/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.EnumBiMap;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class EnumBiMapTest {

    @Test
    void preservesEnumTypesAndBidirectionalMappings() {
        final EnumBiMap<State, Result> source = EnumBiMap.create(State.class, Result.class);
        source.put(State.PENDING, Result.ACCEPTED);
        source.put(State.COMPLETE, Result.STORED);

        final EnumBiMap<State, Result> restored = SerializationUtils.clone(source);

        assertThat(restored.keyType()).isEqualTo(State.class);
        assertThat(restored.valueType()).isEqualTo(Result.class);
        assertThat(restored.inverse().get(Result.STORED)).isEqualTo(State.COMPLETE);
    }

    private enum State {
        PENDING,
        COMPLETE
    }

    private enum Result {
        ACCEPTED,
        STORED
    }
}
