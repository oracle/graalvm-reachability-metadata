/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_transport_native_unix_common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.unix.Buffer;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.DomainSocketReadMode;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.NativeInetAddress;
import io.netty.channel.unix.PreferredDirectByteBufAllocator;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.channel.unix.UnixChannelUtil;
import java.io.File;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Netty_transport_native_unix_commonTest {
    @Test
    void domainSocketAddressExposesPathAndValueSemantics() {
        DomainSocketAddress stringAddress = new DomainSocketAddress("/tmp/netty-test.sock");
        DomainSocketAddress equalAddress = new DomainSocketAddress(new File("/tmp/netty-test.sock"));
        DomainSocketAddress otherAddress = new DomainSocketAddress("/tmp/netty-other.sock");

        assertThat(stringAddress.path()).isEqualTo("/tmp/netty-test.sock");
        assertThat(stringAddress.toString()).isEqualTo("/tmp/netty-test.sock");
        assertThat(stringAddress).isEqualTo(equalAddress);
        assertThat(stringAddress.hashCode()).isEqualTo(equalAddress.hashCode());
        assertThat(stringAddress).isNotEqualTo(otherAddress);
        assertThat(stringAddress).isNotEqualTo("/tmp/netty-test.sock");
        assertThatNullPointerException()
                .isThrownBy(() -> new DomainSocketAddress((String) null));
    }

    @Test
    void nativeInetAddressMapsIpv4AddressToIpv4MappedIpv6Bytes() throws Exception {
        InetAddress loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        NativeInetAddress nativeAddress = NativeInetAddress.newInstance(loopback);
        byte[] expected = new byte[] {
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, (byte) 0xff, (byte) 0xff,
                127, 0, 0, 1
        };

        assertThat(nativeAddress.address()).containsExactly(expected);
        assertThat(nativeAddress.scopeId()).isZero();
        assertThat(NativeInetAddress.ipv4MappedIpv6Address(loopback.getAddress())).containsExactly(expected);

        byte[] copied = new byte[16];
        NativeInetAddress.copyIpv4MappedIpv6Address(new byte[] {10, 20, 30, 40}, copied);
        assertThat(copied).containsExactly(
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, (byte) 0xff, (byte) 0xff,
                10, 20, 30, 40);
    }

    @Test
    void nativeInetAddressPreservesIpv6BytesAndScope() throws Exception {
        byte[] ipv6Bytes = new byte[] {
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 1
        };
        Inet6Address loopback = Inet6Address.getByAddress(null, ipv6Bytes, 0);
        NativeInetAddress nativeAddress = NativeInetAddress.newInstance(loopback);

        assertThat(nativeAddress.address()).containsExactly(ipv6Bytes);
        assertThat(nativeAddress.scopeId()).isEqualTo(loopback.getScopeId());

        NativeInetAddress explicitScope = new NativeInetAddress(ipv6Bytes, 7);
        assertThat(explicitScope.address()).containsExactly(ipv6Bytes);
        assertThat(explicitScope.scopeId()).isEqualTo(7);
    }

    @Test
    void nativeInetAddressDecodesIpv4SocketAddressWithOffset() throws Exception {
        byte[] encoded = new byte[12];
        int offset = 2;
        encoded[offset] = (byte) 192;
        encoded[offset + 1] = (byte) 168;
        encoded[offset + 2] = 1;
        encoded[offset + 3] = 25;
        encodeInt(encoded, offset + 4, 8080);

        InetSocketAddress socketAddress = NativeInetAddress.address(encoded, offset, 8);

        assertThat(socketAddress.getAddress().getAddress()).containsExactly((byte) 192, (byte) 168, 1, 25);
        assertThat(socketAddress.getPort()).isEqualTo(8080);
    }

    @Test
    void nativeInetAddressDecodesIpv6SocketAddressWithScopeAndOffset() throws Exception {
        byte[] encoded = new byte[30];
        int offset = 3;
        byte[] ipv6Bytes = new byte[] {
                0x20, 0x01, 0x0d, (byte) 0xb8,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 5
        };
        System.arraycopy(ipv6Bytes, 0, encoded, offset, ipv6Bytes.length);
        encodeInt(encoded, offset + 16, 9);
        encodeInt(encoded, offset + 20, 443);

        InetSocketAddress socketAddress = NativeInetAddress.address(encoded, offset, 24);

        assertThat(socketAddress.getAddress()).isInstanceOf(Inet6Address.class);
        assertThat(socketAddress.getAddress().getAddress()).containsExactly(ipv6Bytes);
        assertThat(((Inet6Address) socketAddress.getAddress()).getScopeId()).isEqualTo(9);
        assertThat(socketAddress.getPort()).isEqualTo(443);
    }

    @Test
    void bufferAllocatesDirectByteBufferUsingNativeByteOrder() {
        ByteBuffer buffer = Buffer.allocateDirectWithNativeOrder(32);

        assertThat(buffer.isDirect()).isTrue();
        assertThat(buffer.capacity()).isEqualTo(32);
        assertThat(buffer.order()).isEqualTo(ByteOrder.nativeOrder());
    }

    @Test
    void preferredAllocatorDelegatesToDirectBuffersForGenericAndIoAllocations() {
        PreferredDirectByteBufAllocator allocator = new PreferredDirectByteBufAllocator();
        allocator.updateAllocator(UnpooledByteBufAllocator.DEFAULT);
        ByteBuf buffer = allocator.buffer(16, 64);
        ByteBuf ioBuffer = allocator.ioBuffer(8);
        ByteBuf directBuffer = allocator.directBuffer(4);
        ByteBuf heapBuffer = allocator.heapBuffer(4);
        CompositeByteBuf compositeBuffer = allocator.compositeBuffer(2);
        CompositeByteBuf compositeHeapBuffer = allocator.compositeHeapBuffer(2);
        CompositeByteBuf compositeDirectBuffer = allocator.compositeDirectBuffer(2);
        compositeBuffer.addComponent(true, UnpooledByteBufAllocator.DEFAULT.directBuffer(1).writeByte(1));
        compositeHeapBuffer.addComponent(true, UnpooledByteBufAllocator.DEFAULT.heapBuffer(1).writeByte(2));
        compositeDirectBuffer.addComponent(true, UnpooledByteBufAllocator.DEFAULT.directBuffer(1).writeByte(3));

        try {
            assertThat(buffer.isDirect()).isTrue();
            assertThat(buffer.capacity()).isEqualTo(16);
            assertThat(buffer.maxCapacity()).isEqualTo(64);
            assertThat(ioBuffer.isDirect()).isTrue();
            assertThat(directBuffer.isDirect()).isTrue();
            assertThat(heapBuffer.isDirect()).isFalse();
            assertThat(compositeBuffer.isDirect()).isTrue();
            assertThat(compositeHeapBuffer.isDirect()).isFalse();
            assertThat(compositeDirectBuffer.isDirect()).isTrue();
            assertThat(allocator.isDirectBufferPooled())
                    .isEqualTo(UnpooledByteBufAllocator.DEFAULT.isDirectBufferPooled());
            assertThat(allocator.calculateNewCapacity(33, 128))
                    .isEqualTo(UnpooledByteBufAllocator.DEFAULT.calculateNewCapacity(33, 128));
        } finally {
            buffer.release();
            ioBuffer.release();
            directBuffer.release();
            heapBuffer.release();
            compositeBuffer.release();
            compositeHeapBuffer.release();
            compositeDirectBuffer.release();
        }
    }

    @Test
    void nativeInetAddressConstructsFromEncodedBytesWithDefaultScope() {
        byte[] addressBytes = new byte[] {
                0x20, 0x01, 0x0d, (byte) 0xb8,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 9
        };
        NativeInetAddress nativeAddress = new NativeInetAddress(addressBytes);

        assertThat(nativeAddress.address()).containsExactly(addressBytes);
        assertThat(nativeAddress.scopeId()).isZero();
    }

    @Test
    void nativeInetAddressRejectsUnsupportedSocketAddressPayloadLength() {
        byte[] encoded = new byte[16];

        assertThatThrownBy(() -> NativeInetAddress.address(encoded, 0, 12))
                .isInstanceOf(Error.class);
    }

    @Test
    void unixChannelUtilKeepsRequestedHostNameWhenOsAddressIsAvailable() throws Exception {
        InetSocketAddress requested = InetSocketAddress.createUnresolved("requested.example", 1111);
        InetSocketAddress operatingSystemAddress = new InetSocketAddress(
                InetAddress.getByAddress(new byte[] {10, 11, 12, 13}), 2222);

        InetSocketAddress computed = UnixChannelUtil.computeRemoteAddr(requested, operatingSystemAddress);

        assertThat(computed.getHostString()).isEqualTo("requested.example");
        assertThat(computed.getAddress().getAddress()).containsExactly(10, 11, 12, 13);
        assertThat(computed.getPort()).isEqualTo(2222);
        assertThat(UnixChannelUtil.computeRemoteAddr(requested, null)).isSameAs(requested);
    }

    @Test
    void unixChannelOptionsExposeTypedConstantsAndValidation() {
        ChannelOption<Boolean> reusePort = UnixChannelOption.SO_REUSEPORT;
        ChannelOption<DomainSocketReadMode> readMode = UnixChannelOption.DOMAIN_SOCKET_READ_MODE;

        assertThat(reusePort).isSameAs(ChannelOption.valueOf(UnixChannelOption.class, "SO_REUSEPORT"));
        assertThat(readMode).isSameAs(ChannelOption.valueOf(UnixChannelOption.class, "DOMAIN_SOCKET_READ_MODE"));
        assertThat(reusePort.name()).endsWith("SO_REUSEPORT");
        assertThat(readMode.name()).endsWith("DOMAIN_SOCKET_READ_MODE");
        reusePort.validate(Boolean.TRUE);
        readMode.validate(DomainSocketReadMode.BYTES);
        assertThatNullPointerException().isThrownBy(() -> readMode.validate(null));
    }

    @Test
    void domainSocketReadModeSupportsStableEnumLookup() {
        assertThat(DomainSocketReadMode.values()).containsExactly(
                DomainSocketReadMode.BYTES,
                DomainSocketReadMode.FILE_DESCRIPTORS);
        assertThat(DomainSocketReadMode.valueOf("BYTES")).isSameAs(DomainSocketReadMode.BYTES);
        assertThat(DomainSocketReadMode.valueOf("FILE_DESCRIPTORS"))
                .isSameAs(DomainSocketReadMode.FILE_DESCRIPTORS);
    }

    @Test
    void fileDescriptorWrapsIntegerDescriptorWithValueSemanticsWithoutClosingIt() {
        FileDescriptor descriptor = new FileDescriptor(7);
        FileDescriptor equalDescriptor = new FileDescriptor(7);
        FileDescriptor otherDescriptor = new FileDescriptor(8);

        assertThat(descriptor.intValue()).isEqualTo(7);
        assertThat(descriptor.isOpen()).isTrue();
        assertThat(descriptor).isEqualTo(equalDescriptor);
        assertThat(descriptor.hashCode()).isEqualTo(equalDescriptor.hashCode());
        assertThat(descriptor).isNotEqualTo(otherDescriptor);
        assertThat(descriptor.toString()).isEqualTo("FileDescriptor{fd=7}");
        assertThatThrownBy(() -> new FileDescriptor(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void encodeInt(byte[] target, int index, int value) {
        target[index] = (byte) (value >>> 24);
        target[index + 1] = (byte) (value >>> 16);
        target[index + 2] = (byte) (value >>> 8);
        target[index + 3] = (byte) value;
    }
}
