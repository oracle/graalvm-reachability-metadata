/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.mvstore.type.ObjectDataType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectDataTypeTest {
    @Test
    void serializesAndDeserializesObjects() {
        String value = "serialized mvstore payload";

        byte[] serialized = ObjectDataType.serialize(value);
        Object deserialized = ObjectDataType.deserialize(serialized);

        assertThat(serialized).isNotEmpty();
        assertThat(deserialized).isEqualTo(value);
    }
}
