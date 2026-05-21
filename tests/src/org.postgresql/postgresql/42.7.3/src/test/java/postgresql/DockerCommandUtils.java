/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class DockerCommandUtils {

    private DockerCommandUtils() {
    }

    static String commandOutput(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).start();
        CompletableFuture<byte[]> standardOutputFuture = CompletableFuture.supplyAsync(
                () -> readAllBytes(process.getInputStream()));
        CompletableFuture<byte[]> errorOutputFuture = CompletableFuture.supplyAsync(
                () -> readAllBytes(process.getErrorStream()));
        int exitCode = process.waitFor();

        String standardOutput = decodeOutput(standardOutputFuture);
        String errorOutput = decodeOutput(errorOutputFuture);
        if (exitCode != 0) {
            String message = standardOutput.isEmpty() ? errorOutput : standardOutput;
            if (!standardOutput.isEmpty() && !errorOutput.isEmpty()) {
                message = standardOutput + System.lineSeparator() + errorOutput;
            }
            throw new IllegalStateException(
                    "Command failed with exit code " + exitCode + ": " + String.join(" ", command)
                            + (message.isEmpty() ? "" : System.lineSeparator() + message));
        }
        return standardOutput;
    }

    private static byte[] readAllBytes(InputStream inputStream) {
        try (InputStream stream = inputStream) {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String decodeOutput(CompletableFuture<byte[]> outputFuture) throws IOException {
        try {
            return new String(outputFuture.join(), StandardCharsets.UTF_8).trim();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof UncheckedIOException uncheckedIOException) {
                throw uncheckedIOException.getCause();
            }
            throw exception;
        }
    }
}
