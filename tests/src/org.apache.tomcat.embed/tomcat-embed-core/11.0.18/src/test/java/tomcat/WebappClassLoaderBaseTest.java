/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.loader.ParallelWebappClassLoader;
import org.apache.catalina.loader.WebappClassLoaderBase;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class WebappClassLoaderBaseTest {

    private static final String PARENT_ONLY_RESOURCE = "tomcat/TomcatTests.class";
    private static final String PARENT_ONLY_CLASS = "tomcat.TomcatTests";

    @TempDir
    private Path temporaryDirectory;

    @Test
    void delegatesParentResourceAndClassLookups() throws Exception {
        try (TestWebapp webapp = new TestWebapp(temporaryDirectory)) {
            WebappClassLoaderBase loader = webapp.getLoader();

            loader.setDelegate(true);
            assertThat(loader.getResource(PARENT_ONLY_RESOURCE)).isNotNull();
            assertThat(loader.loadClass(PARENT_ONLY_CLASS)).isSameAs(TomcatTests.class);

            loader.setDelegate(false);
            assertThat(loader.getResource(PARENT_ONLY_RESOURCE)).isNotNull();
            try (InputStream stream = loader.getResourceAsStream(PARENT_ONLY_RESOURCE)) {
                assertThat(stream).isNotNull();
            }
        }
    }

    @Test
    void stopScansThreadLocalRmiTimerAndExecutorReferences() throws Exception {
        try (TestWebapp webapp = new TestWebapp(temporaryDirectory)) {
            WebappClassLoaderBase loader = webapp.getLoader();
            loader.setClearReferencesStopThreads(true);
            loader.setClearReferencesStopTimerThreads(true);

            ThreadLocal<String> threadLocal = new ThreadLocal<>();
            InheritableThreadLocal<String> inheritableThreadLocal = new InheritableThreadLocal<>();
            threadLocal.set("webapp value");
            inheritableThreadLocal.set("webapp inherited value");

            Timer timer = createTimerWithContextClassLoader(loader);
            TestRemoteImpl remote = exportRemoteWithContextClassLoader(loader);
            ThreadPoolExecutor targetExecutor = createExecutor(new TargetThreadFactory(loader));
            ThreadPoolExecutor holderExecutor = createExecutor(new HolderThreadFactory(loader));

            CountDownLatch runningTasks = new CountDownLatch(2);
            targetExecutor.execute(new ParkingTask(runningTasks));
            holderExecutor.execute(new ParkingTask(runningTasks));
            assertThat(runningTasks.await(5, TimeUnit.SECONDS)).isTrue();

            try {
                webapp.stopLoader();
            } finally {
                threadLocal.remove();
                inheritableThreadLocal.remove();
                timer.cancel();
                targetExecutor.shutdownNow();
                holderExecutor.shutdownNow();
                unexportRemote(remote);
            }
        }
    }

    private static Timer createTimerWithContextClassLoader(ClassLoader loader) {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            Timer timer = new Timer("webapp-class-loader-cleanup-timer");
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    // Keep the timer thread alive until WebappClassLoaderBase stops it.
                }
            }, Duration.ofMinutes(1).toMillis());
            return timer;
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    private static TestRemoteImpl exportRemoteWithContextClassLoader(ClassLoader loader) throws RemoteException {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            TestRemoteImpl remote = new TestRemoteImpl();
            UnicastRemoteObject.exportObject(remote, 0);
            return remote;
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    private static ThreadPoolExecutor createExecutor(ThreadFactory threadFactory) {
        return new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), threadFactory);
    }

    private static void unexportRemote(Remote remote) throws RemoteException {
        try {
            UnicastRemoteObject.unexportObject(remote, true);
        } catch (NoSuchObjectException e) {
            assertThat(e).isInstanceOf(NoSuchObjectException.class);
        }
    }

    private interface TestRemote extends Remote {
        void ping() throws RemoteException;
    }

    private static final class TestRemoteImpl implements TestRemote {
        @Override
        public void ping() {
        }
    }

    private static final class ParkingTask implements Runnable {
        private final CountDownLatch runningTasks;

        private ParkingTask(CountDownLatch runningTasks) {
            this.runningTasks = runningTasks;
        }

        @Override
        public void run() {
            runningTasks.countDown();
            LockSupport.parkNanos(Duration.ofSeconds(30).toNanos());
        }
    }

    private static final class TargetThreadFactory implements ThreadFactory {
        private final ClassLoader loader;

        private TargetThreadFactory(ClassLoader loader) {
            this.loader = loader;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            return new TargetThread(runnable, loader);
        }
    }

    private static final class HolderThreadFactory implements ThreadFactory {
        private final ClassLoader loader;

        private HolderThreadFactory(ClassLoader loader) {
            this.loader = loader;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            return new HolderThread(runnable, loader);
        }
    }

    public static final class TargetThread extends Thread {
        public final Object target;

        private TargetThread(Runnable target, ClassLoader loader) {
            super(target, "webapp-class-loader-target-thread");
            this.target = target;
            setContextClassLoader(loader);
            setDaemon(true);
        }
    }

    public static final class HolderThread extends Thread {
        public final Holder holder;

        private HolderThread(Runnable task, ClassLoader loader) {
            super(task, "webapp-class-loader-holder-thread");
            this.holder = new Holder(task);
            setContextClassLoader(loader);
            setDaemon(true);
        }
    }

    public static final class Holder {
        public final Object task;

        private Holder(Object task) {
            this.task = task;
        }
    }

    private static final class TestWebapp implements AutoCloseable {
        private final StandardRoot root;
        private final ParallelWebappClassLoader loader;

        private TestWebapp(Path baseDirectory) throws IOException, LifecycleException {
            Path webInfClasses = baseDirectory.resolve("WEB-INF").resolve("classes");
            Path webInfLib = baseDirectory.resolve("WEB-INF").resolve("lib");
            Files.createDirectories(webInfClasses);
            Files.createDirectories(webInfLib);

            StandardContext context = new StandardContext();
            context.setName("webapp-class-loader-test");
            context.setPath("/webapp-class-loader-test");
            context.setDocBase(baseDirectory.toString());

            StandardHost host = new StandardHost();
            host.setName("localhost");
            context.setParent(host);

            root = new StandardRoot(context);
            root.start();

            loader = new ParallelWebappClassLoader(WebappClassLoaderBaseTest.class.getClassLoader());
            loader.setResources(root);
            loader.start();
        }

        private WebappClassLoaderBase getLoader() {
            return loader;
        }

        private void stopLoader() throws LifecycleException {
            if (loader.getState().isAvailable()) {
                loader.stop();
            }
        }

        @Override
        public void close() throws LifecycleException {
            stopLoader();
            if (loader.getState() != LifecycleState.DESTROYED) {
                loader.destroy();
            }
            if (root.getState().isAvailable()) {
                root.stop();
            }
            if (root.getState() != LifecycleState.DESTROYED) {
                root.destroy();
            }
        }
    }
}
