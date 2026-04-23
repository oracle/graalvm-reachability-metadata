/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_classic;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;

public class ThrowableProxyTest {

    @Test
    void capturesSuppressedExceptions() {
        IllegalStateException exception = new IllegalStateException("boom");
        exception.addSuppressed(new IllegalArgumentException("suppressed"));

        ThrowableProxy throwableProxy = new ThrowableProxy(exception);

        assertThat(throwableProxy.getSuppressed()).hasSize(1);
        assertThat(throwableProxy.getSuppressed()[0].getClassName())
                .isEqualTo(IllegalArgumentException.class.getName());
    }
}
