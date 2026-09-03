/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package okhttp3.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import okio.Buffer;
import org.junit.jupiter.api.Test;

public class UtilApiCoverageTest {
    @Test
    void utilityMethodsTransformStringsAndConsumeSources() throws Exception {
        assertThat(Util.concat(new String[] {"a", "b"}, "c"))
                .containsExactly("a", "b", "c");
        assertThat(Util.indexOf(Util.NATURAL_ORDER, new String[] {"a", "c"}, "c"))
                .isEqualTo(1);
        assertThat(Util.toHumanReadableAscii("hello\u00e9")).isEqualTo("hello?");
        assertThat(Util.isAndroidGetsocknameError(new AssertionError("getsockname failed")))
                .isFalse();

        Buffer source = new Buffer().writeUtf8("discard me");
        assertThat(Util.discard(source, 1, TimeUnit.SECONDS)).isTrue();
        Buffer second = new Buffer().writeUtf8("skip me");
        assertThat(Util.skipAll(second, 1, TimeUnit.SECONDS)).isTrue();
        assertThat(second.exhausted()).isTrue();

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Util.closeQuietly(serverSocket);
            assertThat(serverSocket.isClosed()).isTrue();
        }
    }
}
