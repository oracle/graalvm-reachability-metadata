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
    void permissionResourceTemplateMatchesResolvedContextVariable() {
        User user = User.fromName("alice");
        user.authorizations()
            .add(
                "test-provider",
                PermissionBasedAuthorization.create("document.read")
                    .setResource("tenant-42/invoices"));

        AuthorizationContext context = AuthorizationContext.create(user);
        context.variables().add("tenant", "tenant-42");

        PermissionBasedAuthorization requiredAuthorization = PermissionBasedAuthorization
            .create("document.read")
            .setResource("{tenant}/invoices");

        assertThat(requiredAuthorization.match(context)).isTrue();
    }
}
