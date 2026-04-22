/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import org.apache.http.client.utils.JdkIdn;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JdkIdnTest {

    @Test
    void convertsPunycodeLabelsToUnicode() throws Exception {
        JdkIdn jdkIdn = new JdkIdn();

        assertThat(jdkIdn.toUnicode("xn--bcher-kva")).isEqualTo("b\u00FCcher");
    }
}
