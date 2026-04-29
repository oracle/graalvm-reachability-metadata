/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_common.wildfly_common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.wildfly.common.archive.Archive;
import org.wildfly.common.array.ArrayIterator;
import org.wildfly.common.array.Arrays2;
import org.wildfly.common.bytes.ByteStringBuilder;
import org.wildfly.common.codec.Base32Alphabet;
import org.wildfly.common.codec.Base64Alphabet;
import org.wildfly.common.context.ContextManager;
import org.wildfly.common.context.Contextual;
import org.wildfly.common.expression.Expression;
import org.wildfly.common.flags.Flags;
import org.wildfly.common.function.Functions;
import org.wildfly.common.iteration.ByteIterator;
import org.wildfly.common.iteration.CodePointIterator;
import org.wildfly.common.lock.ExtendedLock;
import org.wildfly.common.lock.Locks;
import org.wildfly.common.math.HashMath;
import org.wildfly.common.net.CidrAddress;
import org.wildfly.common.net.CidrAddressTable;
import org.wildfly.common.net.Inet;
import org.wildfly.common.net.URIs;
import org.wildfly.common.ref.Reference;
import org.wildfly.common.ref.References;
import org.wildfly.common.string.CompositeCharSequence;

public class Wildfly_commonTest {
    @Test
    void byteStringBuilderBuildsUtf8BinaryNumbersAndDigests() throws Exception {
        ByteStringBuilder builder = new ByteStringBuilder();
        builder.append(true)
                .append((byte) ':')
                .append("Snowman ")
                .appendUtf8Raw(0x2603)
                .append((byte) '|')
                .appendLatin1("caf\u00e9")
                .append((byte) '|')
                .appendNumber(-42)
                .appendObject(null);

        String text = new String(builder.toArray(), StandardCharsets.UTF_8);
        assertThat(text).isEqualTo("true:Snowman ☃|caf�|-42null");
        assertThat(builder.contentEquals(builder.toArray())).isTrue();
        assertThat(builder.contentEquals("xx".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(builder.byteAt(0)).isEqualTo((byte) 't');

        ByteStringBuilder binary = new ByteStringBuilder();
        binary.appendBE((short) 0x1234)
                .appendBE(0x56789abc)
                .appendBE(0x0123456789abcdefL)
                .appendPackedUnsignedBE(0x1fff)
                .append(ByteIterator.ofBytes((byte) 0xaa, (byte) 0xbb));
        ByteIterator iterator = binary.iterate();
        assertThat(iterator.getBE16()).isEqualTo(0x1234);
        assertThat(iterator.getBE32()).isEqualTo(0x56789abc);
        assertThat(iterator.getBE64()).isEqualTo(0x0123456789abcdefL);
        assertThat(iterator.getPackedBE32()).isEqualTo(0x1fff);
        assertThat(iterator.drain()).containsExactly((byte) 0xaa, (byte) 0xbb);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        builder.updateDigest(digest);
        String digestHex = ByteIterator.ofBytes(digest.digest()).hexEncode().drainToString();
        assertThat(digestHex).hasSize(64);

        builder.setLength(builder.length() + 2);
        assertThat(builder.toArray()).endsWith((byte) 0, (byte) 0);
        assertThatThrownBy(() -> new ByteStringBuilder().appendAscii(CodePointIterator.ofString("é")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void byteAndCodePointIteratorsRoundTripTextAndEncodings() throws Exception {
        byte[] payload = "WildFly Common".getBytes(StandardCharsets.UTF_8);

        assertThat(ByteIterator.ofBytes(payload).base64Encode().drainToString()).isEqualTo("V2lsZEZseSBDb21tb24=");
        assertThat(ByteIterator.ofBytes(payload).base64Encode(Base64Alphabet.STANDARD, false).drainToString())
                .isEqualTo("V2lsZEZseSBDb21tb24");
        assertThat(ByteIterator.ofBytes(payload).base32Encode(Base32Alphabet.STANDARD).drainToString())
                .isEqualTo("K5UWYZCGNR4SAQ3PNVWW63Q=");
        assertThat(ByteIterator.ofBytes(payload).base32Encode(Base32Alphabet.LOWERCASE, false).drainToString())
                .isEqualTo("k5uwyzcgnr4saq3pnvww63q");
        assertThat(ByteIterator.ofBytes(payload).hexEncode().drainToString())
                .isEqualTo("57696c64466c7920436f6d6d6f6e");

        assertThat(CodePointIterator.ofString("V2lsZEZseSBDb21tb24=").base64Decode().drain()).isEqualTo(payload);
        assertThat(CodePointIterator.ofString("K5UWYZCGNR4SAQ3PNVWW63Q=").base32Decode().drain()).isEqualTo(payload);
        assertThat(CodePointIterator.ofString("57696c64466c7920436f6d6d6f6e").hexDecode().drain()).isEqualTo(payload);
        assertThat(CodePointIterator.ofUtf8Bytes(payload).drainToString()).isEqualTo("WildFly Common");
        assertThat(CodePointIterator.ofLatin1Bytes(new byte[] {(byte) 0x41, (byte) 0xe9}).drainToString())
                .isEqualTo("Aé");

        ByteIterator positioned = ByteIterator.ofBytes(new byte[] {1, 2, 3, 4, 5}, 1, 3);
        assertThat(positioned.peekNext()).isEqualTo(2);
        assertThat(positioned.next()).isEqualTo(2);
        assertThat(positioned.peekPrevious()).isEqualTo(2);
        assertThat(positioned.previous()).isEqualTo(2);
        assertThat(positioned.getIndex()).isZero();
        assertThat(positioned.limitedTo(2).drain()).containsExactly((byte) 2, (byte) 3);

        assertThat(ByteIterator.ofByteBuffer(ByteBuffer.wrap(new byte[] {9, 8, 7})).drain())
                .containsExactly((byte) 9, (byte) 8, (byte) 7);
        assertThat(ByteIterator.ofIterators(ByteIterator.ofBytes((byte) 1), ByteIterator.ofBytes((byte) 2)).drain())
                .containsExactly((byte) 1, (byte) 2);
        assertThat(ByteIterator.ofBytes((byte) 'a', (byte) ',', (byte) 'b').delimitedBy(',').drainToLatin1(10))
                .isEqualTo("a");
        byte[] translation = new byte[256];
        translation[1] = 9;
        translation[2] = 8;
        translation[3] = 7;
        assertThat(ByteIterator.ofBytes((byte) 1, (byte) 2, (byte) 3).interleavedWith(translation).drain())
                .containsExactly((byte) 9, (byte) 8, (byte) 7);

        InputStream inputStream = ByteIterator.ofBytes((byte) 11, (byte) 12).asInputStream();
        assertThat(inputStream.read()).isEqualTo(11);
        assertThat(inputStream.read()).isEqualTo(12);
        assertThat(inputStream.read()).isEqualTo(-1);
    }

    @Test
    void expressionsResolveRecursiveDefaultsEscapesAndProperties() {
        Expression expression = Expression.compile(
                "\\t${greeting}, ${missing:${fallback}} $${literal}",
                Expression.Flag.ESCAPES);

        assertThat(expression.getReferencedStrings()).containsExactlyInAnyOrder("greeting", "missing", "fallback");
        assertThat(expression.evaluate((context, target) -> {
            String key = context.getKey();
            if ("greeting".equals(key)) {
                target.append("Hello");
            } else if ("fallback".equals(key)) {
                target.append("guest");
            } else if (context.hasDefault()) {
                target.append('[').append(context.getExpandedDefault()).append(']');
            }
        })).isEqualTo("\tHello, [guest] ${literal}");

        Expression mini = Expression.compile("$a+$b=$c", Expression.Flag.MINI_EXPRS);
        assertThat(mini.evaluate((context, target) -> target.append(context.getKey().toUpperCase())))
                .isEqualTo("A+B=C");

        Expression general = Expression.compile("grant ${{permission.name}}", Expression.Flag.GENERAL_EXPANSION);
        assertThat(general.evaluate((context, target) -> target.append(context.getKey().replace('.', '-'))))
                .isEqualTo("grant permission-name");

        String propertyName = "wildfly.common.test.property";
        String previous = System.getProperty(propertyName);
        try {
            System.setProperty(propertyName, "configured");
            assertThat(Expression.compile("value=${" + propertyName + ":fallback}").evaluateWithProperties(true))
                    .isEqualTo("value=configured");
            System.clearProperty(propertyName);
            assertThat(Expression.compile("value=${" + propertyName + ":fallback}").evaluateWithProperties(true))
                    .isEqualTo("value=fallback");
            assertThat(Expression.compile("value=${" + propertyName + "}").evaluateWithProperties(false))
                    .isEqualTo("value=");
        } finally {
            if (previous == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previous);
            }
        }

        assertThat(Expression.compile("${unterminated", Expression.Flag.LENIENT_SYNTAX)
                .evaluate((context, target) -> target.append(context.getKey())))
                .isEqualTo("unterminated");
    }

    @Test
    void inetCidrAndAddressTablesSelectMostSpecificMappings() throws Exception {
        Inet4Address host = Inet.parseInet4AddressOrFail("192.168.1.42");
        assertThat(Inet.isInet4Address("192.168.1.42")).isTrue();
        assertThat(Inet.isInet4Address("999.168.1.42")).isFalse();
        assertThat(Inet.toOptimalString(host)).isEqualTo("192.168.1.42");
        assertThat(Inet.toURLString(Inet.parseInet6AddressOrFail("2001:db8::1"), false))
                .isEqualTo("[2001:db8::1]");
        assertThat(Inet.toInet6Address(host).getAddress())
                .endsWith((byte) 192, (byte) 168, (byte) 1, (byte) 42);
        assertThat(URIs.getUserFromURI(new java.net.URI("http://alice:secret@example.test/path")))
                .isEqualTo("alice");

        CidrAddress office = CidrAddress.create(host, 24);
        CidrAddress officeHost = CidrAddress.create(new byte[] {(byte) 192, (byte) 168, 1, 42}, 32);
        CidrAddress privateNet = Inet.parseCidrAddress("192.168.0.0/16");
        assertThat(office.toString()).isEqualTo("192.168.1.0/24");
        assertThat(office.getNetworkAddress().getHostAddress()).isEqualTo("192.168.1.0");
        assertThat(office.getBroadcastAddress().getHostAddress()).isEqualTo("192.168.1.255");
        assertThat(office.matches(host)).isTrue();
        assertThat(office.matches(Inet.parseInet4AddressOrFail("192.168.2.1"))).isFalse();
        assertThat(privateNet.matches(office)).isTrue();
        assertThat(office.compareTo(officeHost)).isLessThan(0);
        assertThat(Inet.parseCidrAddress("invalid/24")).isNull();

        CidrAddressTable<String> table = new CidrAddressTable<>();
        assertThat(table.isEmpty()).isTrue();
        assertThat(table.put(privateNet, "private")).isNull();
        assertThat(table.put(office, "office")).isNull();
        assertThat(table.put(officeHost, "host")).isNull();
        assertThat(table.get(host)).isEqualTo("host");
        assertThat(table.get(Inet.parseInet4AddressOrFail("192.168.1.99"))).isEqualTo("office");
        assertThat(table.get(Inet.parseInet4AddressOrFail("192.168.2.99"))).isEqualTo("private");
        assertThat(table.getOrDefault(Inet.parseInet4AddressOrFail("10.0.0.1"), "default")).isEqualTo("default");
        assertThat(table.putIfAbsent(office, "ignored")).isEqualTo("office");
        assertThat(table.replaceExact(office, "office-v2")).isEqualTo("office");
        assertThat(table.replaceExact(office, "office-v2", "office-v3")).isTrue();
        assertThat(table.removeExact(officeHost, "host")).isTrue();
        assertThat(table.get(host)).isEqualTo("office-v3");

        CidrAddressTable<String> cloned = table.clone();
        table.clear();
        assertThat(table).isEmpty();
        assertThat(cloned).hasSize(2);
        List<String> values = new ArrayList<>();
        for (CidrAddressTable.Mapping<String> mapping : cloned) {
            values.add(mapping.getValue());
            if (mapping.getRange().equals(office)) {
                assertThat(mapping.getParent().getValue()).isEqualTo("private");
            }
        }
        assertThat(values).containsExactly("private", "office-v3");
    }

    @Test
    void archivesReadEntriesAndNestedArchives() throws Exception {
        Path archivePath = Files.createTempFile("wildfly-common-archive", ".zip");
        byte[] settings = "mode=dev\n".getBytes(StandardCharsets.UTF_8);
        byte[] nestedArchive = createZip("nested/readme.txt", "nested archive".getBytes(StandardCharsets.UTF_8));
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archivePath))) {
                addZipEntry(zip, "config/", new byte[0]);
                addZipEntry(zip, "config/settings.txt", settings);
                addZipEntry(zip, "nested.zip", nestedArchive);
            }

            try (Archive archive = Archive.open(archivePath)) {
                long directoryHandle = archive.getEntryHandle("config/");
                assertThat(directoryHandle).isNotEqualTo(-1L);
                assertThat(archive.getEntryName(directoryHandle)).isEqualTo("config/");

                long settingsHandle = archive.getEntryHandle("config/settings.txt");
                assertThat(settingsHandle).isNotEqualTo(-1L);
                assertThat(archive.entryNameEquals(settingsHandle, "config/settings.txt")).isTrue();
                assertThat(archive.getEntryName(settingsHandle)).isEqualTo("config/settings.txt");
                assertThat(archive.isDirectory(settingsHandle)).isFalse();
                assertThat(archive.getUncompressedSize(settingsHandle)).isEqualTo((long) settings.length);
                assertThat(readBuffer(archive.getEntryContents(settingsHandle))).isEqualTo(settings);

                List<String> entryNames = new ArrayList<>();
                for (long handle = archive.getFirstEntryHandle(); handle != -1L; handle = archive.getNextEntryHandle(handle)) {
                    entryNames.add(archive.getEntryName(handle));
                }
                assertThat(entryNames).containsExactly("config/", "config/settings.txt", "nested.zip");

                try (Archive nested = archive.getNestedArchive(archive.getEntryHandle("nested.zip"))) {
                    long nestedHandle = nested.getEntryHandle("nested/readme.txt");
                    assertThat(nestedHandle).isNotEqualTo(-1L);
                    assertThat(readBuffer(nested.getEntryContents(nestedHandle)))
                            .isEqualTo("nested archive".getBytes(StandardCharsets.UTF_8));
                }
            }
        } finally {
            Files.deleteIfExists(archivePath);
        }
    }

