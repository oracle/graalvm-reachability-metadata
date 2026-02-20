/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecSpec;
import org.graalvm.internal.tck.harness.TckExtension;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

import static org.graalvm.internal.tck.Utils.readIndexFile;
import static org.graalvm.internal.tck.Utils.splitCoordinates;

/**
 * Abstract task that is used to invoke test subprojects.
 */
@SuppressWarnings("unused")
public abstract class AbstractSubprojectTask extends DefaultTask {

    protected final TckExtension tckExtension;
    private final String coordinates;

    @Inject
    public abstract ExecOperations getExecOperations();

    @Input
    public abstract List<String> getCommand();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public final Set<File> getInputFiles() {
        var inputFiles = getProject().getObjects().fileCollection();
        Path metadataDir = tckExtension.getMetadataDir(coordinates);
        Path testDir = tckExtension.getTestDir(coordinates);
        try {
            inputFiles.from(getProject().files(tckExtension.getMetadataFileList(metadataDir)));
        } catch (IOException e) {
            throw new GradleException("Failed to list metadata files for " + metadataDir, e);
        }
        List<Path> io = inputsFor(testDir);
        inputFiles.from(getProject().files(io));
        return inputFiles.getFiles();
    }

    @OutputFile
    public final File getOutputFile() {
        String hash = md5(String.join(",", getCommand()));
        return getProject().getLayout().getBuildDirectory()
                .file("tests/" + coordinates + "/" + hash + ".out")
                .get().getAsFile();
    }

    @Inject
    public AbstractSubprojectTask(String coordinates) {
        this.tckExtension = getProject().getExtensions().findByType(TckExtension.class);
        this.coordinates = coordinates;
    }

    @SuppressWarnings("unchecked")
    protected final void configureSpec(ExecSpec spec) {
        List<String> parts = splitCoordinates(coordinates);
        String groupId = parts.get(0);
        String artifactId = parts.get(1);
        String version = parts.get(2);
        Path metadataDir = tckExtension.getMetadataDir(coordinates);
        boolean override = false;

        var metadataIndex = readIndexFile(metadataDir.getParent());
        for (Object entryObj : (Iterable<?>) metadataIndex) {
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) entryObj;
            if (((List<String>) entry.get("tested-versions")).contains(version)) {
                if (entry.containsKey("override")) {
                    Object ov = entry.get("override");
                    if (ov instanceof Boolean b) {
                        override |= b;
                    } else if (ov != null) {
                        override |= Boolean.parseBoolean(ov.toString());
                    }
                }
                break;
            }
        }

        Path testDir = tckExtension.getTestDir(coordinates);

        Map<String, String> env = new HashMap<>(System.getenv());
        // Environment variables for setting up TCK
        env.put("GVM_TCK_LC", coordinates);
        env.put("GVM_TCK_EXCLUDE", Boolean.toString(override));
        if (System.getenv("GVM_TCK_LV") == null) {
            // we only set this env variable if user didn't specify it manually
            env.put("GVM_TCK_LV", version);
        }
        env.put("GVM_TCK_MD", metadataDir.toAbsolutePath().toString());
        env.put("GVM_TCK_TCKDIR", tckExtension.getTckRoot().get().getAsFile().toPath().toAbsolutePath().toString());
        spec.environment(env);
        spec.commandLine(getCommand());
        spec.workingDir(testDir.toAbsolutePath().toFile());

        spec.setIgnoreExitValue(true);
        spec.setStandardOutput(System.out);
        spec.setErrorOutput(System.err);
    }

    /**
     * Given project dir returns a list of inputs.
     * @return list of input files
     */
    @Internal
    protected List<Path> inputsFor(Path projectDir) {
        List<String> excludedSubdirNames = List.of(".gradle", ".mvn");
        List<String> excludedSubdirs = excludedSubdirNames.stream()
                .map(name -> {
                    try {
                        return projectDir.resolve(name).toFile().getCanonicalPath() + File.separator;
                    } catch (IOException e) {
                        return projectDir.resolve(name).toFile().getAbsolutePath() + File.separator;
                    }
                })
                .collect(Collectors.toList());

        List<Path> inputFiles = new ArrayList<>();
        try {
            Files.walk(projectDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        try {
                            String canon = p.toFile().getCanonicalPath();
                            return excludedSubdirs.stream().noneMatch(canon::startsWith);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(inputFiles::add);
        } catch (IOException e) {
            // If traversal fails treat as no inputs to avoid breaking configuration phase
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(inputFiles);
    }

    protected void beforeExecute() {
        // do nothing
    }

    protected void afterExecute() {
        // do nothing
    }

    protected String getErrorMessage(int exitCode) {
        return "Execution of " + getCommand() + " failed.";
    }

    @TaskAction
    public final void executeTask() {
        beforeExecute();
        getLogger().lifecycle("Command: " + getCommand());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        var execResult = getExecOperations().exec(spec -> {
            configureSpec(spec);
            spec.setStandardOutput(new TeeOutputStream(out, System.out));
            spec.setErrorOutput(new TeeOutputStream(err, System.err));
        });
        File of = getOutputFile();
        File parent = of.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        String content = "Standard out\n" +
                "-----\n" +
                out.toString(StandardCharsets.UTF_8) +
                "\n-----\n" +
                "Standard err\n" +
                "----\n" +
                err.toString(StandardCharsets.UTF_8) +
                "\n----\n";
        try {
            Files.writeString(of.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GradleException("Failed to write test output to " + of, e);
        }
        int exitCode = execResult.getExitValue();
        if (exitCode != 0) {
            throw new GradleException(getErrorMessage(exitCode));
        }
        afterExecute();
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(Character.forDigit((b & 0xF0) >> 4, 16));
                sb.append(Character.forDigit(b & 0x0F, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
