/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerby_util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Date;

import org.apache.kerby.KOption;
import org.apache.kerby.KOptionGroup;
import org.apache.kerby.KOptionInfo;
import org.apache.kerby.KOptionType;
import org.apache.kerby.KOptions;
import org.apache.kerby.util.Base64;
import org.apache.kerby.util.Base64InputStream;
import org.apache.kerby.util.Base64OutputStream;
import org.apache.kerby.util.ByteArrayReadLine;
import org.apache.kerby.util.CryptoUtil;
import org.apache.kerby.util.Hex;
import org.apache.kerby.util.HexUtil;
import org.apache.kerby.util.HostPort;
import org.apache.kerby.util.IOUtil;
import org.apache.kerby.util.IPAddressParser;
import org.apache.kerby.util.NetworkUtil;
import org.apache.kerby.util.OSUtil;
import org.apache.kerby.util.PublicKeyDeriver;
import org.apache.kerby.util.ReadLine;
import org.apache.kerby.util.SysUtil;
import org.apache.kerby.util.Utf8;
import org.apache.kerby.util.Util;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Kerby_utilTest {
    @TempDir
    private File temporaryDirectory;

    @Test
    void base64EncodesAndDecodesStandardChunkedUrlSafeAndIntegerForms() {
        byte[] payload = "Kerby util: \u03c0, data, and binary \u0000\u0001".getBytes(StandardCharsets.UTF_8);

        String encoded = Base64.encodeBase64String(payload);
        assertThat(encoded.trim()).isEqualTo(java.util.Base64.getEncoder().encodeToString(payload));
        assertThat(Base64.decodeBase64(encoded)).isEqualTo(payload);
        assertThat(new Base64().decode(encoded)).isEqualTo(payload);
        assertThat(new Base64().encodeToString(payload)).isEqualTo(encoded);
        assertThat(Base64.isArrayByteBase64(encoded.getBytes(StandardCharsets.US_ASCII))).isTrue();

        byte[] longPayload = new byte[120];
        for (int index = 0; index < longPayload.length; index++) {
            longPayload[index] = (byte) index;
        }
        byte[] chunked = Base64.encodeBase64Chunked(longPayload);
        String chunkedText = new String(chunked, StandardCharsets.US_ASCII);
        assertThat(chunkedText).contains("\r\n");
        assertThat(Base64.decodeBase64(chunked)).isEqualTo(longPayload);

        String urlSafe = Base64.encodeBase64URLSafeString(new byte[] {(byte) 0xfb, (byte) 0xff, (byte) 0xee});
        assertThat(urlSafe).doesNotContain("+").doesNotContain("/").doesNotContain("=");
        assertThat(Base64.decodeBase64(urlSafe)).isEqualTo(new byte[] {(byte) 0xfb, (byte) 0xff, (byte) 0xee});
        assertThat(new Base64(true).isUrlSafe()).isTrue();
        assertThat(new Base64(false).isUrlSafe()).isFalse();

        BigInteger integer = new BigInteger("123456789abcdef123456789abcdef", 16);
        byte[] integerBytes = Base64.encodeInteger(integer);
        assertThat(Base64.decodeInteger(integerBytes)).isEqualTo(integer);
    }

    @Test
    void base64StreamsRoundTripDataInSmallWritesAndReads() throws IOException {
        byte[] payload = new byte[257];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) (index * 31 + 7);
        }

        ByteArrayOutputStream encodedBytes = new ByteArrayOutputStream();
        try (Base64OutputStream output = new Base64OutputStream(encodedBytes)) {
            output.write(payload, 0, 13);
            for (int index = 13; index < payload.length; index++) {
                output.write(payload[index]);
            }
        }

        byte[] encoded = encodedBytes.toByteArray();
        assertThat(Base64.isArrayByteBase64(encoded)).isTrue();

        ByteArrayOutputStream decodedBytes = new ByteArrayOutputStream();
        try (Base64InputStream input = new Base64InputStream(new ByteArrayInputStream(encoded))) {
            assertThat(input.markSupported()).isFalse();
            byte[] buffer = new byte[17];
            int read = input.read(buffer, 0, buffer.length);
            while (read != -1) {
                decodedBytes.write(buffer, 0, read);
                read = input.read(buffer, 0, buffer.length);
            }
        }
        assertThat(decodedBytes.toByteArray()).isEqualTo(payload);
    }

    @Test
    void base64StreamsCanReverseDefaultDirectionWithCustomLineSeparators() throws IOException {
        byte[] payload = "stream filters can encode on read and decode on write".getBytes(StandardCharsets.UTF_8);
        byte[] lineSeparator = new byte[] {'~'};

        ByteArrayOutputStream encodedBytes = new ByteArrayOutputStream();
        try (Base64InputStream input = new Base64InputStream(new ByteArrayInputStream(payload), true, 8,
                lineSeparator)) {
            byte[] buffer = new byte[5];
            int read = input.read(buffer, 0, buffer.length);
            while (read != -1) {
                encodedBytes.write(buffer, 0, read);
                read = input.read(buffer, 0, buffer.length);
            }
        }

        String encodedText = encodedBytes.toString(StandardCharsets.US_ASCII.name());
        assertThat(encodedText).contains("~");
        assertThat(encodedText.replace("~", ""))
                .isEqualTo(java.util.Base64.getEncoder().encodeToString(payload));

        ByteArrayOutputStream decodedBytes = new ByteArrayOutputStream();
        byte[] encoded = encodedText.getBytes(StandardCharsets.US_ASCII);
        try (Base64OutputStream output = new Base64OutputStream(decodedBytes, false)) {
            output.write(encoded, 0, 7);
            output.write(encoded, 7, encoded.length - 7);
        }
        assertThat(decodedBytes.toByteArray()).isEqualTo(payload);
    }

    @Test
    void hexUtilitiesRoundTripPlainFriendlyAndPartialEncodings() {
        byte[] bytes = new byte[] {0, 1, 15, 16, 31, 127, (byte) 0x80, (byte) 0xff};

        String encoded = Hex.encode(bytes);
        assertThat(encoded).isEqualTo("00010f101f7f80ff");
        assertThat(Hex.decode(encoded)).isEqualTo(bytes);
        assertThat(Hex.decode(encoded.getBytes(StandardCharsets.US_ASCII))).isEqualTo(bytes);
        assertThat(Hex.encode(bytes, 2, 4)).isEqualTo("0f101f7f");

        String friendly = HexUtil.bytesToHexFriendly(bytes);
        assertThat(friendly).isEqualTo("0x00 01 0F 10 1F 7F 80 FF ");
        assertThat(HexUtil.hex2bytesFriendly(friendly)).isEqualTo(bytes);
        assertThat(HexUtil.hex2bytesFriendly("00 01 0F 10 1F 7F 80 FF")).isEqualTo(bytes);
        assertThat(HexUtil.bytesToHex(bytes)).isEqualTo("00010F101F7F80FF");
        assertThat(HexUtil.hex2bytes("00010F101F7F80FF")).isEqualTo(bytes);
    }

    @Test
    void utf8AndLineReadersPreserveTextAndReadMultiplePhysicalLines() throws IOException {
        String text = "ASCII, accents \u00e9, Greek \u03c0, emoji \ud83d\ude80";
        byte[] encoded = Utf8.toBytes(text);
        assertThat(encoded).isEqualTo(text.getBytes(StandardCharsets.UTF_8));
        assertThat(Utf8.toString(encoded)).isEqualTo(text);

        String lines = "alpha\nbeta\ngamma\rdelta";
        ReadLine reader = new ReadLine(new ByteArrayInputStream(lines.getBytes(StandardCharsets.UTF_8)));
        assertThat(reader.next()).isEqualTo("alpha");
        assertThat(reader.next(2)).isEqualTo("betagamma");
        assertThat(reader.next()).isEqualTo("delta");
        assertThat(reader.next()).isNull();

        byte[] twoLines = "one\ntwo".getBytes(StandardCharsets.UTF_8);
        ByteArrayReadLine byteReader = new ByteArrayReadLine(new ByteArrayInputStream(twoLines));
        assertThat(byteReader.next()).isEqualTo("one");
        assertThat(new String(byteReader.nextAsBytes(), StandardCharsets.UTF_8).trim()).startsWith("two");
    }

    @Test
    void ioUtilReadsWritesFilesAndStreams() throws IOException {
        String content = "first line\nsecond line\nunicode \u03c0";
        File file = new File(temporaryDirectory, "kerby-io.txt");

        IOUtil.writeFile(content, file);
        assertThat(IOUtil.readFile(file)).isEqualTo(content);
        ByteArrayInputStream input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        assertThat(IOUtil.readInput(input)).isEqualTo(content);
        assertThat(IOUtil.readInputStream(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))))
                .isEqualTo(content.getBytes(StandardCharsets.UTF_8));

        byte[] fixedBuffer = new byte[content.getBytes(StandardCharsets.UTF_8).length];
        IOUtil.readInputStream(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), fixedBuffer);
        assertThat(fixedBuffer).isEqualTo(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void utilHandlesStreamsFilesPaddingTrimmingAddressesAndBooleans() throws Exception {
        byte[] payload = new byte[5000];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) (index % 251);
        }

        ByteArrayOutputStream piped = new ByteArrayOutputStream();
        Util.pipeStream(new ByteArrayInputStream(payload), piped, false);
        assertThat(piped.toByteArray()).isEqualTo(payload);
        assertThat(Util.streamToBytes(new ByteArrayInputStream(payload))).isEqualTo(payload);
        assertThat(Util.streamToBytes(new ByteArrayInputStream(payload), 128)).isEqualTo(Arrays.copyOf(payload, 128));
        assertThat(Util.resizeArray(new byte[] {1, 2, 3})).hasSize(6).startsWith((byte) 1, (byte) 2, (byte) 3);

        File file = new File(temporaryDirectory, "payload.bin");
        Files.write(file.toPath(), payload);
        assertThat(Util.fileToBytes(file)).isEqualTo(payload);

        assertThat(Util.isYes(" enabled ")).isTrue();
        assertThat(Util.isYes("off")).isFalse();
        assertThat(Util.trim(" \t\r\nvalue\f ")).isEqualTo("value");
        assertThat(Util.trim(null)).isNull();
        assertThat(Util.isWhiteSpace('\0')).isTrue();
        assertThat(Util.isWhiteSpace('x')).isFalse();
        assertThat(Util.pad("id", 5, true)).isEqualTo("   id");
        assertThat(Util.pad("id", 5, false)).isEqualTo("id   ");
        assertThat(Util.pad("already-long", 3, true)).isEqualTo("already-long");
        assertThat(Util.cipherToAuthType("AES256_CTS_HMAC_SHA1_96_WITH_AES")).isEqualTo("CTS_HMAC_SHA1_96");

        HostPort hostPort = Util.toAddress("127.0.0.1:65000", 88);
        assertThat(hostPort.host).isEqualTo("127.0.0.1");
        assertThat(hostPort.port).isEqualTo(65000);
        assertThat(hostPort.toString()).isEqualTo("127.0.0.1:65000");
        assertThat(Util.toInetAddress("127.0.0.1").getHostAddress()).isEqualTo("127.0.0.1");
        assertThatThrownBy(() -> Util.toAddress("host:88:extra", 88)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fillPopulatesExistingBuffersAndReportsEndOfStream() throws IOException {
        byte[] payload = new byte[] {10, 11, 12, 13, 14};
        byte[] partialBuffer = new byte[] {99, 0, 0, 0, 0, 0, 77, 88};

        int[] partialStatus = Util.fill(partialBuffer, 1, new ByteArrayInputStream(payload));
        assertThat(partialStatus[Util.SIZE_KEY]).isEqualTo(6);
        assertThat(partialStatus[Util.LAST_READ_KEY]).isEqualTo(-1);
        assertThat(partialBuffer[0]).isEqualTo((byte) 99);
        assertThat(Arrays.copyOfRange(partialBuffer, 1, 6)).isEqualTo(payload);
        assertThat(Arrays.copyOfRange(partialBuffer, 6, partialBuffer.length)).isEqualTo(new byte[] {77, 88});

        byte[] exactBuffer = new byte[4];
        InputStream source = new ByteArrayInputStream(new byte[] {21, 22, 23, 24, 25});
        int[] exactStatus = Util.fill(exactBuffer, 0, source);
        assertThat(exactStatus[Util.SIZE_KEY]).isEqualTo(exactBuffer.length);
        assertThat(exactStatus[Util.LAST_READ_KEY]).isEqualTo(exactBuffer.length);
        assertThat(exactBuffer).isEqualTo(new byte[] {21, 22, 23, 24});
        assertThat(source.read()).isEqualTo(25);
    }

    @Test
    void optionsStoreTypedValuesAndParseCommandLineText() throws Exception {
        KOptionGroup group = () -> "general";
        KOptionInfo info = new KOptionInfo("name", "description", group, KOptionType.STR);
        assertThat(info.getName()).isEqualTo("name");
        assertThat(info.getDescription()).isEqualTo("description");
        assertThat(info.getGroup().getGroupName()).isEqualTo("general");
        info.setName("renamed");
        info.setDescription("new description");
        info.setType(KOptionType.INT);
        info.setValue(123);
        assertThat(info.getName()).isEqualTo("renamed");
        assertThat(info.getDescription()).isEqualTo("new description");
        assertThat(info.getType()).isEqualTo(KOptionType.INT);
        assertThat(info.getValue()).isEqualTo(123);

        TestOption stringOption = new TestOption("string", KOptionType.STR);
        TestOption intOption = new TestOption("integer", KOptionType.INT);
        TestOption boolOption = new TestOption("boolean", KOptionType.BOOL);
        TestOption fileOption = new TestOption("file", KOptionType.FILE);
        TestOption dirOption = new TestOption("dir", KOptionType.DIR);
        TestOption dateOption = new TestOption("date", KOptionType.DATE);
        TestOption durationOption = new TestOption("duration", KOptionType.DURATION);
        TestOption flagOption = new TestOption("flag", KOptionType.NOV);

        KOptions options = new KOptions();
        options.add(stringOption, "value");
        options.add(intOption, 42);
        options.add(boolOption, "yes");
        options.add(fileOption, new File("settings.conf"));
        options.add(dirOption, temporaryDirectory);
        Date now = new Date();
        options.add(dateOption, now);

        assertThat(options.contains(stringOption)).isTrue();
        assertThat(options.getOptions())
                .contains(stringOption, intOption, boolOption, fileOption, dirOption, dateOption);
        assertThat(options.getOption(stringOption)).isSameAs(stringOption);
        assertThat(options.getStringOption(stringOption)).isEqualTo("value");
        assertThat(options.getIntegerOption(intOption)).isEqualTo(42);
        assertThat(options.getBooleanOption(boolOption, false)).isTrue();
        assertThat(options.getFileOption(fileOption)).isEqualTo(new File("settings.conf"));
        assertThat(options.getDirOption(dirOption)).isEqualTo(temporaryDirectory);
        assertThat(options.getDateOption(dateOption)).isEqualTo(now);
        assertThat(options.getOptionValue(durationOption)).isNull();
        assertThat(options.getIntegerOption(durationOption)).isEqualTo(-1);
        assertThat(options.getBooleanOption(durationOption, true)).isTrue();

        assertThat(KOptions.parseSetValue(stringOption.getOptionInfo(), "updated")).isTrue();
        assertThat(KOptions.parseSetValue(intOption.getOptionInfo(), "1024")).isTrue();
        assertThat(KOptions.parseSetValue(boolOption.getOptionInfo(), "true")).isTrue();
        assertThat(KOptions.parseSetValue(fileOption.getOptionInfo(), "settings.conf")).isTrue();
        assertThat(KOptions.parseSetValue(dirOption.getOptionInfo(), temporaryDirectory.getAbsolutePath())).isTrue();
        assertThat(KOptions.parseSetValue(dateOption.getOptionInfo(), "31/12/24:23:59:58")).isTrue();
        assertThat(KOptions.parseSetValue(durationOption.getOptionInfo(), "1D2H3M4S")).isTrue();
        assertThat(KOptions.parseSetValue(flagOption.getOptionInfo(), null)).isTrue();

        assertThat(stringOption.getOptionInfo().getValue()).isEqualTo("updated");
        assertThat(intOption.getOptionInfo().getValue()).isEqualTo(1024);
        assertThat(boolOption.getOptionInfo().getValue()).isEqualTo(Boolean.TRUE);
        assertThat(fileOption.getOptionInfo().getValue()).isEqualTo(new File("settings.conf"));
        assertThat(dateOption.getOptionInfo().getValue()).isInstanceOf(Date.class);
        assertThat(durationOption.getOptionInfo().getValue()).isEqualTo(93784);

        assertThatThrownBy(() -> KOptions.parseSetValue(intOption.getOptionInfo(), "not-an-integer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid integer");
        File missingDirectory = new File(temporaryDirectory, "missing");
        assertThatThrownBy(() -> KOptions.parseSetValue(dirOption.getOptionInfo(), missingDirectory.getPath()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid dir");
        assertThat(KOptions.parseSetValue(new KOptionInfo("empty", "desc", KOptionType.STR), "")).isFalse();
    }

    @Test
    void addressParsersAcceptValidIpLiteralsAndRejectInvalidOnes() throws Exception {
        assertThat(IPAddressParser.parseIPv4Literal(" 192.168.1.10 "))
                .isEqualTo(new byte[] {(byte) 192, (byte) 168, 1, 10});
        assertThat(IPAddressParser.parseIPv4Literal("256.1.1.1")).isNull();
        assertThat(IPAddressParser.parseIPv4Literal("1.2.3")).isNull();

        byte[] loopback = IPAddressParser.parseIPv6Literal("[::1]");
        assertThat(loopback).hasSize(16);
        assertThat(loopback[15]).isEqualTo((byte) 1);
        assertThat(IPAddressParser.parseIPv6Literal("2001:db8::192.0.2.33"))
                .isEqualTo(InetAddress.getByName("2001:db8::192.0.2.33").getAddress());
        assertThat(IPAddressParser.parseIPv6Literal("2001::db8::1")).isNull();
        assertThat(IPAddressParser.parseIPv6Literal("2001:db8:zzzz::1")).isNull();
    }

    @Test
    void publicKeyDeriverRebuildsRsaPublicKeyAndSystemHelpersReturnSaneValues() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(512);
        KeyPair keyPair = generator.generateKeyPair();

        PublicKey derived = PublicKeyDeriver.derivePublicKey(keyPair.getPrivate());
        assertThat(derived).isInstanceOf(RSAPublicKey.class);
        RSAPublicKey expected = (RSAPublicKey) keyPair.getPublic();
        RSAPublicKey actual = (RSAPublicKey) derived;
        assertThat(actual.getModulus()).isEqualTo(expected.getModulus());
        assertThat(actual.getPublicExponent()).isEqualTo(expected.getPublicExponent());
        assertThatThrownBy(() -> PublicKeyDeriver.derivePublicKey(new UnsupportedPrivateKey()))
                .isInstanceOf(java.security.KeyException.class)
                .hasMessageContaining("DSA or RSA");

        assertThat(CryptoUtil.isAES256Enabled()).isTrue();
        assertThat(NetworkUtil.getServerPort()).isBetween(1, 65535);
        assertThat(SysUtil.getTempDir()).isDirectory();

        String osName = System.getProperty("os.name").toLowerCase();
        assertThat(OSUtil.isWindows()).isEqualTo(osName.contains("win"));
        assertThat(OSUtil.isMac()).isEqualTo(osName.contains("mac"));
        assertThat(OSUtil.isUnix())
                .isEqualTo(osName.contains("nix") || osName.contains("nux") || osName.contains("aix"));
        assertThat(OSUtil.isSolaris()).isEqualTo(osName.contains("sunos"));

        KeyStore first = KeyStore.getInstance(KeyStore.getDefaultType());
        KeyStore second = KeyStore.getInstance(KeyStore.getDefaultType());
        first.load(null, null);
        second.load(null, null);
        assertThat(Util.equals(first, second)).isTrue();
        assertThat(Util.equals(first, null)).isFalse();
        assertThat(Util.equals(null, null)).isTrue();
    }

    private static final class UnsupportedPrivateKey implements PrivateKey {
        private static final long serialVersionUID = 1L;

        @Override
        public String getAlgorithm() {
            return "unsupported";
        }

        @Override
        public String getFormat() {
            return "none";
        }

        @Override
        public byte[] getEncoded() {
            return new byte[0];
        }
    }

    private static final class TestOption implements KOption {
        private final KOptionInfo optionInfo;

        private TestOption(String name, KOptionType type) {
            this.optionInfo = new KOptionInfo(name, "test " + name, type);
        }

        @Override
        public KOptionInfo getOptionInfo() {
            return optionInfo;
        }
    }
}