    @Test
    void arraysFlagsAndHashHelpersBehaveAsValueUtilities() {
        byte[] bytes = {0, 1, 2, 3, 2};
        assertThat(Arrays2.equals(bytes, 1, new byte[] {1, 2, 3}, 0, 3)).isTrue();
        assertThat(Arrays2.equals(bytes, -1, new byte[] {0}, 0, 1)).isFalse();
        assertThat(Arrays2.indexOf(bytes, 2)).isEqualTo(2);
        assertThat(Arrays2.indexOf(bytes, 2, 3)).isEqualTo(4);
        assertThat(Arrays2.toString(new byte[] {0x0f, (byte) 0xa0})).isEqualTo("0fa0");
        assertThat(Arrays2.equals("wildfly", 4, new char[] {'f', 'l', 'y'})).isTrue();
        assertThat(Arrays2.compactNulls(new String[] {"a", null, "b", null})).containsExactly("a", "b");
        assertThat(Arrays2.createArray(String.class, 2)).hasSize(2);
        assertThat(Arrays2.objectToString(new int[] {1, 2, 3})).isEqualTo("[1, 2, 3]");

        ArrayIterator<String> iterator = new ArrayIterator<>(new String[] {"a", "b", "c"}, 1);
        assertThat(iterator.previous()).isEqualTo("a");
        assertThat(iterator.next()).isEqualTo("a");
        assertThat(iterator.next()).isEqualTo("b");
        assertThat(iterator.nextIndex()).isEqualTo(2);
        assertThat(iterator.previousIndex()).isEqualTo(1);

        ArrayIterator<String> descending = new ArrayIterator<>(new String[] {"a", "b", "c"}, true);
        assertThat(descending.next()).isEqualTo("c");
        assertThat(descending.next()).isEqualTo("b");
        assertThat(descending.previous()).isEqualTo("b");

        TestFlags flags = TestFlags.NONE.with(TestFlag.READ, TestFlag.EXECUTE);
        assertThat(flags).containsExactly(TestFlag.READ, TestFlag.EXECUTE);
        assertThat(flags.first()).isEqualTo(TestFlag.READ);
        assertThat(flags.last()).isEqualTo(TestFlag.EXECUTE);
        assertThat(flags.containsAll(TestFlag.READ, TestFlag.EXECUTE)).isTrue();
        assertThat(flags.containsAny(TestFlag.WRITE, TestFlag.EXECUTE)).isTrue();
        assertThat(flags.without(TestFlag.READ)).containsExactly(TestFlag.EXECUTE);
        assertThat(flags.headSet(TestFlag.EXECUTE)).containsExactly(TestFlag.READ);
        assertThat(flags.tailSet(TestFlag.WRITE)).containsExactly(TestFlag.EXECUTE);
        assertThat(flags.complement()).containsExactly(TestFlag.WRITE);
        List<TestFlag> descendingFlags = new ArrayList<>();
        flags.descendingIterator().forEachRemaining(descendingFlags::add);
        assertThat(descendingFlags).containsExactly(TestFlag.EXECUTE, TestFlag.READ);

        assertThat(HashMath.roundToPowerOfTwo(17)).isEqualTo(32);
        assertThat(HashMath.multiplyWrap(0xffff, 0xffff)).isEqualTo((int) ((0xffffL * 0xffffL) ^ ((0xffffL * 0xffffL) >>> 32)));
        assertThat(HashMath.multiHashOrdered(1, 31, 2)).isEqualTo(33);
        assertThat(HashMath.multiHashUnordered(1, 31, 2)).isEqualTo(63);
    }

