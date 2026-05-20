/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.mail.util.WriteTimeoutSocket;
import java.io.FileDescriptor;
import java.net.Socket;
import org.junit.jupiter.api.Test;

public class WriteTimeoutSocketTest {
    @Test
    public void getFileDescriptorInvokesDelegateAndroidCompatibilityMethod() throws Exception {
        FileDescriptor fileDescriptor = new FileDescriptor();

        try (WriteTimeoutSocket socket = new WriteTimeoutSocket(new FileDescriptorSocket(fileDescriptor), 1_000)) {
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
