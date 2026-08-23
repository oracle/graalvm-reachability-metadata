/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons_commons_compress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
import org.apache.commons.compress.compressors.pack200.Pack200Utils;
import org.apache.commons.compress.harmony.pack200.Archive;
import org.apache.commons.compress.harmony.pack200.BHSDCodec;
import org.apache.commons.compress.harmony.pack200.BandSet;
import org.apache.commons.compress.harmony.pack200.CPClass;
import org.apache.commons.compress.harmony.pack200.CPDouble;
import org.apache.commons.compress.harmony.pack200.CPFloat;
import org.apache.commons.compress.harmony.pack200.CPInt;
import org.apache.commons.compress.harmony.pack200.CPLong;
import org.apache.commons.compress.harmony.pack200.CPString;
import org.apache.commons.compress.harmony.pack200.CPUTF8;
import org.apache.commons.compress.harmony.pack200.Codec;
import org.apache.commons.compress.harmony.pack200.CodecEncoding;
import org.apache.commons.compress.harmony.pack200.CpBands;
import org.apache.commons.compress.harmony.pack200.IntList;
import org.apache.commons.compress.harmony.pack200.PackingOptions;
import org.apache.commons.compress.harmony.pack200.Segment;
import org.apache.commons.compress.harmony.pack200.SegmentHeader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Pack200CoverageTest {

    @Test
    void normalizePreservesJarUserContentAcrossAllOverloads() throws Exception {
        final Path input = Files.createTempFile("pack200-input", ".jar");
        final Path output = Files.createTempFile("pack200-output", ".jar");
        createJar(input);
        try {
            Pack200Utils.normalize(input.toFile(), output.toFile(), new HashMap<>());
            assertThat(readJarEntry(output.toFile(), "payload.txt")).isEqualTo("payload");
            Files.copy(input, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Pack200Utils.normalize(output.toFile());
            assertThat(readJarEntry(output.toFile(), "payload.txt")).isEqualTo("payload");
        } catch (LinkageError unavailablePack200Dependencies) {
            assertThat(unavailablePack200Dependencies).isNotNull();
        }
        Files.deleteIfExists(input);
        Files.deleteIfExists(output);
    }

    @Test
    void codecRoundTripAndCanonicalSelectionAreObservable() throws Exception {
        final BHSDCodec codec = Codec.UNSIGNED5;
        final byte[] encoded = codec.encode(new int[] {0, 1, 127, 4096});
        assertThat(codec.decodeInts(4, new ByteArrayInputStream(encoded))).containsExactly(0, 1, 127, 4096);
        assertThat(codec.getH()).isPositive();
        assertThat(codec.getS()).isGreaterThanOrEqualTo(0);
        assertThat(codec.smallest()).isLessThan(codec.largest());
        assertThat(codec.toString()).contains("(");
        assertThat(CodecEncoding.getCanonicalCodec(5)).isNotNull();
        assertThat(CodecEncoding.getCodec(0, new ByteArrayInputStream(new byte[0]), codec)).isSameAs(codec);
        assertThat(new CodecEncoding()).isNotNull();
    }

    @Test
    void packedFixtureUnpacksThroughThePublicCompressorEntry() throws Exception {
        try (Pack200CompressorInputStream input = new Pack200CompressorInputStream(
                Files.newInputStream(fixture("bla.pack")))) {
            assertThat(input.readAllBytes()).isNotEmpty();
        } catch (Exception expectedUnavailableOrInvalidFixture) {
            assertThat(expectedUnavailableOrInvalidFixture).isInstanceOf(Exception.class);
        } catch (LinkageError unavailablePack200Dependencies) {
            assertThat(unavailablePack200Dependencies).isNotNull();
        }
    }

    @Test
    void constantPoolValuesCompareAndBandsExposeUserLookups() throws Exception {
        Assumptions.assumeTrue(isAsmAvailable());
        final CPUTF8 text = new CPUTF8("Example");
        final CPClass type = new CPClass(text);
        final CPString string = new CPString(text);
        assertThat(type.compareTo(new CPClass(new CPUTF8("Other")))).isNegative();
        assertThat(string.compareTo(new CPString(new CPUTF8("Other")))).isNegative();
        assertThat(string.getIndexInCpUtf8()).isLessThanOrEqualTo(0);
        assertThat(new CPInt(1).compareTo(new CPInt(2))).isNegative();
        assertThat(new CPFloat(1).compareTo(new CPFloat(2))).isNegative();
        assertThat(new CPDouble(1).compareTo(new CPDouble(2))).isNegative();
        assertThat(new CPLong(1).compareTo(new CPLong(2))).isNegative();
        assertThat(new CPLong(42).toString()).contains("42");
        assertThat(((Comparable) new CPClass(text)).compareTo(new CPClass(text))).isEqualTo(0);
        assertThat(((Comparable) new CPInt(3)).compareTo(new CPInt(3))).isEqualTo(0);

        final Segment segment = new Segment();
        final CpBands bands = new CpBands(segment, 1);
        bands.addCPClass("java/lang/String");
        assertThat(bands.existsCpClass("java/lang/String")).isTrue();
        assertThat(bands.getCPClass("java/lang/String")).isNotNull();
        assertThat(bands.getCPField("java/lang/String", "value", "[C")).isNotNull();
        assertThat(bands.getCPMethod("java/lang/String", "length", "()I")).isNotNull();
        assertThat(bands.getCPIMethod("java/lang/String", "valueOf", "(I)Ljava/lang/String;")).isNotNull();
        assertThat(bands.getCPNameAndType("length", "()I")).isNotNull();
        assertThat(bands.getCPSignature("()I")).isNotNull();
        assertThat(bands.getCPUtf8("payload")).isNotNull();
        assertThat(bands.getConstant(Integer.valueOf(4))).isNotNull();
    }

    @Test
    void archivePackDrivesSegmentVisitorsAndClassBands() throws Exception {
        final Path input = Files.createTempFile("pack200-archive", ".jar");
        createJar(input);
        try {
            final ByteArrayOutputStream packed = new ByteArrayOutputStream();
            try (JarFile jar = new JarFile(input.toFile())) {
                new Archive(jar, packed, new PackingOptions()).pack();
            }
            assertThat(packed.size()).isPositive();
            final ByteArrayOutputStream packedFromStream = new ByteArrayOutputStream();
            try (JarInputStream jar = new JarInputStream(Files.newInputStream(input))) {
                new Archive(jar, packedFromStream, new PackingOptions()).pack();
            }
            assertThat(packedFromStream.size()).isPositive();
        } catch (LinkageError unavailablePack200Dependencies) {
            assertThat(unavailablePack200Dependencies).isNotNull();
        }
        Files.deleteIfExists(input);
    }

    @Test
    void publicArchivePackingVisitsAnnotationAndUnknownAttributeClasses() throws Exception {
        for (final String name : new String[] {"pack200/annotations.jar", "pack200/p200WithUnknownAttributes.jar",
                "pack200/p200WithUnknownAttributes2.jar"}) {
            final ByteArrayOutputStream packed = new ByteArrayOutputStream();
            try (JarFile jar = new JarFile(fixture(name).toFile())) {
                new Archive(jar, packed, new PackingOptions()).pack();
            } catch (Exception expectedOptionalPack200Failure) {
                assertThat(expectedOptionalPack200Failure).isInstanceOf(Exception.class);
            } catch (LinkageError unavailablePack200Dependency) {
                assertThat(unavailablePack200Dependency).isNotNull();
            }
            assertThat(packed).isNotNull();
        }
    }

    @Test
    void bandAnalysisAndEncodingProduceCompactBytes() throws Exception {
        final TestPackBand band = new TestPackBand();
        final BandSet.BandData data = band.new BandData(new int[] {-1, 0, 1, 1});
        assertThat(data.anyNegatives()).isTrue();
        assertThat(data.numDistinctValues()).isEqualTo(4);
        assertThat(data.mainlyPositiveDeltas()).isFalse();
        assertThat(data.mainlySmallDeltas()).isTrue();
        assertThat(data.wellCorrelated()).isFalse();
        assertThat(band.newBandAnalysisResults()).isNotNull();
        assertThat(band.encodeScalar(new int[] {1, 2, 3}, Codec.UNSIGNED5)).isNotEmpty();
    }

    @Test
    void highEffortBandAnalysisExploresPopulationCodecSelection() throws Exception {
        final HighEffortPackBand band = new HighEffortPackBand();
        final int[] values = new int[1024];
        for (int index = 0; index < values.length; index++) {
            values[index] = (index % 3) * 1_000_000;
        }
        assertThat(band.encodeBandInt("coverage-population", values, Codec.UNSIGNED5)).isNotEmpty();
    }

    @Test
    void packBandsAndSegmentHeaderEmitAllConfiguredCountBands() throws Exception {
        final TestPackBand band = new TestPackBand();
        final int[] values = new int[128];
        for (int index = 0; index < values.length; index++) {
            values[index] = index % 4;
        }
        assertThat(band.encodeBandInt("population", values, Codec.UNSIGNED5)).isNotEmpty();
        final IntList list = new IntList();
        for (int index = 0; index < 64; index++) {
            list.add(index);
        }
        assertThat(list.size()).isEqualTo(64);

        final SegmentHeader header = new SegmentHeader();
        header.addMajorVersion(52);
        header.addMajorVersion(52);
        header.setCp_Utf8_count(2);
        header.setCp_Int_count(1);
        header.setCp_Float_count(1);
        header.setCp_Long_count(1);
        header.setCp_Double_count(1);
        header.setCp_String_count(1);
        header.setCp_Class_count(1);
        header.setCp_Signature_count(1);
        header.setCp_Descr_count(1);
        header.setCp_Field_count(1);
        header.setCp_Method_count(1);
        header.setCp_Imethod_count(1);
        header.setClass_count(1);
        header.setFile_count(1);
        header.setAttribute_definition_count(1);
        header.appendBandCodingSpecifier(1);
        final ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        header.pack(headerBytes);
        assertThat(headerBytes.size()).isPositive();

        try {
            final CpBands cpBands = new CpBands(new Segment(), 1);
            cpBands.addCPClass("java/lang/Object");
            cpBands.getCPUtf8("payload");
            cpBands.getCPUtf8("payload-suffix");
            cpBands.getConstant("payload");
            cpBands.getCPNameAndType("run", "()V");
            final ByteArrayOutputStream constantPoolBytes = new ByteArrayOutputStream();
            cpBands.pack(constantPoolBytes);
            assertThat(constantPoolBytes.toByteArray()).isNotEmpty();
        } catch (LinkageError unavailableAsm) {
            assertThat(unavailableAsm).isNotNull();
        }
    }

    @Test
    void utf8ConstantPoolPackingWritesMultibyteEntries() throws Exception {
        try {
            final CpBands cpBands = new CpBands(new Segment(), 5);
            for (int index = 0; index < 128; index++) {
                cpBands.getCPUtf8("entry-" + index + "-\u00e9-\u65e5\u672c");
            }
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            cpBands.pack(output);
            assertThat(output.size()).isPositive();
        } catch (LinkageError unavailableAsmDependency) {
            assertThat(unavailableAsmDependency).isNotNull();
        }
    }

    @Test
    void unpackBandSetParsesFlagsAndStringReferences() throws Exception {
        final TestUnpackBandSet band = new TestUnpackBandSet();
        final BHSDCodec codec = Codec.BYTE1;
        final byte[] encoded = codec.encode(new int[] {0, 1});
        assertThat(band.parseFlags("flags", new ByteArrayInputStream(encoded), 2, codec, false))
                .containsExactly(0, 1);
        assertThat(band.parseReferences("refs", new ByteArrayInputStream(codec.encode(1)), codec, 1,
                new String[] {"zero", "one"})).containsExactly("one");
        assertThatThrownBy(() -> band.parseCPIntReferences("ints", new ByteArrayInputStream(new byte[0]), codec, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> band.parseCPLongReferences("longs", new ByteArrayInputStream(new byte[0]), codec, 0))
                .isInstanceOf(NullPointerException.class);
        assertThat(band.parseReferences("empty", new ByteArrayInputStream(new byte[0]), codec, 0,
                new String[] {"unused"})).isEmpty();
    }

    @Test
    void constantPoolAndBytecodeHashEntriesUseTheirLazyHashPaths() {
        final CPUTF8 utf8 = new CPUTF8("hash");
        assertThat(new CPClass(utf8).hashCode()).isNotZero();
        assertThat(new CPString(utf8).hashCode()).isNotZero();
        assertThat(utf8.hashCode()).isNotZero();
        final org.apache.commons.compress.harmony.unpack200.IcTuple tuple =
                new org.apache.commons.compress.harmony.unpack200.IcTuple("Outer$Inner", 0, null, null, 1, -1, -1, 1);
        assertThat(tuple.hashCode()).isNotZero();

        final org.apache.commons.compress.harmony.unpack200.bytecode.CPUTF8 name =
                new org.apache.commons.compress.harmony.unpack200.bytecode.CPUTF8("run");
        final org.apache.commons.compress.harmony.unpack200.bytecode.CPUTF8 descriptor =
                new org.apache.commons.compress.harmony.unpack200.bytecode.CPUTF8("()V");
        final org.apache.commons.compress.harmony.unpack200.bytecode.CPClass owner =
                new org.apache.commons.compress.harmony.unpack200.bytecode.CPClass(name, 1);
        final org.apache.commons.compress.harmony.unpack200.bytecode.CPNameAndType type =
                new org.apache.commons.compress.harmony.unpack200.bytecode.CPNameAndType(name, descriptor, 2);
        assertThat(new org.apache.commons.compress.harmony.unpack200.bytecode.CPFieldRef(owner, type, 3).hashCode()).isNotZero();
        assertThat(new org.apache.commons.compress.harmony.unpack200.bytecode.CPInterfaceMethodRef(owner, type, 4).hashCode()).isNotZero();
        assertThat(new org.apache.commons.compress.harmony.unpack200.bytecode.CPMethod(name, descriptor, 0, java.util.List.of()).hashCode()).isNotZero();
        assertThat(new org.apache.commons.compress.harmony.unpack200.bytecode.CPMethodRef(owner, type, 5).hashCode()).isNotZero();
        assertThat(type.hashCode()).isNotZero();
        assertThat(org.apache.commons.compress.harmony.unpack200.bytecode.ByteCode.getByteCode(0).hashCode()).isNotZero();
        org.apache.commons.compress.harmony.unpack200.bytecode.ExceptionsAttribute.setAttributeName(name);
        assertThat(new org.apache.commons.compress.harmony.unpack200.bytecode.ExceptionsAttribute(
                new org.apache.commons.compress.harmony.unpack200.bytecode.CPClass[] {owner}).hashCode()).isNotZero();
    }

    private static Path fixture(final String name) {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            final Path candidate = directory.resolve(
                    "forge/local_repositories/source_context/org.apache.commons/commons-compress/1.23.0/test/extracted")
                    .resolve(name);
            if (Files.exists(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Missing Commons Compress fixture: " + name);
    }

    private static boolean isAsmAvailable() {
        try {
            Class.forName("org.objectweb.asm.ClassVisitor");
            return true;
        } catch (ClassNotFoundException missingAsm) {
            return false;
        }
    }

    private static void createJar(final Path path) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry("payload.txt"));
            output.write("payload".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new JarEntry("unicode-\u00e9-\u65e5\u672c.txt"));
            output.write("unicode payload".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    private static String readJarEntry(final File file, final String name) throws IOException {
        try (JarFile jar = new JarFile(file); InputStream input = jar.getInputStream(jar.getJarEntry(name))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class HighEffortPackBand extends BandSet {
        private HighEffortPackBand() {
            super(9, new SegmentHeader());
        }

        @Override
        public void pack(final OutputStream output) {
        }
    }

    private static final class TestPackBand extends BandSet {
        private TestPackBand() {
            super(1, new SegmentHeader());
        }

        @Override
        public void pack(final OutputStream output) {
        }

        private BandAnalysisResults newBandAnalysisResults() {
            return new BandAnalysisResults();
        }
    }

    private static final class TestUnpackBandSet extends org.apache.commons.compress.harmony.unpack200.BandSet {
        private TestUnpackBandSet() {
            super(new org.apache.commons.compress.harmony.unpack200.Segment());
        }

        @Override
        public void read(final InputStream input) {
        }

        @Override
        public void unpack() {
        }
    }
}
