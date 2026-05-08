/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.UnsupportedCharsetException;

import org.junit.jupiter.api.Test;

import org.apache.tika.utils.CharsetUtils;

public class CharsetUtilsTest {

    @Test
    public void isSupportedChecksOptionalIcuCharsetProvider() {
        assertThat(CharsetUtils.isSupported("UTF-8")).isTrue();
        assertThat(CharsetUtils.isSupported("not a charset")).isFalse();
    }

    @Test
    public void forNameAttemptsOptionalIcuCharsetLookupBeforeJdkFallback() {
        assertThatThrownBy(() -> CharsetUtils.forName("x-tika-not-a-real-charset"))
                .isInstanceOf(UnsupportedCharsetException.class)
                .hasMessageContaining("x-tika-not-a-real-charset");
    }
}
