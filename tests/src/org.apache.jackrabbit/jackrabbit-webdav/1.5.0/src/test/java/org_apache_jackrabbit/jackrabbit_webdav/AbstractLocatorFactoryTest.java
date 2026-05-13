/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.AbstractLocatorFactory;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.junit.jupiter.api.Test;

public class AbstractLocatorFactoryTest {
    @Test
    public void createResourceLocatorSplitsAbsoluteHrefIntoWorkspaceAndResourcePaths() {
        TestLocatorFactory factory = new TestLocatorFactory("/server");

        DavResourceLocator locator = factory.createResourceLocator(
                "http://localhost",
                "http://localhost/server/default/doc%20name");

        assertThat(locator.getPrefix()).isEqualTo("http://localhost/server");
        assertThat(locator.getWorkspacePath()).isEqualTo("/default");
        assertThat(locator.getWorkspaceName()).isEqualTo("default");
        assertThat(locator.getResourcePath()).isEqualTo("/default/doc name");
        assertThat(locator.getHref(false)).isEqualTo("http://localhost/server/default/doc%20name");
        assertThat(locator.getHref(true)).isEqualTo("http://localhost/server/default/doc%20name/");
        assertThat(locator.isRootLocation()).isFalse();
        assertThat(locator.isSameWorkspace("default")).isTrue();
        assertThat(locator.getFactory()).isSameAs(factory);
        assertThat(locator.getRepositoryPath()).isEqualTo("repository:/default:/default/doc name");
    }

    @Test
    public void createResourceLocatorBuildsRootAndConvertsRepositoryPaths() {
        TestLocatorFactory factory = new TestLocatorFactory("/server");

        DavResourceLocator root = factory.createResourceLocator(
                "http://localhost",
                "http://localhost/server/");
        DavResourceLocator converted = factory.createResourceLocator(
                "http://localhost/server",
                "/default",
                "node",
                false);

        assertThat(root.isRootLocation()).isTrue();
        assertThat(root.getWorkspacePath()).isNull();
        assertThat(root.getResourcePath()).isNull();
        assertThat(root.getHref(true)).isEqualTo("http://localhost/server/");
        assertThat(root.getHref(false)).isEqualTo("http://localhost/server");
        assertThat(root.isSameWorkspace((String) null)).isTrue();

        assertThat(converted.getResourcePath()).isEqualTo("/default/from-repository/node");
        assertThat(converted.getHref(false)).isEqualTo("http://localhost/server/default/from-repository/node");
        assertThat(root.isSameWorkspace(converted)).isFalse();
    }

    private static final class TestLocatorFactory extends AbstractLocatorFactory {
        private TestLocatorFactory(String pathPrefix) {
            super(pathPrefix);
        }

        protected String getRepositoryPath(String resourcePath, String wspPath) {
            return "repository:" + wspPath + ":" + resourcePath;
        }

        protected String getResourcePath(String repositoryPath, String wspPath) {
            return wspPath + "/from-repository/" + repositoryPath;
        }
    }
}
