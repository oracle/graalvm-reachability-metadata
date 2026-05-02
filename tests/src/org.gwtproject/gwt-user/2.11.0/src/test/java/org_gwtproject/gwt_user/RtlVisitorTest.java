/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.resources.css.RtlVisitor;
import com.google.gwt.resources.css.ast.CssProperty;
import com.google.gwt.resources.css.ast.CssProperty.IdentValue;
import com.google.gwt.resources.css.ast.CssProperty.ListValue;
import com.google.gwt.resources.css.ast.CssProperty.NumberValue;
import com.google.gwt.resources.css.ast.CssProperty.Value;
import com.google.gwt.resources.css.ast.CssRule;
import com.google.gwt.resources.css.ast.CssSelector;
import com.google.gwt.resources.css.ast.CssStylesheet;

import org.junit.jupiter.api.Test;

public class RtlVisitorTest {
    @Test
    public void invokesSingleValueAndListPropertyHandlers() {
        CssProperty textAlign = property("text-align", new IdentValue("left"));
        CssProperty margin = property("margin",
                new NumberValue(1, "px"),
                new NumberValue(2, "px"),
                new NumberValue(3, "px"),
                new NumberValue(4, "px"));
        CssStylesheet stylesheet = stylesheet(rule(".navigation", textAlign, margin));

        new RtlVisitor().accept(stylesheet);

        assertThat(textAlign.getValues().toCss()).isEqualTo("right");
        assertThat(margin.getValues().toCss()).isEqualTo("1px 4px 3px 2px");
    }

    private static CssStylesheet stylesheet(CssRule rule) {
        CssStylesheet stylesheet = new CssStylesheet();
        stylesheet.getNodes().add(rule);
        return stylesheet;
    }

    private static CssRule rule(String selector, CssProperty... properties) {
        CssRule rule = new CssRule();
        rule.getSelectors().add(new CssSelector(selector));
        for (CssProperty property : properties) {
            rule.getProperties().add(property);
        }
        return rule;
    }

    private static CssProperty property(String name, Value... values) {
        return new CssProperty(name, new ListValue(values), false);
    }
}
