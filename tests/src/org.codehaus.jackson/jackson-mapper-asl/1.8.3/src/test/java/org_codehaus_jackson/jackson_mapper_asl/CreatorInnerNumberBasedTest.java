/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class CreatorInnerNumberBasedTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long LONG_VALUE = 2147483648L;

    @Test
    public void deserializesIntegerUsingNumberBasedConstructor() throws Exception {
        IntegerConstructorTarget target = MAPPER.readValue("37", IntegerConstructorTarget.class);

        assertThat(target.value).isEqualTo(37);
    }

    @Test
    public void deserializesIntegerUsingNumberBasedFactoryMethod() throws Exception {
        IntegerFactoryTarget target = MAPPER.readValue("38", IntegerFactoryTarget.class);

        assertThat(target.value).isEqualTo(38);
        assertThat(target.createdByFactory).isTrue();
    }

    @Test
    public void deserializesLongUsingNumberBasedConstructor() throws Exception {
        LongConstructorTarget target = MAPPER.readValue(Long.toString(LONG_VALUE), LongConstructorTarget.class);

        assertThat(target.value).isEqualTo(LONG_VALUE);
    }

    @Test
    public void deserializesLongUsingNumberBasedFactoryMethod() throws Exception {
        LongFactoryTarget target = MAPPER.readValue(Long.toString(LONG_VALUE + 1L), LongFactoryTarget.class);

        assertThat(target.value).isEqualTo(LONG_VALUE + 1L);
        assertThat(target.createdByFactory).isTrue();
    }

    public static final class IntegerConstructorTarget {
        final int value;

        @JsonCreator
        public IntegerConstructorTarget(int value) {
            this.value = value;
        }
    }

    public static final class IntegerFactoryTarget {
        final int value;
        final boolean createdByFactory;

        private IntegerFactoryTarget(int value, boolean createdByFactory) {
            this.value = value;
            this.createdByFactory = createdByFactory;
        }

        @JsonCreator
        public static IntegerFactoryTarget fromNumber(int value) {
            return new IntegerFactoryTarget(value, true);
        }
    }

    public static final class LongConstructorTarget {
        final long value;

        @JsonCreator
        public LongConstructorTarget(long value) {
            this.value = value;
        }
    }

    public static final class LongFactoryTarget {
        final long value;
        final boolean createdByFactory;

        private LongFactoryTarget(long value, boolean createdByFactory) {
            this.value = value;
            this.createdByFactory = createdByFactory;
        }

        @JsonCreator
        public static LongFactoryTarget fromNumber(long value) {
            return new LongFactoryTarget(value, true);
        }
    }
}
