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
public class GetFieldInvokerTest {
    @Test
    void readsFieldsWithoutBeanGetters() {
        Invoice invoice = new Invoice();
        invoice.reference = "INV-42";
        invoice.total = 125;
        Probe probe = ProbeFactory.getProbe(invoice);
        assertThat(probe.getObject(invoice, "reference")).isEqualTo("INV-42");
        assertThat(probe.getObject(invoice, "total")).isEqualTo(125);
        assertThat(probe.getPropertyTypeForGetter(invoice, "total")).isEqualTo(int.class);
    }

    public static class Invoice {
        public String reference;
        public int total;
    }
}