    @Test
    void compositeCharSequencesBehaveAsSingleCharSequence() {
        CompositeCharSequence sequence = new CompositeCharSequence("Wild", "Fly", " ", new StringBuilder("Common"));

        assertThat(sequence.length()).isEqualTo("WildFly Common".length());
        assertThat(sequence.charAt(0)).isEqualTo('W');
        assertThat(sequence.charAt(4)).isEqualTo('F');
        assertThat(sequence.charAt(8)).isEqualTo('C');
        assertThat(sequence.toString()).isEqualTo("WildFly Common");
        assertThat(sequence.subSequence(0, 4).toString()).isEqualTo("Wild");
        assertThat(sequence.subSequence(4, 8).toString()).isEqualTo("Fly ");
        assertThat(sequence.subSequence(6, 11).toString()).isEqualTo("y Com");
        assertThat(sequence.subSequence(3, 3)).isEqualTo("");
        assertThat(sequence.equals(new StringBuilder("WildFly Common"))).isTrue();
        assertThat(sequence.hashCode()).isEqualTo("WildFly Common".hashCode());

        CompositeCharSequence fromList = new CompositeCharSequence(Arrays.asList("WildFly", " Common"));
        assertThat(fromList).isEqualTo(sequence);
        assertThatThrownBy(() -> sequence.charAt(sequence.length())).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> sequence.subSequence(-1, 2)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void contextFunctionsReferencesAndLocksCoordinateState() throws Exception {
        ContextValue global = new ContextValue("global");
        ContextValue thread = new ContextValue("thread");
        ContextValue loader = new ContextValue("loader");
        ContextValue active = new ContextValue("active");
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            ContextValue.MANAGER.setGlobalDefault(global);
            assertThat(ContextValue.MANAGER.get()).isSameAs(global);
            assertThat(ContextValue.MANAGER.setGlobalDefaultSupplierIfNotSet(() -> () -> thread)).isFalse();

            ContextValue.MANAGER.setThreadDefault(thread);
            assertThat(ContextValue.MANAGER.getThreadDefault()).isSameAs(thread);
            assertThat(ContextValue.MANAGER.get()).isSameAs(thread);

            ContextValue.MANAGER.setClassLoaderDefault(classLoader, loader);
            assertThat(ContextValue.MANAGER.getClassLoaderDefault(classLoader)).isSameAs(loader);
            assertThat(ContextValue.MANAGER.get()).isSameAs(loader);
            assertThat(ContextValue.MANAGER.getPrivilegedSupplier().get()).isSameAs(loader);

            AtomicReference<ContextValue> observed = new AtomicReference<>();
            String result = active.runFunction(value -> {
                observed.set(ContextValue.MANAGER.get());
                return value + ":" + ContextValue.MANAGER.get().name;
            }, "payload");
            assertThat(observed.get()).isSameAs(active);
            assertThat(result).isEqualTo("payload:active");
            assertThat(ContextValue.MANAGER.get()).isSameAs(loader);

            AtomicInteger counter = new AtomicInteger();
            Functions.capturingRunnable(AtomicInteger::addAndGet, counter, 3).run();
            Functions.runnableConsumer().accept(counter::incrementAndGet);
            assertThat(counter.get()).isEqualTo(4);
            assertThat(Functions.constantSupplier("constant").get()).isEqualTo("constant");
            assertThat(Functions.<String, Integer>functionBiFunction().apply(String::length, "wildfly")).isEqualTo(7);
            assertThat(Functions.<String, IOException>exceptionSupplierFunction().apply(() -> "checked"))
                    .isEqualTo("checked");
            assertThatThrownBy(() -> Functions.<String, IOException>exceptionSupplierFunction().apply(() -> {
                throw new IOException("boom");
            })).isInstanceOf(IOException.class).hasMessage("boom");
            Functions.discardingConsumer().accept("ignored");
            Functions.discardingBiConsumer().accept("ignored", 1);

            Reference<String, String> strong = References.create(Reference.Type.STRONG, "referent", "attachment");
            assertThat(strong.get()).isEqualTo("referent");
            assertThat(strong.getAttachment()).isEqualTo("attachment");
            assertThat(strong.getType()).isEqualTo(Reference.Type.STRONG);
            strong.clear();
            assertThat(strong.get()).isNull();
            Reference<String, String> nullReference = References.create(Reference.Type.NULL, "value", "attachment");
            assertThat(nullReference).isSameAs(References.getNullReference());
            assertThat(Reference.Type.isFull(EnumSet.allOf(Reference.Type.class))).isTrue();
            assertThat(Reference.Type.STRONG.in(Reference.Type.WEAK, Reference.Type.STRONG)).isTrue();

            ExtendedLock lock = Locks.reentrantLock(true);
            assertThat(lock.isFair()).isTrue();
            assertThat(lock.isLocked()).isFalse();
            lock.lock();
            try {
                assertThat(lock.isLocked()).isTrue();
                assertThat(lock.isHeldByCurrentThread()).isTrue();
            } finally {
                lock.unlock();
            }
            assertThat(lock.isLocked()).isFalse();

            ExtendedLock spinLock = Locks.spinLock();
            assertThat(spinLock.tryLock()).isTrue();
            try {
                assertThat(spinLock.isLocked()).isTrue();
                assertThat(spinLock.isHeldByCurrentThread()).isTrue();
            } finally {
                spinLock.unlock();
            }
        } finally {
            ContextValue.MANAGER.setClassLoaderDefault(classLoader, null);
            ContextValue.MANAGER.setThreadDefault(null);
            ContextValue.MANAGER.setGlobalDefault(null);
        }
    }

