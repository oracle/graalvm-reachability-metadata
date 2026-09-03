/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_auth_common;

import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authorization.AuthorizationContext;
import io.vertx.ext.auth.authorization.PermissionBasedAuthorization;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VariableAwareExpressionTest {
    @Test
    void resolvesResourceVariableWhenMatchingPermission() {
        User user = User.fromName("alice");
        PermissionBasedAuthorization grantedAuthorization = PermissionBasedAuthorization.create("read")
                .setResource("orders/42");
        user.authorizations().add("test-provider", grantedAuthorization);

        PermissionBasedAuthorization requestedAuthorization = PermissionBasedAuthorization.create("read")
                .setResource("orders/{orderId}");
        AuthorizationContext context = AuthorizationContext.create(user);
        context.variables().add("orderId", "42");

        assertThat(requestedAuthorization.match(context)).isTrue();
    }
}
