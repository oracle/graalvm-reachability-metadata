/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_core;

import static org.assertj.core.api.Assertions.assertThat;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

import org.apache.cxf.interceptor.security.NamePasswordCallbackHandler;
import org.junit.jupiter.api.Test;

public class NamePasswordCallbackHandlerTest {
    @Test
    void populatesStandardAndReflectivePasswordCallbacks() throws Exception {
        NameCallback name = new NameCallback("name");
        PasswordCallback password = new PasswordCallback("password", false);
        ObjectPasswordCallback objectPassword = new ObjectPasswordCallback();
        NamePasswordCallbackHandler handler =
                new NamePasswordCallbackHandler("alice", "correct horse");

        handler.handle(new Callback[] {name, password, objectPassword});

        assertThat(name.getName()).isEqualTo("alice");
        assertThat(password.getPassword()).containsExactly("correct horse".toCharArray());
        assertThat(objectPassword.password).isEqualTo("correct horse");
    }

    public static final class ObjectPasswordCallback implements Callback {
        private String password;

        public void setObject(Object value) {
            password = new String((char[]) value);
        }
    }
}
