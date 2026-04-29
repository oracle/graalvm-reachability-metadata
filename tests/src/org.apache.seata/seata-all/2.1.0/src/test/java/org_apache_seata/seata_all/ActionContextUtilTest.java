/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.seata.integration.tx.api.interceptor.ActionContextUtil;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.junit.jupiter.api.Test;

public class ActionContextUtilTest {
    @Test
    void fetchesAnnotatedFieldValuesIntoActionContext() {
        ActionContextUtilFieldFixture fixture = new ActionContextUtilFieldFixture("order-7", 3, "ignored");

        Map<String, Object> context = ActionContextUtil.fetchContextFromObject(fixture);

        assertThat(context)
                .containsEntry("orderId", "order-7")
                .containsEntry("quantity", 3)
                .doesNotContainKey("internalNote")
                .doesNotContainKey("optionalValue");
    }
}

final class ActionContextUtilFieldFixture {
    @BusinessActionContextParameter(paramName = "orderId")
    private final String orderId;

    @BusinessActionContextParameter("quantity")
    private final int quantity;

    @BusinessActionContextParameter("optionalValue")
    private final String optionalValue = null;

    private final String internalNote;

    ActionContextUtilFieldFixture(String orderId, int quantity, String internalNote) {
        this.orderId = orderId;
        this.quantity = quantity;
        this.internalNote = internalNote;
    }
}
