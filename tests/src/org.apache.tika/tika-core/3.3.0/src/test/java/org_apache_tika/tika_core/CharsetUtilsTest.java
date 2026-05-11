/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

import org.apache.tika.utils.CharsetUtils;

public class CharsetUtilsTest {

    @Test
    public void isSupportedChecksOptionalIcuCharsetProvider() {
        assertThat(CharsetUtils.isSupported("UTF-8")).isTrue();
        assertThat(CharsetUtils.isSupported("not a charset")).isFalse();
    }

    @Test
    public void forNameUsesOptionalIcuCharsetProviderForNonCommonCharset() {
        Charset charset = CharsetUtils.forName("ibm-1047");

        assertThat(charset.name()).contains("1047");
    }
}
