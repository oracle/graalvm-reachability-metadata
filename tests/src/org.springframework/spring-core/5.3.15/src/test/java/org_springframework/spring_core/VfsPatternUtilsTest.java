/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.VfsResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class VfsPatternUtilsTest {

    @TempDir
    Path tempDirectory;

    @Test
    void resolvesVfsPatternThroughVisitorProxy() throws Exception {
        Path vfsRoot = Files.createDirectories(this.tempDirectory.resolve("vfs-root"));
        Path matchingFile = Files.writeString(vfsRoot.resolve("matching.txt"), "vfs-match", StandardCharsets.UTF_8);
        Files.writeString(vfsRoot.resolve("ignored.bin"), "ignored", StandardCharsets.UTF_8);
        Files.createDirectories(vfsRoot.resolve("nested"));
        Files.writeString(vfsRoot.resolve("nested").resolve("other.txt"), "nested", StandardCharsets.UTF_8);

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(
                new VfsRootResourceLoader(vfsUrl(vfsRoot)));

        Resource[] resources = resolver.getResources("vfs:" + toUrlPath(vfsRoot) + "/*.txt");

        assertThat(resources).hasSize(1);
        assertThat(resources[0]).isInstanceOf(VfsResource.class);
        assertThat(resources[0].getFilename()).isEqualTo("matching.txt");
        assertThat(readContent(resources[0])).isEqualTo(Files.readString(matchingFile, StandardCharsets.UTF_8));
    }

    private static String readContent(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static URL vfsUrl(Path directory) throws MalformedURLException {
        return new URL(null, "vfs:" + toUrlPath(directory) + "/", new VfsUrlStreamHandler());
    }

    private static String toUrlPath(Path path) {
        String rawPath = path.toUri().getRawPath();
        return rawPath.endsWith("/") ? rawPath.substring(0, rawPath.length() - 1) : rawPath;
    }

    private static final class VfsRootResourceLoader extends DefaultResourceLoader {

        private final Resource rootResource;

        private VfsRootResourceLoader(URL rootUrl) {
            this.rootResource = new VfsRootResource(rootUrl);
        }

        @Override
        public Resource getResource(String location) {
            return this.rootResource;
        }
    }

    private static final class VfsRootResource extends AbstractResource {

        private final URL url;

        private VfsRootResource(URL url) {
            this.url = url;
        }

        @Override
        public InputStream getInputStream() {
            throw new UnsupportedOperationException("VFS root test resource only exposes its URL");
        }

        @Override
        public URL getURL() {
            return this.url;
        }

        @Override
        public String getDescription() {
            return "VFS root " + this.url;
        }
    }

    private static final class VfsUrlStreamHandler extends URLStreamHandler {

        @Override
        protected URLConnection openConnection(URL url) {
            throw new UnsupportedOperationException("VFS test URLs are resolved through org.jboss.vfs.VFS");
        }
    }
}
