/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Socket;

import io.trino.hadoop.SocksSocketFactory;
import io.trino.hadoop.TextLineLengthLimitExceededException;
import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.Test;

public class HadoopApacheTest {
    @Test
    void socksSocketFactoryCreatesUnconnectedSocketUsingConfiguredProxy() throws Exception {
        Configuration conf = new Configuration(false);
        conf.set("hadoop.socks.server", "127.0.0.1:1080");
        SocksSocketFactory socketFactory = new SocksSocketFactory();

        socketFactory.setConf(conf);

        try (Socket socket = socketFactory.createSocket()) {
            assertThat(socketFactory.getConf()).isSameAs(conf);
            assertThat(socket.isConnected()).isFalse();
        }
    }

    @Test
    void lineLengthLimitExceptionCarriesMessage() {
        TextLineLengthLimitExceededException exception =
                new TextLineLengthLimitExceededException("line exceeded configured limit");

        assertThat(exception).hasMessage("line exceeded configured limit");
    }
}
