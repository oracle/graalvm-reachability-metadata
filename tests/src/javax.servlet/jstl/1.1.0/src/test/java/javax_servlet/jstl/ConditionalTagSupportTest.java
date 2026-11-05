/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.jstl;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.jstl.core.ConditionalTagSupport;
import javax.servlet.jsp.tagext.Tag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConditionalTagSupportTest {
    @Test
    void controlsBodyEvaluationUsingTheCurrentCondition() throws JspException {
        MinimumQuantityTag tag = new MinimumQuantityTag();
        try {
            tag.setQuantity(5);
            assertEquals(Tag.EVAL_BODY_INCLUDE, tag.doStartTag());
            assertEquals(Tag.EVAL_PAGE, tag.doEndTag());

            tag.setQuantity(1);
            assertEquals(Tag.SKIP_BODY, tag.doStartTag());
            assertEquals(Tag.EVAL_PAGE, tag.doEndTag());
        } finally {
            tag.release();
        }
    }

    private static final class MinimumQuantityTag extends ConditionalTagSupport {
        private int quantity;

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        @Override
        protected boolean condition() {
            return quantity >= 3;
        }
    }
}
