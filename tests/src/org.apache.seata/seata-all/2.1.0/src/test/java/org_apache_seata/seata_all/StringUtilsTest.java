/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.seata.common.util.StringUtils;
import org.junit.jupiter.api.Test;

public class StringUtilsTest {
    @Test
    void toStringReadsDeclaredAndInheritedFields() {
        TransactionSnapshot snapshot = new TransactionSnapshot("global-order", 17, "prepared");
        snapshot.parent = snapshot;

        String description = StringUtils.toString(snapshot);

        assertThat(description).startsWith("TransactionSnapshot(")
                .endsWith(")")
                .contains("transactionName=\"global-order\"")
                .contains("branchId=17")
                .contains("state=\"prepared\"")
                .contains("parent=(this TransactionSnapshot)");
    }

    public static class BaseSnapshot {
        private final String transactionName;

        public BaseSnapshot(String transactionName) {
            this.transactionName = transactionName;
        }
    }

    public static class TransactionSnapshot extends BaseSnapshot {
        private final int branchId;
        private final String state;
        private TransactionSnapshot parent;

        public TransactionSnapshot(String transactionName, int branchId, String state) {
            super(transactionName);
            this.branchId = branchId;
            this.state = state;
        }
    }
}
