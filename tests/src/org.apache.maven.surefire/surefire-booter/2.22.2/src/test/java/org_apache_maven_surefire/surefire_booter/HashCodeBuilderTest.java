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
    public void reflectionHashCodeReadsPrivateDeclaredFields() {
        HashRecord first = new HashRecord("alpha", 1);
        HashRecord second = new HashRecord("alpha", 1);
        HashRecord different = new HashRecord("alpha", 2);

        int firstHash = HashCodeBuilder.reflectionHashCode(first);
        int secondHash = HashCodeBuilder.reflectionHashCode(second);
        int differentHash = HashCodeBuilder.reflectionHashCode(different);

        assertThat(firstHash).isEqualTo(secondHash);
        assertThat(firstHash).isNotEqualTo(differentHash);
    }

    private static final class HashRecord {
        private final String name;
        private final int rank;

        private HashRecord(String name, int rank) {
            this.name = name;
            this.rank = rank;
        }
    }
}
