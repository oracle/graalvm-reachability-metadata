/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.restart.RestartInitializer;
import org.springframework.boot.devtools.restart.Restarter;
import org.springframework.boot.devtools.restart.classloader.RestartClassLoader;

import static org.assertj.core.api.Assertions.assertThat;

public class RestarterTest {

    private static final long RELAUNCH_TIMEOUT_MILLIS = 10_000;

    @BeforeEach
    void clearRestarterBeforeTest() {
        Restarter.clearInstance();
        RestartingApplication.reset();
    }

    @AfterEach
    void clearRestarterAfterTest() {
        Restarter.clearInstance();
    }

    @Test
    void initializePreparesRestartAndRestartCleansCachesBeforeRelaunching() throws Exception {
        RestartingApplication.main(new String[] { "restart" });

        waitForRelaunch();

        assertThat(RestartingApplication.relaunched).isTrue();
        assertThat(RestartingApplication.relaunchArguments).containsExactly("relaunched");
        assertThat(RestartingApplication.relaunchContextClassLoader).isInstanceOf(RestartClassLoader.class);
    }

    private static void waitForRelaunch() throws InterruptedException {
        long deadline = System.currentTimeMillis() + RELAUNCH_TIMEOUT_MILLIS;
        while (!RestartingApplication.relaunched && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
    }

}

class RestartingApplication {

    static volatile boolean relaunched;

    static volatile String[] relaunchArguments;

    static volatile ClassLoader relaunchContextClassLoader;

    static void reset() {
        relaunched = false;
        relaunchArguments = null;
        relaunchContextClassLoader = null;
    }

    static void main(String[] args) {
        if (args.length > 0 && "restart".equals(args[0])) {
            Restarter.initialize(new String[] { "relaunched" }, false, RestartInitializer.NONE, false);
            Restarter.getInstance().restart();
            return;
        }
        relaunched = true;
        relaunchArguments = args.clone();
        relaunchContextClassLoader = Thread.currentThread().getContextClassLoader();
    }

}
