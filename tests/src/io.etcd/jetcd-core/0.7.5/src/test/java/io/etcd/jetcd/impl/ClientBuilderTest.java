/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.etcd.jetcd.impl;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.ClientBuilder;
import io.vertx.grpc.VertxChannelBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Random;
import java.util.stream.Stream;

import static io_etcd.jetcd_core.impl.TestUtil.bytesOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("resource")
public class ClientBuilderTest {
    static Stream<Arguments> namespaceProvider() {
        return Stream.of(
            Arguments.of(ByteSequence.EMPTY, ByteSequence.EMPTY),
            Arguments.of(bytesOf("/namespace1/"), bytesOf("/namespace1/")),
            Arguments.of(bytesOf("namespace2/"), bytesOf("namespace2/")));
    }

    @Test
    public void testEndPoints_Null() {
        assertThatThrownBy(() -> Client.builder().endpoints((URI) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testEndPoints_Verify_Empty() {
        assertThatThrownBy(() -> Client.builder().endpoints(new URI(""))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testEndPoints_Verify_SomeEmpty() {
        assertThatThrownBy(() -> Client.builder().endpoints(new URI("http://127.0.0.1:2379"), new URI("")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testBuild_WithoutEndpoints() {
        assertThatThrownBy(() -> Client.builder().build()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testMaxInboundMessageSize() throws URISyntaxException {
        final int value = 1024 + new Random().nextInt(10);
        final ClientBuilder builder = Client.builder().endpoints(new URI("http://127.0.0.1:2379")).maxInboundMessageSize(value);
        final VertxChannelBuilder channelBuilder = (VertxChannelBuilder) new ClientConnectionManager(builder)
            .defaultChannelBuilder();
        assertThat(channelBuilder.nettyBuilder()).hasFieldOrPropertyWithValue("maxInboundMessageSize", value);
    }

    @Test
    public void testDefaultNamespace() throws URISyntaxException {
        final ClientBuilder builder = Client.builder().endpoints(new URI("http://127.0.0.1:2379"));
        final ClientConnectionManager connectionManager = new ClientConnectionManager(builder);
        assertThat(connectionManager.getNamespace()).isEqualTo(ByteSequence.EMPTY);
    }

    @ParameterizedTest
    @MethodSource("namespaceProvider")
    public void testNamespace(ByteSequence namespaceSetting, ByteSequence expectedNamespace) throws URISyntaxException {
        final ClientBuilder builder = Client.builder().endpoints(new URI("http://127.0.0.1:2379")).namespace(namespaceSetting);
        final ClientConnectionManager connectionManager = new ClientConnectionManager(builder);
        assertThat(connectionManager.getNamespace()).isEqualTo(expectedNamespace);
    }
}
