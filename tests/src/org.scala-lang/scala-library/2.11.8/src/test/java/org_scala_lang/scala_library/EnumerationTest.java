/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import scala.Enumeration;
import scala.math.BigDecimal.RoundingMode$;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises reflective value naming and singleton restoration. §FS-repository-functional-spec.5.2 */
public class EnumerationTest {

    @Test
    void resolvesValueNamesAndRestoresTheCanonicalEnumeration() throws Exception {
        RoundingMode$ roundingModes = RoundingMode$.MODULE$;

        Enumeration.Value halfEven = roundingModes.withName("HALF_EVEN");

        assertThat(halfEven).isSameAs(roundingModes.HALF_EVEN());
        assertThat(halfEven.toString()).isEqualTo("HALF_EVEN");
        assertThat(roundTrip(roundingModes)).isSameAs(roundingModes);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
