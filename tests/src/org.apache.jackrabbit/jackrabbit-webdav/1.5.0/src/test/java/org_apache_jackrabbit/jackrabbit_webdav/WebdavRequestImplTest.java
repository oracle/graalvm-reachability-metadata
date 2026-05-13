/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.jackrabbit.webdav.AbstractLocatorFactory;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.DavSession;
import org.apache.jackrabbit.webdav.WebdavRequestImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class WebdavRequestImplTest {
    @Test
    public void constructorInitializesRequestAndBuildsRequestLocator() {
        MockHttpServletRequest httpRequest = newWebdavRequest("/server/default/doc%20name");
        WebdavRequestImpl request = new WebdavRequestImpl(httpRequest, new TestLocatorFactory());

        DavResourceLocator locator = request.getRequestLocator();

        assertThat(locator.getPrefix()).isEqualTo("https://example.test/server");
        assertThat(locator.getWorkspacePath()).isEqualTo("/default");
        assertThat(locator.getResourcePath()).isEqualTo("/default/doc name");
        assertThat(locator.getHref(false)).isEqualTo("https://example.test/server/default/doc%20name");
    }

    @Test
    public void setDavSessionCopiesLockTokensFromHeaders() {
        MockHttpServletRequest httpRequest = newWebdavRequest("/server/default/doc");
        httpRequest.addHeader(DavConstants.HEADER_LOCK_TOKEN, "<opaquelocktoken:lock-token>");
        httpRequest.addHeader(DavConstants.HEADER_IF, "(<opaquelocktoken:if-token>)");
        WebdavRequestImpl request = new WebdavRequestImpl(httpRequest, new TestLocatorFactory());
        RecordingDavSession session = new RecordingDavSession();

        request.setDavSession(session);

        assertThat(request.getDavSession()).isSameAs(session);
        assertThat(session.lockTokens).containsExactly("opaquelocktoken:lock-token", "opaquelocktoken:if-token");
    }

    private static MockHttpServletRequest newWebdavRequest(String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setContextPath("/server");
        request.setRequestURI(requestUri);
        request.addHeader("Host", "example.test");
        return request;
    }

    private static final class TestLocatorFactory extends AbstractLocatorFactory {
        private TestLocatorFactory() {
            super(null);
        }

        @Override
        protected String getRepositoryPath(String resourcePath, String wspPath) {
            return resourcePath;
        }

        @Override
        protected String getResourcePath(String repositoryPath, String wspPath) {
            return wspPath + repositoryPath;
        }
    }

    private static final class RecordingDavSession implements DavSession {
        private final List<Object> references = new ArrayList<Object>();
        private final List<String> lockTokens = new ArrayList<String>();

        @Override
        public void addReference(Object reference) {
            references.add(reference);
        }

        @Override
        public void removeReference(Object reference) {
            references.remove(reference);
        }

        @Override
        public void addLockToken(String token) {
            lockTokens.add(token);
        }

        @Override
        public String[] getLockTokens() {
            return lockTokens.toArray(new String[lockTokens.size()]);
        }

        @Override
        public void removeLockToken(String token) {
            lockTokens.remove(token);
        }
    }
}
