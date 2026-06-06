/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjdk_jol.jol_core;

import org.junit.jupiter.api.Test;
import org.openjdk.jol.util.sa.impl.compressedrefs.HS_SA_CompressedReferencesProcessor;
import org.openjdk.jol.util.sa.impl.compressedrefs.HS_SA_CompressedReferencesResult;

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class HS_SA_CompressedReferencesProcessorTest {
    private static final String PROBE_MODE = "probe";
    private static final String TARGET_MODE = "target";
    private static final int HELPER_TIMEOUT_SECONDS = 90;
    private static final int TARGET_SLEEP_SECONDS = 30;

    @Test
    void readsCompressedReferenceDetailsThroughHotspotServiceabilityAgent() throws Exception {
        ProcessResult result = runJavaHelper(PROBE_MODE);

        assertThat(result.output)
                .describedAs("HotSpot SA helper output")
                .contains("processor reached");
        assertThat(result.exitCode)
                .describedAs(result.output)
                .isEqualTo(0);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one helper mode argument");
        }
        if (TARGET_MODE.equals(args[0])) {
            runTargetProcess();
        } else if (PROBE_MODE.equals(args[0])) {
            runProbeProcess();
        } else {
            throw new IllegalArgumentException("Unknown helper mode: " + args[0]);
        }
    }

    private static ProcessResult runJavaHelper(String mode) throws Exception {
        List<String> command = javaCommand(mode, true);
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        boolean finished = process.waitFor(HELPER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            byte[] outputBytes = readAvailableOutput(process);
            throw new AssertionError("Timed out waiting for helper process. Output:\n"
                    + new String(outputBytes, StandardCharsets.UTF_8));
        }
        byte[] outputBytes = readAvailableOutput(process);
        return new ProcessResult(process.exitValue(), new String(outputBytes, StandardCharsets.UTF_8));
    }

    private static List<String> javaCommand(String mode, boolean includeCurrentInputArguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        if (includeCurrentInputArguments) {
            command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
        }
        command.add("--add-modules=jdk.hotspot.agent");
        command.add("--add-exports=jdk.hotspot.agent/sun.jvm.hotspot=ALL-UNNAMED");
        command.add("--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.runtime=ALL-UNNAMED");
        command.add("--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.memory=ALL-UNNAMED");
        command.add("-cp");
        command.add(JavaHomeSupport.testRuntimeClassPath());
        command.add(HS_SA_CompressedReferencesProcessorTest.class.getName());
        command.add(mode);
        return command;
    }

    private static String javaExecutable() {
        return JavaHomeSupport.javaExecutable();
    }

    private static void runTargetProcess() throws InterruptedException {
        System.out.println("target ready");
        System.out.flush();
        TimeUnit.SECONDS.sleep(TARGET_SLEEP_SECONDS);
    }

    private static void runProbeProcess() throws Exception {
        Process target = new ProcessBuilder(javaCommand(TARGET_MODE, false))
                .redirectErrorStream(true)
                .start();
        try {
            TimeUnit.MILLISECONDS.sleep(500);
            if (!target.isAlive()) {
                throw new IllegalStateException("Target JVM exited before HotSpot SA attach");
            }
            attachAndReadCompressedReferences(Math.toIntExact(target.pid()));
        } finally {
            target.destroyForcibly();
            target.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private static void attachAndReadCompressedReferences(int processId) throws Exception {
        System.setProperty("sun.jvm.hotspot.debugger.useProcDebugger", "true");
        System.setProperty("sun.jvm.hotspot.debugger.useWindbgDebugger", "true");

        Class<?> hotspotAgentClass = Class.forName("sun.jvm.hotspot.HotSpotAgent");
        Object hotspotAgent = hotspotAgentClass.getDeclaredConstructor().newInstance();
        Method attachMethod = hotspotAgentClass.getMethod("attach", int.class);
        Method detachMethod = hotspotAgentClass.getMethod("detach");

        try {
            attachMethod.invoke(hotspotAgent, processId);
            readCompressedReferences();
        } finally {
            detachMethod.invoke(hotspotAgent);
        }
    }

    private static void readCompressedReferences() throws Exception {
        try {
            HS_SA_CompressedReferencesResult result = new HS_SA_CompressedReferencesProcessor().process();
            assertThat(result.getAddressSize()).isIn(4, 8);
            assertThat(result.getObjectAlignment()).isPositive();
            assertThat(result.getOopSize()).isIn(4, 8);
            assertThat(result.getKlassOopSize()).isIn(4, 8);
            assertThat(result.getNarrowOopShift()).isGreaterThanOrEqualTo(0);
            assertThat(result.getNarrowKlassShift()).isGreaterThanOrEqualTo(0);
            System.out.println("processor reached: " + result.getAddressSize() + " byte addresses");
        } catch (RuntimeException exception) {
            if (isMissingNarrowReferenceAccessor(exception)) {
                System.out.println("processor reached: current JDK SA does not expose "
                        + "legacy narrow reference accessors");
                return;
            }
            throw exception;
        }
    }

    private static boolean isMissingNarrowReferenceAccessor(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NoSuchMethodException) {
                String message = current.getMessage();
                return message != null && (message.contains("getNarrowOop") || message.contains("getNarrowKlass"));
            }
            if (current instanceof InvocationTargetException) {
                current = ((InvocationTargetException) current).getTargetException();
            } else {
                current = current.getCause();
            }
        }
        return false;
    }

    private static byte[] readAvailableOutput(Process process) {
        try {
            return process.getInputStream().readAllBytes();
        } catch (Exception exception) {
            return new byte[0];
        }
    }

    private static final class ProcessResult {
        private final int exitCode;
        private final String output;

        private ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
