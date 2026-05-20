/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Socket;

import com.sun.mail.util.WriteTimeoutSocket;
import org.junit.jupiter.api.Test;

public class WriteTimeoutSocketTest {
    @Test
    void delegatesAndroidFileDescriptorLookupToWrappedSocket() throws IOException {
        DescriptorSocket descriptorSocket = new DescriptorSocket();

        try (WriteTimeoutSocket socket = new WriteTimeoutSocket(descriptorSocket, 1000)) {
            assertThat(socket.getFileDescriptor$()).isSameAs(descriptorSocket.fileDescriptor());
        }
    }

    public static final class DescriptorSocket extends Socket {
        private final FileDescriptor fileDescriptor = new FileDescriptor();

        public FileDescriptor getFileDescriptor$() {
            return fileDescriptor;
        }

        FileDescriptor fileDescriptor() {
            return fileDescriptor;
        }
    }
}
