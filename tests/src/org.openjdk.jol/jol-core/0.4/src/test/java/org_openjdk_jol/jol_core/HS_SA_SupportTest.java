/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjdk_jol.jol_core;

import org.junit.jupiter.api.Test;
import org.openjdk.jol.util.HS_SA_Support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamClass;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class HS_SA_SupportTest {
    private static final String INIT_MODE = "init";
    private static final String MAIN_MODE = "main";
    private static final String TARGET_MODE = "target";
    private static final int HELPER_TIMEOUT_SECONDS = 45;
    private static final int TARGET_SLEEP_SECONDS = 30;
    private static final int AGENT_TIMEOUT_MILLISECONDS = 5_000;
    private static final String HS_SA_REQUEST_CLASS_NAME = "org.openjdk.jol.util.HS_SA_Support$HS_SA_Request";

    @Test
    void initializesHotspotServiceabilityAgentSupportWithVmManagementAccess() throws Exception {
        ProcessResult result = runJavaHelper(INIT_MODE, false);

        assertThat(result.output)
                .describedAs("HotSpot SA support initialization output")
                .contains("support initialized");
        assertThat(result.exitCode)
                .describedAs(result.output)
                .isEqualTo(0);
    }

    @Test
    void mainConsumesSerializedAgentRequestAndWritesResponse() throws Exception {
        ProcessResult result = runJavaHelper(MAIN_MODE, false);

        assertThat(result.output)
                .describedAs("HotSpot SA support main output")
                .contains("main reached");
        assertThat(result.exitCode)
                .describedAs(result.output)
                .isEqualTo(0);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one helper mode argument");
        }
        if (INIT_MODE.equals(args[0])) {
            initializeSupport();
        } else if (MAIN_MODE.equals(args[0])) {
            invokeSupportMainAgainstChildJvm();
        } else if (TARGET_MODE.equals(args[0])) {
            runTargetProcess();
        } else {
            throw new IllegalArgumentException("Unknown helper mode: " + args[0]);
        }
    }

    private static ProcessResult runJavaHelper(String mode, boolean includeCurrentInputArguments) throws Exception {
        List<String> command = javaCommand(mode, includeCurrentInputArguments);
        ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
        processBuilder.environment().remove("JDK_JAVA_OPTIONS");
        processBuilder.environment().remove("JAVA_TOOL_OPTIONS");
        Process process = processBuilder.start();
        CapturedProcessOutput capturedOutput = captureProcessOutput(process);

        boolean finished = process.waitFor(HELPER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            byte[] outputBytes = capturedOutput.awaitBytes();
            throw new AssertionError("Timed out waiting for helper process. Output:\n"
                    + new String(outputBytes, StandardCharsets.UTF_8));
        }
        byte[] outputBytes = capturedOutput.awaitBytes();
        return new ProcessResult(process.exitValue(), new String(outputBytes, StandardCharsets.UTF_8));
    }

    private static CapturedProcessOutput captureProcessOutput(Process process) {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        Thread outputReader = new Thread(() -> {
            try (InputStream input = process.getInputStream()) {
                input.transferTo(outputBytes);
            } catch (IOException ignored) {
            }
        }, "jol-core-hs-sa-support-output-reader");
        outputReader.setDaemon(true);
        outputReader.start();
        return new CapturedProcessOutput(outputReader, outputBytes);
    }

    private static List<String> javaCommand(String mode, boolean includeCurrentInputArguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        if (includeCurrentInputArguments) {
            command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
        }
        command.addAll(hotspotServiceabilityAgentOptionList());
        command.add("-cp");
        command.add(JavaHomeSupport.testRuntimeClassPath());
        command.add(HS_SA_SupportTest.class.getName());
        command.add(mode);
        return command;
    }

    private static List<String> hotspotServiceabilityAgentOptionList() {
        List<String> options = new ArrayList<>();
        options.add("--add-modules=jdk.hotspot.agent");
        options.add("--add-exports=java.management/sun.management=ALL-UNNAMED");
        options.add("--add-opens=java.management/sun.management=ALL-UNNAMED");
        options.add("--add-exports=jdk.hotspot.agent/sun.jvm.hotspot=ALL-UNNAMED");
        options.add("--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.debugger=ALL-UNNAMED");
        options.add("--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.runtime=ALL-UNNAMED");
        options.add("--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.memory=ALL-UNNAMED");
        return options;
    }

    private static String javaExecutable() {
        return JavaHomeSupport.javaExecutable();
    }

    private static void initializeSupport() {
        boolean enabled = HS_SA_Support.isEnable();
        System.out.println("support initialized: " + enabled);
    }

    private static void invokeSupportMainAgainstChildJvm() throws Exception {
        Process target = new ProcessBuilder(javaCommand(TARGET_MODE, false))
                .redirectErrorStream(true)
                .start();
        try {
            TimeUnit.MILLISECONDS.sleep(500);
            if (!target.isAlive()) {
                byte[] outputBytes = target.getInputStream().readAllBytes();
                throw new IllegalStateException("Target JVM exited before HotSpot SA attach. Output:\n"
                        + new String(outputBytes, StandardCharsets.UTF_8));
            }
            byte[] response = invokeSupportMain(Math.toIntExact(target.pid()));
            assertThat(response).describedAs("serialized HotSpot SA response").isNotEmpty();
            System.out.println("main reached: " + response.length + " response bytes");
        } finally {
            target.destroyForcibly();
            target.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private static byte[] invokeSupportMain(int processId) throws Exception {
        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;
        ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
        try (PrintStream responseOut = new PrintStream(responseBytes, true, StandardCharsets.ISO_8859_1.name())) {
            System.setIn(new ByteArrayInputStream(serializedRequest(processId)));
            System.setOut(responseOut);
            HS_SA_Support.main(new String[0]);
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
        return responseBytes.toByteArray();
    }

    private static byte[] serializedRequest(int processId) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeShort(0xACED);
            out.writeShort(5);
            out.writeByte(0x73); // TC_OBJECT
            out.writeByte(0x72); // TC_CLASSDESC
            out.writeUTF(HS_SA_REQUEST_CLASS_NAME);
            out.writeLong(requestSerialVersionUid());
            out.writeByte(0x02); // SC_SERIALIZABLE
            out.writeShort(3);
            out.writeByte('I');
            out.writeUTF("processId");
            out.writeByte('I');
            out.writeUTF("timeout");
            out.writeByte('L');
            out.writeUTF("processor");
            out.writeByte(0x74); // TC_STRING
            out.writeUTF("Lorg/openjdk/jol/util/sa/HS_SA_Processor;");
            out.writeByte(0x78); // TC_ENDBLOCKDATA
            out.writeByte(0x70); // TC_NULL superclass
            out.writeInt(processId);
            out.writeInt(AGENT_TIMEOUT_MILLISECONDS);
            out.writeByte(0x70); // null processor
        }
        return bytes.toByteArray();
    }

    private static long requestSerialVersionUid() throws ClassNotFoundException {
        Class<?> requestClass = ClassLoader.getSystemClassLoader().loadClass(HS_SA_REQUEST_CLASS_NAME);
        return ObjectStreamClass.lookup(requestClass).getSerialVersionUID();
    }

    private static void runTargetProcess() throws InterruptedException {
        System.out.println("target ready");
        System.out.flush();
        TimeUnit.SECONDS.sleep(TARGET_SLEEP_SECONDS);
    }

    private static final class ProcessResult {
        private final int exitCode;
        private final String output;

        private ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private static final class CapturedProcessOutput {
        private final Thread outputReader;
        private final ByteArrayOutputStream outputBytes;

        private CapturedProcessOutput(Thread outputReader, ByteArrayOutputStream outputBytes) {
            this.outputReader = outputReader;
            this.outputBytes = outputBytes;
        }

        private byte[] awaitBytes() throws InterruptedException {
            outputReader.join(TimeUnit.SECONDS.toMillis(5));
            return outputBytes.toByteArray();
        }
    }
}
