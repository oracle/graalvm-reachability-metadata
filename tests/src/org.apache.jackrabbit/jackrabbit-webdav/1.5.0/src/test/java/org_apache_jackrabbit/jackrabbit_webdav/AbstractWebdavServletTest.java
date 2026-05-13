/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.DavLocatorFactory;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceFactory;
import org.apache.jackrabbit.webdav.DavSessionProvider;
import org.apache.jackrabbit.webdav.WebdavRequest;
import org.apache.jackrabbit.webdav.server.AbstractWebdavServlet;
import org.junit.jupiter.api.Test;

public class AbstractWebdavServletTest {
    @Test
    public void instantiatingSubclassInitializesAbstractServlet() {
        TestWebdavServlet servlet = new TestWebdavServlet();

        servlet.setDavSessionProvider(null);
        servlet.setLocatorFactory(null);
        servlet.setResourceFactory(null);

        assertThat(servlet.getAuthenticateHeaderValue()).isEqualTo(AbstractWebdavServlet.DEFAULT_AUTHENTICATE_HEADER);
        assertThat(servlet.isPreconditionAccepted(null, null)).isTrue();
        assertThat(servlet.getDavSessionProvider()).isNull();
        assertThat(servlet.getLocatorFactory()).isNull();
        assertThat(servlet.getResourceFactory()).isNull();
    }

    private static final class TestWebdavServlet extends AbstractWebdavServlet {
        private DavSessionProvider davSessionProvider;
        private DavLocatorFactory locatorFactory;
        private DavResourceFactory resourceFactory;

        @Override
        protected boolean isPreconditionValid(WebdavRequest request, DavResource resource) {
            return true;
        }

        private boolean isPreconditionAccepted(WebdavRequest request, DavResource resource) {
            return isPreconditionValid(request, resource);
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
}
