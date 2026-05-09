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
    public void reflectionCompareReadsDeclaredFieldsFromComparedClass() {
        CompareToBuilder lowerBuilder = new CompareToBuilder().append(1, 2);
        CompareToBuilder higherBuilder = new CompareToBuilder().append(2, 1);

        int result = CompareToBuilder.reflectionCompare(lowerBuilder, higherBuilder);

        assertThat(result).isLessThan(0);
    }

    @Test
    public void reflectionCompareReturnsZeroForEqualBuilderState() {
        CompareToBuilder leftBuilder = new CompareToBuilder().append("same", "same");
        CompareToBuilder rightBuilder = new CompareToBuilder().append("same", "same");

        int result = CompareToBuilder.reflectionCompare(leftBuilder, rightBuilder);

        assertThat(result).isZero();
    }
}
