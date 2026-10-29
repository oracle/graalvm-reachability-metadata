/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ibatis.ibatis_sqlmap;

import com.ibatis.common.beans.Probe;
import com.ibatis.common.beans.ProbeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class SetFieldInvokerTest {
    @Test
    void writesReferenceAndPrimitiveFieldsWithoutBeanSetters() {
        Stock stock = new Stock();
        Probe probe = ProbeFactory.getProbe(stock);
        probe.setObject(stock, "product", "pencil");
        probe.setObject(stock, "quantity", 24);
        assertThat(stock.product).isEqualTo("pencil");
        assertThat(stock.quantity).isEqualTo(24);
        probe.setObject(stock, "product", null);
        assertThat(stock.product).isNull();
    }

    public static class Stock {
        public String product;
        public int quantity;
    }
}
