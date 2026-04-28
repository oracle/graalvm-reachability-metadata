/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Kit;

import static org.assertj.core.api.Assertions.assertThat;

public class KitTest {
    @Test
    void initCauseAttachesThrowableCause() {
        RuntimeException exception = new RuntimeException("script execution failed");
        IllegalArgumentException cause = new IllegalArgumentException("invalid argument");

        RuntimeException returnedException = Kit.initCause(exception, cause);

        assertThat(returnedException).isSameAs(exception);
        assertThat(exception).hasCause(cause);
    }
}
