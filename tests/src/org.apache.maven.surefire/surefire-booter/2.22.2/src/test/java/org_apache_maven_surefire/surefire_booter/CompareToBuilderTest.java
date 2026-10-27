/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_booter;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.builder.CompareToBuilder;
import org.junit.jupiter.api.Test;

public class CompareToBuilderTest {

    @Test
    public void reflectionCompareOrdersObjectsByDeclaredFieldValues() {
        RankedValue lowerRank = new RankedValue(1);
        RankedValue higherRank = new RankedValue(2);

        int comparison = CompareToBuilder.reflectionCompare(lowerRank, higherRank);

        assertThat(comparison).isLessThan(0);
        assertThat(CompareToBuilder.reflectionCompare(higherRank, lowerRank)).isGreaterThan(0);
        RankedValue sameRank = new RankedValue(3);
        RankedValue equalRank = new RankedValue(3);
        int equalComparison = CompareToBuilder.reflectionCompare(sameRank, equalRank);

        assertThat(equalComparison).isZero();
    }

    public static final class RankedValue {
        private final int rank;

        public RankedValue(int rank) {
            this.rank = rank;
        }
    }
}
