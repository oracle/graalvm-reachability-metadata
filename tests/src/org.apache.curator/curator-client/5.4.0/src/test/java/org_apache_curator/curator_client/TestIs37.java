/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import org.apache.curator.test.compatibility.CuratorTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestIs37 extends CuratorTestBase {
    @Test
    @Tag(zk37Group)
    public void testIsZk37() throws Exception {
        assertNotNull(Class.forName("org.apache.zookeeper.proto.WhoAmIResponse"));
    }

    @Override
    protected void createServer() {
    }
}
