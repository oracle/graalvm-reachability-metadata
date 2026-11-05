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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises OGNL's static-field access subsystem for a tax-report template.
 *
 * A reporting application lets its templates select the JDK rounding policy used to display
 * calculated amounts, keeping that policy in the expression rather than in the report model.
 */
public class TaxReportStaticFieldAccessTest {
    @Test
    @Timeout(60)
    void readsRoundingPolicyAndUsesItToFormatReportAmount() throws OgnlException {
        TaxReport report = new TaxReport(new BigDecimal("125.675"));
        Map context = Ognl.createDefaultContext(report);

        Object roundingPolicy = Ognl.getValue(
                "@java.math.RoundingMode@HALF_UP", context, report);
        Object roundedTaxableAmount = Ognl.getValue(
                "amount.setScale(2, @java.math.RoundingMode@HALF_UP)", context, report);

        assertThat(roundingPolicy).isEqualTo(RoundingMode.HALF_UP);
        assertThat(roundedTaxableAmount).isEqualTo(new BigDecimal("125.68"));
    }

    public static final class TaxReport {
        private final BigDecimal amount;

        private TaxReport(BigDecimal amount) {
            this.amount = amount;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }
}
