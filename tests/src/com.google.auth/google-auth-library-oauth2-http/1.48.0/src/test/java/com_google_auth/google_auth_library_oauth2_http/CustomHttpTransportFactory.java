/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auth.google_auth_library_oauth2_http;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.http.HttpTransportFactory;

public final class CustomHttpTransportFactory implements HttpTransportFactory {
    public CustomHttpTransportFactory() {
    }

    @Override
    public HttpTransport create() {
        return new NetHttpTransport();
    }
}
