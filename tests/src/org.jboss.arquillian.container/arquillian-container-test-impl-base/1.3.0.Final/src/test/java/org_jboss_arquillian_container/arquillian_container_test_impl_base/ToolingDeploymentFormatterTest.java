/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_impl_base;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

import org.jboss.arquillian.container.test.impl.client.deployment.tool.ToolingDeploymentFormatter;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ToolingDeploymentFormatterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void formatNodeIncludesFileAssetSourceLocation() throws IOException {
        Path configurationFile = temporaryDirectory.resolve("arquillian.xml");
        Files.writeString(configurationFile, "<arquillian/>\n");
        File sourceFile = configurationFile.toFile();
        StringBuilder deployment = new StringBuilder();
        Node fileNode = new SimpleNode(new FileAsset(sourceFile), "/META-INF/arquillian.xml");

        new ToolingDeploymentFormatter(ToolingDeploymentFormatterTest.class).formatNode(fileNode, deployment);

        assertThat(deployment.toString())
                .contains("type=\"FileAsset\"")
                .contains("path=\"/META-INF/arquillian.xml\"")
                .contains("source=\"" + sourceFile.getAbsolutePath() + "\"");
    }

    private static final class SimpleNode implements Node {
        private final Asset asset;
        private final ArchivePath path;

        private SimpleNode(Asset asset, String path) {
            this.asset = asset;
            this.path = new SimpleArchivePath(path);
        }

        @Override
        public Asset getAsset() {
            return asset;
        }

        @Override
        public Set<Node> getChildren() {
            return Collections.emptySet();
        }

        @Override
        public ArchivePath getPath() {
            return path;
        }
    }

    private static final class SimpleArchivePath implements ArchivePath {
        private final String path;

        private SimpleArchivePath(String path) {
            this.path = path;
        }

        @Override
        public String get() {
            return path;
        }

        @Override
        public ArchivePath getParent() {
            return null;
        }

        @Override
        public int compareTo(ArchivePath other) {
            return path.compareTo(other.get());
        }
    }
}
