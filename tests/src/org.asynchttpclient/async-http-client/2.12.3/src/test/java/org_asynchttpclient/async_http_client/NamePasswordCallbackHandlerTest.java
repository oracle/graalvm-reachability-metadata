/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_asynchttpclient.async_http_client;

import org.asynchttpclient.spnego.NamePasswordCallbackHandler;
import org.junit.jupiter.api.Test;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;

import static org.assertj.core.api.Assertions.assertThat;

public class NamePasswordCallbackHandlerTest {
    @Test
    void invokesGenericPasswordCallbackMethod() throws Exception {
        NamePasswordCallbackHandler handler = new NamePasswordCallbackHandler("alice", "secret");
        NameCallback nameCallback = new NameCallback("User");
        RecordingPasswordCallback passwordCallback = new RecordingPasswordCallback();

        handler.handle(new Callback[] {nameCallback, passwordCallback});

        assertThat(nameCallback.getName()).isEqualTo("alice");
        assertThat(passwordCallback.password).containsExactly('s', 'e', 'c', 'r', 'e', 't');
    }

    public static final class RecordingPasswordCallback implements Callback {
        private char[] password;

        public void setObject(Object value) {
            password = (char[]) value;
        }
    }
}
