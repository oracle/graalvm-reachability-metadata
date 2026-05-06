/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.spi.SystemStream;
import org.jline.terminal.spi.TerminalProvider;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;

public class TerminalProviderTest {
    @Test
    void loadInstantiatesProviderDeclaredInResource() throws IOException {
        TerminalProvider provider = TerminalProvider.load("synthetic");

        assertThat(provider).isInstanceOf(SyntheticProvider.class);
        assertThat(provider.name()).isEqualTo("synthetic");
    }

    public static final class SyntheticProvider implements TerminalProvider {
        public SyntheticProvider() {
        }

        @Override
        public String name() {
            return "synthetic";
        }

        @Override
        public Terminal sysTerminal(String name, String type, boolean ansiPassThrough, Charset encoding,
                Charset inputEncoding, Charset outputEncoding, Charset configurationEncoding, boolean nativeSignals,
                Terminal.SignalHandler signalHandler, boolean paused, SystemStream systemStream) throws IOException {
            throw new UnsupportedOperationException("The synthetic provider is only used to exercise provider loading");
        }

        @Override
        public Terminal newTerminal(String name, String type, InputStream masterInput, OutputStream masterOutput,
                Charset encoding, Charset inputEncoding, Charset outputEncoding, Charset configurationEncoding,
                Terminal.SignalHandler signalHandler, boolean paused, Attributes attributes, Size size)
                throws IOException {
            throw new UnsupportedOperationException("The synthetic provider is only used to exercise provider loading");
        }

        @Override
        public boolean isSystemStream(SystemStream stream) {
            return false;
        }

        @Override
        public String systemStreamName(SystemStream stream) {
            return stream.name();
        }

        @Override
        public int systemStreamWidth(SystemStream stream) {
            return -1;
        }
    }
}
