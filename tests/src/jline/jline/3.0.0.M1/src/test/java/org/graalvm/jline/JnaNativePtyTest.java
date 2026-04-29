/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.impl.jna.JnaNativePty;
import org.junit.jupiter.api.Test;

import java.io.FileDescriptor;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class JnaNativePtyTest {

    @Test
    void createsFileDescriptorForNativePtyFileDescriptorNumber() {
        FileDescriptor descriptor = TestNativePty.standardInputDescriptor();

        assertThat(descriptor).isNotSameAs(FileDescriptor.in);
        assertThat(descriptor.valid()).isTrue();
    }

    private static class TestNativePty extends JnaNativePty {

        TestNativePty() {
            super(-1, FileDescriptor.in, -1, FileDescriptor.in, "test-pty");
        }

        static FileDescriptor standardInputDescriptor() {
            return newDescriptor(0);
        }

        @Override
        public Attributes getAttr() throws IOException {
            return new Attributes();
        }

        @Override
        public void setAttr(Attributes attr) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Size getSize() throws IOException {
            return new Size(80, 24);
        }

        @Override
        public void setSize(Size size) throws IOException {
            throw new UnsupportedOperationException();
        }
    }
}
