/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.security.auth.Subject;

import org.apache.tomcat.util.compat.JreCompat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jre21CompatTest {

    @Test
    void startsNamedVirtualThreadAndCallsAsSubject() throws Exception {
        JreCompat compatibility = JreCompat.getInstance();
        CountDownLatch executed = new CountDownLatch(1);
        Object builder = compatibility.createVirtualThreadBuilder("tomcat-compat-");

        compatibility.threadBuilderStart(builder, executed::countDown);
        String result = compatibility.callAs(new Subject(), () -> "called");

        assertThat(executed.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(result).isEqualTo("called");
    }
}
