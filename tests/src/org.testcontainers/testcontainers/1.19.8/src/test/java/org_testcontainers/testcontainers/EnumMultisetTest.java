/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.EnumMultiset;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumMultisetTest {
    @Test
    void preservesEnumCountsWhenSerialized() {
        EnumMultiset<Value> values = EnumMultiset.create(Value.class);
        values.add(Value.ONE, 3);
        EnumMultiset<Value> restored = SerializationUtils.roundtrip(values);

        assertThat(restored.count(Value.ONE)).isEqualTo(3);
    }

    public enum Value { ONE }
}
