/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package image.pull;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;


class DockerImagePullTests {

    private void runImage(String image, boolean shouldSucceed) throws Exception {
        Process process = new ProcessBuilder("docker", "run", image)
                .redirectOutput(new File("hello-world-stdout.txt"))
                .redirectError(new File("hello-world-stderr.txt")).start();

        boolean finished = process.waitFor(5, TimeUnit.SECONDS);
        int retCode = finished ? process.exitValue() : 0;
        if (!shouldSucceed && retCode == 0) {
            throw new IllegalStateException("Docker image should not be pulled successfully");
        }

        if (shouldSucceed && retCode != 0) {
            throw new IllegalStateException("Docker image should be pulled successfully");
        }
    }

    @Test
    void pullAllowedImage() throws Exception {
        runImage("container-registry.oracle.com/mysql/community-server:8.3", true);
    }

    @Test
    void pullNotAllowedImage() throws Exception {
        runImage("hello-world", false);
    }
}
