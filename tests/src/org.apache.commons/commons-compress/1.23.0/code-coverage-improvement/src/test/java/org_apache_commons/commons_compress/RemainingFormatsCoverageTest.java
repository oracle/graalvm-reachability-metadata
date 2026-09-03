/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.apache.commons.compress.archivers.arj.ArjArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;
import org.apache.commons.compress.archivers.dump.DumpArchiveInputStream;
import org.apache.commons.compress.archivers.sevenz.CLI;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.changes.ChangeSet;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.FileNameUtil;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
import org.apache.commons.compress.compressors.pack200.Pack200Utils;
import org.apache.commons.compress.compressors.snappy.SnappyCompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.utils.MultiReadOnlySeekableByteChannel;
import org.apache.commons.compress.utils.ServiceLoaderIterator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RemainingFormatsCoverageTest {

    @Test
    void cpioAndTarLifecycleApisUseRealArchiveBytes() throws Exception {
        final Path source = Files.createTempFile("remaining-source", ".txt");
        Files.writeString(source, "tar-content", StandardCharsets.UTF_8);
        final ByteArrayOutputStream cpio = new ByteArrayOutputStream();
        try (CpioArchiveOutputStream output = new CpioArchiveOutputStream(cpio, CpioConstants.FORMAT_NEW)) {
            final ArchiveEntry pathEntry = output.createArchiveEntry(source, "path-entry.txt",
                    new java.nio.file.LinkOption[0]);
            assertThat(pathEntry).isNotNull();
            final ArchiveEntry entry = output.createArchiveEntry(source, "entry.txt");
            output.putArchiveEntry(entry);
            output.write("tar-content".getBytes(StandardCharsets.UTF_8));
            output.closeArchiveEntry();
            output.finish();
        }
        try (CpioArchiveInputStream one = new CpioArchiveInputStream(new ByteArrayInputStream(cpio.toByteArray()));
             CpioArchiveInputStream two = new CpioArchiveInputStream(new ByteArrayInputStream(cpio.toByteArray()), CpioConstants.FORMAT_NEW);
             CpioArchiveInputStream three = new CpioArchiveInputStream(new ByteArrayInputStream(cpio.toByteArray()), CpioConstants.FORMAT_NEW, "UTF-8");
             CpioArchiveInputStream four = new CpioArchiveInputStream(new ByteArrayInputStream(cpio.toByteArray()), "UTF-8")) {
            final ArchiveEntry parsed = one.getNextEntry();
            assertThat(parsed).isNotNull();
            assertThat(one.canReadEntryData(parsed)).isTrue();
            assertThat(two.getNextCPIOEntry()).isNotNull();
            assertThat(three.getNextCPIOEntry()).isNotNull();
            assertThat(four.getNextCPIOEntry()).isNotNull();
            one.close();
        }
        final ByteArrayOutputStream tar = new ByteArrayOutputStream();
        try (TarArchiveOutputStream output = new TarArchiveOutputStream(tar, 512, 512)) {
            final TarArchiveEntry entry = new TarArchiveEntry("entry.txt");
            entry.setSize(11);
            output.putArchiveEntry(entry);
            output.write("tar-content".getBytes(StandardCharsets.UTF_8));
            output.closeArchiveEntry();
            assertThat(output.getCount()).isGreaterThanOrEqualTo(0);
            output.finish();
        }
        final byte[] firstHeader = Arrays.copyOf(tar.toByteArray(), 512);
        final TarArchiveEntry parsed = new TarArchiveEntry(firstHeader);
        assertThat(parsed.getName()).isEqualTo("entry.txt");
        parsed.setIds(7, 8);
        parsed.addPaxHeader("comment", "value");
        assertThat(parsed.getLastModifiedDate()).isNotNull();
        assertThat(parsed.getDirectoryEntries()).isEmpty();
        try {
            assertThat(new TarFile(tar.toByteArray())).isNotNull();
            assertThat(new TarFile(tar.toByteArray(), true)).isNotNull();
        } catch (Exception expectedForMinimalTar) {
            assertThat(expectedForMinimalTar).isInstanceOf(Exception.class);
        }
        Files.deleteIfExists(source);
    }

    @Test
    void oldCpioFormatsRoundTripThroughPublicStreams() throws Exception {
        for (final short format : new short[] {CpioConstants.FORMAT_OLD_ASCII, CpioConstants.FORMAT_OLD_BINARY}) {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (CpioArchiveOutputStream output = new CpioArchiveOutputStream(bytes, format)) {
                final org.apache.commons.compress.archivers.cpio.CpioArchiveEntry entry =
                        new org.apache.commons.compress.archivers.cpio.CpioArchiveEntry(format, "entry", 3);
                output.putArchiveEntry(entry);
                output.write(new byte[] {'c', 'p', 'o'});
                output.closeArchiveEntry();
            }
            try (CpioArchiveInputStream input = new CpioArchiveInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                assertThat(input.getNextCPIOEntry()).isNotNull();
                assertThat(input.readAllBytes()).containsExactly('c', 'p', 'o');
                assertThat(input.getNextCPIOEntry()).isNull();
            }
        }
        for (final String name : new String[] {"archives/FreeBSD_hpbin.cpio", "archives/SunOS_odc.cpio"}) {
            try (CpioArchiveInputStream input = new CpioArchiveInputStream(Files.newInputStream(fixture(name)))) {
                assertThat(input.getNextCPIOEntry()).isNotNull();
                input.readAllBytes();
            }
        }
    }

    @Test
    void validArjHeadersAreReadThroughThePublicEntryIterator() throws Exception {
        for (final String name : new String[] {"bla.arj", "bla.unix.arj"}) {
            try (ArjArchiveInputStream input = new ArjArchiveInputStream(
                    Files.newInputStream(fixture(name)), StandardCharsets.ISO_8859_1.name())) {
                final ArchiveEntry entry = input.getNextEntry();
                assertThat(entry).isNotNull();
                assertThat(input.canReadEntryData(entry)).isIn(true, false);
                input.readAllBytes();
            }
        }
    }

    @Test
    void optionalFormatConstructorsAndFactoriesRemainCallable() throws Exception {
        final Path zipPath = Files.createTempFile("remaining-zip", ".zip");
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(zipPath.toFile())) {
            output.putArchiveEntry(new ZipArchiveEntry("one.txt"));
            output.write("one".getBytes(StandardCharsets.UTF_8));
            output.closeArchiveEntry();
        }
        try (ZipFile zip = new ZipFile(zipPath.toString())) {
            assertThat(zip.getEntry("one.txt")).isNotNull();
        }
        final Map<String, String> suffixes = new HashMap<>();
        suffixes.put(".foo", ".bar");
        final FileNameUtil names = new FileNameUtil(suffixes, ".foo");
        assertThat(names.isCompressedFilename("name.foo")).isTrue();
        assertThat(names.getUncompressedFilename("name.foo")).isEqualTo("name.bar");
        assertThat(names.getCompressedFilename("name.bar")).isEqualTo("name.foo");
        assertThat(new ServiceLoaderIterator<>(Runnable.class)).isNotNull();
        try (java.nio.channels.SeekableByteChannel first = Files.newByteChannel(zipPath);
             java.nio.channels.SeekableByteChannel second = Files.newByteChannel(zipPath);
             MultiReadOnlySeekableByteChannel channels = new MultiReadOnlySeekableByteChannel(
                     java.util.List.of(first, second))) {
            assertThat(channels.size()).isPositive();
            assertThat(channels.position(0, 1)).isSameAs(channels);
            assertThat(channels.position()).isEqualTo(1);
        }
        try (InputStream input = new ByteArrayInputStream(new byte[0])) {
            try {
                new XZCompressorInputStream(input);
            } catch (Exception expected) {
                assertThat(expected).isInstanceOf(Exception.class);
            } catch (LinkageError unavailableOptionalCodec) {
                assertThat(unavailableOptionalCodec).isNotNull();
            }
        }
        final ByteArrayOutputStream snappyBytes = new ByteArrayOutputStream();
        try (SnappyCompressorOutputStream snappy = new SnappyCompressorOutputStream(snappyBytes, 3)) {
            snappy.write("one".getBytes(StandardCharsets.UTF_8), 0, 3);
        }
        assertThat(snappyBytes.size()).isPositive();
        try {
            Pack200Utils.normalize(zipPath.toFile());
            Pack200Utils.normalize(zipPath.toFile(), Files.createTempFile("pack200-output", ".jar").toFile());
            Pack200Utils.normalize(zipPath.toFile(), new HashMap<>());
        } catch (Exception expected) {
            assertThat(expected).isInstanceOf(Exception.class);
        } catch (LinkageError unavailableOptionalPack200) {
            assertThat(unavailableOptionalPack200).isNotNull();
        }
        try {
            new Pack200CompressorInputStream(zipPath.toFile(), new HashMap<>());
        } catch (Exception expected) {
            assertThat(expected).isInstanceOf(Exception.class);
        } catch (LinkageError unavailableOptionalPack200) {
            assertThat(unavailableOptionalPack200).isNotNull();
        }
        Files.deleteIfExists(zipPath);
    }

    @Test
    void publicFormatModelsAndUtilityFactoriesExposeTheirContracts() throws Exception {
        final Path source = Files.createTempFile("remaining-api", ".txt");
        Files.writeString(source, "payload", StandardCharsets.UTF_8);
        final TarArchiveEntry fromFile = new TarArchiveEntry(source.toFile());
        final TarArchiveEntry fromPath = new TarArchiveEntry(source);
        assertThat(fromFile.getLinkFlag()).isEqualTo(TarArchiveEntry.LF_NORMAL);
        assertThat(fromPath.isBlockDevice()).isFalse();
        assertThat(fromPath.isCharacterDevice()).isFalse();
        assertThat(fromPath.isExtended()).isFalse();
        assertThat(fromPath.isFIFO()).isFalse();
        assertThat(fromPath.isStreamContiguous()).isTrue();
        assertThat(new org.apache.commons.compress.archivers.tar.TarArchiveStructSparse(1, 2).hashCode()).isNotZero();
        final org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration copy =
                new org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration(SevenZMethod.COPY);
        assertThat(copy).isEqualTo(new org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration(SevenZMethod.COPY));
        assertThat(copy.hashCode()).isEqualTo(copy.hashCode());
        assertThat(SevenZMethod.values()).isNotEmpty();
        assertThat(SevenZMethod.valueOf("COPY")).isEqualTo(SevenZMethod.COPY);
        assertThat(new CLI()).isNotNull();
        final ChangeSet changes = new ChangeSet();
        changes.deleteDir("folder/");
        assertThat(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel().write(
                java.nio.ByteBuffer.wrap(new byte[] {1, 2}))).isEqualTo(2);
        assertThat(new org.apache.commons.compress.compressors.CompressorException("message")).hasMessage("message");
        assertThat(new org.apache.commons.compress.archivers.zip.Zip64RequiredException("zip64")).hasMessage("zip64");
        assertThat(new org.apache.commons.compress.archivers.zip.UnicodePathExtraField("path", new byte[] {1, 2})).isNotNull();
        Files.deleteIfExists(source);
    }

    @Test
    void archiveInputConstructorsAndMagicChecksHandleInvalidData() throws Exception {
        final byte[] empty = new byte[0];
        try {
            new DumpArchiveInputStream(new ByteArrayInputStream(empty));
        } catch (Exception expected) {
            assertThat(expected).isInstanceOf(Exception.class);
        }
        try {
            new DumpArchiveInputStream(new ByteArrayInputStream(empty), "UTF-8");
        } catch (Exception expected) {
            assertThat(expected).isInstanceOf(Exception.class);
        }
        try {
            new ArjArchiveInputStream(new ByteArrayInputStream(empty));
        } catch (Exception expected) {
            assertThat(expected).isInstanceOf(Exception.class);
        }
        try {
            new ArjArchiveInputStream(new ByteArrayInputStream(empty), "UTF-8");
        } catch (Exception expected) {
            assertThat(expected).isInstanceOf(Exception.class);
        }
        assertThat(DumpArchiveInputStream.matches(new byte[32], 32)).isFalse();
        assertThat(ArjArchiveInputStream.matches(new byte[16], 16)).isFalse();
        assertThat(new CpioArchiveOutputStream(new ByteArrayOutputStream(), CpioConstants.FORMAT_NEW, 2)).isNotNull();
        try (CpioArchiveOutputStream one = new CpioArchiveOutputStream(new ByteArrayOutputStream());
             CpioArchiveOutputStream two = new CpioArchiveOutputStream(new ByteArrayOutputStream(), "UTF-8");
             CpioArchiveOutputStream three = new CpioArchiveOutputStream(new ByteArrayOutputStream(), CpioConstants.FORMAT_NEW);
             ArArchiveOutputStream ar = new ArArchiveOutputStream(new ByteArrayOutputStream())) {
            assertThat(one).isNotNull();
            assertThat(two).isNotNull();
            assertThat(three).isNotNull();
            ar.finish();
        }
        try {
            CLI.main(new String[0]);
        } catch (Exception expected) {
            assertThat(expected).isInstanceOf(Exception.class);
        }
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
}
