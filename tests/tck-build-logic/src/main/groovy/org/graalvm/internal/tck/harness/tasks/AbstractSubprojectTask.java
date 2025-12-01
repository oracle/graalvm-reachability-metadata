/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import groovy.transform.Internal;
import org.graalvm.internal.tck.harness.TckExtension;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.graalvm.internal.tck.Utils.coordinatesMatch;
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
    protected abstract ExecOperations getExecOperations();

    @Inject
    public AbstractSubprojectTask(String coordinates) {
        this.tckExtension = getProject().getExtensions().findByType(TckExtension.class);
        this.coordinates = coordinates;
    }

    @Input
    public abstract List<String> getCommand();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public final Set<File> getInputFiles() {
        ConfigurableFileCollection inputFiles = getProject().getObjects().fileCollection();
        Path metadataDir = tckExtension.getMetadataDir(coordinates);
        Path testDir = tckExtension.getTestDir(coordinates);
        try {
            inputFiles.from(getProject().files(tckExtension.getMetadataFileList(metadataDir)));
        } catch (IOException e) {
            throw new GradleException("Failed to list metadata files under " + metadataDir, e);
        }
        List<Path> io = inputsFor(testDir);
        inputFiles.from(io);
        return inputFiles.getFiles();
    }

    @OutputFile
    public final File getOutputFile() {
        String hash = md5Hex(String.join(",", getCommand()));
        return getProject().getLayout().getBuildDirectory().file("tests/" + coordinates + "/" + hash + ".out").get().getAsFile();
    }

    protected final void configureSpec(ExecSpec spec) {
        List<String> parts = splitCoordinates(coordinates);
        String groupId = parts.get(0);
        String artifactId = parts.get(1);
        String version = parts.get(2);

        Path metadataDir = tckExtension.getMetadataDir(coordinates);
        boolean override = false;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> metadataIndex = (List<Map<String, Object>>) readIndexFile(metadataDir.getParent());
        for (Map<String, Object> entry : metadataIndex) {
            String module = String.valueOf(entry.get("module"));
            @SuppressWarnings("unchecked")
            List<String> testedVersions = (List<String>) entry.get("tested-versions");
            if (coordinatesMatch(module, groupId, artifactId) && testedVersions != null && testedVersions.contains(version)) {
                if (entry.containsKey("override")) {
                    override |= Boolean.parseBoolean(String.valueOf(entry.get("override")));
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
     * Given project dir returns a list of input files, excluding known build dirs.
     * @return list of input files
     */
    @org.gradle.api.tasks.Internal
    public List<Path> inputsFor(Path projectDir) {
        File dir = projectDir.toFile();
        List<String> excludedSubdirNames = Arrays.asList(".gradle", ".mvn");

        List<String> excludedSubdirs = excludedSubdirNames.stream()
                .map(name -> projectDir.resolve(name).toFile())
                .map(f -> {
                    try {
                        return f.getCanonicalPath() + File.separator;
                    } catch (Exception e) {
                        return f.getAbsolutePath() + File.separator;
                    }
                })
                .collect(Collectors.toList());

        List<Path> inputFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(projectDir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                try {
                    String canon = p.toFile().getCanonicalPath();
                    boolean excluded = false;
                    for (String prefix : excludedSubdirs) {
                        if (canon.startsWith(prefix)) {
                            excluded = true;
                            break;
                        }
                    }
                    if (!excluded) {
                        inputFiles.add(p);
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception e) {
            throw new GradleException("Failed to traverse inputs under " + projectDir, e);
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
        getLogger().lifecycle("Command: {}", String.join(" ", getCommand()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ExecResult execResult = getExecOperations().exec(spec -> {
            configureSpec(spec);
            spec.setStandardOutput(new TeeOutputStream(out, System.out));
            spec.setErrorOutput(new TeeOutputStream(err, System.err));
        });
        File outFile = getOutputFile();
        File parent = outFile.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        String content = "Standard out\n" +
                "-----\n" +
                out.toString(StandardCharsets.UTF_8) + "\n" +
                "-----\n" +
                "Standard err\n" +
                "----\n" +
                err.toString(StandardCharsets.UTF_8) + "\n" +
                "----\n";
        try {
            Files.writeString(outFile.toPath(), content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new GradleException("Failed writing output file " + outFile, e);
        }
        int exitCode = execResult.getExitValue();
        if (exitCode != 0) {
            throw new GradleException(getErrorMessage(exitCode));
        }
        afterExecute();
    }

    private static String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
