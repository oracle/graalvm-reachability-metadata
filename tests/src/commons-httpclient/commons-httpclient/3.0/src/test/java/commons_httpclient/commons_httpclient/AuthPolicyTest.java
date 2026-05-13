/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScheme;
import org.apache.commons.httpclient.auth.BasicScheme;
import org.junit.jupiter.api.Test;

public class AuthPolicyTest {
    @Test
    void defaultAuthSchemeCanBeInstantiatedByPolicy() {
        AuthScheme scheme = AuthPolicy.getAuthScheme(AuthPolicy.BASIC);

        assertThat(scheme).isInstanceOf(BasicScheme.class);
        assertThat(scheme.getSchemeName()).isEqualTo("basic");
    }

    @Test
    void defaultAuthPreferencesExposeBuiltInSchemesInPreferenceOrder() {
        List preferences = AuthPolicy.getDefaultAuthPrefs();

        assertThat(preferences).containsExactly("ntlm", "digest", "basic");
    }
}
