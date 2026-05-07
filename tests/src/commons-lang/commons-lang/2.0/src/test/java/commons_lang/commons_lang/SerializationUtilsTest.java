/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_lang.commons_lang;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang.SerializationUtils;
import org.junit.jupiter.api.Test;

public class SerializationUtilsTest {

    @Test
    public void roundTripsSerializableObjectsThroughByteArraySerialization() {
        byte[] serialized = SerializationUtils.serialize("commons-lang");

        Object deserialized = SerializationUtils.deserialize(serialized);

        assertThat(deserialized).isEqualTo("commons-lang");
    }
}
