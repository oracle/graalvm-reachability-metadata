/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jline.jline_terminal_jni;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.jni.JniNativePty;
import org.jline.terminal.impl.jni.JniTerminalProvider;
import org.jline.terminal.spi.Pty;
import org.jline.terminal.spi.SystemStream;
import org.jline.terminal.spi.TerminalExt;
import org.jline.terminal.spi.TerminalProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Jline_terminal_jniTest {
    private static final Set<String> SUPPORTED_POSIX_OS_PREFIXES = Set.of(
            "Linux", "Mac", "Darwin", "Solaris", "SunOS", "FreeBSD");

    @Test
    void loadsJniTerminalProviderThroughPublicServiceApi() throws Exception {
        TerminalProvider provider = TerminalProvider.load("jni");

        assertThat(provider).isInstanceOf(JniTerminalProvider.class);
        assertThat(provider.name()).isEqualTo("jni");
        assertThat(provider).hasToString("TerminalProvider[jni]");

        for (SystemStream stream : SystemStream.values()) {
            assertThat(provider.systemStreamWidth(stream)).isGreaterThanOrEqualTo(-1);
            assertThat(provider.isSystemStream(stream)).isIn(true, false);
        }
    }

    @Test
    void openCreatesConfiguredNativePtyOnSupportedPosixPlatforms() throws Exception {
        TerminalProvider provider = TerminalProvider.load("jni");
        Attributes attributes = attributesForPty();
        Size requestedSize = new Size(81, 27);

        if (isSupportedPosixOperatingSystem()) {
            try (Pty pty = ((JniTerminalProvider) provider).open(attributes, requestedSize)) {
                assertThat(pty).isInstanceOf(JniNativePty.class);
                assertThat(pty.getProvider()).isSameAs(provider);
                assertThat(pty.getSystemStream()).isNull();

                JniNativePty nativePty = (JniNativePty) pty;
                assertThat(nativePty.getMaster()).isGreaterThan(0);
                assertThat(nativePty.getSlave()).isGreaterThan(0);
                assertThat(nativePty.getSlaveOut()).isEqualTo(nativePty.getSlave());
                assertThat(nativePty.getMasterFD()).isNotNull();
                assertThat(nativePty.getSlaveFD()).isNotNull();
                assertThat(nativePty.getSlaveOutFD()).isSameAs(nativePty.getSlaveFD());
                assertThat(nativePty.getName()).isNotBlank();
                assertThat(nativePty).hasToString("NativePty[" + nativePty.getName() + "]");

                assertThat(nativePty.getSize()).isEqualTo(requestedSize);
                Size resized = new Size(100, 31);
                nativePty.setSize(resized);
                assertThat(nativePty.getSize()).isEqualTo(resized);

                Attributes actualAttributes = nativePty.getAttr();
                assertThat(actualAttributes.getLocalFlag(Attributes.LocalFlag.ECHO)).isFalse();
                assertThat(actualAttributes.getLocalFlag(Attributes.LocalFlag.ICANON)).isTrue();
                assertThat(actualAttributes.getInputFlag(Attributes.InputFlag.ICRNL)).isTrue();
                assertThat(actualAttributes.getControlChar(Attributes.ControlChar.VERASE)).isEqualTo(127);
            }
        } else {
            assertThatThrownBy(() -> ((JniTerminalProvider) provider).open(attributes, requestedSize))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void nativePtyExposesConnectedMasterAndSlaveStreams() throws Exception {
        TerminalProvider provider = TerminalProvider.load("jni");
        Attributes attributes = attributesForPty();
        Size requestedSize = new Size(80, 24);

        if (isSupportedPosixOperatingSystem()) {
            try (Pty pty = ((JniTerminalProvider) provider).open(attributes, requestedSize)) {
                byte[] payload = "jni-pty-payload".getBytes(StandardCharsets.UTF_8);
                InputStream masterInput = pty.getMasterInput();
                OutputStream slaveOutput = pty.getSlaveOutput();
                ExecutorService executor = newDaemonSingleThreadExecutor("jni-pty-reader");
                Future<byte[]> readFromMaster = executor.submit(() -> readExactly(masterInput, payload.length));

                try {
                    slaveOutput.write(payload);
                    slaveOutput.flush();

                    assertThat(readFromMaster.get(5, TimeUnit.SECONDS)).containsExactly(payload);
                } finally {
                    readFromMaster.cancel(true);
                    executor.shutdownNow();
                    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                }
            }
        } else {
            assertThatThrownBy(() -> ((JniTerminalProvider) provider).open(attributes, requestedSize))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void nativePtyAppliesAttributeChangesAfterOpen() throws Exception {
        TerminalProvider provider = TerminalProvider.load("jni");
        Attributes attributes = attributesForPty();
        Size requestedSize = new Size(80, 24);

        if (isSupportedPosixOperatingSystem()) {
            try (Pty pty = ((JniTerminalProvider) provider).open(attributes, requestedSize)) {
                Attributes updatedAttributes = new Attributes(pty.getAttr());
                updatedAttributes.setLocalFlag(Attributes.LocalFlag.ECHO, true);
                updatedAttributes.setLocalFlag(Attributes.LocalFlag.ICANON, false);
                updatedAttributes.setInputFlag(Attributes.InputFlag.ICRNL, false);
                updatedAttributes.setControlChar(Attributes.ControlChar.VMIN, 0);
                updatedAttributes.setControlChar(Attributes.ControlChar.VTIME, 1);

                pty.setAttr(updatedAttributes);

                Attributes actualAttributes = pty.getAttr();
                assertThat(actualAttributes.getLocalFlag(Attributes.LocalFlag.ECHO)).isTrue();
                assertThat(actualAttributes.getLocalFlag(Attributes.LocalFlag.ICANON)).isFalse();
                assertThat(actualAttributes.getInputFlag(Attributes.InputFlag.ICRNL)).isFalse();
                assertThat(actualAttributes.getControlChar(Attributes.ControlChar.VMIN)).isZero();
                assertThat(actualAttributes.getControlChar(Attributes.ControlChar.VTIME)).isEqualTo(1);
            }
        } else {
            assertThatThrownBy(() -> ((JniTerminalProvider) provider).open(attributes, requestedSize))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void terminalBuilderCreatesJniBackedTerminalForExplicitStreams() throws Exception {
        if (isSupportedPosixOperatingSystem()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (Terminal terminal = buildTerminal(output)) {
                assertThat(terminal.getName()).isEqualTo("jni-test-terminal");
                assertThat(terminal.getType()).isEqualTo("xterm-256color");
                assertThat(terminal.encoding()).isEqualTo(StandardCharsets.UTF_8);
                assertThat(terminal).isInstanceOf(TerminalExt.class);

                TerminalExt terminalExt = (TerminalExt) terminal;
                assertThat(terminalExt.getProvider().name()).isEqualTo("jni");
                assertThat(terminalExt.getSystemStream()).isNull();

                assertThat(terminal.getSize()).isEqualTo(new Size(90, 33));
                terminal.setSize(new Size(91, 34));
                assertThat(terminal.getWidth()).isEqualTo(91);
                assertThat(terminal.getHeight()).isEqualTo(34);

                assertThat(terminal.echo()).isFalse();
                assertThat(terminal.canPauseResume()).isTrue();
                assertThat(terminal.paused()).isTrue();
                terminal.pause();
                assertThat(terminal.paused()).isTrue();
                assertThat(terminal.reader()).isNotNull();
                assertThat(terminal.writer()).isNotNull();
                assertThat(output.size()).isZero();
            }
        } else {
            assertThatThrownBy(() -> buildTerminal(new ByteArrayOutputStream()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    private static ExecutorService newDaemonSingleThreadExecutor(String threadName) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    private static byte[] readExactly(InputStream input, int length) throws Exception {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new EOFException("Expected " + length + " bytes, read " + offset);
            }
            offset += read;
        }
        return buffer;
    }

    private static Terminal buildTerminal(ByteArrayOutputStream output) throws Exception {
        return TerminalBuilder.builder()
                .provider("jni")
                .system(false)
                .name("jni-test-terminal")
                .type("xterm-256color")
                .encoding(StandardCharsets.UTF_8)
                .streams(new ByteArrayInputStream(new byte[0]), output)
                .attributes(attributesForPty())
                .size(new Size(90, 33))
                .nativeSignals(false)
                .paused(true)
                .build();
    }

    private static Attributes attributesForPty() {
        Attributes attributes = new Attributes();
        attributes.setLocalFlag(Attributes.LocalFlag.ECHO, false);
        attributes.setLocalFlag(Attributes.LocalFlag.ICANON, true);
        attributes.setInputFlag(Attributes.InputFlag.ICRNL, true);
        attributes.setOutputFlag(Attributes.OutputFlag.OPOST, true);
        attributes.setControlFlag(Attributes.ControlFlag.CREAD, true);
        attributes.setControlChar(Attributes.ControlChar.VERASE, 127);
        attributes.setControlChar(Attributes.ControlChar.VMIN, 1);
        return attributes;
    }

    private static boolean isSupportedPosixOperatingSystem() {
        String osName = System.getProperty("os.name", "");
        return SUPPORTED_POSIX_OS_PREFIXES.stream().anyMatch(osName::startsWith);
    }
}