    private static byte[] createZip(String entryName, byte[] contents) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            addZipEntry(zip, entryName, contents);
        }
        return bytes.toByteArray();
    }

    private static void addZipEntry(ZipOutputStream zip, String name, byte[] contents) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(contents);
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(contents.length);
        entry.setCompressedSize(contents.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(contents);
        zip.closeEntry();
    }

    private static byte[] readBuffer(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private enum TestFlag {
        READ,
        WRITE,
        EXECUTE
    }

    private static final class TestFlags extends Flags<TestFlag, TestFlags> {
        private static final int MASK = (1 << TestFlag.values().length) - 1;
        private static final TestFlags[] VALUES = new TestFlags[1 << TestFlag.values().length];
        private static final TestFlags NONE;

        static {
            for (int i = 0; i < VALUES.length; i++) {
                VALUES[i] = new TestFlags(i);
            }
            NONE = VALUES[0];
        }

        private TestFlags(int bits) {
            super(bits);
        }

        @Override
        protected TestFlags value(int bits) {
            return VALUES[bits & MASK];
        }

        @Override
        protected TestFlags this_() {
            return this;
        }

        @Override
        protected TestFlag itemOf(int index) {
            return TestFlag.values()[index];
        }

        @Override
        protected TestFlag castItemOrNull(Object obj) {
            return obj instanceof TestFlag ? (TestFlag) obj : null;
        }

        @Override
        protected TestFlags castThis(Object obj) {
            return (TestFlags) obj;
        }
    }

    private static final class ContextValue implements Contextual<ContextValue> {
        private static final ContextManager<ContextValue> MANAGER = new ContextManager<>(ContextValue.class);

        private final String name;

        private ContextValue(String name) {
            this.name = name;
        }

        @Override
        public ContextManager<ContextValue> getInstanceContextManager() {
            return MANAGER;
        }
    }
}
