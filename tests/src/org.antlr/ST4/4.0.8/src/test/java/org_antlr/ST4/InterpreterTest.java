/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.ST4;

import org.junit.jupiter.api.Test;
import org.stringtemplate.v4.ST;

import static org.assertj.core.api.Assertions.assertThat;

public class InterpreterTest {
    @Test
    void rendersParenthesizedTemplateExpressionAsString() {
        final ST nestedTemplate = new ST("<first>-<second>");
        nestedTemplate.add("first", "inner");
        nestedTemplate.add("second", "value");

        final ST template = new ST("before <(nestedTemplate)> after");
        template.add("nestedTemplate", nestedTemplate);

        final String rendered = template.render();

        assertThat(rendered).isEqualTo("before inner-value after");
    }
}
