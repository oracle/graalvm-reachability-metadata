/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack.handlebars;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.jknack.handlebars.ValueResolver;
import com.github.jknack.handlebars.context.FieldValueResolver;
import org.junit.jupiter.api.Test;

public class FieldValueResolverTest {
    @Test
    public void resolveReadsDeclaredAndInheritedInstanceFields() {
        FieldBackedModel model = new FieldBackedModel("declared", "inherited");
        FieldValueResolver resolver = new FieldValueResolver();

        Object declaredValue = resolver.resolve(model, "declaredLabel");
        Object inheritedValue = resolver.resolve(model, "inheritedLabel");
        Object staticValue = resolver.resolve(model, "staticLabel");

        assertThat(declaredValue).isEqualTo("declared");
        assertThat(inheritedValue).isEqualTo("inherited");
        assertThat(staticValue).isSameAs(ValueResolver.UNRESOLVED);
    }

    private static class BaseFieldModel {
        private final String inheritedLabel;

        private BaseFieldModel(String inheritedLabel) {
            this.inheritedLabel = inheritedLabel;
        }
    }

    private static final class FieldBackedModel extends BaseFieldModel {
        private static String staticLabel = "static";

        private final String declaredLabel;

        private FieldBackedModel(String declaredLabel, String inheritedLabel) {
            super(inheritedLabel);
            this.declaredLabel = declaredLabel;
        }
    }
}
