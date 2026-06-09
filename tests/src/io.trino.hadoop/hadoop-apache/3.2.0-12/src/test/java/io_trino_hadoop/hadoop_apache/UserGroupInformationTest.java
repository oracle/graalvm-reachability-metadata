/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.PrivilegedExceptionAction;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.junit.jupiter.api.Test;

public class UserGroupInformationTest {
    @Test
    void proxyUserKeepsRealUserGroupsAndRunsPrivilegedAction() throws Exception {
        Configuration conf = new Configuration(false);
        conf.set("hadoop.security.authentication", "simple");
        UserGroupInformation.setConfiguration(conf);
        UserGroupInformation realUser = UserGroupInformation.createUserForTesting(
                "real@example.com",
                new String[] {"analytics", "operators"});

        UserGroupInformation proxyUser = UserGroupInformation.createProxyUser("proxy", realUser);
        PrivilegedExceptionAction<String> currentUserName = proxyUser::getUserName;
        String result = proxyUser.doAs(currentUserName);

        assertThat(result).isEqualTo("proxy");
        assertThat(proxyUser.getRealUser()).isEqualTo(realUser);
        assertThat(realUser.getGroupNames()).containsExactly("analytics", "operators");
        assertThat(proxyUser.getAuthenticationMethod())
                .isEqualTo(UserGroupInformation.AuthenticationMethod.PROXY);
    }

    @Test
    void userStoresTokensInCredentials() {
        UserGroupInformation user = UserGroupInformation.createRemoteUser("token-user");
        Token<TokenIdentifier> token = new Token<>(
                "identifier".getBytes(StandardCharsets.UTF_8),
                "password".getBytes(StandardCharsets.UTF_8),
                new Text("kind"),
                new Text("service"));

        user.addToken(new Text("alias"), token);

        assertThat(user.getTokens()).hasSize(1);
        assertThat(user.getCredentials().getToken(new Text("alias"))).isEqualTo(token);
    }
}
