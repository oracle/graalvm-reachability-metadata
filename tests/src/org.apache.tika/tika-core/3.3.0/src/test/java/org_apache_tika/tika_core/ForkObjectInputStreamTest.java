/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.fork.ForkParser;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ErrorParser;
import org.apache.tika.parser.ParseContext;

public class ForkObjectInputStreamTest {

    @Test
    public void forkParserSerializesRequestsAndDeserializesRemoteExceptions() throws Exception {
        ForkParser parser = new ForkParser(ForkParser.class.getClassLoader(), ErrorParser.INSTANCE);
        parser.setPoolSize(1);
        parser.setServerPulseMillis(100L);
        parser.setServerParseTimeoutMillis(5_000L);
        parser.setServerWaitTimeoutMillis(5_000L);
        parser.setJavaCommand(List.of(
                javaExecutable().toString(), "-Xmx64m", "-Djava.awt.headless=true"));
        try {
            TikaException exception = assertThrows(TikaException.class, () -> parser.parse(
                    new ByteArrayInputStream(new byte[] {1, 2, 3}),
                    new DefaultHandler(),
                    new Metadata(),
                    new ParseContext()));

            assertThat(exception.getMessage())
                    .isIn("Parse error", "Failed to communicate with a forked parser process."
                            + " The process has most likely crashed due to some error"
                            + " like running out of memory. A new process will be"
                            + " started for the next parsing request.");
            if (!"Parse error".equals(exception.getMessage())) {
                assertThat(exception).hasCauseInstanceOf(IOException.class);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            parser.close();
        }
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }
}
