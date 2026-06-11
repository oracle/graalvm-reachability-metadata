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
import java.util.concurrent.atomic.AtomicInteger;

public class OAuth2CredentialsTestTransportFactory implements HttpTransportFactory {
    private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

    public OAuth2CredentialsTestTransportFactory() {
        CONSTRUCTOR_CALLS.incrementAndGet();
    }

    public static void resetConstructorCalls() {
        CONSTRUCTOR_CALLS.set(0);
    }

    public static int getConstructorCalls() {
        return CONSTRUCTOR_CALLS.get();
    }

    @Override
    public HttpTransport create() {
        return new NetHttpTransport();
    }
}
