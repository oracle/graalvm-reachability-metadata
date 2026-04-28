/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time.joda_time;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.joda.time.DateTimeZone;
import org.joda.time.chrono.ISOChronology;
import org.junit.jupiter.api.Test;

public class ISOChronologyInnerStubTest {

    @Test
    void serializesAndDeserializesChronologyViaStub() throws Exception {
        ISOChronology original = ISOChronology.getInstance(DateTimeZone.forID("Europe/Paris"));

        ISOChronology restored = deserialize(serialize(original));

        assertThat(restored).isSameAs(original);
        assertThat(restored.getZone()).isEqualTo(original.getZone());
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
    }

    private static ISOChronology deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (ISOChronology) objectInput.readObject();
        }
    }
}
