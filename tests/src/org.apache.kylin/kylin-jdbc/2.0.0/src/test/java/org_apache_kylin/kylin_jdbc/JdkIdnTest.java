/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.org.apache.http.client.utils.JdkIdn;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JdkIdnTest {
    @Test
    void convertsAnAsciiInternationalizedDomainToUnicode() throws Exception {
        assertThat(new JdkIdn().toUnicode("xn--bcher-kva.example")).isEqualTo("b\u00fccher.example");
    }
}
