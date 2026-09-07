/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.zeroturnaround.exec.close.StandardProcessCloser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ExceptionUtilTest {
    @Test
    void preservesEveryStreamCloseException() {
        StandardProcessCloser closer = new StandardProcessCloser(null);

        assertThatThrownBy(() -> closer.close(new CloseThrowingProcess()))
            .isInstanceOf(IOException.class)
            .satisfies(error -> assertThat(error.getSuppressed()).hasSize(2));
    }

    public static class CloseThrowingProcess extends Process {
        @Override
        public OutputStream getOutputStream() {
            return new OutputStream() {
                @Override
                public void write(int value) {}

                @Override
                public void close() throws IOException {
                    throw new IOException("output");
                }
            };
        }

        @Override
        public InputStream getInputStream() {
            return new CloseThrowingInputStream("input");
        }

        @Override
        public InputStream getErrorStream() {
            return new CloseThrowingInputStream("error");
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {}
    }

    public static class CloseThrowingInputStream extends InputStream {
        private final String name;

        public CloseThrowingInputStream(String name) {
            this.name = name;
        }

        @Override
        public int read() {
            return -1;
        }

        @Override
        public void close() throws IOException {
            throw new IOException(name);
        }
    }
}
