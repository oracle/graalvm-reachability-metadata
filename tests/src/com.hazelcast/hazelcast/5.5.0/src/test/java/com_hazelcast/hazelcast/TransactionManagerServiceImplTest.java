/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.transaction.TransactionContext;
import com.hazelcast.transaction.TransactionOptions;
import com.hazelcast.transaction.TransactionalMap;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionManagerServiceImplTest {
    @Test
    void commitsAndRollsBackTransactionalMapChanges() {
        Config config = new Config();
        config.setClusterName("transaction-test");
        config.getNetworkConfig().setPort(0);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);

        HazelcastInstance instance = Hazelcast.newHazelcastInstance(config);
        try {
            IMap<String, Integer> inventory = instance.getMap("transactional-inventory");
            inventory.put("widgets", 10);
            TransactionOptions options = new TransactionOptions().setTimeout(30, TimeUnit.SECONDS);

            TransactionContext committed = instance.newTransactionContext(options);
            committed.beginTransaction();
            TransactionalMap<String, Integer> committedInventory = committed.getMap("transactional-inventory");
            assertThat(committedInventory.getForUpdate("widgets")).isEqualTo(10);
            committedInventory.put("widgets", 7);
            committed.commitTransaction();
            assertThat(inventory.get("widgets")).isEqualTo(7);

            TransactionContext rolledBack = instance.newTransactionContext(options);
            rolledBack.beginTransaction();
            TransactionalMap<String, Integer> rolledBackInventory = rolledBack.getMap("transactional-inventory");
            assertThat(rolledBackInventory.getForUpdate("widgets")).isEqualTo(7);
            rolledBackInventory.put("widgets", 0);
            rolledBack.rollbackTransaction();
            assertThat(inventory.get("widgets")).isEqualTo(7);
        } finally {
            instance.shutdown();
        }
    }
}
