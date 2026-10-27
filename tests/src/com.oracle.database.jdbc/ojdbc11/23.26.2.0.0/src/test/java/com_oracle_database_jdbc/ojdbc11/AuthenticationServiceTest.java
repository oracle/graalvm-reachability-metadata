/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.CompletionException;
import javax.security.auth.Subject;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.diagnostics.CommonDiagnosable;
import oracle.jdbc.driver.DMSFactory;
import oracle.net.ano.Ano;
import oracle.net.ns.ClientProfile;
import oracle.net.ns.NIONSDataChannel;
import oracle.net.ns.NetException;
import oracle.net.ns.SessionAtts;
import oracle.net.nt.NTAdapter;
import oracle.net.nt.NTAdapter.NetworkAdapterType;
import org.junit.jupiter.api.Test;

public class AuthenticationServiceTest {
    private static final int ANO_MAGIC = 0xDEADBEEF;
    private static final int ANO_PROTOCOL_VERSION = 0x17001000;
    private static final int AUTHENTICATION_ACCEPTED = 64255;
    private static final int AUTHENTICATION_SERVICE = 1;
    private static final int STRING_TYPE = 0;
    private static final int UB1_TYPE = 2;
    private static final int VERSION_TYPE = 5;
    private static final int STATUS_TYPE = 6;

    @Test
    void rejectsKerberosAuthenticationWithoutKerberosPrincipal() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(
                OracleConnection.CONNECTION_PROPERTY_THIN_NET_AUTHENTICATION_SERVICES,
                "(KERBEROS5)");
        ClientProfile profile = new ClientProfile(properties);
        SessionAtts session = new ScriptedSessionAtts(profile, kerberosSelectionResponse());
        Ano ano = new Ano();
        ano.init(session, false);

        CompletionException exception =
                assertThrows(
                        CompletionException.class,
                        () ->
                                Subject.callAs(
                                        new Subject(),
                                        () -> {
                                            ano.negotiation(false, false, null);
                                            return null;
                                        }));

        assertThat(exception.getCause())
                .isInstanceOf(NetException.class)
                .hasMessageContaining("Unable to find valid kerberos principal");
        assertThat(ano.getAuthenticationAdaptorName()).isEqualTo("KERBEROS5");
    }

    private static byte[] kerberosSelectionResponse() {
        ByteBuffer response = ByteBuffer.allocate(128);

        putAnoHeader(response, 53, 1);
        putServiceHeader(response, AUTHENTICATION_SERVICE, 4);
        putPacketHeader(response, 4, VERSION_TYPE);
        response.putInt(ANO_PROTOCOL_VERSION);
        putPacketHeader(response, 2, STATUS_TYPE);
        response.putShort((short) AUTHENTICATION_ACCEPTED);
        putPacketHeader(response, 1, UB1_TYPE);
        response.put((byte) AUTHENTICATION_SERVICE);
        putString(response, "KERBEROS5");

        putAnoHeader(response, 44, 1);
        putServiceHeader(response, AUTHENTICATION_SERVICE, 2);
        putString(response, "oracle");
        putString(response, "localhost");

        response.flip();
        byte[] bytes = new byte[response.remaining()];
        response.get(bytes);
        return bytes;
    }

    private static void putAnoHeader(ByteBuffer response, int packetLength, int services) {
        response.putInt(ANO_MAGIC);
        response.putShort((short) packetLength);
        response.putInt(ANO_PROTOCOL_VERSION);
        response.putShort((short) services);
        response.put((byte) 0);
    }

    private static void putServiceHeader(ByteBuffer response, int service, int subPackets) {
        response.putShort((short) service);
        response.putShort((short) subPackets);
        response.putInt(0);
    }

    private static void putString(ByteBuffer response, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        putPacketHeader(response, bytes.length, STRING_TYPE);
        response.put(bytes);
    }

    private static void putPacketHeader(ByteBuffer response, int length, int type) {
        response.putShort((short) length);
        response.putShort((short) type);
    }

    private static final class ScriptedSessionAtts extends SessionAtts {
        private static final NTAdapter NETWORK_ADAPTER = new TestNetworkAdapter();

        private ScriptedSessionAtts(ClientProfile profile, byte[] response) {
            super(null, 8192, 8192, false, false, CommonDiagnosable.getInstance());
            this.profile = profile;
            payloadDataBufferForRead = ByteBuffer.wrap(response);
            payloadDataBufferForWrite = ByteBuffer.allocate(8192);
            dataChannel = new ScriptedDataChannel(this);
        }

        @Override
        public NTAdapter getNTAdapter() {
            return NETWORK_ADAPTER;
        }
    }

    private static final class ScriptedDataChannel extends NIONSDataChannel {
        private final SessionAtts session;

        private ScriptedDataChannel(SessionAtts session) {
            super(session);
            this.session = session;
        }

        @Override
        public void readDataFromSocketChannel() {
            throw new AssertionError("Scripted Kerberos response was exhausted");
        }

        @Override
        public void writeDataToSocketChannel(int flags) {
            session.payloadDataBufferForWrite.clear();
        }
    }

    private static final class TestNetworkAdapter implements NTAdapter {
        @Override
        public NetworkAdapterType getNetworkAdapterType() {
            return NetworkAdapterType.TCP;
        }

        @Override
        public void connect(DMSFactory.DMSNoun noun) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void disconnect() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SocketChannel getSocketChannel() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getInputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public OutputStream getOutputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setOption(int option, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getOption(int option) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void abort() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void sendUrgentByte(int value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCharacteristicUrgentSupported() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setReadTimeoutIfRequired(Properties properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isConnectionSocketKeepAlive() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InetAddress getInetAddress() {
            return InetAddress.getLoopbackAddress();
        }
    }
}
