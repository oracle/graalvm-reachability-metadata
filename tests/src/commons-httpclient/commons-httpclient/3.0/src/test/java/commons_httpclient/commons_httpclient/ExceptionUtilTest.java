/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import org.apache.commons.httpclient.util.ExceptionUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class ExceptionUtilTest {
    @Test
    void initializesCausesAndIdentifiesSocketTimeouts() {
        IOException failure = new IOException("transport failure");
        IllegalStateException cause = new IllegalStateException("closed");
        ExceptionUtil.initCause(failure, cause);
        assertSame(cause, failure.getCause());
        assertTrue(ExceptionUtil.isSocketTimeoutException(new SocketTimeoutException("read timed out")));
        assertFalse(ExceptionUtil.isSocketTimeoutException(new InterruptedIOException("interrupted")));
    }
}
