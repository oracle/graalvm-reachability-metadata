/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack.handlebars;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.jknack.handlebars.context.MethodValueResolver;
import org.junit.jupiter.api.Test;

public class MethodValueResolverTest {
    @Test
    public void resolveInvokesPublicZeroArgumentMethod() {
        MethodBackedModel model = new MethodBackedModel("ready");
        MethodValueResolver resolver = new MethodValueResolver();

        Object resolvedValue = resolver.resolve(model, "status");

        assertThat(resolvedValue).isEqualTo("ready");
    }

    public static final class MethodBackedModel {
        private final String status;

        public MethodBackedModel(String status) {
            this.status = status;
        }

        public String status() {
            return status;
        }
    }
}
