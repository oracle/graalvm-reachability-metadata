/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.junit.client.impl.JUnitResult;

import org.junit.jupiter.api.Test;

public class JUnitResultTest {
    @Test
    public void matchesStoredExceptionAgainstExpectedType() {
        JUnitResult result = new JUnitResult();

        result.setException(new IllegalArgumentException("invalid argument"));

        assertThat(result.isAnyException()).isTrue();
        assertThat(result.isExceptionOf(RuntimeException.class)).isTrue();
        assertThat(result.isExceptionOf(IllegalStateException.class)).isFalse();
    }
}
