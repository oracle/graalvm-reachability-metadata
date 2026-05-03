/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.internal.util.JavaEightUtil;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaEightUtilTest {
    @Test
    void createsEmptyOptionalAndDurationUsingJavaEightReflectionHelpers() {
        assertThat(JavaEightUtil.emptyOptional()).isEqualTo(Optional.empty());
        assertThat(JavaEightUtil.emptyDuration()).isEqualTo(Duration.ZERO);
    }
}
