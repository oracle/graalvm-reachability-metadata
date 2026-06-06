/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.lang.builder.HashCodeBuilder;
import org.junit.jupiter.api.Test;

public class HashCodeBuilderTest {

    @Test
    public void reflectionHashCodeReadsPrivateInstanceFields() {
        HashRecord first = new HashRecord("alpha", 1, "session-one");
        HashRecord sameValues = new HashRecord("alpha", 1, "session-two");
        HashRecord differentRank = new HashRecord("alpha", 2, "session-one");

        int firstHash = HashCodeBuilder.reflectionHashCode(first);
        int sameValuesHash = HashCodeBuilder.reflectionHashCode(sameValues);
        int differentRankHash = HashCodeBuilder.reflectionHashCode(differentRank);

        assertThat(firstHash).isEqualTo(sameValuesHash);
        assertThat(firstHash).isNotEqualTo(differentRankHash);
    }

    private static final class HashRecord {
        private static final String TYPE = "record";

        private final String name;
        private final int rank;
        private transient String sessionNote;

        private HashRecord(String name, int rank, String sessionNote) {
            assertThat(TYPE).isEqualTo("record");
            this.name = name;
            this.rank = rank;
            this.sessionNote = sessionNote;
        }
    }
}
