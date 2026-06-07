/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_sender_okhttp3;

import org.junit.jupiter.api.Test;
import zipkin2.reporter.okhttp3.OkHttpSender;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.AbstractList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PlatformInnerJre8Test {

    @Test
    void senderWrapsIOExceptionWhilePreparingRequestBody() {
        OkHttpSender sender = OkHttpSender.create("http://127.0.0.1:1/api/v2/spans");
        try {
            assertThatThrownBy(() -> sender.sendSpans(new IOExceptionThrowingSpanList()))
                    .isInstanceOf(UncheckedIOException.class)
                    .satisfies(throwable -> assertThat(throwable.getCause())
                            .isInstanceOf(IOException.class)
                            .hasMessage("encoded spans unavailable"));
        } finally {
            sender.close();
        }
    }

    private static final class IOExceptionThrowingSpanList extends AbstractList<byte[]> {

        @Override
        public byte[] get(int index) {
            throwAsUnchecked(new IOException("encoded spans unavailable"));
            throw new AssertionError("unreachable");
        }

        @Override
        public int size() {
            return 1;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwAsUnchecked(Throwable throwable) throws T {
        throw (T) throwable;
    }
}
