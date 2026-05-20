/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import javax.security.auth.Subject;

import org.apache.tomcat.util.compat.JreCompat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jre21CompatTest {

    @Test
    void invokesVirtualThreadBuilderAndSubjectCallAs() throws Exception {
        JreCompat compat = JreCompat.getInstance();
        CountDownLatch latch = new CountDownLatch(1);

        Object builder = compat.createVirtualThreadBuilder("tomcat-test-");
        compat.threadBuilderStart(builder, latch::countDown);

        assertThat(latch.await(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)).isTrue();
        assertThat(compat.callAs(new Subject(), () -> "done")).isEqualTo("done");
    }
}
