/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_client;

import com.clickhouse.client.ClickHouseLoadBalancingPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClickHouseLoadBalancingPolicyTest extends ClickHouseLoadBalancingPolicy {
    @Test
    void createsCustomPolicyFromClassName() {
        ClickHouseLoadBalancingPolicy policy = ClickHouseLoadBalancingPolicy.of(ClickHouseLoadBalancingPolicyTest.class.getName());

        assertThat(policy).isInstanceOf(ClickHouseLoadBalancingPolicyTest.class);
    }
}
