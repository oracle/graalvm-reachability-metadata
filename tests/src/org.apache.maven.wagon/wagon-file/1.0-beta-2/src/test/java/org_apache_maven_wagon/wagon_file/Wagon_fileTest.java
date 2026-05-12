/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_wagon.wagon_file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.wagon.InputData;
import org.apache.maven.wagon.OutputData;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.events.SessionEvent;
import org.apache.maven.wagon.events.SessionListener;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.apache.maven.wagon.providers.file.FileWagon;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.repository.Repository;
import org.apache.maven.wagon.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Wagon_fileTest {

    private static final String TEXT_CONTENT = "alpha\nbeta\ngamma\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void connectsToFileRepositoryCreatesMissingBaseDirectoryAndEmitsSessionEvents() throws Exception {
        Path repositoryDirectory = temporaryDirectory.resolve("created-repository");
        FileWagon wagon = new FileWagon();
        RecordingSessionListener listener = new RecordingSessionListener();
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setUserName("user-from-authentication-info");
        ProxyInfo proxyInfo = new ProxyInfo();
        proxyInfo.setType(ProxyInfo.PROXY_HTTP);
        proxyInfo.setHost("localhost");
        proxyInfo.setPort(8080);

        wagon.addSessionListener(listener);
        assertThat(wagon.hasSessionListener(listener)).isTrue();
        wagon.setInteractive(false);

        try {
            wagon.connect(repository(repositoryDirectory), authenticationInfo, proxyInfo);

            assertThat(repositoryDirectory).isDirectory();
            assertThat(wagon.getRepository().getBasedir()).isEqualTo(repositoryDirectory.toAbsolutePath().toString());
            assertThat(wagon.getAuthenticationInfo()).isSameAs(authenticationInfo);
            assertThat(wagon.getProxyInfo()).isSameAs(proxyInfo);
            assertThat(wagon.isInteractive()).isFalse();
        } finally {
            wagon.disconnect();
        }

        assertThat(listener.events).containsExactly(
                SessionEvent.SESSION_OPENING,
                SessionEvent.SESSION_OPENED,
                SessionEvent.SESSION_DISCONNECTING,
                SessionEvent.SESSION_DISCONNECTED);
        wagon.removeSessionListener(listener);
        assertThat(wagon.hasSessionListener(listener)).isFalse();
    }

    @Test
    void putsAndGetsFilesWithNestedPathsAndTransferNotifications() throws Exception {
        Path repositoryDirectory = Files.createDirectory(temporaryDirectory.resolve("repository"));
        Path source = temporaryDirectory.resolve("source.txt");
        Path downloaded = temporaryDirectory.resolve("downloads").resolve("copy.txt");
        Files.writeString(source, TEXT_CONTENT, StandardCharsets.UTF_8);
        RecordingTransferListener listener = new RecordingTransferListener();
        FileWagon wagon = connectedWagon(repositoryDirectory);
        wagon.addTransferListener(listener);

        try {
            wagon.put(source.toFile(), "nested/path/remote.txt");
            wagon.get("nested/path/remote.txt", downloaded.toFile());
        } finally {
            wagon.disconnect();
        }

        assertThat(Files.readString(repositoryDirectory.resolve("nested/path/remote.txt"), StandardCharsets.UTF_8))
                .isEqualTo(TEXT_CONTENT);
        assertThat(Files.readString(downloaded, StandardCharsets.UTF_8)).isEqualTo(TEXT_CONTENT);
        assertThat(listener.events).containsExactly(
                "initiated:PUT:nested/path/remote.txt",
                "started:PUT:nested/path/remote.txt",
                "progress:PUT:" + TEXT_CONTENT.getBytes(StandardCharsets.UTF_8).length,
                "completed:PUT:nested/path/remote.txt",
                "initiated:GET:nested/path/remote.txt",
                "started:GET:nested/path/remote.txt",
                "progress:GET:" + TEXT_CONTENT.getBytes(StandardCharsets.UTF_8).length,
                "completed:GET:nested/path/remote.txt");
        assertThat(wagon.hasTransferListener(listener)).isTrue();
        wagon.removeTransferListener(listener);
        assertThat(wagon.hasTransferListener(listener)).isFalse();
    }

    @Test
    void getIfNewerDownloadsOnlyWhenRemoteResourceIsNewerThanTimestamp() throws Exception {
        Path repositoryDirectory = Files.createDirectory(temporaryDirectory.resolve("repository"));
        Path remote = repositoryDirectory.resolve("artifact.txt");
        Path destination = temporaryDirectory.resolve("artifact-copy.txt");
        Files.writeString(remote, "remote-content", StandardCharsets.UTF_8);
        Files.writeString(destination, "existing-content", StandardCharsets.UTF_8);
        long remoteLastModified = Files.getLastModifiedTime(remote).toMillis();
        FileWagon wagon = connectedWagon(repositoryDirectory);

        try {
            boolean skipped = wagon.getIfNewer("artifact.txt", destination.toFile(), remoteLastModified + 60_000L);
            assertThat(skipped).isFalse();
            assertThat(Files.readString(destination, StandardCharsets.UTF_8)).isEqualTo("existing-content");

            boolean downloaded = wagon.getIfNewer("artifact.txt", destination.toFile(), 0L);
            assertThat(downloaded).isTrue();
            assertThat(Files.readString(destination, StandardCharsets.UTF_8)).isEqualTo("remote-content");
        } finally {
            wagon.disconnect();
        }
    }

    @Test
    void copiesDirectoriesListsFilesAndChecksResourceExistence() throws Exception {
        Path repositoryDirectory = Files.createDirectory(temporaryDirectory.resolve("repository"));
        Path sourceDirectory = Files.createDirectory(temporaryDirectory.resolve("site"));
        Files.writeString(sourceDirectory.resolve("index.html"), "<h1>Index</h1>", StandardCharsets.UTF_8);
        Files.createDirectories(sourceDirectory.resolve("assets/css"));
        Files.writeString(
                sourceDirectory.resolve("assets/css/site.css"),
                "body { color: black; }",
                StandardCharsets.UTF_8);
        FileWagon wagon = connectedWagon(repositoryDirectory);

        try {
            assertThat(wagon.supportsDirectoryCopy()).isTrue();

            wagon.putDirectory(sourceDirectory.toFile(), "published\\site");
            List<?> rootFiles = wagon.getFileList("published/site");
            List<?> assetFiles = wagon.getFileList("published/site/assets/css");

            assertThat(rootFiles.stream().map(String.class::cast).toList()).contains("index.html", "assets");
            assertThat(assetFiles.stream().map(String.class::cast).toList()).containsExactly("site.css");
            assertThat(wagon.resourceExists("published/site/index.html")).isTrue();
            assertThat(wagon.resourceExists("published/site/missing.html")).isFalse();

            wagon.putDirectory(sourceDirectory.toFile(), ".");
            assertThat(Files.readString(repositoryDirectory.resolve("index.html"), StandardCharsets.UTF_8))
                    .isEqualTo("<h1>Index</h1>");
        } finally {
            wagon.disconnect();
        }
    }

    @Test
    void putDirectoryMergesNewFilesIntoExistingDestinationDirectory() throws Exception {
        Path repositoryDirectory = Files.createDirectory(temporaryDirectory.resolve("repository"));
        Files.createDirectories(repositoryDirectory.resolve("published/site/assets"));
        Files.writeString(
                repositoryDirectory.resolve("published/site/index.html"),
                "existing-index",
                StandardCharsets.UTF_8);
        Files.writeString(
                repositoryDirectory.resolve("published/site/assets/existing.css"),
                "body { color: blue; }",
                StandardCharsets.UTF_8);
        Path sourceDirectory = Files.createDirectory(temporaryDirectory.resolve("site-update"));
        Files.writeString(sourceDirectory.resolve("about.html"), "new-about", StandardCharsets.UTF_8);
        Files.createDirectories(sourceDirectory.resolve("assets/images"));
        Files.writeString(sourceDirectory.resolve("assets/images/logo.txt"), "logo", StandardCharsets.UTF_8);
        FileWagon wagon = connectedWagon(repositoryDirectory);

        try {
            wagon.putDirectory(sourceDirectory.toFile(), "published/site");
        } finally {
            wagon.disconnect();
        }

        assertThat(Files.readString(repositoryDirectory.resolve("published/site/index.html"), StandardCharsets.UTF_8))
                .isEqualTo("existing-index");
        assertThat(Files.readString(
                        repositoryDirectory.resolve("published/site/assets/existing.css"),
                        StandardCharsets.UTF_8))
                .isEqualTo("body { color: blue; }");
        assertThat(Files.readString(repositoryDirectory.resolve("published/site/about.html"), StandardCharsets.UTF_8))
                .isEqualTo("new-about");
        assertThat(Files.readString(
                        repositoryDirectory.resolve("published/site/assets/images/logo.txt"),
                        StandardCharsets.UTF_8))
                .isEqualTo("logo");
    }

    @Test
    void createsZipArchiveFromSelectedFilesUsingRelativeEntryNames() throws Exception {
        Path sourceDirectory = Files.createDirectory(temporaryDirectory.resolve("archive-source"));
        Files.writeString(sourceDirectory.resolve("index.txt"), "index-content", StandardCharsets.UTF_8);
        Files.createDirectories(sourceDirectory.resolve("docs"));
        Files.writeString(sourceDirectory.resolve("docs/readme.txt"), "readme-content", StandardCharsets.UTF_8);
        Files.writeString(sourceDirectory.resolve("not-selected.txt"), "ignored", StandardCharsets.UTF_8);
        Path archive = temporaryDirectory.resolve("selected-files.zip");
        FileWagon wagon = new FileWagon();

        wagon.createZip(List.of("index.txt", "docs/readme.txt"), archive.toFile(), sourceDirectory.toFile());

        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            assertThat(readZipEntry(zipFile, "index.txt")).isEqualTo("index-content");
            assertThat(readZipEntry(zipFile, "docs/readme.txt")).isEqualTo("readme-content");
            assertThat(zipFile.getEntry("not-selected.txt")).isNull();
        }
    }

    @Test
    void exposesStreamBasedInputAndOutputDataForResources() throws Exception {
        Path repositoryDirectory = Files.createDirectory(temporaryDirectory.resolve("repository"));
        FileWagon wagon = connectedWagon(repositoryDirectory);

        try {
            Resource outputResource = new Resource("streams/generated.txt");
            OutputData outputData = new OutputData();
            outputData.setResource(outputResource);
            wagon.fillOutputData(outputData);
            try (OutputStream outputStream = outputData.getOutputStream()) {
                outputStream.write("streamed-content".getBytes(StandardCharsets.UTF_8));
            }

            Resource inputResource = new Resource("streams/generated.txt");
            InputData inputData = new InputData();
            inputData.setResource(inputResource);
            wagon.fillInputData(inputData);
            String content;
            try (InputStream inputStream = inputData.getInputStream()) {
                content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            assertThat(content).isEqualTo("streamed-content");
            assertThat(inputResource.getContentLength())
                    .isEqualTo("streamed-content".getBytes(StandardCharsets.UTF_8).length);
            assertThat(inputResource.getLastModified()).isPositive();
        } finally {
            wagon.disconnect();
        }
    }

    @Test
    void reportsMissingResourcesAndInvalidDirectoryListings() throws Exception {
        Path repositoryDirectory = Files.createDirectory(temporaryDirectory.resolve("repository"));
        Files.writeString(repositoryDirectory.resolve("plain-file.txt"), "content", StandardCharsets.UTF_8);
        FileWagon wagon = connectedWagon(repositoryDirectory);

        try {
            assertThatThrownBy(() -> wagon.get("missing.txt", temporaryDirectory.resolve("missing-copy.txt").toFile()))
                    .isInstanceOf(ResourceDoesNotExistException.class)
                    .hasMessageContaining("does not exist");
            assertThatThrownBy(() -> wagon.getFileList("plain-file.txt"))
                    .isInstanceOf(ResourceDoesNotExistException.class)
                    .hasMessageContaining("Path is not a directory");
        } finally {
            wagon.disconnect();
        }
    }

    @Test
    void failsFastWhenRepositoryBaseDirectoryIsNull() throws Exception {
        FileWagon wagon = new FileWagon();
        Repository repository = new Repository();
        repository.setId("null-basedir");
        wagon.connect(repository);

        try {
            assertThatThrownBy(() -> wagon.resourceExists("anything.txt"))
                    .isInstanceOf(TransferFailedException.class)
                    .hasMessageContaining("null basedir");
            assertThatThrownBy(() -> wagon.putDirectory(temporaryDirectory.toFile(), "target"))
                    .isInstanceOf(TransferFailedException.class)
                    .hasMessageContaining("null basedir");
        } finally {
            wagon.disconnect();
        }
    }

    private static FileWagon connectedWagon(Path repositoryDirectory) throws Exception {
        FileWagon wagon = new FileWagon();
        wagon.connect(repository(repositoryDirectory));
        return wagon;
    }

    private static Repository repository(Path repositoryDirectory) {
        return new Repository("file-repository", repositoryDirectory.toUri().toString());
    }

    private static String readZipEntry(ZipFile zipFile, String name) throws IOException {
        ZipEntry entry = zipFile.getEntry(name);
        assertThat(entry).isNotNull();
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class RecordingTransferListener extends AbstractTransferListener {

        private final List<String> events = new ArrayList<>();

        @Override
        public void transferInitiated(TransferEvent transferEvent) {
            events.add("initiated:" + requestType(transferEvent) + ":" + transferEvent.getResource().getName());
        }

        @Override
        public void transferStarted(TransferEvent transferEvent) {
            events.add("started:" + requestType(transferEvent) + ":" + transferEvent.getResource().getName());
        }

        @Override
        public void transferProgress(TransferEvent transferEvent, byte[] buffer, int length) {
            events.add("progress:" + requestType(transferEvent) + ":" + length);
        }

        @Override
        public void transferCompleted(TransferEvent transferEvent) {
            events.add("completed:" + requestType(transferEvent) + ":" + transferEvent.getResource().getName());
        }

        private static String requestType(TransferEvent transferEvent) {
            if (transferEvent.getRequestType() == TransferEvent.REQUEST_GET) {
                return "GET";
            }
            return "PUT";
        }
    }

    private static final class RecordingSessionListener implements SessionListener {

        private final List<Integer> events = new ArrayList<>();

        @Override
        public void sessionOpening(SessionEvent sessionEvent) {
            events.add(sessionEvent.getEventType());
        }

        @Override
        public void sessionOpened(SessionEvent sessionEvent) {
            events.add(sessionEvent.getEventType());
        }

        @Override
        public void sessionDisconnecting(SessionEvent sessionEvent) {
            events.add(sessionEvent.getEventType());
        }

        @Override
        public void sessionDisconnected(SessionEvent sessionEvent) {
            events.add(sessionEvent.getEventType());
        }

        @Override
        public void sessionConnectionRefused(SessionEvent sessionEvent) {
            events.add(sessionEvent.getEventType());
        }

        @Override
        public void sessionLoggedIn(SessionEvent sessionEvent) {
            events.add(sessionEvent.getEventType());
        }

        @Override
        public void sessionLoggedOff(SessionEvent sessionEvent) {
            events.add(sessionEvent.getEventType());
        }

        @Override
        public void sessionError(SessionEvent sessionEvent) {
            events.add(sessionEvent.getEventType());
        }

        @Override
        public void debug(String message) {
        }
    }
}
