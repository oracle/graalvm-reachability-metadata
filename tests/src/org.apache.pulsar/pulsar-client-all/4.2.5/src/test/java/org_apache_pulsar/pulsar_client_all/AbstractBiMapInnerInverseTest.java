/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.Map;
import org.apache.pulsar.shade.com.google.common.collect.BiMap;
import org.apache.pulsar.shade.com.google.common.collect.EnumBiMap;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class AbstractBiMapInnerInverseTest {

    @Test
    void preservesAnInverseViewAndItsForwardMapping() {
        final EnumBiMap<State, Result> source = EnumBiMap.create(State.class, Result.class);
        source.put(State.PENDING, Result.ACCEPTED);
        source.put(State.COMPLETE, Result.STORED);
        final BiMap<Result, State> inverse = source.inverse();

        final Map<?, ?> restored = (Map<?, ?>) SerializationUtils.clone((Serializable) inverse);

        assertThat(restored.get(Result.ACCEPTED)).isEqualTo(State.PENDING);
        assertThat(restored.get(Result.STORED)).isEqualTo(State.COMPLETE);
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
