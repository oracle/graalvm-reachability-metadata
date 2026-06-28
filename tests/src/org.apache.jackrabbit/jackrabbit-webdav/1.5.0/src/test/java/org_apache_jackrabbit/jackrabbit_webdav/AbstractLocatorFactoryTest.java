/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.AbstractLocatorFactory;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractLocatorFactoryTest {
    @Test
    void createsLocatorFromHrefAndExposesParsedWorkspaceAndResourcePaths() {
        AbstractLocatorFactory factory = new PathPreservingLocatorFactory("/server");

        DavResourceLocator locator = factory.createResourceLocator(
                "http://localhost:8080",
                "http://localhost:8080/server/default/some%20node");
        DavResourceLocator rootLocator = factory.createResourceLocator(
                "http://localhost:8080",
                "http://localhost:8080/server/");

        assertThat(locator.getPrefix()).isEqualTo("http://localhost:8080/server");
        assertThat(locator.getWorkspacePath()).isEqualTo("/default");
        assertThat(locator.getWorkspaceName()).isEqualTo("default");
        assertThat(locator.getResourcePath()).isEqualTo("/default/some node");
        assertThat(locator.getRepositoryPath()).isEqualTo("/some node");
        assertThat(locator.getHref(true)).isEqualTo("http://localhost:8080/server/default/some%20node/");
        assertThat(locator.getHref(false)).isEqualTo("http://localhost:8080/server/default/some%20node");
        assertThat(locator.isRootLocation()).isFalse();
        assertThat(locator.getFactory()).isSameAs(factory);
        assertThat(locator.isSameWorkspace("default")).isTrue();
        assertThat(locator.isSameWorkspace(rootLocator)).isFalse();

        assertThat(rootLocator.isRootLocation()).isTrue();
        assertThat(rootLocator.getWorkspacePath()).isNull();
        assertThat(rootLocator.getResourcePath()).isNull();
        assertThat(rootLocator.getHref(true)).isEqualTo("http://localhost:8080/server/");
    }

    @Test
    void createsLocatorFromRepositoryPathThroughSubclassConversion() {
        AbstractLocatorFactory factory = new PathPreservingLocatorFactory("/server");

        DavResourceLocator locator = factory.createResourceLocator(
                "/dav",
                "/default",
                "/from repository",
                false);

        assertThat(locator.getPrefix()).isEqualTo("/dav");
        assertThat(locator.getWorkspacePath()).isEqualTo("/default");
        assertThat(locator.getResourcePath()).isEqualTo("/default/from repository");
        assertThat(locator.getRepositoryPath()).isEqualTo("/from repository");
        assertThat(locator.getHref(true)).isEqualTo("/dav/default/from%20repository/");
        assertThat(locator.isSameWorkspace("default")).isTrue();
    }

    private static final class PathPreservingLocatorFactory extends AbstractLocatorFactory {
        private PathPreservingLocatorFactory(String pathPrefix) {
            super(pathPrefix);
        }

        @Override
        protected String getRepositoryPath(String resourcePath, String workspacePath) {
            if (resourcePath == null || workspacePath == null) {
                return resourcePath;
            }
            if (resourcePath.equals(workspacePath)) {
                return "/";
            }
            return resourcePath.substring(workspacePath.length());
        }

        @Override
        protected String getResourcePath(String repositoryPath, String workspacePath) {
            if (repositoryPath == null || workspacePath == null) {
                return repositoryPath;
            }
            if ("/".equals(repositoryPath)) {
                return workspacePath;
            }
            return workspacePath + repositoryPath;
        }
    }
}
