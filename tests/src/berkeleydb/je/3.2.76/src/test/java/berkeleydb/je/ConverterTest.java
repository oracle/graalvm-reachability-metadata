/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.persist.evolve.Conversion;
import com.sleepycat.persist.evolve.Converter;
import com.sleepycat.persist.model.EntityModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link Converter} validation of user supplied conversions.
 */
public class ConverterTest {

    @Test
    void converterAcceptsConversionWithExplicitEqualsImplementation() {
        IntToStringConversion conversion = new IntToStringConversion();

        Converter converter = new Converter(
            ConvertedRecord.class.getName(),
            0,
            "numericValue",
            conversion);

        assertThat(converter.getConversion()).isSameAs(conversion);
        assertThat(converter.getConversion().convert(Integer.valueOf(17))).isEqualTo("17");
        assertThat(converter.toString()).contains(ConvertedRecord.class.getName(), "numericValue");
    }

    private static final class IntToStringConversion implements Conversion {
        private static final long serialVersionUID = 1L;

        @Override
        public void initialize(EntityModel model) {
        }

        @Override
        public Object convert(Object fromValue) {
            return String.valueOf(fromValue);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof IntToStringConversion;
        }

        @Override
        public int hashCode() {
            return IntToStringConversion.class.hashCode();
        }
    }

    private static final class ConvertedRecord {
        private String numericValue;
    }
}
