/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class SerializationUtilsTest {

    @Test
    public void cloneCreatesIndependentCopyOfSerializableCollection() {
        final ArrayList<String> original = new ArrayList<>(Arrays.asList("one", "two"));

        final ArrayList<String> clone = SerializationUtils.clone(original);
        clone.add("three");

        assertThat(clone).isNotSameAs(original).containsExactly("one", "two", "three");
        assertThat(original).containsExactly("one", "two");
    }

    @Test
    public void serializeAndDeserializeRoundTripThroughStreams() {
        final ArrayList<String> original = new ArrayList<>(Arrays.asList("alpha", "beta"));
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        SerializationUtils.serialize(original, outputStream);
        final ArrayList<String> deserialized = SerializationUtils.deserialize(
                new ByteArrayInputStream(outputStream.toByteArray()));

        assertThat(outputStream.toByteArray()).isNotEmpty();
        assertThat(deserialized).isNotSameAs(original).containsExactly("alpha", "beta");
    }
}
