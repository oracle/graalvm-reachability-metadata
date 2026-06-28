/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_booter;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.builder.HashCodeBuilder;
import org.junit.jupiter.api.Test;

public class HashCodeBuilderTest {

    @Test
    public void reflectionHashCodeUsesDeclaredFieldValues() {
        HashInput first = new HashInput(2, "first note");
        HashInput sameValues = new HashInput(2, "different note");
        HashInput differentCount = new HashInput(3, "first note");
        int expectedHash = new HashCodeBuilder().append(2).toHashCode();

        int firstHash = HashCodeBuilder.reflectionHashCode(first);

        assertThat(firstHash).isEqualTo(expectedHash);
        assertThat(firstHash).isEqualTo(HashCodeBuilder.reflectionHashCode(sameValues));
        assertThat(firstHash).isNotEqualTo(HashCodeBuilder.reflectionHashCode(differentCount));
    }

    public static final class HashInput {
        private static final String CATEGORY = "provider";

        private final int count;
        private final transient String note;

        public HashInput(int count, String note) {
            this.count = count;
            this.note = note;
        }
    }
}
