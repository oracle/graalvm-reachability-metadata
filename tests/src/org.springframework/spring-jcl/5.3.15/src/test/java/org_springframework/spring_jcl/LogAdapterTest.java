/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_jcl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class LogAdapterTest {
    @Test
    void createsLogThroughPublicFactory() {
        Log log = LogFactory.getLog(LogAdapterTest.class);

        assertThat(log).isNotNull();
        assertThatCode(() -> log.debug("spring-jcl adapter test message")).doesNotThrowAnyException();
    }
}
