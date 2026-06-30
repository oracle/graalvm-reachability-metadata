/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavLocatorFactory;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceFactory;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.DavServletRequest;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.DavSession;
import org.apache.jackrabbit.webdav.DavSessionProvider;
import org.apache.jackrabbit.webdav.WebdavRequest;
import org.apache.jackrabbit.webdav.server.AbstractWebdavServlet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractWebdavServletTest {
    @Test
    void initializesServletClassAndStoresWebdavCollaborators() {
        TestWebdavServlet servlet = new TestWebdavServlet();
        DavSessionProvider sessionProvider = new NoOpSessionProvider();
        DavLocatorFactory locatorFactory = new NoOpLocatorFactory();
        DavResourceFactory resourceFactory = new NoOpResourceFactory();

        servlet.setDavSessionProvider(sessionProvider);
        servlet.setLocatorFactory(locatorFactory);
        servlet.setResourceFactory(resourceFactory);

        assertThat(servlet.getDavSessionProvider()).isSameAs(sessionProvider);
        assertThat(servlet.getLocatorFactory()).isSameAs(locatorFactory);
        assertThat(servlet.getResourceFactory()).isSameAs(resourceFactory);
        assertThat(servlet.getAuthenticateHeaderValue())
                .isEqualTo(AbstractWebdavServlet.DEFAULT_AUTHENTICATE_HEADER);
    }

    private static final class TestWebdavServlet extends AbstractWebdavServlet {
        private DavSessionProvider davSessionProvider;
        private DavLocatorFactory locatorFactory;
        private DavResourceFactory resourceFactory;

        @Override
        protected boolean isPreconditionValid(WebdavRequest request, DavResource resource) {
            return true;
        }

        @Override
        public DavSessionProvider getDavSessionProvider() {
            return davSessionProvider;
        }

        @Override
        public void setDavSessionProvider(DavSessionProvider davSessionProvider) {
            this.davSessionProvider = davSessionProvider;
        }

        @Override
        public DavLocatorFactory getLocatorFactory() {
            return locatorFactory;
        }

        @Override
        public void setLocatorFactory(DavLocatorFactory locatorFactory) {
            this.locatorFactory = locatorFactory;
        }

        @Override
        public DavResourceFactory getResourceFactory() {
            return resourceFactory;
        }

        @Override
        public void setResourceFactory(DavResourceFactory resourceFactory) {
            this.resourceFactory = resourceFactory;
        }

        @Override
        public String getAuthenticateHeaderValue() {
            return DEFAULT_AUTHENTICATE_HEADER;
        }
    }

    private static final class NoOpLocatorFactory implements DavLocatorFactory {
        @Override
        public DavResourceLocator createResourceLocator(String prefix, String href) {
            return null;
        }

        @Override
        public DavResourceLocator createResourceLocator(String prefix, String workspacePath, String resourcePath) {
            return null;
        }

        @Override
        public DavResourceLocator createResourceLocator(
                String prefix,
                String workspacePath,
                String path,
                boolean isResourcePath) {
            return null;
        }
    }

    private static final class NoOpResourceFactory implements DavResourceFactory {
        @Override
        public DavResource createResource(
                DavResourceLocator locator,
                DavServletRequest request,
                DavServletResponse response) {
            return null;
        }

        @Override
        public DavResource createResource(DavResourceLocator locator, DavSession session) {
            return null;
        }
    }

    private static final class NoOpSessionProvider implements DavSessionProvider {
        @Override
        public boolean attachSession(WebdavRequest request) {
            return true;
        }

        @Override
        public void releaseSession(WebdavRequest request) {
        }
    }
}
