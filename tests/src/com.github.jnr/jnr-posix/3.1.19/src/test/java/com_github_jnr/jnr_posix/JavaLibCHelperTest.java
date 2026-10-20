/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_posix;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.Pipe;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import jnr.posix.HANDLE;
import jnr.posix.JavaLibCHelper;
import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;
import jnr.posix.util.DefaultPOSIXHandler;
import jnr.posix.util.Platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaLibCHelperTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void extractsDescriptorsFromSelectableAndFileChannels() throws IOException {
        Pipe pipe = Pipe.open();
        try (Pipe.SourceChannel source = pipe.source(); Pipe.SinkChannel sink = pipe.sink()) {
            FileDescriptor descriptor = JavaLibCHelper.getDescriptorFromChannel(source);
            assertThat(descriptor.valid()).isTrue();
            assertThat(JavaLibCHelper.getfdFromDescriptor(descriptor)).isGreaterThanOrEqualTo(0);
        }

        Path file = temporaryDirectory.resolve("channel.txt");
        try (FileChannel channel = FileChannel.open(
                file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            FileDescriptor descriptor = JavaLibCHelper.getDescriptorFromChannel(channel);
            assertThat(descriptor.valid()).isTrue();
            assertThat(JavaLibCHelper.getfdFromDescriptor(descriptor)).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void extractsDescriptorFromCustomChannelContract() throws IOException {
        try (DescriptorChannel channel = new DescriptorChannel(37)) {
            FileDescriptor descriptor = JavaLibCHelper.getDescriptorFromChannel(channel);

            assertThat(descriptor.valid()).isTrue();
            assertThat(JavaLibCHelper.getfdFromDescriptor(descriptor)).isEqualTo(37);
        }
    }

    @Test
    void convertsIntegerFileDescriptors() {
        FileDescriptor descriptor = JavaLibCHelper.toFileDescriptor(41);

        assertThat(descriptor.valid()).isTrue();
        assertThat(JavaLibCHelper.getfdFromDescriptor(descriptor)).isEqualTo(41);
    }

    @Test
    void obtainsPidWhenHandlerDoesNotProvideOne() {
        POSIX posix = POSIXFactory.getJavaPOSIX(new UnsupportedPidHandler());

        assertThat(posix.getpid()).isPositive();
    }

    @Test
    void convertsPlatformFileHandles() {
        if (Platform.IS_WINDOWS) {
            HANDLE expected = HANDLE.valueOf(43L);
            FileDescriptor descriptor = JavaLibCHelper.toFileDescriptor(expected);

            assertThat(descriptor.valid()).isTrue();
            assertThat(JavaLibCHelper.gethandle(descriptor).toPointer().address()).isEqualTo(43L);
        } else {
            assertThat(JavaLibCHelper.gethandle(FileDescriptor.in).isValid()).isFalse();
        }
    }

    private static final class UnsupportedPidHandler extends DefaultPOSIXHandler {
        @Override
        public int getPID() {
            throw new UnsupportedOperationException("PID lookup delegated to the JDK");
        }
    }

    public static final class DescriptorChannel implements Channel {
        private final int descriptor;
        private boolean open = true;

        DescriptorChannel(int descriptor) {
            this.descriptor = descriptor;
        }

        public int getFD() {
            return descriptor;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
