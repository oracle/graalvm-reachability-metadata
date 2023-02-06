/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.curator.framework.imps;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.test.WatchersDebug;
import org.apache.curator.test.compatibility.Timing2;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.ZooKeeper;
import org.awaitility.Awaitility;

import java.util.concurrent.Callable;

@SuppressWarnings("ConstantValue")
public final class TestCleanState {
    private static final boolean IS_ENABLED = Boolean.getBoolean("PROPERTY_VALIDATE_NO_REMAINING_WATCHERS");

    public static void closeAndTestClean(CuratorFramework client) {
        if ((client == null) || !IS_ENABLED) {
            return;
        }
        try {
            Timing2 timing = new Timing2();
            CuratorFrameworkImpl internalClient = (CuratorFrameworkImpl) client;
            EnsembleTracker ensembleTracker = internalClient.getEnsembleTracker();
            if (ensembleTracker != null) {
                Awaitility.await()
                        .until(() -> !ensembleTracker.hasOutstanding());
                ensembleTracker.close();
            }
            ZooKeeper zooKeeper = internalClient.getZooKeeper();
            if (zooKeeper != null) {
                final int maxLoops = 3;
                for (int i = 0; i < maxLoops; ++i) {
                    if (i > 0) {
                        timing.multiple(.5).sleepABit();
                    }
                    boolean isLast = (i + 1) == maxLoops;
                    if (WatchersDebug.getChildWatches(zooKeeper).size() != 0) {
                        if (isLast) {
                            throw new AssertionError("One or more child watchers are still registered: " + WatchersDebug.getChildWatches(zooKeeper));
                        }
                        continue;
                    }
                    if (WatchersDebug.getExistWatches(zooKeeper).size() != 0) {
                        if (isLast) {
                            throw new AssertionError("One or more exists watchers are still registered: " + WatchersDebug.getExistWatches(zooKeeper));
                        }
                        continue;
                    }
                    if (WatchersDebug.getDataWatches(zooKeeper).size() != 0) {
                        if (isLast) {
                            throw new AssertionError("One or more data watchers are still registered: " + WatchersDebug.getDataWatches(zooKeeper));
                        }
                        continue;
                    }
                    break;
                }
            }
        } catch (IllegalStateException ignore) {
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    public static void test(CuratorFramework client, Callable<Void> proc) throws Exception {
        boolean succeeded = false;
        try {
            proc.call();
            succeeded = true;
        } finally {
            if (succeeded) {
                closeAndTestClean(client);
            } else {
                CloseableUtils.closeQuietly(client);
            }
        }
    }

    private TestCleanState() {
    }
}
