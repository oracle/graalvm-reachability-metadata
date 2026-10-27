/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_security.micronaut_security_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.expressions.context.ExpressionEvaluationContextRegistrar;
import io.micronaut.inject.visitor.TypeElementVisitor;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration coverage for security expression context service discovery. */
@Timeout(55)
public class SecuredEvaluationContextRegistrarTest {
    @Test
    void discoversSecurityExpressionContextThroughTypeElementVisitorService() {
        List<String> contextClassNames = ServiceLoader.load(TypeElementVisitor.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(ExpressionEvaluationContextRegistrar.class::isInstance)
                .map(ExpressionEvaluationContextRegistrar.class::cast)
                .map(ExpressionEvaluationContextRegistrar::getContextClassName)
                .toList();

        assertThat(contextClassNames).contains("io.micronaut.security.expressions.SecuredEvaluationContext");
    }
}
