/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.PGProperty;
import org.postgresql.core.SocketFactoryFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Properties;

import javax.net.SocketFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectFactoryTest {
    @Test
    void createsConfiguredSocketFactoryWithPropertiesConstructor() throws Exception {
        Properties properties = new Properties();
        PGProperty.SOCKET_FACTORY.set(
                properties, PropertiesConstructorSocketFactory.class.getName());

        SocketFactory socketFactory = SocketFactoryFactory.getSocketFactory(properties);

        assertThat(socketFactory).isInstanceOf(PropertiesConstructorSocketFactory.class);
        PropertiesConstructorSocketFactory typedFactory =
                (PropertiesConstructorSocketFactory) socketFactory;
        assertThat(typedFactory.properties).isSameAs(properties);
    }

    @Test
    void createsConfiguredSocketFactoryWithStringConstructor() throws Exception {
        Properties properties = new Properties();
        PGProperty.SOCKET_FACTORY.set(properties, StringConstructorSocketFactory.class.getName());
        PGProperty.SOCKET_FACTORY_ARG.set(properties, "socket factory argument");

        SocketFactory socketFactory = SocketFactoryFactory.getSocketFactory(properties);

        assertThat(socketFactory).isInstanceOf(StringConstructorSocketFactory.class);
        StringConstructorSocketFactory typedFactory =
                (StringConstructorSocketFactory) socketFactory;
        assertThat(typedFactory.argument).isEqualTo("socket factory argument");
    }

    @Test
    void createsConfiguredSocketFactoryWithNoArgumentConstructor() throws Exception {
        Properties properties = new Properties();
        PGProperty.SOCKET_FACTORY.set(properties, NoArgumentSocketFactory.class.getName());

        SocketFactory socketFactory = SocketFactoryFactory.getSocketFactory(properties);

        assertThat(socketFactory).isInstanceOf(NoArgumentSocketFactory.class);
    }

    public static class PropertiesConstructorSocketFactory extends SocketFactory {
        private final Properties properties;

        public PropertiesConstructorSocketFactory(Properties properties) {
            this.properties = properties;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return SocketFactory.getDefault().createSocket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                throws IOException {
            return SocketFactory.getDefault().createSocket(host, port, localHost, localPort);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return SocketFactory.getDefault().createSocket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
                int localPort) throws IOException {
            return SocketFactory.getDefault().createSocket(address, port, localAddress, localPort);
        }
    }

    public static class StringConstructorSocketFactory extends SocketFactory {
        private final String argument;

        public StringConstructorSocketFactory(String argument) {
            this.argument = argument;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return SocketFactory.getDefault().createSocket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                throws IOException {
            return SocketFactory.getDefault().createSocket(host, port, localHost, localPort);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return SocketFactory.getDefault().createSocket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
                int localPort) throws IOException {
            return SocketFactory.getDefault().createSocket(address, port, localAddress, localPort);
        }
    }

    public static class NoArgumentSocketFactory extends SocketFactory {
        public NoArgumentSocketFactory() {
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return SocketFactory.getDefault().createSocket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                throws IOException {
            return SocketFactory.getDefault().createSocket(host, port, localHost, localPort);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return SocketFactory.getDefault().createSocket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
                int localPort) throws IOException {
            return SocketFactory.getDefault().createSocket(address, port, localAddress, localPort);
        }
    }
}
