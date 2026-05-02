/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.user.client.rpc.RpcToken;
import com.google.gwt.user.server.Util;
import com.google.gwt.user.server.rpc.NoXsrfProtect;
import com.google.gwt.user.server.rpc.XsrfProtect;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

public class UtilTest {
    @Test
    void detectsImplicitXsrfProtectionFromSiblingRpcTokenMethod() throws Exception {
        Method serviceMethod = TokenAwareService.class.getMethod("updateRecord");

        boolean protectedMethod = Util.isMethodXsrfProtected(serviceMethod, XsrfProtect.class,
                NoXsrfProtect.class, RpcToken.class);

        assertThat(protectedMethod).isTrue();
    }

    @Test
    void doesNotTreatRpcTokenProviderMethodAsProtectedByItself() throws Exception {
        Method tokenMethod = TokenAwareService.class.getMethod("getRpcToken");

        boolean protectedMethod = Util.isMethodXsrfProtected(tokenMethod, XsrfProtect.class,
                NoXsrfProtect.class, RpcToken.class);

        assertThat(protectedMethod).isFalse();
    }

    public interface TokenAwareService {
        void updateRecord();

        RpcToken getRpcToken();
    }
}
