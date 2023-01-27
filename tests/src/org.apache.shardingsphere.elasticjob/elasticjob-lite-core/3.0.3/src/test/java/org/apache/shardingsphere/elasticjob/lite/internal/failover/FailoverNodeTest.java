/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.shardingsphere.elasticjob.lite.internal.failover;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

public final class FailoverNodeTest {
    private final FailoverNode failoverNode = new FailoverNode("test_job");

    @Test
    public void assertGetItemsNode() {
        assertThat(FailoverNode.getItemsNode(0), is("leader/failover/items/0"));
    }

    @Test
    public void assertGetExecutionFailoverNode() {
        assertThat(FailoverNode.getExecutionFailoverNode(0), is("sharding/0/failover"));
    }

    @Test
    public void assertGetItemWhenNotExecutionFailoverPath() {
        assertNull(failoverNode.getItemByExecutionFailoverPath("/test_job/sharding/0/completed"));
    }

    @Test
    public void assertGetItemByExecutionFailoverPath() {
        assertThat(failoverNode.getItemByExecutionFailoverPath("/test_job/sharding/0/failover"), is(0));
    }

    @Test
    public void assertGetProcessingFailoverNode() {
        assertThat(FailoverNode.getExecutingFailoverNode(0), is("sharding/0/failovering"));
    }
}
