/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_aether.aether_spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Aether_spiTest {
    @TempDir
    File temporaryDirectory;

    @Test
    void artifactTransfersRetainRepositoryRequestAndFailureState() {
        Artifact artifact = new DefaultArtifact("org.example:demo:jar:1.0.0");
        File artifactFile = new File(temporaryDirectory, "demo-1.0.0.jar");
        RemoteRepository repository = new RemoteRepository.Builder("central", "default",
                "https://repo.example.org/maven2/").build();
        RequestTrace trace = new RequestTrace("root-request").newChild("artifact-download");
        RecordingTransferListener listener = new RecordingTransferListener();
        ArtifactTransferException failure = new ArtifactTransferException(artifact, repository, "download failed");

        ArtifactDownload download = new ArtifactDownload(artifact, "project", artifactFile, "fail")
                .setExistenceCheck(true)
                .setSupportedContexts(Arrays.asList("project", "plugin"))
                .setRepositories(Collections.singletonList(repository))
                .setTrace(trace)
                .setListener(listener)
                .setException(failure);

        assertThat(download.getArtifact()).isSameAs(artifact);
        assertThat(download.getFile()).isSameAs(artifactFile);
        assertThat(download.getChecksumPolicy()).isEqualTo("fail");
        assertThat(download.getRequestContext()).isEqualTo("project");
        assertThat(download.isExistenceCheck()).isTrue();
        assertThat(download.getSupportedContexts()).containsExactly("project", "plugin");
        assertThat(download.getRepositories()).containsExactly(repository);
        assertThat(download.getTrace()).isSameAs(trace);
        assertThat(download.getListener()).isSameAs(listener);
        assertThat(download.getException()).isSameAs(failure);
        assertThat(download.toString()).contains("org.example:demo:jar:1.0.0", "?", artifactFile.getName());

        download.setChecksumPolicy(null).setRequestContext(null).setSupportedContexts(Collections.emptyList())
                .setRepositories(null);

        assertThat(download.getChecksumPolicy()).isEmpty();
        assertThat(download.getRequestContext()).isEmpty();
        assertThat(download.getSupportedContexts()).containsExactly("");
        assertThat(download.getRepositories()).isEmpty();

        ArtifactUpload upload = new ArtifactUpload()
                .setArtifact(artifact)
                .setFile(artifactFile)
                .setTrace(trace)
                .setListener(listener)
                .setException(failure);

        assertThat(upload.getArtifact()).isSameAs(artifact);
        assertThat(upload.getFile()).isSameAs(artifactFile);
        assertThat(upload.getTrace()).isSameAs(trace);
        assertThat(upload.getListener()).isSameAs(listener);
        assertThat(upload.getException()).isSameAs(failure);
        assertThat(upload.toString()).contains("org.example:demo:jar:1.0.0", artifactFile.getName());
    }

    @Test
    void metadataTransfersRetainRepositoryRequestAndFailureState() {
        Metadata metadata = new DefaultMetadata("org.example", "demo", "1.0.0", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);
        File metadataFile = new File(temporaryDirectory, "maven-metadata.xml");
        RemoteRepository repository = new RemoteRepository.Builder("internal", "default",
                "https://repo.example.org/internal/").build();
        RequestTrace trace = RequestTrace.newChild(new RequestTrace("root-request"), "metadata-download");
        RecordingTransferListener listener = new RecordingTransferListener();
        MetadataTransferException failure = new MetadataTransferException(metadata, repository, "metadata unavailable");

        MetadataDownload download = new MetadataDownload(metadata, "metadata", metadataFile, "warn")
                .setRepositories(Collections.singletonList(repository))
                .setTrace(trace)
                .setListener(listener)
                .setException(failure);

        assertThat(download.getMetadata()).isSameAs(metadata);
        assertThat(download.getFile()).isSameAs(metadataFile);
        assertThat(download.getChecksumPolicy()).isEqualTo("warn");
        assertThat(download.getRequestContext()).isEqualTo("metadata");
        assertThat(download.getRepositories()).containsExactly(repository);
        assertThat(download.getTrace()).isSameAs(trace);
        assertThat(download.getListener()).isSameAs(listener);
        assertThat(download.getException()).isSameAs(failure);
        assertThat(download.toString()).contains("org.example", "demo", metadataFile.getName());

        download.setChecksumPolicy(null).setRequestContext(null).setRepositories(null);

        assertThat(download.getChecksumPolicy()).isEmpty();
        assertThat(download.getRequestContext()).isEmpty();
        assertThat(download.getRepositories()).isEmpty();

        MetadataUpload upload = new MetadataUpload(metadata, metadataFile)
                .setTrace(trace)
                .setListener(listener)
                .setException(failure);

        assertThat(upload.getMetadata()).isSameAs(metadata);
        assertThat(upload.getFile()).isSameAs(metadataFile);
        assertThat(upload.getTrace()).isSameAs(trace);
        assertThat(upload.getListener()).isSameAs(listener);
        assertThat(upload.getException()).isSameAs(failure);
        assertThat(upload.toString()).contains("maven-metadata.xml");
    }

    @Test
    void getTaskSupportsMemoryDownloadsFilesResumeAndChecksumBookkeeping() throws IOException {
        URI location = URI.create("org/example/demo/1.0.0/demo-1.0.0.jar");
        CountingTransportListener listener = new CountingTransportListener();
        GetTask task = new GetTask(location).setListener(listener);

        assertThat(task.getLocation()).isEqualTo(location);
        assertThat(task.getListener()).isSameAs(listener);
        assertThat(task.toString()).isEqualTo("<< " + location);
        assertThat(task.getDataBytes()).isEmpty();
        assertThat(task.getDataString()).isEmpty();
        assertThat(task.getResumeOffset()).isZero();

        try (OutputStream outputStream = task.newOutputStream()) {
            outputStream.write("first".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(task.getDataBytes()).isEqualTo("first".getBytes(StandardCharsets.UTF_8));
        assertThat(task.getDataString()).isEqualTo("first");

        try (OutputStream outputStream = task.newOutputStream(false)) {
            outputStream.write("second".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(task.getDataString()).isEqualTo("second");

        task.setChecksum("SHA-1", "abcdef").setChecksum("MD5", "123456").setChecksum("MD5", "");
        assertThat(task.getChecksums()).containsEntry("SHA-1", "abcdef").doesNotContainKey("MD5");
        task.setChecksum(null, "ignored");
        assertThat(task.getChecksums()).containsOnlyKeys("SHA-1");

        File outputFile = new File(temporaryDirectory, "downloaded.bin");
        Files.write(outputFile.toPath(), "base".getBytes(StandardCharsets.UTF_8));
        task.setDataFile(outputFile, true);
        assertThat(task.getDataFile()).isEqualTo(outputFile);
        assertThat(task.getResumeOffset()).isEqualTo(4L);
        try (OutputStream outputStream = task.newOutputStream(true)) {
            outputStream.write("-tail".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8)).isEqualTo("base-tail");

        task.setListener(null);
        assertThat(task.getListener()).isNotNull();
        assertThrows(IllegalArgumentException.class, () -> new GetTask(null));
    }

    @Test
    void putTaskStreamsBytesStringsAndFiles() throws IOException {
        URI location = URI.create("org/example/demo/1.0.0/demo-1.0.0.pom");
        CountingTransportListener listener = new CountingTransportListener();
        PutTask task = new PutTask(location).setListener(listener);

        assertThat(task.getLocation()).isEqualTo(location);
        assertThat(task.getListener()).isSameAs(listener);
        assertThat(task.toString()).isEqualTo(">> " + location);
        assertThat(task.getDataLength()).isZero();
        assertThat(readUtf8(task.newInputStream())).isEmpty();

        task.setDataString("metadata \u00E4\u00F6\u00FC");
        assertThat(task.getDataFile()).isNull();
        assertThat(task.getDataLength()).isEqualTo("metadata \u00E4\u00F6\u00FC".getBytes(StandardCharsets.UTF_8).length);
        assertThat(readUtf8(task.newInputStream())).isEqualTo("metadata \u00E4\u00F6\u00FC");

        byte[] bytes = new byte[] {1, 2, 3, 4};
        task.setDataBytes(bytes);
        assertThat(task.getDataLength()).isEqualTo(4L);
        assertThat(task.newInputStream().readAllBytes()).isEqualTo(bytes);

        task.setDataBytes(null);
        assertThat(task.getDataLength()).isZero();
        assertThat(task.newInputStream().readAllBytes()).isEmpty();

        File sourceFile = new File(temporaryDirectory, "upload.txt");
        Files.write(sourceFile.toPath(), "file payload".getBytes(StandardCharsets.UTF_8));
        task.setDataFile(sourceFile);
        assertThat(task.getDataFile()).isEqualTo(sourceFile);
        assertThat(task.getDataLength()).isEqualTo(sourceFile.length());
        assertThat(readUtf8(task.newInputStream())).isEqualTo("file payload");

        task.setListener(null);
        assertThat(task.getListener()).isNotNull();
        assertThrows(IllegalArgumentException.class, () -> new PutTask(null));
    }

    @Test
    void abstractTransporterCopiesPayloadsNotifiesListenersAndRejectsWorkAfterClose() throws Exception {
        InMemoryTransporter transporter = new InMemoryTransporter("downloaded-content");
        CountingTransportListener downloadListener = new CountingTransportListener();
        GetTask download = new GetTask(URI.create("content.bin")).setListener(downloadListener);

        transporter.get(download);

        assertThat(download.getDataString()).isEqualTo("downloaded-content");
        assertThat(downloadListener.started).isEqualTo(1);
        assertThat(downloadListener.startedOffset).isZero();
        assertThat(downloadListener.startedLength)
                .isEqualTo("downloaded-content".getBytes(StandardCharsets.UTF_8).length);
        assertThat(downloadListener.progressedBytes.toString(StandardCharsets.UTF_8.name()))
                .isEqualTo("downloaded-content");

        CountingTransportListener uploadListener = new CountingTransportListener();
        PutTask upload = new PutTask(URI.create("uploaded.bin"))
                .setDataString("uploaded-content")
                .setListener(uploadListener);
        transporter.put(upload);

        assertThat(transporter.uploadedContent()).isEqualTo("uploaded-content");
        assertThat(uploadListener.started).isEqualTo(1);
        assertThat(uploadListener.startedLength).isEqualTo("uploaded-content".getBytes(StandardCharsets.UTF_8).length);
        assertThat(uploadListener.progressedBytes.toString(StandardCharsets.UTF_8.name()))
                .isEqualTo("uploaded-content");

        PeekTask peek = new PeekTask(URI.create("peeked.bin"));
        transporter.peek(peek);
        assertThat(peek.toString()).isEqualTo("?? " + peek.getLocation());
        assertThat(transporter.peekedLocation).isEqualTo(peek.getLocation());
        assertThat(transporter.classify(new FileNotFoundException("missing"))).isEqualTo(Transporter.ERROR_NOT_FOUND);
        assertThat(transporter.classify(new IOException("other"))).isEqualTo(Transporter.ERROR_OTHER);

        transporter.close();
        transporter.close();
        assertThat(transporter.closeCount).isEqualTo(1);
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> transporter.get(new GetTask(URI.create("after-close.bin"))))
                .withMessageContaining("transporter closed")
                .withMessageContaining("after-close.bin");
    }

    @Test
    void abstractTransporterCanAppendResumedDownloadsToExistingFiles() throws Exception {
        InMemoryTransporter transporter = new InMemoryTransporter("-suffix");
        File target = new File(temporaryDirectory, "resume.bin");
        Files.write(target.toPath(), "prefix".getBytes(StandardCharsets.UTF_8));
        CountingTransportListener listener = new CountingTransportListener();
        GetTask download = new GetTask(URI.create("resume.bin")).setDataFile(target, true).setListener(listener);

        transporter.get(download);

        assertThat(new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8)).isEqualTo("prefix-suffix");
        assertThat(listener.startedOffset).isEqualTo(6L);
        assertThat(listener.startedLength).isEqualTo("-suffix".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void transportListenerCancellationStopsTransfersBeforePayloadCopy() throws Exception {
        InMemoryTransporter transporter = new InMemoryTransporter("downloaded-content");
        CancellingTransportListener downloadListener = new CancellingTransportListener();
        GetTask download = new GetTask(URI.create("cancelled-download.bin")).setListener(downloadListener);

        assertThatExceptionOfType(TransferCancelledException.class)
                .isThrownBy(() -> transporter.get(download))
                .withMessageContaining("cancelled before transfer");

        assertThat(downloadListener.started).isEqualTo(1);
        assertThat(download.getDataString()).isEmpty();

        CancellingTransportListener uploadListener = new CancellingTransportListener();
        PutTask upload = new PutTask(URI.create("cancelled-upload.bin"))
                .setDataString("uploaded-content")
                .setListener(uploadListener);

        assertThatExceptionOfType(TransferCancelledException.class)
                .isThrownBy(() -> transporter.put(upload))
                .withMessageContaining("cancelled before transfer");

        assertThat(uploadListener.started).isEqualTo(1);
        assertThat(transporter.uploadedContent()).isEmpty();
    }

    @Test
    void repositoryLayoutChecksumsNormalizeLocationsAndRejectInvalidInput() {
        RepositoryLayout.Checksum generated = RepositoryLayout.Checksum.forLocation(
                URI.create("org/example/demo/1.0.0/demo-1.0.0.jar"), "SHA-256");

        assertThat(generated.getAlgorithm()).isEqualTo("SHA-256");
        assertThat(generated.getLocation()).isEqualTo(URI.create("org/example/demo/1.0.0/demo-1.0.0.jar.sha256"));
        assertThat(generated.toString()).isEqualTo("org/example/demo/1.0.0/demo-1.0.0.jar.sha256 (SHA-256)");

        RepositoryLayout.Checksum explicit = new RepositoryLayout.Checksum("MD5", URI.create("checksums/demo.md5"));
        assertThat(explicit.getAlgorithm()).isEqualTo("MD5");
        assertThat(explicit.getLocation()).isEqualTo(URI.create("checksums/demo.md5"));

        assertThrows(IllegalArgumentException.class,
                () -> new RepositoryLayout.Checksum("", URI.create("checksums/demo.sha1")));
        assertThrows(IllegalArgumentException.class,
                () -> new RepositoryLayout.Checksum("SHA-1", URI.create("https://repo.example.org/demo.sha1")));
        assertThrows(IllegalArgumentException.class,
                () -> RepositoryLayout.Checksum.forLocation(URI.create("demo.jar?raw=true"), "SHA-1"));
        assertThrows(IllegalArgumentException.class,
                () -> RepositoryLayout.Checksum.forLocation(URI.create("demo.jar#fragment"), "SHA-1"));
    }

    @Test
    void checksumPolicyCallbacksCanDistinguishOfficialMatchesFailuresAndRetries() {
        StrictChecksumPolicy policy = new StrictChecksumPolicy();

        assertThat(policy.onChecksumMatch("SHA-1", ChecksumPolicy.KIND_UNOFFICIAL)).isFalse();
        assertThat(policy.onChecksumMatch("SHA-256", ChecksumPolicy.KIND_UNOFFICIAL + 1)).isTrue();
        assertThat(policy.acceptedMatches).containsExactly("SHA-256");

        ChecksumFailureException mismatch = new ChecksumFailureException("expected-sha1", "actual-sha1");
        ChecksumFailureException thrownMismatch = assertThrows(ChecksumFailureException.class,
                () -> policy.onChecksumMismatch("SHA-1", 1, mismatch));
        assertThat(thrownMismatch).isSameAs(mismatch);
        assertThat(thrownMismatch.getExpected()).isEqualTo("expected-sha1");
        assertThat(thrownMismatch.getActual()).isEqualTo("actual-sha1");
        assertThat(policy.mismatches).containsExactly("SHA-1:1");

        ChecksumFailureException checksumError = new ChecksumFailureException("metadata checksum unavailable");
        policy.onChecksumError("MD5", ChecksumPolicy.KIND_UNOFFICIAL, checksumError);
        ChecksumFailureException noMoreChecksums = assertThrows(ChecksumFailureException.class,
                policy::onNoMoreChecksums);
        assertThat(noMoreChecksums).isSameAs(checksumError);
        assertThat(policy.errors).containsExactly("MD5:" + ChecksumPolicy.KIND_UNOFFICIAL);

        ChecksumFailureException retryableFailure = new ChecksumFailureException(true,
                "temporary checksum stream failure", null);
        ChecksumFailureException fatalFailure = new ChecksumFailureException(false,
                "permanent checksum stream failure", null);
        assertThat(policy.onTransferChecksumFailure(retryableFailure)).isTrue();
        policy.onTransferRetry();
        assertThat(policy.onTransferChecksumFailure(fatalFailure)).isFalse();
        assertThat(policy.transferFailures).containsExactly(retryableFailure, fatalFailure);
        assertThat(policy.retryCount).isEqualTo(1);
    }

    @Test
    void nullLoggerFactoryAlwaysSuppliesNoOpLogger() {
        Logger defaultLogger = NullLoggerFactory.INSTANCE.getLogger("any.category");

        assertThat(defaultLogger).isSameAs(NullLoggerFactory.LOGGER);
        assertThat(defaultLogger.isDebugEnabled()).isFalse();
        assertThat(defaultLogger.isWarnEnabled()).isFalse();
        defaultLogger.debug("debug message");
        defaultLogger.debug("debug message", new RuntimeException("ignored"));
        defaultLogger.warn("warning message");
        defaultLogger.warn("warning message", new RuntimeException("ignored"));

        Logger customLogger = new RecordingLogger();
        LoggerFactory customFactory = name -> customLogger;
        LoggerFactory nullReturningFactory = name -> null;

        assertThat(NullLoggerFactory.getSafeLogger(null, Aether_spiTest.class)).isSameAs(NullLoggerFactory.LOGGER);
        assertThat(NullLoggerFactory.getSafeLogger(nullReturningFactory, Aether_spiTest.class))
                .isSameAs(NullLoggerFactory.LOGGER);
        assertThat(NullLoggerFactory.getSafeLogger(customFactory, Aether_spiTest.class)).isSameAs(customLogger);
    }

    @Test
    void serviceImplementationsCanUseLocatorForSingleAndMultipleServices() {
        DemoService service = new DemoService();
        DemoDependency primary = new DemoDependency("primary");
        DemoDependency secondary = new DemoDependency("secondary");
        DemoServiceLocator locator = new DemoServiceLocator(primary, Arrays.asList(primary, secondary));

        service.initService(locator);

        assertThat(service.primaryDependency).isSameAs(primary);
        assertThat(service.allDependencies).containsExactly(primary, secondary);
        assertThat(service.allDependencies).extracting(DemoDependency::name).containsExactly("primary", "secondary");
    }

    private static String readUtf8(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static final class InMemoryTransporter extends AbstractTransporter {
        private final byte[] downloadPayload;
        private final ByteArrayOutputStream uploadedBytes = new ByteArrayOutputStream();
        private URI peekedLocation;
        private int closeCount;

        private InMemoryTransporter(String downloadPayload) {
            this.downloadPayload = downloadPayload.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public int classify(Throwable error) {
            if (error instanceof FileNotFoundException) {
                return ERROR_NOT_FOUND;
            }
            return ERROR_OTHER;
        }

        @Override
        protected void implPeek(PeekTask task) {
            peekedLocation = task.getLocation();
        }

        @Override
        protected void implGet(GetTask task) throws Exception {
            utilGet(task, new ByteArrayInputStream(downloadPayload), true, downloadPayload.length, true);
        }

        @Override
        protected void implPut(PutTask task) throws Exception {
            utilPut(task, uploadedBytes, false);
        }

        @Override
        protected void implClose() {
            closeCount++;
        }

        private String uploadedContent() throws IOException {
            return uploadedBytes.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static final class CountingTransportListener extends TransportListener {
        private final ByteArrayOutputStream progressedBytes = new ByteArrayOutputStream();
        private int started;
        private long startedOffset;
        private long startedLength;

        @Override
        public void transportStarted(long resumeOffset, long dataLength) {
            started++;
            startedOffset = resumeOffset;
            startedLength = dataLength;
        }

        @Override
        public void transportProgressed(ByteBuffer data) throws TransferCancelledException {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            progressedBytes.write(chunk, 0, chunk.length);
        }
    }

    private static final class CancellingTransportListener extends TransportListener {
        private int started;

        @Override
        public void transportStarted(long resumeOffset, long dataLength) throws TransferCancelledException {
            started++;
            throw new TransferCancelledException("cancelled before transfer");
        }
    }

    private static final class StrictChecksumPolicy implements ChecksumPolicy {
        private final List<String> acceptedMatches = new ArrayList<>();
        private final List<String> mismatches = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<ChecksumFailureException> transferFailures = new ArrayList<>();
        private ChecksumFailureException lastError;
        private int retryCount;

        @Override
        public boolean onChecksumMatch(String algorithm, int kind) {
            if (kind == KIND_UNOFFICIAL) {
                return false;
            }
            acceptedMatches.add(algorithm);
            return true;
        }

        @Override
        public void onChecksumMismatch(String algorithm, int kind, ChecksumFailureException exception)
                throws ChecksumFailureException {
            mismatches.add(algorithm + ":" + kind);
            throw exception;
        }

        @Override
        public void onChecksumError(String algorithm, int kind, ChecksumFailureException exception) {
            errors.add(algorithm + ":" + kind);
            lastError = exception;
        }

        @Override
        public void onNoMoreChecksums() throws ChecksumFailureException {
            if (lastError != null) {
                throw lastError;
            }
        }

        @Override
        public void onTransferRetry() {
            retryCount++;
        }

        @Override
        public boolean onTransferChecksumFailure(ChecksumFailureException exception) {
            transferFailures.add(exception);
            return exception.isRetryWorthy();
        }
    }

    private static final class RecordingTransferListener implements TransferListener {
        @Override
        public void transferInitiated(TransferEvent event) throws TransferCancelledException {
        }

        @Override
        public void transferStarted(TransferEvent event) throws TransferCancelledException {
        }

        @Override
        public void transferProgressed(TransferEvent event) throws TransferCancelledException {
        }

        @Override
        public void transferCorrupted(TransferEvent event) throws TransferCancelledException {
        }

        @Override
        public void transferSucceeded(TransferEvent event) {
        }

        @Override
        public void transferFailed(TransferEvent event) {
        }
    }

    private static final class RecordingLogger implements Logger {
        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void debug(String message) {
        }

        @Override
        public void debug(String message, Throwable error) {
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void warn(String message, Throwable error) {
        }
    }

    private static final class DemoService implements Service {
        private DemoDependency primaryDependency;
        private List<DemoDependency> allDependencies;

        @Override
        public void initService(ServiceLocator locator) {
            primaryDependency = locator.getService(DemoDependency.class);
            allDependencies = locator.getServices(DemoDependency.class);
        }
    }

    private static final class DemoServiceLocator implements ServiceLocator {
        private final DemoDependency primaryDependency;
        private final List<DemoDependency> allDependencies;

        private DemoServiceLocator(DemoDependency primaryDependency, List<DemoDependency> allDependencies) {
            this.primaryDependency = primaryDependency;
            this.allDependencies = allDependencies;
        }

        @Override
        public <T> T getService(Class<T> type) {
            if (type == DemoDependency.class) {
                return type.cast(primaryDependency);
            }
            return null;
        }

        @Override
        public <T> List<T> getServices(Class<T> type) {
            if (type == DemoDependency.class) {
                return Arrays.asList(type.cast(allDependencies.get(0)), type.cast(allDependencies.get(1)));
            }
            return Collections.emptyList();
        }
    }

    private static final class DemoDependency {
        private final String name;

        private DemoDependency(String name) {
            this.name = name;
        }

        private String name() {
            return name;
        }
    }
}
