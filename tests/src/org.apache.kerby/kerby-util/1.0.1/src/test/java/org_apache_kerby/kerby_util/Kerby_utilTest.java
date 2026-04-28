/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerby_util;

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
import org.apache.kerby.util.SysUtil;
import org.apache.kerby.util.Utf8;
import org.apache.kerby.util.Util;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Kerby_utilTest {
    @Test
    void base64StaticAndInstanceCodecsRoundTripBinaryData() {
        byte[] data = new byte[] {0, 1, 2, 3, 4, 5, 6, 63, 64, 65, 126, 127, -128, -2, -1};

        byte[] encoded = Base64.encodeBase64(data);
        assertThat(Utf8.toString(encoded)).isEqualTo(java.util.Base64.getEncoder().encodeToString(data));
        assertThat(Base64.decodeBase64(encoded)).containsExactly(data);
        assertThat(Base64.isArrayByteBase64(encoded)).isTrue();
        assertThat(Base64.isBase64((byte) '*')).isFalse();

        Base64 standardCodec = new Base64(0);
        assertThat(standardCodec.isUrlSafe()).isFalse();
        assertThat(standardCodec.encodeToString(data)).isEqualTo(java.util.Base64.getEncoder().encodeToString(data));
        assertThat(standardCodec.decode(standardCodec.encodeToString(data))).containsExactly(data);
        assertThat((byte[]) standardCodec.decode((Object) standardCodec.encode(data))).containsExactly(data);
        assertThat((byte[]) standardCodec.encode((Object) data)).containsExactly(standardCodec.encode(data));
    }

    @Test
    void base64UrlSafeChunkedAndIntegerEncodingUseExpectedFormats() {
        byte[] bytesRequiringUrlAlphabet = new byte[] {(byte) 0xfb, (byte) 0xff, (byte) 0xee, 0, 1, 2};

        assertThat(new Base64(true).isUrlSafe()).isTrue();
        assertThat(Base64.encodeBase64URLSafeString(bytesRequiringUrlAlphabet))
                .isEqualTo(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytesRequiringUrlAlphabet));
        assertThat(Base64.decodeBase64(Base64.encodeBase64URLSafe(bytesRequiringUrlAlphabet)))
                .containsExactly(bytesRequiringUrlAlphabet);

        byte[] longInput = new byte[60];
        for (int i = 0; i < longInput.length; i++) {
            longInput[i] = (byte) i;
        }
        String chunked = Utf8.toString(Base64.encodeBase64Chunked(longInput));
        assertThat(chunked).contains("\r\n");
        assertThat(Base64.decodeBase64(chunked)).containsExactly(longInput);

        BigInteger integer = new BigInteger("123456789abcdef123456789abcdef", 16);
        byte[] encodedInteger = Base64.encodeInteger(integer);
        assertThat(Base64.decodeInteger(encodedInteger)).isEqualTo(integer);
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> Base64.encodeInteger(null));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new Base64().encode("not-bytes"));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new Base64().decode(new Object()));
    }

    @Test
    void base64StreamsEncodeAndDecodeIncrementalWritesAndReads() throws Exception {
        byte[] data = Utf8.toBytes("Kerby stream data with unicode: \u041f\u0440\u0438\u0432\u0435\u0442 \u4e16\u754c and binary \u0000 markers");

        ByteArrayOutputStream encodedOutput = new ByteArrayOutputStream();
        try (Base64OutputStream stream = new Base64OutputStream(encodedOutput, true, 8, new byte[] {'~'})) {
            stream.write(data, 0, 7);
            stream.write(data[7]);
            stream.write(data, 8, data.length - 8);
        }
        byte[] encoded = encodedOutput.toByteArray();
        assertThat(Utf8.toString(encoded)).contains("~");

        ByteArrayOutputStream decodedOutput = new ByteArrayOutputStream();
        try (Base64OutputStream stream = new Base64OutputStream(decodedOutput, false)) {
            stream.write(encoded, 0, encoded.length);
        }
        assertThat(decodedOutput.toByteArray()).containsExactly(data);

        try (Base64InputStream encodingInput = new Base64InputStream(new ByteArrayInputStream(data), true, 0, null)) {
            byte[] streamEncoded = IOUtil.readInputStream(encodingInput);
            assertThat(Utf8.toString(streamEncoded)).isEqualTo(java.util.Base64.getEncoder().encodeToString(data));
        }

        try (Base64InputStream decodingInput = new Base64InputStream(new ByteArrayInputStream(encoded))) {
            byte[] streamDecoded = IOUtil.readInputStream(decodingInput);
            assertThat(streamDecoded).containsExactly(data);
            assertThat(decodingInput.markSupported()).isFalse();
        }
    }

    @Test
    void hexUtilitiesConvertFriendlyAndCompactRepresentations() {
        byte[] data = new byte[] {0, 15, 16, 31, 32, 127, -128, -1};

        String lowerCaseHex = Hex.encode(data);
        assertThat(lowerCaseHex).isEqualTo("000f101f207f80ff");
        assertThat(Hex.decode(lowerCaseHex)).containsExactly(data);
        assertThat(Hex.decode(lowerCaseHex.getBytes(StandardCharsets.US_ASCII))).containsExactly(data);
        assertThat(Hex.encode(data, 2, 4)).isEqualTo("101f207f");

        String upperCaseHex = HexUtil.bytesToHex(data);
        assertThat(upperCaseHex).isEqualTo("000F101F207F80FF");
        assertThat(HexUtil.hex2bytes(upperCaseHex)).containsExactly(data);

        String friendly = HexUtil.bytesToHexFriendly(data);
        assertThat(friendly).isEqualTo("0x00 0F 10 1F 20 7F 80 FF ");
        assertThat(HexUtil.hex2bytesFriendly(friendly)).containsExactly(data);
        assertThatThrownBy(() -> HexUtil.hex2bytesFriendly("0x0 F"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid hex string");
    }

    @Test
    void utf8IoAndStreamUtilitiesPreserveContent(@TempDir Path tempDir) throws Exception {
        String content = "first line\nsecond line with emoji \uD83D\uDE80\nthird line";
        byte[] contentBytes = Utf8.toBytes(content);
        File file = tempDir.resolve("kerby-util.txt").toFile();

        IOUtil.writeFile(content, file);
        assertThat(IOUtil.readFile(file)).isEqualTo(content);
        assertThat(IOUtil.readInput(new ByteArrayInputStream(contentBytes))).isEqualTo(content);
        assertThat(IOUtil.readInputStream(new ByteArrayInputStream(contentBytes))).containsExactly(contentBytes);

        byte[] fixedBuffer = new byte[contentBytes.length];
        IOUtil.readInputStream(new ByteArrayInputStream(contentBytes), fixedBuffer);
        assertThat(fixedBuffer).containsExactly(contentBytes);
        assertThatExceptionOfType(java.io.IOException.class)
                .isThrownBy(() -> IOUtil.readInputStream(new ByteArrayInputStream(new byte[] {1, 2}), new byte[3]))
                .withMessageContaining("premature EOF");

        assertThat(Util.fileToBytes(file)).containsExactly(contentBytes);
        assertThat(Util.streamToBytes(new ByteArrayInputStream(contentBytes), contentBytes.length + 10))
                .containsExactly(contentBytes);
        assertThat(Util.streamToBytes(new ByteArrayInputStream(contentBytes))).containsExactly(contentBytes);

        ByteArrayOutputStream piped = new ByteArrayOutputStream();
        Util.pipeStream(new ByteArrayInputStream(contentBytes), piped, false);
        assertThat(piped.toByteArray()).containsExactly(contentBytes);
    }

    @Test
    void readLineReadsTextLinesAndBytePrefixes() {
        ByteArrayReadLine readLine = new ByteArrayReadLine(
                new ByteArrayInputStream(Utf8.toBytes("alpha\r\nbeta\ngamma")));
        assertThat(readLine.next()).isEqualTo("alpha");
        assertThat(readLine.next()).isEqualTo("beta");
        assertThat(readLine.next()).isEqualTo("gamma");
        assertThat(readLine.next()).isNull();

        ByteArrayReadLine groupedLines = new ByteArrayReadLine(
                new ByteArrayInputStream(Utf8.toBytes("one\ntwo\nthree\n")));
        assertThat(groupedLines.next(2)).isEqualTo("onetwo");
        assertThat(groupedLines.next()).isEqualTo("three");

        ByteArrayReadLine byteLines = new ByteArrayReadLine(new ByteArrayInputStream(new byte[] {65, 66, 10, 67}));
        assertThat(byteLines.nextAsBytes()).startsWith((byte) 65, (byte) 66);
        assertThat(byteLines.nextAsBytes()).startsWith((byte) 67);
        assertThat(byteLines.nextAsBytes()).isNull();
    }

    @Test
    void optionsParseValuesAndExposeTypedAccessors() throws Exception {
        KOptionGroup group = () -> "general";
        KOptionInfo stringInfo = new KOptionInfo("principal", "Kerberos principal", group, KOptionType.STR);
        KOptionInfo intInfo = new KOptionInfo("retries", "Retry count", KOptionType.INT);
        KOptionInfo boolInfo = new KOptionInfo("debug", "Enable debug", KOptionType.BOOL);
        KOptionInfo durationInfo = new KOptionInfo("timeout", "Timeout", KOptionType.DURATION);
        KOptionInfo fileInfo = new KOptionInfo("keytab", "Keytab", KOptionType.FILE);
        KOptionInfo dirInfo = new KOptionInfo("cache", "Cache directory", KOptionType.DIR);
        KOptionInfo dateInfo = new KOptionInfo("start", "Start date", KOptionType.DATE);
        KOptionInfo noValueInfo = new KOptionInfo("verbose", "Verbose", KOptionType.NOV);

        assertThat(KOptions.parseSetValue(stringInfo, "alice@EXAMPLE.COM")).isTrue();
        assertThat(KOptions.parseSetValue(intInfo, "3")).isTrue();
        assertThat(KOptions.parseSetValue(boolInfo, "true")).isTrue();
        assertThat(KOptions.parseSetValue(durationInfo, "1D2H3M4S")).isTrue();
        assertThat(KOptions.parseSetValue(fileInfo, "target/keytab.bin")).isTrue();
        assertThat(KOptions.parseSetValue(dirInfo, ".")).isTrue();
        assertThat(KOptions.parseSetValue(dateInfo, "31/12/23:23:59:58")).isTrue();
        assertThat(KOptions.parseSetValue(noValueInfo, null)).isTrue();

        assertThat(stringInfo.getName()).isEqualTo("principal");
        assertThat(stringInfo.getDescription()).isEqualTo("Kerberos principal");
        assertThat(stringInfo.getGroup()).isSameAs(group);
        assertThat(group.getGroupName()).isEqualTo("general");
        assertThat(intInfo.getValue()).isEqualTo(3);
        assertThat(boolInfo.getValue()).isEqualTo(Boolean.TRUE);
        assertThat(durationInfo.getValue()).isEqualTo(93_784);
        assertThat(fileInfo.getValue()).isEqualTo(new File("target/keytab.bin"));
        assertThat(dirInfo.getValue()).isEqualTo(new File("."));
        assertThat(dateInfo.getValue()).isInstanceOf(Date.class);

        Date expectedDate = new SimpleDateFormat("dd/MM/yy:HH:mm:ss").parse("31/12/23:23:59:58");
        assertThat(dateInfo.getValue()).isEqualTo(expectedDate);
    }

    @Test
    void optionsContainerHandlesObjectsDefaultsAndInvalidInput() {
        TestOption principal = new TestOption("principal", KOptionType.STR);
        TestOption retries = new TestOption("retries", KOptionType.INT);
        TestOption keytab = new TestOption("keytab", KOptionType.FILE);
        TestOption start = new TestOption("start", KOptionType.DATE);
        TestOption debug = new TestOption("debug", KOptionType.BOOL);
        TestOption missing = new TestOption("missing", KOptionType.STR);

        KOptions options = new KOptions();
        options.add(principal, "bob@EXAMPLE.COM");
        options.add(retries, "7");
        options.add(keytab, new File("bob.keytab"));
        options.add(start, new Date(1_234_567L));
        options.add(debug, "yes");
        options.add(null);

        assertThat(options.contains(principal)).isTrue();
        assertThat(options.contains(missing)).isFalse();
        assertThat(options.getOptions()).containsExactlyInAnyOrder(principal, retries, keytab, start, debug);
        assertThat(options.getOption(principal)).isSameAs(principal);
        assertThat(options.getOption(missing)).isNull();
        assertThat(options.getOptionValue(missing)).isNull();
        assertThat(options.getStringOption(principal)).isEqualTo("bob@EXAMPLE.COM");
        assertThat(options.getIntegerOption(retries)).isEqualTo(7);
        assertThat(options.getFileOption(keytab)).isEqualTo(new File("bob.keytab"));
        assertThat(options.getDateOption(start)).isEqualTo(new Date(1_234_567L));
        assertThat(options.getBooleanOption(debug, false)).isTrue();
        assertThat(options.getBooleanOption(missing, true)).isTrue();

        KOptionInfo duration = new KOptionInfo("duration", "Duration", KOptionType.DURATION);
        assertThat(KOptions.parseSetValue(duration, "01:02:03")).isTrue();
        assertThat(duration.getValue()).isEqualTo(3_723);
        assertThat(KOptions.parseSetValue(new KOptionInfo("empty", "Empty", KOptionType.STR), "")).isFalse();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> KOptions.parseSetValue(new KOptionInfo("bad", "Bad", KOptionType.INT), "abc"))
                .withMessageContaining("Invalid integer");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> KOptions.parseSetValue(
                        new KOptionInfo("dir", "Directory", KOptionType.DIR), "missing-dir"))
                .withMessageContaining("Invalid dir");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> KOptions.parseSetValue(
                        new KOptionInfo("date", "Date", KOptionType.DATE), "not-a-date"))
                .withMessageContaining("Fail to parse");
    }

    @Test
    void ipAddressParsingAndHostPortAvoidReverseLookupsForLiterals() throws Exception {
        assertThat(IPAddressParser.parseIPv4Literal(" 192.0.2.33 "))
                .containsExactly((byte) 192, (byte) 0, (byte) 2, (byte) 33);
        assertThat(IPAddressParser.parseIPv4Literal("256.0.2.33")).isNull();
        assertThat(IPAddressParser.parseIPv4Literal("192.0.2")).isNull();

        byte[] ipv6 = IPAddressParser.parseIPv6Literal("[2001:db8::192.0.2.33]");
        assertThat(ipv6).hasSize(16);
        assertThat(ipv6).startsWith((byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8);
        assertThat(ipv6).endsWith((byte) 192, (byte) 0, (byte) 2, (byte) 33);
        assertThat(IPAddressParser.parseIPv6Literal("1::2::3")).isNull();
        assertThat(IPAddressParser.parseIPv6Literal("2001:db8:0:0:0:0:0:1"))
                .containsExactly(new byte[] {(byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});

        assertThat(Util.toInetAddress("127.0.0.1").getHostAddress()).isEqualTo("127.0.0.1");
        HostPort hostPort = Util.toAddress("127.0.0.1:8443", 88);
        assertThat(hostPort.host).isEqualTo("127.0.0.1");
        assertThat(hostPort.port).isEqualTo(8443);
        assertThat(hostPort.toString()).isEqualTo("127.0.0.1:8443");
        assertThat(hostPort.addr.getHostAddress()).isEqualTo("127.0.0.1");
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> Util.toAddress("a:b:c", 88));
    }

    @Test
    void networkUtilityAllocatesBindableServerPort() throws Exception {
        int port = NetworkUtil.getServerPort();

        assertThat(port).isBetween(1, 65_535);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            assertThat(serverSocket.getLocalPort()).isEqualTo(port);
        }
    }

    @Test
    void generalUtilitiesHandleStringsPaddingSystemAndCipherNames() {
        assertThat(Util.isYes(" yes ")).isTrue();
        assertThat(Util.isYes("ENABLED")).isTrue();
        assertThat(Util.isYes("off")).isFalse();
        assertThat(Util.isYes(null)).isFalse();
        assertThat(Util.trim(" \tvalue\r\n")).isEqualTo("value");
        assertThat(Util.trim(null)).isNull();
        assertThat(Util.trim("")).isEmpty();
        assertThat(Util.isWhiteSpace('\0')).isTrue();
        assertThat(Util.isWhiteSpace('x')).isFalse();
        assertThat(Util.pad("kerby", 8, true)).isEqualTo("   kerby");
        assertThat(Util.pad("kerby", 8, false)).isEqualTo("kerby   ");
        assertThat(Util.pad("kerby", 3, true)).isEqualTo("kerby");
        assertThat(Util.pad(null, 3, false)).isEqualTo("   ");
        assertThat(Util.resizeArray(new byte[] {1, 2, 3})).containsExactly(new byte[] {1, 2, 3, 0, 0, 0});
        assertThat(Util.cipherToAuthType("SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA")).isEqualTo("DHE_DSS_EXPORT");
        assertThat(Util.cipherToAuthType("TLS_RSA_WITH_AES_128_CBC_SHA")).isEqualTo("RSA");
        assertThat(Util.cipherToAuthType(null)).isNull();
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> Util.cipherToAuthType("SSL"));

        assertThat(SysUtil.getTempDir()).isDirectory();
        assertThat(OSUtil.isWindows() || OSUtil.isMac() || OSUtil.isUnix() || OSUtil.isSolaris()).isTrue();
        assertThat(CryptoUtil.isAES256Enabled()).isTrue();
    }

    @Test
    void keyStoreComparisonHandlesNullsAndEntryKinds() throws Exception {
        char[] password = "changeit".toCharArray();
        SecretKey secretKey = new SecretKeySpec(new byte[] {
                0, 1, 2, 3, 4, 5, 6, 7,
                8, 9, 10, 11, 12, 13, 14, 15}, "AES");
        KeyStore firstKeyStore = createInMemoryKeyStore();
        KeyStore secondKeyStore = createInMemoryKeyStore();
        addSecretKeyEntry(firstKeyStore, "session", secretKey, password);
        addSecretKeyEntry(secondKeyStore, "session", secretKey, password);

        assertThat(Util.equals(firstKeyStore, secondKeyStore)).isTrue();
        assertThat(Util.equals(firstKeyStore, null)).isFalse();
        assertThat(Util.equals(null, null)).isTrue();

        KeyStore certificateStore = createInMemoryKeyStore();
        certificateStore.setCertificateEntry("session", new TestCertificate(new byte[] {1, 2, 3}));
        assertThat(Util.equals(firstKeyStore, certificateStore)).isFalse();
    }

    @Test
    void publicKeyDeriverCreatesPublicKeysForSupportedPrivateKeyTypes() throws Exception {
        KeyPair rsaKeyPair = generateKeyPair("RSA");

        PublicKey derivedRsa = PublicKeyDeriver.derivePublicKey(rsaKeyPair.getPrivate());

        assertThat(derivedRsa.getAlgorithm()).isEqualTo("RSA");
        assertThat(derivedRsa.getEncoded()).containsExactly(rsaKeyPair.getPublic().getEncoded());

        KeyPair dsaKeyPair = generateKeyPair("DSA");

        PublicKey derivedDsa = PublicKeyDeriver.derivePublicKey(dsaKeyPair.getPrivate());

        assertThat(derivedDsa.getAlgorithm()).isEqualTo("DSA");
        assertThat(derivedDsa.getEncoded()).isNotEmpty();
    }

    private static KeyPair generateKeyPair(String algorithm) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
        generator.initialize(1024);
        return generator.generateKeyPair();
    }

    private static KeyStore createInMemoryKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JCEKS");
        keyStore.load(null, null);
        return keyStore;
    }

    private static void addSecretKeyEntry(KeyStore keyStore, String alias, SecretKey secretKey, char[] password)
            throws Exception {
        keyStore.setEntry(alias, new KeyStore.SecretKeyEntry(secretKey), new KeyStore.PasswordProtection(password));
    }

    private static final class TestCertificate extends Certificate {
        private final byte[] encoded;

        private TestCertificate(byte[] encoded) {
            super("TEST");
            this.encoded = encoded.clone();
        }

        @Override
        public byte[] getEncoded() {
            return encoded.clone();
        }

        @Override
        public void verify(PublicKey key) {
        }

        @Override
        public void verify(PublicKey key, String sigProvider) {
        }

        @Override
        public String toString() {
            return "TestCertificate";
        }

        @Override
        public PublicKey getPublicKey() {
            return null;
        }
    }

    private static final class TestOption implements KOption {
        private final KOptionInfo optionInfo;

        private TestOption(String name, KOptionType type) {
            this.optionInfo = new KOptionInfo(name, "Test option " + name, type);
        }

        @Override
        public KOptionInfo getOptionInfo() {
            return optionInfo;
        }
    }
}
