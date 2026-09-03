/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.compress.harmony.archive.internal.nls;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessagesCoverageTest {

    @Test
    void messageOverloadsReturnStableFallbackOrFormattedText() {
        assertThat(Messages.getString("missing.key")).isEqualTo("missing.key");
        assertThat(Messages.getString("missing.key", 'x')).contains("missing.key");
        assertThat(Messages.getString("missing.key", 7)).contains("missing.key");
        assertThat(Messages.getString("missing.key", "value")).contains("missing.key");
        assertThat(Messages.getString("missing.key", "left", "right")).contains("missing.key");
        assertThat(Messages.getString("missing.key", new Object[] {"first", "second"}))
                .contains("missing.key");
    }
}
