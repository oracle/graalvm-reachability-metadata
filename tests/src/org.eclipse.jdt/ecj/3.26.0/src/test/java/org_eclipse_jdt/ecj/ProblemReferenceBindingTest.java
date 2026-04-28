/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt.ecj;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.jdt.internal.compiler.lookup.ProblemReasons;
import org.eclipse.jdt.internal.compiler.lookup.ProblemReferenceBinding;
import org.junit.jupiter.api.Test;

public class ProblemReferenceBindingTest {
    @Test
    void problemReasonStringResolvesPublicProblemReasonFields() {
        assertThat(ProblemReferenceBinding.problemReasonString(ProblemReasons.NotFound))
                .isEqualTo("ProblemReasons.NotFound");
    }
}
