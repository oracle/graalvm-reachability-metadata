/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_transports_http;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/// Creates fixture URLs without loading a platform HTTPS protocol handler.
final class HttpsTestUrls {
    private HttpsTestUrls() {
    }

    static URL create(String path) throws MalformedURLException {
        return new URL(null, "https://localhost" + path, new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                throw new UnsupportedOperationException("Open the HTTPS fixture connection directly");
            }
        });
    }
}
