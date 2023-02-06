/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.curator;

import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.utils.EnsurePath;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("deprecation")
public class TestEnsurePath {
    @Test
    @Disabled("https://github.com/mockito/mockito/issues/2435")
    public void testBasic() throws Exception {
        ZooKeeper client = mock(ZooKeeper.class, Mockito.RETURNS_MOCKS);
        CuratorZookeeperClient curator = mock(CuratorZookeeperClient.class);
        RetryPolicy retryPolicy = new RetryOneTime(1);
        RetryLoop retryLoop = new RetryLoopImpl(retryPolicy, null);
        when(curator.getZooKeeper()).thenReturn(client);
        when(curator.getRetryPolicy()).thenReturn(retryPolicy);
        when(curator.newRetryLoop()).thenReturn(retryLoop);
        Stat fakeStat = mock(Stat.class);
        when(client.exists(Mockito.any(), anyBoolean())).thenReturn(fakeStat);
        EnsurePath ensurePath = new EnsurePath("/one/two/three");
        ensurePath.ensure(curator);
        verify(client, times(3)).exists(Mockito.any(), anyBoolean());
        ensurePath.ensure(curator);
        verifyNoMoreInteractions(client);
        ensurePath.ensure(curator);
        verifyNoMoreInteractions(client);
    }

    @Test
    @Disabled("https://github.com/mockito/mockito/issues/2435")
    public void testSimultaneous() throws Exception {
        ZooKeeper client = mock(ZooKeeper.class, Mockito.RETURNS_MOCKS);
        RetryPolicy retryPolicy = new RetryOneTime(1);
        RetryLoop retryLoop = new RetryLoopImpl(retryPolicy, null);
        final CuratorZookeeperClient curator = mock(CuratorZookeeperClient.class);
        when(curator.getZooKeeper()).thenReturn(client);
        when(curator.getRetryPolicy()).thenReturn(retryPolicy);
        when(curator.newRetryLoop()).thenReturn(retryLoop);
        final Stat fakeStat = mock(Stat.class);
        final CountDownLatch startedLatch = new CountDownLatch(2);
        final CountDownLatch finishedLatch = new CountDownLatch(2);
        final Semaphore semaphore = new Semaphore(0);
        when(client.exists(Mockito.any(), anyBoolean())).thenAnswer((Answer<Stat>) invocation -> {
            semaphore.acquire();
            return fakeStat;
        });
        final EnsurePath ensurePath = new EnsurePath("/one/two/three");
        ExecutorService service = Executors.newCachedThreadPool();
        IntStream.range(0, 2).mapToObj(i -> (Callable<Void>) () -> {
            startedLatch.countDown();
            ensurePath.ensure(curator);
            finishedLatch.countDown();
            return null;
        }).forEach(service::submit);
        assertTrue(startedLatch.await(10, TimeUnit.SECONDS));
        semaphore.release(3);
        assertTrue(finishedLatch.await(10, TimeUnit.SECONDS));
        verify(client, times(3)).exists(Mockito.any(), anyBoolean());
        ensurePath.ensure(curator);
        verifyNoMoreInteractions(client);
        ensurePath.ensure(curator);
        verifyNoMoreInteractions(client);
    }
}
