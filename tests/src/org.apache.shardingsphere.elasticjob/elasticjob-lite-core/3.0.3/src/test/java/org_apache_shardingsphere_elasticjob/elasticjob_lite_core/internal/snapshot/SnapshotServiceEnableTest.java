/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.internal.snapshot;

import org_apache_shardingsphere_elasticjob.elasticjob_lite_core.fixture.job.DetailedFooJob;
import org.apache.shardingsphere.elasticjob.lite.internal.snapshot.SnapshotService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public final class SnapshotServiceEnableTest extends BaseSnapshotServiceTest {
    public SnapshotServiceEnableTest() {
        super(new DetailedFooJob());
    }

    @BeforeEach
    public void listenMonitor() {
        getSnapshotService().listen();
    }

    @AfterEach
    public void closeMonitor() {
        getSnapshotService().close();
    }

    @Test
    public void assertMonitorWithCommand() throws IOException {
        assertNotNull(SocketUtils.sendCommand(SnapshotService.DUMP_COMMAND + getJobName(), DUMP_PORT));
        assertNull(SocketUtils.sendCommand("unknown_command", DUMP_PORT));
    }
}
