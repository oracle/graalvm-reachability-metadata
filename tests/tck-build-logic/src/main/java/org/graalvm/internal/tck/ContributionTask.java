package org.graalvm.internal.tck;

import org.graalvm.internal.tck.utils.ConfigurationStringBuilder;
import org.graalvm.internal.tck.utils.InteractiveTaskUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ContributionTask extends DefaultTask {

    @Inject
    protected abstract ExecOperations getExecOperations();

    private Path testsDirectory;
    private Path metadataDirectory;

    private static final String BUILD_FILE = "build.gradle";
    private static final String USER_CODE_FILTER_FILE = "user-code-filter.json";

    private void initializeWorkingDirectories(Coordinates coordinates){
        testsDirectory = Path.of(getProject().file(CoordinateUtils.replace("tests/src/$group$/$artifact$/$version$", coordinates)).getAbsolutePath());
        metadataDirectory = Path.of(getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/$version$", coordinates)).getAbsolutePath());
    }


    @TaskAction
    void run() throws IOException {
        InteractiveTaskUtils.printUserInfo("Hello! This task will help you contributing to metadata repository." +
                " Please answer the following questions. In case you don't know the answer on the question, type \"help\" for more information");

        Coordinates coordinates = getCoordinates();
        InteractiveTaskUtils.closeSection();

        Path testsLocation = getTestsLocation();
        InteractiveTaskUtils.closeSection();

        Path resourcesLocation = getResourcesLocation();
        InteractiveTaskUtils.closeSection();

        List<String> packages = getAllowedPackages();
        InteractiveTaskUtils.closeSection();

        List<Coordinates> additionalTestImplementationDependencies = getAdditionalDependencies();
        InteractiveTaskUtils.closeSection();

        // initialize project
        initializeWorkingDirectories(coordinates);
        createStubs(coordinates);

        // generate necessary infrastructure
        addTests(testsLocation, coordinates);
        // TODO packages not accurate after move
        addResources(resourcesLocation);
        addUserCodeFilterFile(packages);
        // TODO Update allowed-packages
        addAdditionalDependencies(additionalTestImplementationDependencies);
        addAgentConfigBlock();

        // run agent in conditional mode
        collectMetadata();

        // create a PR
        boolean shouldCreatePR = shouldCreatePullRequest();
        if (shouldCreatePR) {
            Map<PullRequestInfo, Object> pullRequestInfo = new HashMap<>();
        }

        // TODO ask user to check metadata. If everything is okay, ask user if the task should create a PR for him

    }

    private Coordinates getCoordinates() {
        String question = "What library you want to support? Maven coordinates: ";
        String helpMessage = "Maven coordinates consist of three parts in the following format \"groupId:artifactId:version\". " +
                "For more information visit: https://maven.apache.org/repositories/artifacts.html";

        return InteractiveTaskUtils.askQuestion(question, helpMessage, (answer) -> {
            String[] coordinatesParts = answer.split(":");
            if (coordinatesParts.length != 3) {
                throw new IllegalStateException("Maven coordinates not provided in the correct format. Type help for explanation.");
            }

            String group = coordinatesParts[0];
            String artifact = coordinatesParts[1];
            String version = coordinatesParts[2];
            return new Coordinates(group, artifact, version);
        });
    }

    private Path getTestsLocation() {
        String question = "Where are your tests implemented? Absolute path to the directory where you implemented tests (on your system): ";
        String helpMessage = "An absolute path to the directory that contains your tests. " +
                "This path must be on your system and not some online location. " +
                "Be aware that for all tests where you are not the sole author, you have to add a comment that proves that you may publish them under the specified license manually";

        return InteractiveTaskUtils.askQuestion(question, helpMessage, (answer) -> {
            Path testsLocation = Path.of(answer).toAbsolutePath();
            if (!Files.exists(testsLocation)) {
                throw new IllegalStateException("Cannot find tests directory on the given location: " + testsLocation + ". Type help for explanation.");
            }

            if (!Files.isDirectory(testsLocation)) {
                throw new IllegalStateException("Provided path does not represent a directory: " + testsLocation + ". Type help for explanation.");
            }

            return testsLocation;
        });
    }

    private Path getResourcesLocation(){
        String question = "Do your tests need any kind of resources? " +
                "Absolute path to the resources directory required for your tests (type \"-\" if resources are not required): ";
        String helpMessage = "An absolute path to the directory that contains resources required for your tests. " +
                "This path must be on your system and not some online location. ";

        return InteractiveTaskUtils.askQuestion(question, helpMessage, (answer) -> {
            if (answer.equalsIgnoreCase("-")) {
                return null;
            }

            Path resourcesLocation = Path.of(answer).toAbsolutePath();
            if (!Files.exists(resourcesLocation)) {
                throw new IllegalStateException("Cannot find resources directory on the given location: " + resourcesLocation + ". Type help for explanation.");
            }

            if (!Files.isDirectory(resourcesLocation)) {
                throw new IllegalStateException("Provided path does not represent a directory: " + resourcesLocation + ". Type help for explanation.");
            }

            return resourcesLocation;
        });
    }

    private List<String> getAllowedPackages() {
        String question = "What package you want to include? Enter the next package (to stop type \"-\")";
        String helpMessage = "Enter the packages (pres enter after each package you entered) that you want to include in your metadata. " +
                "When you finish adding packages, type \"-\" to terminate the inclusion process";

        List<String> packages = new ArrayList<>();
        while (true) {
            String nextPackage = InteractiveTaskUtils.askQuestion(question, helpMessage, answer -> answer);
            if (nextPackage.equalsIgnoreCase("-")) {
                if (packages.isEmpty()) {
                    System.out.println("[ERROR] At least one package must be provided. Type help for explanation.");
                    continue;
                }

                break;
            }

            packages.add(nextPackage);
        }

        return packages;
    }

    private List<Coordinates> getAdditionalDependencies() {
        String question = "What additional testImplementation dependencies you want to include? Enter the next dependency (to stop type \"-\")";
        String helpMessage = "Enter the testImplementation dependencies (pres enter after each dependency) you want to include use in tests. " +
                "Provide dependencies in form of Maven coordinates" +
                "Maven coordinates consist of three parts in the following format \"groupId:artifactId:version\". " +
                "For more information visit: https://maven.apache.org/repositories/artifacts.html" +
                "When you finish adding dependencies, type \"-\" to terminate the inclusion process";

        List<Coordinates> dependencies = new ArrayList<>();
        while (true) {
            Coordinates dependency = InteractiveTaskUtils.askQuestion(question, helpMessage, answer -> {
                if (answer.equalsIgnoreCase("-")) {
                    return null;
                }

                String[] coordinatesParts = answer.split(":");
                if (coordinatesParts.length != 3) {
                    throw new IllegalStateException("Maven coordinates not provided in the correct format. Type help for explanation.");
                }

                String group = coordinatesParts[0];
                String artifact = coordinatesParts[1];
                String version = coordinatesParts[2];
                return new Coordinates(group, artifact, version);
            });

            if (dependency == null) {
                break;
            }

            dependencies.add(dependency);
        }

        return dependencies;
    }

    private void createStubs(Coordinates coordinates){
        InteractiveTaskUtils.printUserInfo("Generating stubs for: " + coordinates );

        ByteArrayOutputStream execOutput = new ByteArrayOutputStream();
        String[] command = { "scaffold", "--coordinates", coordinates.toString() };
        var scaffoldResult = getExecOperations().exec(execSpec -> {
            execSpec.setExecutable("gradle");
            execSpec.setArgs(List.of(command));
            execSpec.setStandardOutput(execOutput);
        });

        if (scaffoldResult.getExitValue() != 0) {
            throw new RuntimeException("Cannot generate stubs for: " + coordinates);
        }
    }

    private void addTests(Path originalTestsLocation, Coordinates coordinates){
        Path destination = testsDirectory.resolve("src")
                .resolve("test")
                .resolve("java")
                .resolve(coordinates.group().replace(".", "_").replace("-", "_"))
                .resolve(coordinates.artifact().replace(".", "_").replace("-", "_"));
        Path allTests = originalTestsLocation.resolve(".");

        InteractiveTaskUtils.printUserInfo("Removing dummy test stubs");
        ByteArrayOutputStream deleteExecOutput = new ByteArrayOutputStream();
        String[] deleteCommand = { "-r", destination.toString() };
        var deleteResult = getExecOperations().exec(execSpec -> {
            execSpec.setExecutable("rm");
            execSpec.setArgs(List.of(deleteCommand));
            execSpec.setStandardOutput(deleteExecOutput);
        });

        if (deleteResult.getExitValue() != 0) {
            throw new RuntimeException("Cannot delete files from: " + destination);
        }

        InteractiveTaskUtils.printUserInfo("Copying tests from: " + originalTestsLocation + " to " + destination);
        ByteArrayOutputStream copyExecOutput = new ByteArrayOutputStream();
        String[] copyCommand = { "-a", allTests.toString(), destination.toString()};
        var copyResult = getExecOperations().exec(execSpec -> {
            execSpec.setExecutable("cp");
            execSpec.setArgs(List.of(copyCommand));
            execSpec.setStandardOutput(copyExecOutput);
        });

        if (copyResult.getExitValue() != 0) {
            throw new RuntimeException("Cannot copy files to: " + destination);
        }
    }

    private void addResources(Path originalResourcesDirectory){
        if (originalResourcesDirectory == null) {
            return;
        }

        Path destination = testsDirectory.resolve("src").resolve("test");
        InteractiveTaskUtils.printUserInfo("Copying resources from: " + originalResourcesDirectory + " to " + destination);

        ByteArrayOutputStream copyExecCommand = new ByteArrayOutputStream();
        String[] copyCommand = { "-r", originalResourcesDirectory.toString(), destination.toString()};
        var moveResult = getExecOperations().exec(execSpec -> {
            execSpec.setExecutable("cp");
            execSpec.setArgs(List.of(copyCommand));
            execSpec.setStandardOutput(copyExecCommand);
        });

        if (moveResult.getExitValue() != 0) {
            throw new RuntimeException("Cannot copy files to: " + destination);
        }
    }

    private void addUserCodeFilterFile(List<String> packages) throws IOException {
        InteractiveTaskUtils.printUserInfo("Generating " + USER_CODE_FILTER_FILE);

        ConfigurationStringBuilder sb = new ConfigurationStringBuilder();
        sb.openObject().newLine();
        sb.indent();
        sb.quote("rules").separateWithSemicolon().openArray().newLine();
        sb.indent();
        sb.openObject().appendStringProperty("excludeClasses", "**").closeObject().concat().newLine();
        for (int i = 0; i < packages.size(); i++) {
            String nextPackage = packages.get(i) + ".**";
            sb.openObject().appendStringProperty("includeClasses", nextPackage).closeObject();
            if (i < packages.size() - 1) {
                sb.concat();
            }

            sb.newLine();
        }

        sb.unindent();
        sb.closeArray().newLine();
        sb.unindent();
        sb.closeObject();

        writeToFile(testsDirectory.resolve(USER_CODE_FILTER_FILE), sb.toString(), StandardOpenOption.CREATE);
    }

    private void addAdditionalDependencies(List<Coordinates> dependencies) {
        if (dependencies == null) {
            return;
        }

        Path buildFilePath = testsDirectory.resolve(BUILD_FILE);
        InteractiveTaskUtils.printUserInfo("Adding following dependencies to " + BUILD_FILE + " file: " + dependencies);

        if (!Files.exists(buildFilePath) || !Files.isRegularFile(buildFilePath)) {
            throw new RuntimeException("Cannot add additional dependencies to " + buildFilePath + ". Please check if a " + BUILD_FILE + " exists on that location.");
        }

        try {
            ConfigurationStringBuilder sb = new ConfigurationStringBuilder();
            List<String> content = Files.readAllLines(buildFilePath);
            for (var line : content) {
                sb.append(line).newLine();
                if (line.trim().equalsIgnoreCase("dependencies {")) {
                    sb.indent();
                    for (var dependency : dependencies) {
                        sb.append("testImplementation").space().quote(dependency.toString()).newLine();
                    }
                    sb.unindent();
                }
            }

            writeToFile(buildFilePath, sb.toString(), StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void addAgentConfigBlock() {
        Path buildFilePath = testsDirectory.resolve(BUILD_FILE);
        InteractiveTaskUtils.printUserInfo("Configuring agent block in: " + BUILD_FILE);

        if (!Files.exists(buildFilePath) || !Files.isRegularFile(buildFilePath)) {
            throw new RuntimeException("Cannot add additional dependencies to " + buildFilePath + ". Please check if a " + BUILD_FILE + " exists on that location.");
        }

        ConfigurationStringBuilder sb = new ConfigurationStringBuilder();
        sb.newLine();
        sb.append("graalvmNative").space().openObject().newLine();
        sb.indent().append("agent").space().openObject().newLine();

        sb.indent().appendAssignedVariable("defaultMode", "conditional").newLine();

        sb.append("modes").space().openObject().newLine();
        sb.indent().append("conditional").space().openObject().newLine();
        sb.indent().appendAssignedVariable("userCodeFilterPath", USER_CODE_FILTER_FILE).newLine();
        sb.unindent().closeObject().newLine();
        sb.unindent().closeObject().newLine();

        sb.append("metadataCopy").space().openObject().newLine();
        sb.indent().append("mergeWithExisting").space().append("=").space().append("false").newLine();
        sb.append("inputTaskNames.add").quoteInBrackets("test").newLine();
        sb.append("outputDirectories.add").quoteInBrackets(metadataDirectory.toString()).newLine();
        sb.unindent().closeObject().newLine();

        sb.unindent().closeObject().newLine();
        sb.unindent().closeObject();

        try {
            writeToFile(buildFilePath, sb.toString(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Cannot add agent block into: " + buildFilePath);
        }
    }

    private void collectMetadata() {
        InteractiveTaskUtils.printUserInfo("Generating metadata");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        String[] generateMetadataCommand = { "-Pagent", "test" };
        var generateMetadataResult = getExecOperations().exec(execSpec -> {
            execSpec.setWorkingDir(testsDirectory);
            execSpec.setExecutable("gradle");
            execSpec.setArgs(List.of(generateMetadataCommand));
            execSpec.setStandardOutput(outputStream);
        });

        if (generateMetadataResult.getExitValue() != 0) {
            throw new RuntimeException("Cannot generate metadata. See: " + outputStream);
        }

        InteractiveTaskUtils.printUserInfo("Performing metadata copy");
        ByteArrayOutputStream metadataCopyOutput = new ByteArrayOutputStream();
        String[] metadataCopyCommand = { "metadataCopy" };
        var metadataCopyResult = getExecOperations().exec(execSpec -> {
            execSpec.setWorkingDir(testsDirectory);
            execSpec.setExecutable("gradle");
            execSpec.setArgs(List.of(metadataCopyCommand));
            execSpec.setStandardOutput(metadataCopyOutput);
        });

        if (metadataCopyResult.getExitValue() != 0) {
            throw new RuntimeException("Cannot perform metadata copy. See: " + outputStream);
        }
    }

    enum PullRequestInfo {
        USER_NAME,
        CONSIDER_CONTRIBUTING_TO_LIBRARY,
        USER_IS_ORIGINAL_AUTHOR,
        USER_HAVE_LICENSES,
        METADATA_IS_FORMATTED,
        TESTS_ARE_ADDED,
        PULL_REQUEST_ACCESS_PLACES
    }


    private boolean shouldCreatePullRequest() {
        String question = "Do you want to create a pull request to the reachability metadata repository [Y/n]: ";
        String helpMessage = "If you want, we can create a pull request for you! " +
                "All you have to do is to provide necessary information for the GitHub CLI, and answer few questions regarding the pull request description.";

        return InteractiveTaskUtils.askYesNoQuestion(question, helpMessage, true);
    }

    private Map<PullRequestInfo, Object> collectPullRequestInfo() {
        Map<PullRequestInfo, Object> answers = new HashMap<>();
        String username = InteractiveTaskUtils.askQuestion("Your GitHub username:", "Your GitHub username", answer -> answer);
        Boolean libraryContributionConsidered = InteractiveTaskUtils.askYesNoQuestion(
                "I have considered including reachability metadata directly in the library or the framework [Y/n]",
                "See <contributing guide>", true);
        Boolean userIsOriginalAuthor = InteractiveTaskUtils.askYesNoQuestion(
                "I am the original author of all content provided in the pull request, and I did not copy the content from any other source [Y/n]",
                "See <test section>",
                true);
        Boolean userHaveLicenses = InteractiveTaskUtils.askYesNoQuestion(
                "For all tests where I am not the sole author, I have added a comment that proves I may publish them under the specified license [Y/n]",
                "See <test section>",
                true);

        answers.put(PullRequestInfo.USER_NAME, username);
        answers.put(PullRequestInfo.CONSIDER_CONTRIBUTING_TO_LIBRARY, libraryContributionConsidered);
        answers.put(PullRequestInfo.USER_IS_ORIGINAL_AUTHOR, userIsOriginalAuthor);
        answers.put(PullRequestInfo.USER_HAVE_LICENSES, userHaveLicenses);
        answers.put(PullRequestInfo.METADATA_IS_FORMATTED, true);
        answers.put(PullRequestInfo.TESTS_ARE_ADDED, true);
        answers.put(PullRequestInfo.PULL_REQUEST_ACCESS_PLACES, false);

        InteractiveTaskUtils.printUserInfo("After your pull requests gets generated, please update the pull requests description to mention all places where your pull request" +
                "accesses files, network, docker, or any other external service, and check if all checks in the description are correctly filled");

        return answers;
    }

    private void writeToFile(Path path, String content, StandardOpenOption writeOption) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8, writeOption);
    }

}
