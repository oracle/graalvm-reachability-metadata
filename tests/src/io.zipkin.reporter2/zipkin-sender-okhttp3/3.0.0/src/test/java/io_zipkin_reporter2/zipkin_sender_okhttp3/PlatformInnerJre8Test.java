/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_sender_okhttp3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.AbstractList;

import org.junit.jupiter.api.Test;

import zipkin2.reporter.okhttp3.OkHttpSender;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PlatformInnerJre8Test {

    @Test
    void sendSpansWrapsIoExceptionAsUncheckedIOExceptionOnJre8Platform() {
        OkHttpSender sender = OkHttpSender.create("http://127.0.0.1:9411/api/v2/spans");
        try {
            assertThatThrownBy(() -> sender.sendSpans(new EncodedSpanListWithFailingSize()))
                    .isInstanceOf(UncheckedIOException.class)
                    .hasCauseInstanceOf(IOException.class)
                    .hasRootCauseMessage("encoded span list size unavailable");
        } finally {
            sender.close();
        }
    }

    private static final class EncodedSpanListWithFailingSize extends AbstractList<byte[]> {

        @Override
        public byte[] get(int index) {
            throw new AssertionError("size should fail before any span is read");
        }

        @Override
        public int size() {
            throwAsUnchecked(new IOException("encoded span list size unavailable"));
            throw new AssertionError("unreachable");
        }
    }

    private static void throwAsUnchecked(IOException exception) {
        PlatformInnerJre8Test.<RuntimeException>sneakyThrow(exception);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable throwable) throws E {
        throw (E) throwable;
    }
}
