/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.JChannel;
import org.jgroups.protocols.TP;
import org.jgroups.util.GenerateGettersAndSetters;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class GenerateGettersAndSettersTest {
    @Test
    void generatesAccessorsForAnnotatedProtocolFields() throws Exception {
        JChannel channel = new JChannel(false);
        try {
            String output = captureStandardOutput(() -> GenerateGettersAndSetters.main(
                    new String[] {"-class", TP.class.getName(), "-use-generics"}));

            assertThat(output)
                    .contains("public InetAddress getBindAddr() {return bind_addr;}")
                    .contains("public <T extends TP> T setBindAddr(InetAddress b) "
                            + "{this.bind_addr=b; return (T)this;}");
        } finally {
            channel.close();
        }
    }

    private static String captureStandardOutput(ThrowingRunnable runnable) throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8);
        try {
            System.setOut(capture);
            runnable.run();
        } finally {
            System.setOut(originalOut);
            capture.close();
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
