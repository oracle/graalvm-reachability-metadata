/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.grpc.netty.shaded.io.netty.handler.ssl.SslHandler;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;

public class SslHandlerTest {
    @TempDir
    Path directory;

    @Test
    void forwardsNonSocketReadErrorsAfterReceivingCloseNotify() throws Exception {
        Path file = directory.resolve("payload.bin");
        Files.write(file, new byte[]{1, 2, 3});
        FileInputStream input = new FileInputStream(file.toFile());
        input.close();
        IOException readFailure = assertThrows(IOException.class, () -> input.read(new byte[1]));
        TlsTestSupport.exchange(SslProvider.JDK, null, null, (server, client) -> {
            SslHandler serverTls = server.pipeline().get(SslHandler.class);
            SslHandler clientTls = client.pipeline().get(SslHandler.class);
            assertTrue(serverTls.closeOutbound().isSuccess());
            TlsTestSupport.transfer(server, client);
            assertTrue(clientTls.sslCloseFuture().isSuccess());
            client.pipeline().fireExceptionCaught(readFailure);
            assertSame(readFailure, assertThrows(IOException.class, client::checkException));
        });
    }
}
