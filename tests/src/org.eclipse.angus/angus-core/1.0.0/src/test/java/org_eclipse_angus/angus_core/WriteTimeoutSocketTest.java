/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.mail.util.WriteTimeoutSocket;
import java.io.FileDescriptor;
import java.net.Socket;
import org.junit.jupiter.api.Test;

public class WriteTimeoutSocketTest {
    @Test
    public void androidFileDescriptorLookupDelegatesToWrappedSocket() throws Exception {
        FileDescriptor fileDescriptor = new FileDescriptor();
        FileDescriptorSocket delegate = new FileDescriptorSocket(fileDescriptor);

        try (WriteTimeoutSocket socket = new WriteTimeoutSocket(delegate, 10_000)) {
            assertThat(socket.getFileDescriptor$()).isSameAs(fileDescriptor);
        }
    }

    public static final class FileDescriptorSocket extends Socket {
        private final FileDescriptor fileDescriptor;

        public FileDescriptorSocket(FileDescriptor fileDescriptor) {
            this.fileDescriptor = fileDescriptor;
        }

        public FileDescriptor getFileDescriptor$() {
            return fileDescriptor;
        }
    }
}
