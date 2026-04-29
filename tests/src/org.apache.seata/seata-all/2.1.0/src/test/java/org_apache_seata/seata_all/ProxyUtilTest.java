/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.seata.integration.tx.api.util.ProxyUtil;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.junit.jupiter.api.Test;

public class ProxyUtilTest {
    @Test
    void createsSubclassProxyForTransactionalService() {
        TransactionalService target = new TransactionalService("original-message");

        TransactionalService proxy = ProxyUtil.createProxy(target, "transactionalService");

        assertThat(proxy).isNotSameAs(target);
        assertThat(proxy).isInstanceOf(TransactionalService.class);
        assertThat(proxy.getClass()).isNotEqualTo(TransactionalService.class);
    }

    @Test
    void reusesPreviouslyCreatedProxyForSameTarget() {
        TransactionalService target = new TransactionalService("cached-message");

        TransactionalService proxy = ProxyUtil.createProxy(target, "cachedService");

        assertThat(ProxyUtil.createProxy(target, "sameCachedService")).isSameAs(proxy);
    }

    public static class TransactionalService {
        private String message;

        public TransactionalService() {
            this("default-message");
        }

        public TransactionalService(String message) {
            this.message = message;
        }

        public String readMessage() {
            return message;
        }

        @GlobalTransactional
        public void updateMessage(String newMessage) {
            this.message = newMessage;
        }
    }
}
