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
public class MethodInvokerTest {
    @Test
    void invokesBeanAccessorsIncludingSetterLogic() {
        Account account = new Account();
        Probe probe = ProbeFactory.getProbe(account);
        probe.setObject(account, "name", "  Grace  ");
        assertThat(account.getName()).isEqualTo("Grace");
        assertThat(probe.getObject(account, "name")).isEqualTo("Grace");
        probe.setObject(account, "name", "  Linus ");
        assertThat(probe.getObject(account, "name")).isEqualTo("Linus");
    }

    public static class Account {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name.trim();
        }
    }
}
