/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;

import net.sourceforge.htmlunit.corejs.javascript.ObjArray;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ObjArrayTest {
    @Test
    void serializationRoundTripPreservesElementsStoredInFieldsAndBackingArray() throws Exception {
        ObjArray array = new ObjArray();
        List<Object> values = Arrays.asList("zero", "one", "two", "three", "four", "five", "six");
        for (Object value : values) {
            array.add(value);
        }
        array.seal();

        ObjArray copy = serializeAndDeserialize(array);

        assertThat(copy).isNotSameAs(array);
        assertThat(copy.isSealed()).isTrue();
        assertThat(copy.size()).isEqualTo(values.size());
        assertThat(copy.toArray()).containsExactlyElementsOf(values);
        assertThatThrownBy(() -> copy.add("blocked")).isInstanceOf(IllegalStateException.class);
    }

    private static ObjArray serializeAndDeserialize(ObjArray array) throws Exception {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(array);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object value = input.readObject();
            assertThat(value).isInstanceOf(ObjArray.class);
            return (ObjArray) value;
        }
    }
}
