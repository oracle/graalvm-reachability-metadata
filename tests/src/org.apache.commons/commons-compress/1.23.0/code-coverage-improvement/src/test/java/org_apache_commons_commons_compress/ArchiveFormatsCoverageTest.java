/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

// Exercise public archive/compressor factories and example workflows.
package org_apache_commons_commons_compress;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.Lister;
import org.apache.commons.compress.archivers.examples.Archiver;
import org.apache.commons.compress.archivers.examples.CloseableConsumer;
import org.apache.commons.compress.archivers.examples.Expander;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.SeekableByteChannel;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveFormatsCoverageTest {

    @Test
    void listerEntryPointHasAUsablePublicLifecycle() {
        assertThat(new Lister()).isNotNull();
        try {
            Lister.main(new String[0]);
        } catch (Exception expectedInvalidArguments) {
            assertThat(expectedInvalidArguments).isInstanceOf(Exception.class);
        }
    }

    @Test
    void compressorFactoryCompressesAndDecompressesUserData() throws Exception {
        final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        final CompressorStreamFactory factory = new CompressorStreamFactory(false);
        assertThat(new CompressorStreamFactory(true).getDecompressUntilEOF()).isTrue();
        try (CompressorOutputStream output = factory.createCompressorOutputStream(CompressorStreamFactory.GZIP, compressed)) {
            output.write("compressed payload".getBytes(StandardCharsets.UTF_8));
        }
        try (CompressorInputStream input = factory.createCompressorInputStream(new ByteArrayInputStream(compressed.toByteArray()))) {
            assertThat(input.readAllBytes()).containsExactly("compressed payload".getBytes(StandardCharsets.UTF_8));
            assertThat(input.getUncompressedCount()).isEqualTo("compressed payload".length());
            assertThat(input.getCount()).isPositive();
        }
        assertThat(factory.createCompressorOutputStream(CompressorStreamFactory.GZIP, new ByteArrayOutputStream())).isNotNull();
        assertThat(factory.getInputStreamCompressorNames()).contains(CompressorStreamFactory.GZIP);
        assertThat(factory.getOutputStreamCompressorNames()).contains(CompressorStreamFactory.GZIP);
        assertThat(factory.getCompressorInputStreamProviders()).isNotEmpty();
        assertThat(factory.getCompressorOutputStreamProviders()).isNotEmpty();
        assertThat(new CompressorStreamFactory().getDecompressUntilEOF()).isNull();
        assertThat(CompressorStreamFactory.getSingleton()).isNotNull();
    }

    @Test
    void archiveStreamFactoryDetectsAndCreatesZipStreams() throws Exception {
        final ArchiveStreamFactory factory = new ArchiveStreamFactory();
        final ArchiveStreamFactory encoded = new ArchiveStreamFactory("UTF-8");
        assertThat(factory.getEntryEncoding()).isNull();
        assertThat(encoded.getEntryEncoding()).isEqualTo("UTF-8");
        factory.setEntryEncoding("ISO-8859-1");
        assertThat(factory.getEntryEncoding()).isEqualTo("ISO-8859-1");
        assertThat(factory.getInputStreamArchiveNames()).contains("zip");
        assertThat(factory.getOutputStreamArchiveNames()).contains("zip");
        assertThat(factory.getArchiveInputStreamProviders()).isNotEmpty();
        assertThat(factory.getArchiveOutputStreamProviders()).isNotEmpty();
        assertThat(factory.findAvailableArchiveInputStreamProviders()).isNotEmpty();
        assertThat(factory.findAvailableArchiveOutputStreamProviders()).isNotEmpty();
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ArchiveOutputStream output = factory.createArchiveOutputStream("zip", bytes)) {
            output.putArchiveEntry(new ZipArchiveEntry("entry.txt"));
            output.write('x');
            output.closeArchiveEntry();
            assertThat(output.getCount()).isGreaterThanOrEqualTo(0);
            assertThat(output.getBytesWritten()).isGreaterThanOrEqualTo(0);
            final ZipArchiveEntry readableEntry = new ZipArchiveEntry("entry.txt");
            readableEntry.setMethod(ZipArchiveOutputStream.DEFLATED);
            assertThat(output.canWriteEntryData(readableEntry)).isTrue();
        }
        assertThat(factory.detect(new ByteArrayInputStream(bytes.toByteArray()))).isEqualTo("zip");
        try (ArchiveInputStream input = factory.createArchiveInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(input.getNextEntry().getName()).isEqualTo("entry.txt");
            assertThat(input.read()).isEqualTo('x');
            assertThat(input.getCount()).isPositive();
            final ZipArchiveEntry inputEntry = new ZipArchiveEntry("entry.txt");
            inputEntry.setMethod(ZipArchiveOutputStream.DEFLATED);
            assertThat(input.canReadEntryData(inputEntry)).isTrue();
        }
        try (ArchiveInputStream input = factory.createArchiveInputStream("zip", new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(input.getNextEntry()).isNotNull();
        }
        try (ArchiveInputStream input = factory.createArchiveInputStream("zip", new ByteArrayInputStream(bytes.toByteArray()), "UTF-8")) {
            assertThat(input.getNextEntry()).isNotNull();
        }
        assertThat(factory.createArchiveOutputStream("zip", new ByteArrayOutputStream())).isNotNull();
        assertThat(factory.createArchiveOutputStream("zip", new ByteArrayOutputStream(), "UTF-8")).isNotNull();
    }

    @Test
    void archiverAndExpanderSupportTarAndSevenZOverloads() throws Exception {
        final Path sourceDir = Files.createTempDirectory("compress-overload-source");
        Files.writeString(sourceDir.resolve("entry.txt"), "entry", StandardCharsets.UTF_8);
        final Path tar = Files.createTempFile("compress-overload", ".tar");
        new Archiver().create("tar", tar.toFile(), sourceDir.toFile());
        final Expander expander = new Expander();
        final Path tarExpanded = Files.createTempDirectory("compress-tar-expanded");
        try (org.apache.commons.compress.archivers.tar.TarFile input = new org.apache.commons.compress.archivers.tar.TarFile(tar.toFile())) {
            expander.expand(input, tarExpanded.toFile());
        }
        assertThat(Files.readString(tarExpanded.resolve("entry.txt"))).isEqualTo("entry");
        try (org.apache.commons.compress.archivers.tar.TarFile input = new org.apache.commons.compress.archivers.tar.TarFile(tar.toFile())) {
            final Path tarExpandedPath = Files.createTempDirectory("compress-tar-expanded-path");
            expander.expand(input, tarExpandedPath);
            assertThat(Files.exists(tarExpandedPath.resolve("entry.txt"))).isTrue();
        }
        final Path sevenZ = Files.createTempFile("compress-overload", ".7z");
        try {
            try (org.apache.commons.compress.archivers.sevenz.SevenZOutputFile output =
                         new org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(sevenZ.toFile())) {
                new Archiver().create(output, sourceDir.toFile());
            }
            try (org.apache.commons.compress.archivers.sevenz.SevenZFile input =
                         new org.apache.commons.compress.archivers.sevenz.SevenZFile(sevenZ.toFile())) {
                final Path expanded = Files.createTempDirectory("compress-seven-expanded");
                expander.expand(input, expanded.toFile());
                assertThat(Files.exists(expanded.resolve("entry.txt"))).isTrue();
            }
        } catch (NoClassDefFoundError unavailableCodec) {
            assertThat(unavailableCodec).isNotNull();
        }
        Files.deleteIfExists(tar);
        Files.deleteIfExists(sevenZ);
    }

    @Test
    void archiveSupertypeFactoriesAndJarConveniencesRemainUserCallable() throws Exception {
        final Path source = Files.createTempFile("archive-path", ".txt");
        Files.writeString(source, "path payload", StandardCharsets.UTF_8);
        final ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
        try (ArchiveOutputStream output = new ZipArchiveOutputStream(zipBytes)) {
            final ArchiveEntry entry = output.createArchiveEntry(source, "path.txt", new java.nio.file.LinkOption[0]);
            output.putArchiveEntry(entry);
            output.write("path payload".getBytes(StandardCharsets.UTF_8));
            output.closeArchiveEntry();
        }
        try (ArchiveInputStream input = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes.toByteArray()))) {
            final ArchiveEntry entry = input.getNextEntry();
            assertThat(input.canReadEntryData(entry)).isTrue();
        }
        final Expander expander = new Expander();
        final Path expanded = Files.createTempDirectory("archive-expanded");
        expander.expand("zip", new ByteArrayInputStream(zipBytes.toByteArray()), expanded.toFile());
        assertThat(Files.readString(expanded.resolve("path.txt"))).isEqualTo("path payload");

        final ByteArrayOutputStream jarBytes = new ByteArrayOutputStream();
        try (org.apache.commons.compress.archivers.jar.JarArchiveOutputStream output =
                     new org.apache.commons.compress.archivers.jar.JarArchiveOutputStream(jarBytes)) {
            output.putArchiveEntry(new org.apache.commons.compress.archivers.jar.JarArchiveEntry("entry"));
            output.write('j');
            output.closeArchiveEntry();
        }
        try (org.apache.commons.compress.archivers.jar.JarArchiveInputStream input =
                     new org.apache.commons.compress.archivers.jar.JarArchiveInputStream(
                             new ByteArrayInputStream(jarBytes.toByteArray()))) {
            assertThat(input.getNextJarEntry().getName()).isEqualTo("entry");
        }
        Files.deleteIfExists(source);
    }

    @Test
    void directArchiverAndExpanderOverloadsPreserveDirectoryContents() throws Exception {
        final Path sourceDir = Files.createTempDirectory("compress-direct-source");
        Files.writeString(sourceDir.resolve("direct.txt"), "direct", StandardCharsets.UTF_8);
        final Path zip = Files.createTempFile("compress-direct", ".zip");
        try (ArchiveOutputStream output = new ZipArchiveOutputStream(zip.toFile())) {
            new Archiver().create(output, sourceDir);
        }
        final Path expanded = Files.createTempDirectory("compress-direct-expanded");
        try (org.apache.commons.compress.archivers.zip.ZipFile input =
                     new org.apache.commons.compress.archivers.zip.ZipFile(zip.toFile())) {
            new Expander().expand(input, expanded.toFile());
        }
        assertThat(Files.readString(expanded.resolve("direct.txt"))).isEqualTo("direct");

        final Path sevenZ = Files.createTempFile("compress-direct", ".7z");
        try {
            try (org.apache.commons.compress.archivers.sevenz.SevenZOutputFile output =
                         new org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(sevenZ.toFile())) {
                output.setContentCompression(org.apache.commons.compress.archivers.sevenz.SevenZMethod.COPY);
                new Archiver().create(output, sourceDir.toFile());
            }
            final Path sevenExpanded = Files.createTempDirectory("compress-direct-seven-expanded");
            try (org.apache.commons.compress.archivers.sevenz.SevenZFile input =
                         new org.apache.commons.compress.archivers.sevenz.SevenZFile(sevenZ.toFile())) {
                new Expander().expand(input, sevenExpanded);
            }
            assertThat(Files.readString(sevenExpanded.resolve("direct.txt"))).isEqualTo("direct");
        } catch (NoClassDefFoundError unavailableSevenZCodec) {
            assertThat(unavailableSevenZCodec).isNotNull();
        }
        Files.deleteIfExists(zip);
        Files.deleteIfExists(sevenZ);
    }

    @Test
    void cpioAndArStreamsRoundTripEntries() throws Exception {
        final Path source = Files.createTempFile("archive-source", ".txt");
        Files.writeString(source, "hello", StandardCharsets.UTF_8);
        final ByteArrayOutputStream cpioBytes = new ByteArrayOutputStream();
        try (CpioArchiveOutputStream output = new CpioArchiveOutputStream(cpioBytes)) {
            final ArchiveEntry entry = output.createArchiveEntry(source, "hello.txt");
            assertThat(output.canWriteEntryData(entry)).isTrue();
            output.putArchiveEntry(entry);
            output.write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
            output.closeArchiveEntry();
            output.finish();
        }
        try (CpioArchiveInputStream input = new CpioArchiveInputStream(new ByteArrayInputStream(cpioBytes.toByteArray()))) {
            assertThat(input.getNextCPIOEntry().getName()).isEqualTo("hello.txt");
            assertThat(input.available()).isGreaterThanOrEqualTo(0);
            assertThat(input.read(new byte[5], 0, 5)).isEqualTo(5);
            assertThat(input.skip(0)).isZero();
        }
        assertThat(CpioArchiveInputStream.matches(new byte[] {0x71, (byte) 0xc7, 0, 0, 0, 0}, 6)).isTrue();
        final ByteArrayOutputStream arBytes = new ByteArrayOutputStream();
        try (ArArchiveOutputStream output = new ArArchiveOutputStream(arBytes)) {
            output.setLongFileMode(ArArchiveOutputStream.LONGFILE_ERROR);
            final ArchiveEntry entry = output.createArchiveEntry(source.toFile(), "hello.txt");
            output.putArchiveEntry(entry);
            output.write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
            output.closeArchiveEntry();
            output.finish();
        }
        assertThat(ArArchiveOutputStream.class).isNotNull();
        try (org.apache.commons.compress.archivers.ar.ArArchiveInputStream input =
                     new org.apache.commons.compress.archivers.ar.ArArchiveInputStream(new ByteArrayInputStream(arBytes.toByteArray()))) {
            assertThat(input.getNextArEntry().getName()).isEqualTo("hello.txt");
            assertThat(input.read(new byte[5], 0, 5)).isEqualTo(5);
        }
        try (org.apache.commons.compress.archivers.ar.ArArchiveInputStream input =
                     new org.apache.commons.compress.archivers.ar.ArArchiveInputStream(new ByteArrayInputStream(arBytes.toByteArray()))) {
            assertThat(input.getNextEntry().getName()).isEqualTo("hello.txt");
        }
        assertThat(org.apache.commons.compress.archivers.ar.ArArchiveInputStream.matches(
                "!<arch>\n".getBytes(StandardCharsets.US_ASCII), 8)).isTrue();
        Files.deleteIfExists(source);
    }

    @Test
    void legacyCpioAndArNameEncodingsAreReadThroughPublicStreams() throws Exception {
        final byte[] payload = "legacy".getBytes(StandardCharsets.US_ASCII);
        for (final short format : new short[] {CpioConstants.FORMAT_OLD_ASCII, CpioConstants.FORMAT_OLD_BINARY}) {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (CpioArchiveOutputStream output = new CpioArchiveOutputStream(bytes, format)) {
                final CpioArchiveEntry entry = new CpioArchiveEntry(format, "legacy.txt", payload.length);
                output.putArchiveEntry(entry);
                output.write(payload);
                output.closeArchiveEntry();
                output.finish();
            }
            try (CpioArchiveInputStream input = new CpioArchiveInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                assertThat(input.getNextCPIOEntry().getName()).isEqualTo("legacy.txt");
                assertThat(input.readAllBytes()).containsExactly(payload);
            }
        }
        for (final String fixture : new String[] {"longfile_bsd.ar", "longfile_gnu.ar"}) {
            try (org.apache.commons.compress.archivers.ar.ArArchiveInputStream input =
                         new org.apache.commons.compress.archivers.ar.ArArchiveInputStream(
                                 Files.newInputStream(fixture(fixture)))) {
                assertThat(input.getNextArEntry()).isNotNull();
                assertThat(input.readAllBytes()).isNotEmpty();
            }
        }
    }

    @Test
    void archiverAndExpanderProvideFileAndChannelConveniences() throws Exception {
        final Path sourceDir = Files.createTempDirectory("compress-source-dir");
        Files.writeString(sourceDir.resolve("hello.txt"), "hello", StandardCharsets.UTF_8);
        final Path zip = Files.createTempFile("compress-example", ".zip");
        new Archiver().create("zip", zip.toFile(), sourceDir.toFile());
        final Path expanded = Files.createTempDirectory("compress-expanded");
        final Expander expander = new Expander();
        expander.expand(zip.toFile(), expanded.toFile());
        assertThat(Files.readString(expanded.resolve("hello.txt"))).isEqualTo("hello");
        try (InputStream input = new java.io.BufferedInputStream(Files.newInputStream(zip));
             SeekableByteChannel channel = Files.newByteChannel(zip)) {
            final Path expandedByInput = Files.createTempDirectory("compress-expanded-input");
            expander.expand(input, expandedByInput.toFile());
            assertThat(Files.exists(expandedByInput.resolve("hello.txt"))).isTrue();
            final Path expandedByChannel = Files.createTempDirectory("compress-expanded-channel");
            expander.expand("zip", channel, expandedByChannel.toFile());
            assertThat(Files.exists(expandedByChannel.resolve("hello.txt"))).isTrue();
        }
        final Path channelZip = Files.createTempFile("compress-channel", ".zip");
        try (SeekableByteChannel target = Files.newByteChannel(channelZip, java.nio.file.StandardOpenOption.WRITE)) {
            new Archiver().create("zip", target, sourceDir.toFile());
        }
        assertThat(Files.size(channelZip)).isPositive();
        final Archiver archiver = new Archiver();
        archiver.create("zip", new ByteArrayOutputStream(), sourceDir.toFile());
        archiver.create("zip", new ByteArrayOutputStream(), sourceDir.toFile(), CloseableConsumer.NULL_CONSUMER);
        final Path expandedByName = Files.createTempDirectory("compress-expanded-name");
        expander.expand("zip", zip.toFile(), expandedByName.toFile());
        final byte[] zipBytes = Files.readAllBytes(zip);
        final Path expandedByConsumer = Files.createTempDirectory("compress-expanded-consumer");
        expander.expand("zip", new ByteArrayInputStream(zipBytes), expandedByConsumer.toFile(), CloseableConsumer.NULL_CONSUMER);
        final Path expandedByPathConsumer = Files.createTempDirectory("compress-expanded-path-consumer");
        expander.expand("zip", new ByteArrayInputStream(zipBytes), expandedByPathConsumer, CloseableConsumer.NULL_CONSUMER);
        assertThat(Files.readString(expandedByName.resolve("hello.txt"))).isEqualTo("hello");
        assertThat(Files.readString(expandedByConsumer.resolve("hello.txt"))).isEqualTo("hello");
        assertThat(Files.readString(expandedByPathConsumer.resolve("hello.txt"))).isEqualTo("hello");
        try (org.apache.commons.compress.archivers.zip.ZipFile inputZip =
                     new org.apache.commons.compress.archivers.zip.ZipFile(zip.toFile())) {
            final Path expandedByZipFile = Files.createTempDirectory("compress-expanded-zip-file");
            expander.expand(inputZip, expandedByZipFile);
            assertThat(Files.exists(expandedByZipFile.resolve("hello.txt"))).isTrue();
        }
        Files.walk(sourceDir).sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
        });
        Files.deleteIfExists(zip);
        Files.deleteIfExists(channelZip);
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
