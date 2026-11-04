/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl_ognl;

import ognl.Ognl;
import ognl.OgnlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises OGNL's assignment and sequence-expression subsystem for an order review workflow.
 *
 * An order service evaluates one configured expression to normalize submitted review data, mark it
 * ready for review, and produce the label shown to the next application step.
 */
public class OrderReviewAssignmentExpressionTest {
    @Test
    @Timeout(60)
    void normalizesAndStagesOrderReviewWithOneExpression() throws OgnlException {
        OrderReview review = new OrderReview("  Alex Morgan  ", "DRAFT");
        Map context = Ognl.createDefaultContext(review);
        Object stagedLabel = Ognl.getValue(
                Ognl.parseExpression(
                        "(customerName = customerName.trim(), "
                                + "status = 'READY_FOR_REVIEW', "
                                + "customerName + ' / ' + status)"),
                context,
                review);

        assertThat(stagedLabel).isEqualTo("Alex Morgan / READY_FOR_REVIEW");
        assertThat(review.getCustomerName()).isEqualTo("Alex Morgan");
        assertThat(review.getStatus()).isEqualTo("READY_FOR_REVIEW");
    }

    public static final class OrderReview {
        private String customerName;
        private String status;

        private OrderReview(String customerName, String status) {
            this.customerName = customerName;
            this.status = status;
        }

        public String getCustomerName() {
            return customerName;
        }

        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
