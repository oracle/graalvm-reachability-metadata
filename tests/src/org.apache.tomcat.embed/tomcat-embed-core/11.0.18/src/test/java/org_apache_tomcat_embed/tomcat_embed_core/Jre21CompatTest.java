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
    void createsStartsAndNamesVirtualThreads() throws Exception {
        JreCompat compatibility = JreCompat.getInstance();
        CountDownLatch completed = new CountDownLatch(1);
        Object builder = compatibility.createVirtualThreadBuilder("tomcat-virtual-");

        compatibility.threadBuilderStart(builder, completed::countDown);

        assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void callsActionWithSubject() {
        JreCompat compatibility = JreCompat.getInstance();
        Subject subject = new Subject();

        String result = compatibility.callAs(subject, () -> "subject-action");

        assertThat(result).isEqualTo("subject-action");
    }
}
