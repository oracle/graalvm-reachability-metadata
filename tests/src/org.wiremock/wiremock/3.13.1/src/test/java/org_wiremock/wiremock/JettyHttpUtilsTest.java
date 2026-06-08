/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wiremock.wiremock;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.jetty.JettyHttpUtils;
import org.junit.jupiter.api.Test;

public class JettyHttpUtilsTest {
    @Test
    void detectsBundledJettyServerClasses() {
        assertThat(JettyHttpUtils.isJetty()).isTrue();
    }
}
