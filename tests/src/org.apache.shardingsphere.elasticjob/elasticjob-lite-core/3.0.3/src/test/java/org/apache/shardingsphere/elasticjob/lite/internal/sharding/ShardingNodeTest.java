/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.shardingsphere.elasticjob.lite.internal.sharding;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

public final class ShardingNodeTest {

    private final ShardingNode shardingNode = new ShardingNode("test_job");

    @Test
    public void assertGetRunningNode() {
        assertThat(ShardingNode.getRunningNode(0), is("sharding/0/running"));
    }

    @Test
    public void assertGetMisfireNode() {
        assertThat(ShardingNode.getMisfireNode(0), is("sharding/0/misfire"));
    }

    @Test
    public void assertGetItemWhenNotRunningItemPath() {
        assertNull(shardingNode.getItemByRunningItemPath("/test_job/sharding/0/completed"));
    }

    @Test
    public void assertGetItemByRunningItemPath() {
        assertThat(shardingNode.getItemByRunningItemPath("/test_job/sharding/0/running"), is(0));
    }
}
