/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.hk2.api.AnnotationLiteral;
import org.glassfish.hk2.api.Rank;
import org.junit.jupiter.api.Test;

public class AnnotationLiteralTest {
    @Test
    void equalsComparesAnnotationMembers() {
        RankLiteral rank = new RankLiteral(37);
        RankLiteral sameRank = new RankLiteral(37);
        RankLiteral differentRank = new RankLiteral(38);

        assertThat(rank).isEqualTo(sameRank);
        assertThat(rank).isNotEqualTo(differentRank);
    }

    @Test
    void hashCodeUsesAnnotationMemberValues() {
        RankLiteral rank = new RankLiteral(12);

        int expectedHashCode = (127 * "value".hashCode()) ^ Integer.valueOf(12).hashCode();
        assertThat(rank.annotationType()).isEqualTo(Rank.class);
        assertThat(rank.hashCode()).isEqualTo(expectedHashCode);
    }

    private static final class RankLiteral extends AnnotationLiteral<Rank> implements Rank {
        private static final long serialVersionUID = 1L;

        private final int value;

        private RankLiteral(int value) {
            this.value = value;
        }

        @Override
        public int value() {
            return value;
        }
    }
}
