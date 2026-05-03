/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_resolver.maven_resolver_spi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.checksums.ProvidedChecksumsSource;
import org.eclipse.aether.spi.checksums.TrustedChecksumsSource;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithm;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySupport;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmHelper;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Maven_resolver_spiTest {
    private static final Crc32ChecksumAlgorithmFactory CRC32_FACTORY = new Crc32ChecksumAlgorithmFactory();

    @TempDir
    Path tempDirectory;

    @Test
    void artifactAndMetadataTransfersPreserveFluentStateAndDefaults() {
        Artifact artifact = artifact();
        Metadata metadata = metadata();
        Path file = tempDirectory.resolve("artifact.jar");
        RemoteRepository repository = repository();
        RequestTrace trace = new RequestTrace("root").newChild("child");
        TransferListener listener = new NoOpTransferListener();
        ArtifactTransferException artifactException = new ArtifactTransferException(
                artifact, repository, "artifact failed");
        MetadataTransferException metadataException = new MetadataTransferException(
                metadata, repository, "metadata failed");

        ArtifactDownload artifactDownload = new ArtifactDownload(artifact, "resolve", file, "fail")
                .setExistenceCheck(true)
                .setRepositories(List.of(repository))
                .setSupportedContexts(List.of("resolve", "plugin"))
                .setTrace(trace)
                .setListener(listener)
                .setException(artifactException);

        assertThat(artifactDownload.getArtifact()).isSameAs(artifact);
        assertThat(artifactDownload.getPath()).isEqualTo(file);
        assertThat(artifactDownload.getRequestContext()).isEqualTo("resolve");
        assertThat(artifactDownload.getChecksumPolicy()).isEqualTo("fail");
        assertThat(artifactDownload.isExistenceCheck()).isTrue();
        assertThat(artifactDownload.getRepositories()).containsExactly(repository);
        assertThat(artifactDownload.getSupportedContexts()).containsExactly("resolve", "plugin");
        assertThat(artifactDownload.getTrace()).isSameAs(trace);
        assertThat(artifactDownload.getListener()).isSameAs(listener);
        assertThat(artifactDownload.getException()).isSameAs(artifactException);
        assertThat(artifactDownload.toString()).contains("?", file.getFileName().toString());

        ArtifactDownload defaults = new ArtifactDownload().setRequestContext(null).setChecksumPolicy(null);
        assertThat(defaults.getRequestContext()).isEmpty();
        assertThat(defaults.getChecksumPolicy()).isEmpty();
        assertThat(defaults.setRequestContext("runtime").setSupportedContexts(null).getSupportedContexts())
                .containsExactly("runtime");
        assertThat(defaults.setSupportedContexts(List.of()).getSupportedContexts()).containsExactly("runtime");
        assertThat(defaults.setRepositories(null).getRepositories()).isEmpty();

        MetadataDownload metadataDownload = new MetadataDownload(metadata, "metadata", file, "warn")
                .setRepositories(List.of(repository))
                .setTrace(trace)
                .setListener(listener)
                .setException(metadataException);

        assertThat(metadataDownload.getMetadata()).isSameAs(metadata);
        assertThat(metadataDownload.getPath()).isEqualTo(file);
        assertThat(metadataDownload.getRequestContext()).isEqualTo("metadata");
        assertThat(metadataDownload.getChecksumPolicy()).isEqualTo("warn");
        assertThat(metadataDownload.getRepositories()).containsExactly(repository);
        assertThat(metadataDownload.getTrace()).isSameAs(trace);
        assertThat(metadataDownload.getListener()).isSameAs(listener);
        assertThat(metadataDownload.getException()).isSameAs(metadataException);
        assertThat(metadataDownload.toString()).contains(file.getFileName().toString());
        assertThat(new MetadataDownload().setRequestContext(null).setChecksumPolicy(null).setRepositories(null))
                .extracting(MetadataDownload::getRequestContext, MetadataDownload::getChecksumPolicy,
                        MetadataDownload::getRepositories)
                .containsExactly("", "", List.of());
    }

    @Test
    void uploadsExposePathsAndTypedExceptions() throws Exception {
        Artifact artifact = artifact();
        Metadata metadata = metadata();
        Path file = tempDirectory.resolve("upload.jar");
        Files.writeString(file, "payload");
        RequestTrace trace = new RequestTrace("upload");
        TransferListener listener = new NoOpTransferListener();
        RemoteRepository repository = repository();
        ArtifactTransferException artifactException = new ArtifactTransferException(
                artifact, repository, "upload failed");
        MetadataTransferException metadataException = new MetadataTransferException(
                metadata, repository, "metadata upload failed");

        ArtifactUpload artifactUpload = new ArtifactUpload(artifact, file)
                .setTrace(trace)
                .setListener(listener)
                .setException(artifactException);
        MetadataUpload metadataUpload = new MetadataUpload(metadata, file)
                .setTrace(trace)
                .setListener(listener)
                .setException(metadataException);

        assertThat(artifactUpload.getArtifact()).isSameAs(artifact);
        assertThat(artifactUpload.getPath()).isEqualTo(file);
        assertThat(Files.readString(artifactUpload.getPath())).isEqualTo("payload");
        assertThat(artifactUpload.getTrace()).isSameAs(trace);
        assertThat(artifactUpload.getListener()).isSameAs(listener);
        assertThat(artifactUpload.getException()).isSameAs(artifactException);
        assertThat(artifactUpload.toString()).contains("demo", file.getFileName().toString());

        assertThat(metadataUpload.getMetadata()).isSameAs(metadata);
        assertThat(metadataUpload.getPath()).isEqualTo(file);
        assertThat(metadataUpload.getTrace()).isSameAs(trace);
        assertThat(metadataUpload.getListener()).isSameAs(listener);
        assertThat(metadataUpload.getException()).isSameAs(metadataException);
        assertThat(metadataUpload.toString()).contains(file.getFileName().toString());
    }

    @Test
    void getAndPutTransportTasksHandleMemoryFilesResumeAndChecksums() throws Exception {
        URI location = URI.create("org/example/demo/1.0/demo-1.0.jar");
        GetTask inMemoryGet = new GetTask(location).setDataPath(null, true);
        try (OutputStream output = inMemoryGet.newOutputStream()) {
            output.write("one".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(inMemoryGet.getLocation()).isEqualTo(location);
        assertThat(inMemoryGet.getResumeOffset()).isEqualTo(3L);
        try (OutputStream output = inMemoryGet.newOutputStream(true)) {
            output.write("two".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(inMemoryGet.getDataString()).isEqualTo("onetwo");
        assertThat(inMemoryGet.getDataBytes()).isEqualTo("onetwo".getBytes(StandardCharsets.UTF_8));
        assertThat(inMemoryGet.toString()).isEqualTo("<< " + location);

        inMemoryGet.setChecksum("CRC32", "abc123").setChecksum("SHA-1", "").setChecksum(null, "ignored");
        assertThat(inMemoryGet.getChecksums()).containsExactly(Map.entry("CRC32", "abc123"));
        inMemoryGet.setChecksum("CRC32", null);
        assertThat(inMemoryGet.getChecksums()).isEmpty();

        Path download = tempDirectory.resolve("download.bin");
        Files.writeString(download, "prefix");
        GetTask fileGet = new GetTask(location).setDataPath(download, true);
        assertThat(fileGet.getDataPath()).isEqualTo(download);
        assertThat(fileGet.getResumeOffset()).isEqualTo(6L);
        try (OutputStream output = fileGet.newOutputStream(true)) {
            output.write("-suffix".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(Files.readString(download)).isEqualTo("prefix-suffix");
        try (OutputStream output = fileGet.newOutputStream(false)) {
            output.write("reset".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(Files.readString(download)).isEqualTo("reset");
        assertThat(fileGet.getDataBytes()).isEmpty();
        assertThat(fileGet.getDataString()).isEmpty();

        PutTask putFromBytes = new PutTask(location).setDataString("upload");
        assertThat(putFromBytes.getLocation()).isEqualTo(location);
        assertThat(putFromBytes.getDataLength()).isEqualTo(6L);
        assertThat(putFromBytes.newInputStream().readAllBytes()).isEqualTo("upload".getBytes(StandardCharsets.UTF_8));
        assertThat(putFromBytes.toString()).isEqualTo(">> " + location);
        assertThat(putFromBytes.setDataBytes(null).getDataLength()).isZero();

        Path upload = tempDirectory.resolve("upload.bin");
        Files.writeString(upload, "from-file");
        PutTask putFromFile = new PutTask(location).setDataPath(upload);
        assertThat(putFromFile.getDataPath()).isEqualTo(upload);
        assertThat(putFromFile.getDataLength()).isEqualTo(9L);
        assertThat(putFromFile.newInputStream().readAllBytes()).isEqualTo("from-file".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void abstractTransporterCopiesContentNotifiesListenersAndRejectsUseAfterClose() throws Exception {
        RecordingTransporter transporter = new RecordingTransporter("download-body");
        RecordingTransportListener getListener = new RecordingTransportListener();
        RecordingTransportListener putListener = new RecordingTransportListener();
        URI location = URI.create("repo/resource.txt");

        PeekTask peekTask = new PeekTask(location);
        transporter.peek(peekTask);
        assertThat(transporter.peekedLocations).containsExactly(location);
        assertThat(peekTask.toString()).isEqualTo("?? " + location);

        GetTask getTask = new GetTask(location).setListener(getListener);
        transporter.get(getTask);
        assertThat(getTask.getDataString()).isEqualTo("download-body");
        assertThat(getListener.startedOffset.get()).isEqualTo(0L);
        assertThat(getListener.startedLength.get()).isEqualTo(13L);
        assertThat(getListener.progressed).containsExactly("download-body");

        PutTask putTask = new PutTask(location).setDataString("uploaded-body").setListener(putListener);
        transporter.put(putTask);
        assertThat(transporter.uploaded.toString(StandardCharsets.UTF_8)).isEqualTo("uploaded-body");
        assertThat(putListener.startedOffset.get()).isEqualTo(0L);
        assertThat(putListener.startedLength.get()).isEqualTo(13L);
        assertThat(putListener.progressed).containsExactly("uploaded-body");
        assertThat(transporter.classify(new FileNotFoundException("missing"))).isEqualTo(Transporter.ERROR_NOT_FOUND);
        assertThat(transporter.classify(new IOException("other"))).isEqualTo(Transporter.ERROR_OTHER);

        transporter.close();
        transporter.close();
        assertThat(transporter.closeCount).hasValue(1);
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> transporter.get(new GetTask(location)))
                .withMessageContaining("transporter closed");
    }

    @Test
    void checksumHelpersAndLocationsUseFactoryMetadata() throws Exception {
        byte[] data = "resolver-spi".getBytes(StandardCharsets.UTF_8);
        Path file = tempDirectory.resolve("checksum.txt");
        Files.write(file, data);

        assertThat(CRC32_FACTORY.getName()).isEqualTo("CRC32");
        assertThat(CRC32_FACTORY.getFileExtension()).isEqualTo("crc32");
        assertThat(ChecksumAlgorithmHelper.calculate(data, List.of(CRC32_FACTORY)))
                .containsExactly(Map.entry("CRC32", expectedCrc32(data)));
        assertThat(ChecksumAlgorithmHelper.calculate(file, List.of(CRC32_FACTORY)))
                .containsExactly(Map.entry("CRC32", expectedCrc32(data)));

        RepositoryLayout.ChecksumLocation explicit = new RepositoryLayout.ChecksumLocation(
                URI.create("demo-1.0.jar.crc32"), CRC32_FACTORY);
        assertThat(explicit.getLocation()).isEqualTo(URI.create("demo-1.0.jar.crc32"));
        assertThat(explicit.getChecksumAlgorithmFactory()).isSameAs(CRC32_FACTORY);
        assertThat(explicit.toString()).isEqualTo("demo-1.0.jar.crc32 (CRC32)");

        RepositoryLayout.ChecksumLocation derived = RepositoryLayout.ChecksumLocation.forLocation(
                URI.create("demo-1.0.jar"), CRC32_FACTORY);
        assertThat(derived.getLocation()).isEqualTo(URI.create("demo-1.0.jar.crc32"));
        assertThatThrownBy(() -> RepositoryLayout.ChecksumLocation.forLocation(
                URI.create("https://repo.example/demo.jar"), CRC32_FACTORY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relative");
        assertThatThrownBy(() -> RepositoryLayout.ChecksumLocation.forLocation(
                URI.create("demo.jar?download=true"), CRC32_FACTORY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query");
        assertThatThrownBy(() -> RepositoryLayout.ChecksumLocation.forLocation(
                URI.create("demo.jar#fragment"), CRC32_FACTORY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fragment");
        assertThatNullPointerException().isThrownBy(() -> new RepositoryLayout.ChecksumLocation(null, CRC32_FACTORY));
        assertThatNullPointerException().isThrownBy(
                () -> new RepositoryLayout.ChecksumLocation(URI.create("demo.jar"), null));
    }

    @Test
    void fileProcessorImplementationsCanReportProgressAndManageChecksumFiles() throws Exception {
        FileProcessor processor = new LocalFileProcessor();
        File directory = tempDirectory.resolve("processor/nested").toFile();
        File textFile = new File(directory, "text.txt");
        File binaryFile = new File(directory, "binary.bin");
        File copiedFile = tempDirectory.resolve("processor/copy.bin").toFile();
        File movedFile = tempDirectory.resolve("processor/moved.bin").toFile();
        File checksumFile = tempDirectory.resolve("processor/artifact.jar.sha1").toFile();
        List<String> progressedChunks = new ArrayList<>();

        assertThat(processor.mkdirs(directory)).isTrue();
        processor.write(textFile, "resolver-spi file processor");
        processor.write(binaryFile, new ByteArrayInputStream("abcdef".getBytes(StandardCharsets.UTF_8)));
        long copiedBytes = processor.copy(binaryFile, copiedFile, data -> {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            progressedChunks.add(new String(bytes, StandardCharsets.UTF_8));
        });
        processor.writeChecksum(checksumFile, "  abc123  ");
        processor.move(copiedFile, movedFile);
        processor.copy(movedFile, copiedFile);

        assertThat(Files.readString(textFile.toPath())).isEqualTo("resolver-spi file processor");
        assertThat(copiedBytes).isEqualTo(6L);
        assertThat(progressedChunks).containsExactly("abcd", "ef");
        assertThat(Files.readAllBytes(copiedFile.toPath())).isEqualTo("abcdef".getBytes(StandardCharsets.UTF_8));
        assertThat(Files.readAllBytes(movedFile.toPath())).isEqualTo("abcdef".getBytes(StandardCharsets.UTF_8));
        assertThat(Files.readString(checksumFile.toPath())).isEqualTo("  abc123  " + System.lineSeparator());
        assertThat(processor.readChecksum(checksumFile)).isEqualTo("abc123");
    }

    @Test
    void serviceProviderInterfacesCanBeImplementedAndComposed() throws Exception {
        Artifact artifact = artifact();
        Metadata metadata = metadata();
        RemoteRepository repository = repository();
        ArtifactDownload artifactDownload = new ArtifactDownload(artifact, "context", (Path) null, "fail");
        MetadataDownload metadataDownload = new MetadataDownload(metadata, "context", (Path) null, "fail");
        ArtifactUpload artifactUpload = new ArtifactUpload(artifact, (Path) null);
        MetadataUpload metadataUpload = new MetadataUpload(metadata, (Path) null);

        InMemoryProvidedChecksumsSource providedSource = new InMemoryProvidedChecksumsSource(
                Map.of("CRC32", "provided"));
        assertThat(providedSource.getProvidedArtifactChecksums(
                null, artifactDownload, repository, List.of(CRC32_FACTORY)))
                .containsExactly(Map.entry("CRC32", "provided"));

        InMemoryTrustedChecksumsSource trustedSource = new InMemoryTrustedChecksumsSource();
        TrustedChecksumsSource.Writer writer = trustedSource.getTrustedArtifactChecksumsWriter(null);
        writer.addTrustedArtifactChecksums(artifact, repository, List.of(CRC32_FACTORY), Map.of("CRC32", "trusted"));
        assertThat(trustedSource.getTrustedArtifactChecksums(null, artifact, repository, List.of(CRC32_FACTORY)))
                .containsExactly(Map.entry("CRC32", "trusted"));

        ExtensionAwareRepositoryFilter filter = new ExtensionAwareRepositoryFilter("jar");
        assertThat(filter.acceptArtifact(repository, artifact).isAccepted()).isTrue();
        assertThat(filter.acceptArtifact(repository, new DefaultArtifact("org.example", "demo", "", "pom", "1.0")))
                .extracting(RemoteRepositoryFilter.Result::isAccepted, RemoteRepositoryFilter.Result::reasoning)
                .containsExactly(false, "extension pom is not accepted");
        assertThat(filter.acceptMetadata(repository, metadata))
                .extracting(RemoteRepositoryFilter.Result::isAccepted, RemoteRepositoryFilter.Result::reasoning)
                .containsExactly(true, "metadata accepted");

        RecordingRepositoryConnector connector = new RecordingRepositoryConnector();
        connector.get(List.of(artifactDownload), List.of(metadataDownload));
        connector.put(List.of(artifactUpload), List.of(metadataUpload));
        connector.close();
        assertThat(connector.artifactDownloads).containsExactly(artifactDownload);
        assertThat(connector.metadataDownloads).containsExactly(metadataDownload);
        assertThat(connector.artifactUploads).containsExactly(artifactUpload);
        assertThat(connector.metadataUploads).containsExactly(metadataUpload);
        assertThat(connector.closed).isTrue();

        RecordingSyncContextFactory syncContextFactory = new RecordingSyncContextFactory();
        try (SyncContext syncContext = syncContextFactory.newInstance(null, true)) {
            syncContext.acquire(List.of(artifact), List.of(metadata));
        }
        assertThat(syncContextFactory.created).hasValue(1);
        assertThat(syncContextFactory.lastShared).isTrue();
        assertThat(syncContextFactory.context.acquiredArtifacts).containsExactly(artifact);
        assertThat(syncContextFactory.context.acquiredMetadata).containsExactly(metadata);
        assertThat(syncContextFactory.context.closed).isTrue();

        RecordingChecksumPolicy checksumPolicy = new RecordingChecksumPolicy();
        assertThat(checksumPolicy.onChecksumMatch("CRC32", ChecksumPolicy.ChecksumKind.PROVIDED)).isTrue();
        checksumPolicy.onChecksumMismatch("CRC32", ChecksumPolicy.ChecksumKind.REMOTE_EXTERNAL,
                new ChecksumFailureException("mismatch"));
        checksumPolicy.onChecksumError("CRC32", ChecksumPolicy.ChecksumKind.REMOTE_INCLUDED,
                new ChecksumFailureException("error"));
        checksumPolicy.onNoMoreChecksums();
        checksumPolicy.onTransferRetry();
        assertThat(checksumPolicy.onTransferChecksumFailure(new ChecksumFailureException("transfer"))).isFalse();
        assertThat(checksumPolicy.events).containsExactly(
                "match:CRC32:PROVIDED",
                "mismatch:CRC32:REMOTE_EXTERNAL",
                "error:CRC32:REMOTE_INCLUDED",
                "no-more",
                "retry",
                "transfer");
    }

    private static Artifact artifact() {
        return new DefaultArtifact("org.example", "demo", "", "jar", "1.0");
    }

    private static Metadata metadata() {
        return new DefaultMetadata("org.example", "demo", "1.0", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);
    }

    private static RemoteRepository repository() {
        return new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build();
    }

    private static String expectedCrc32(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data, 0, data.length);
        return Long.toHexString(crc32.getValue());
    }

    private static final class Crc32ChecksumAlgorithmFactory extends ChecksumAlgorithmFactorySupport {
        private Crc32ChecksumAlgorithmFactory() {
            super("CRC32", "crc32");
        }

        @Override
        public ChecksumAlgorithm getAlgorithm() {
            return new Crc32ChecksumAlgorithm();
        }
    }

    private static final class Crc32ChecksumAlgorithm implements ChecksumAlgorithm {
        private final CRC32 crc32 = new CRC32();

        @Override
        public void update(ByteBuffer input) {
            crc32.update(input);
        }

        @Override
        public String checksum() {
            return Long.toHexString(crc32.getValue());
        }
    }

    private static final class RecordingTransporter extends AbstractTransporter {
        private final byte[] payload;
        private final List<URI> peekedLocations = new ArrayList<>();
        private final ByteArrayOutputStream uploaded = new ByteArrayOutputStream();
        private final AtomicInteger closeCount = new AtomicInteger();

        private RecordingTransporter(String payload) {
            this.payload = payload.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public int classify(Throwable error) {
            return error instanceof FileNotFoundException ? ERROR_NOT_FOUND : ERROR_OTHER;
        }

        @Override
        protected void implPeek(PeekTask task) {
            peekedLocations.add(task.getLocation());
        }

        @Override
        protected void implGet(GetTask task) throws IOException, TransferCancelledException {
            utilGet(task, new ByteArrayInputStream(payload), true, payload.length, false);
        }

        @Override
        protected void implPut(PutTask task) throws IOException, TransferCancelledException {
            utilPut(task, uploaded, false);
        }

        @Override
        protected void implClose() {
            closeCount.incrementAndGet();
        }
    }

    private static final class RecordingTransportListener extends TransportListener {
        private final AtomicReference<Long> startedOffset = new AtomicReference<>();
        private final AtomicReference<Long> startedLength = new AtomicReference<>();
        private final List<String> progressed = new ArrayList<>();

        @Override
        public void transportStarted(long resumeOffset, long dataLength) {
            startedOffset.set(resumeOffset);
            startedLength.set(dataLength);
        }

        @Override
        public void transportProgressed(ByteBuffer data) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            progressed.add(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static final class LocalFileProcessor implements FileProcessor {
        @Override
        public boolean mkdirs(File directory) {
            return directory.mkdirs() || directory.isDirectory();
        }

        @Override
        public void write(File target, String data) throws IOException {
            ensureParentDirectory(target);
            Files.writeString(target.toPath(), data, StandardCharsets.UTF_8);
        }

        @Override
        public void write(File target, InputStream data) throws IOException {
            ensureParentDirectory(target);
            Files.copy(data, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void move(File source, File target) throws IOException {
            ensureParentDirectory(target);
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void copy(File source, File target) throws IOException {
            copy(source, target, null);
        }

        @Override
        public long copy(File source, File target, FileProcessor.ProgressListener listener) throws IOException {
            ensureParentDirectory(target);
            long copiedBytes = 0L;
            byte[] buffer = new byte[4];
            try (InputStream input = Files.newInputStream(source.toPath());
                    OutputStream output = Files.newOutputStream(target.toPath())) {
                int bytesRead;
                while ((bytesRead = input.read(buffer)) >= 0) {
                    if (bytesRead == 0) {
                        continue;
                    }
                    output.write(buffer, 0, bytesRead);
                    copiedBytes += bytesRead;
                    if (listener != null) {
                        listener.progressed(ByteBuffer.wrap(buffer, 0, bytesRead).asReadOnlyBuffer());
                    }
                }
            }
            return copiedBytes;
        }

        @Override
        public String readChecksum(File source) throws IOException {
            return Files.readString(source.toPath(), StandardCharsets.UTF_8).trim();
        }

        @Override
        public void writeChecksum(File target, String checksum) throws IOException {
            write(target, checksum + System.lineSeparator());
        }

        private static void ensureParentDirectory(File target) throws IOException {
            File parent = target.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
        }
    }

    private static final class NoOpTransferListener implements TransferListener {
        @Override
        public void transferInitiated(TransferEvent event) {
        }

        @Override
        public void transferStarted(TransferEvent event) {
        }

        @Override
        public void transferProgressed(TransferEvent event) {
        }

        @Override
        public void transferCorrupted(TransferEvent event) {
        }

        @Override
        public void transferSucceeded(TransferEvent event) {
        }

        @Override
        public void transferFailed(TransferEvent event) {
        }
    }

    private static final class InMemoryProvidedChecksumsSource implements ProvidedChecksumsSource {
        private final Map<String, String> checksums;

        private InMemoryProvidedChecksumsSource(Map<String, String> checksums) {
            this.checksums = checksums;
        }

        @Override
        public Map<String, String> getProvidedArtifactChecksums(RepositorySystemSession session,
                ArtifactDownload transfer, RemoteRepository repository,
                List<ChecksumAlgorithmFactory> checksumAlgorithmFactories) {
            return checksums;
        }
    }

    private static final class InMemoryTrustedChecksumsSource implements TrustedChecksumsSource {
        private final Map<String, Map<String, String>> trustedChecksums = new HashMap<>();

        @Override
        public Map<String, String> getTrustedArtifactChecksums(RepositorySystemSession session, Artifact artifact,
                ArtifactRepository repository, List<ChecksumAlgorithmFactory> checksumAlgorithmFactories) {
            return trustedChecksums.getOrDefault(key(artifact, repository), Map.of());
        }

        @Override
        public Writer getTrustedArtifactChecksumsWriter(RepositorySystemSession session) {
            return (artifact, repository, checksumAlgorithmFactories, checksums) -> trustedChecksums.put(
                    key(artifact, repository), Map.copyOf(checksums));
        }

        private static String key(Artifact artifact, ArtifactRepository repository) {
            return artifact + "@" + repository.getId();
        }
    }

    private static final class ExtensionAwareRepositoryFilter implements RemoteRepositoryFilter {
        private final String acceptedExtension;

        private ExtensionAwareRepositoryFilter(String acceptedExtension) {
            this.acceptedExtension = acceptedExtension;
        }

        @Override
        public Result acceptArtifact(RemoteRepository repository, Artifact artifact) {
            if (acceptedExtension.equals(artifact.getExtension())) {
                return new FilterResult(true, "extension accepted");
            }
            return new FilterResult(false, "extension " + artifact.getExtension() + " is not accepted");
        }

        @Override
        public Result acceptMetadata(RemoteRepository repository, Metadata metadata) {
            return new FilterResult(true, "metadata accepted");
        }
    }

    private record FilterResult(boolean isAccepted, String reasoning) implements RemoteRepositoryFilter.Result {
    }

    private static final class RecordingRepositoryConnector implements RepositoryConnector {
        private final List<ArtifactDownload> artifactDownloads = new ArrayList<>();
        private final List<MetadataDownload> metadataDownloads = new ArrayList<>();
        private final List<ArtifactUpload> artifactUploads = new ArrayList<>();
        private final List<MetadataUpload> metadataUploads = new ArrayList<>();
        private boolean closed;

        @Override
        public void get(Collection<? extends ArtifactDownload> artifactDownloads,
                Collection<? extends MetadataDownload> metadataDownloads) {
            this.artifactDownloads.addAll(artifactDownloads);
            this.metadataDownloads.addAll(metadataDownloads);
        }

        @Override
        public void put(Collection<? extends ArtifactUpload> artifactUploads,
                Collection<? extends MetadataUpload> metadataUploads) {
            this.artifactUploads.addAll(artifactUploads);
            this.metadataUploads.addAll(metadataUploads);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class RecordingSyncContextFactory implements SyncContextFactory {
        private final AtomicInteger created = new AtomicInteger();
        private RecordingSyncContext context;
        private boolean lastShared;

        @Override
        public SyncContext newInstance(RepositorySystemSession session, boolean shared) {
            created.incrementAndGet();
            lastShared = shared;
            context = new RecordingSyncContext();
            return context;
        }
    }

    private static final class RecordingSyncContext implements SyncContext {
        private final List<Artifact> acquiredArtifacts = new ArrayList<>();
        private final List<Metadata> acquiredMetadata = new ArrayList<>();
        private boolean closed;

        @Override
        public void acquire(Collection<? extends Artifact> artifacts, Collection<? extends Metadata> metadata) {
            acquiredArtifacts.addAll(artifacts);
            acquiredMetadata.addAll(metadata);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class RecordingChecksumPolicy implements ChecksumPolicy {
        private final List<String> events = new ArrayList<>();

        @Override
        public boolean onChecksumMatch(String algorithm, ChecksumKind checksumKind) {
            events.add("match:" + algorithm + ':' + checksumKind);
            return true;
        }

        @Override
        public void onChecksumMismatch(String algorithm, ChecksumKind checksumKind,
                ChecksumFailureException exception) {
            events.add("mismatch:" + algorithm + ':' + checksumKind);
        }

        @Override
        public void onChecksumError(String algorithm, ChecksumKind checksumKind, ChecksumFailureException exception) {
            events.add("error:" + algorithm + ':' + checksumKind);
        }

        @Override
        public void onNoMoreChecksums() {
            events.add("no-more");
        }

        @Override
        public void onTransferRetry() {
            events.add("retry");
        }

        @Override
        public boolean onTransferChecksumFailure(ChecksumFailureException exception) {
            events.add("transfer");
            return false;
        }
    }
}
