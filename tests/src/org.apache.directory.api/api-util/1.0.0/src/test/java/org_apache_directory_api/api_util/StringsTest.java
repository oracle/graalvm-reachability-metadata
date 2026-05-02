/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_directory_api.api_util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;

import org.apache.directory.api.util.Strings;
import org.junit.jupiter.api.Test;

public class StringsTest {

    @Test
    public void defaultCharsetNameMatchesJdkDefaultCharset() {
        String charsetName = Strings.getDefaultCharsetName();

        assertThat(charsetName).isEqualTo(Charset.defaultCharset().name());
    }
}
