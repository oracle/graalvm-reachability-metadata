/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.junit.jupiter.api.Test;

public class HashCodeBuilderTest {

    @Test
    public void reflectionHashCodeReadsPrivateFields() {
        HashedRecord first = new HashedRecord("alpha", 7);
        HashedRecord matching = new HashedRecord("alpha", 7);
        HashedRecord different = new HashedRecord("alpha", 8);

        int firstHashCode = HashCodeBuilder.reflectionHashCode(first);
        int matchingHashCode = HashCodeBuilder.reflectionHashCode(matching);
        int differentHashCode = HashCodeBuilder.reflectionHashCode(different);

        assertThat(firstHashCode).isEqualTo(matchingHashCode);
        assertThat(firstHashCode).isNotEqualTo(differentHashCode);
    }

    @Test
    public void reflectionHashCodeCanIncludeTransientFieldsWhenRequested() {
        SessionRecord first = new SessionRecord("alpha", "token-1");
        SessionRecord matchingPersistentState = new SessionRecord("alpha", "token-2");

        int defaultHashCode = HashCodeBuilder.reflectionHashCode(first);
        int defaultMatchingHashCode = HashCodeBuilder.reflectionHashCode(matchingPersistentState);
        int transientHashCode = HashCodeBuilder.reflectionHashCode(first, true);
        int transientMatchingHashCode = HashCodeBuilder.reflectionHashCode(matchingPersistentState, true);

        assertThat(defaultHashCode).isEqualTo(defaultMatchingHashCode);
        assertThat(transientHashCode).isNotEqualTo(transientMatchingHashCode);
    }

    private static final class HashedRecord {
        private final String name;
        private final int rank;

        private HashedRecord(String name, int rank) {
            this.name = name;
            this.rank = rank;
        }
    }

    private static final class SessionRecord {
        private final String label;
        private transient String token;

        private SessionRecord(String label, String token) {
            this.label = label;
            this.token = token;
        }
    }
}
