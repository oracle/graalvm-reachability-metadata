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
import java.util.*;

public abstract class ContributionTask extends DefaultTask {

    @Inject
    protected abstract ExecOperations getExecOperations();

    private Path testsDirectory;
    private Path metadataDirectory;
    private Coordinates coordinates;

    private static final String BUILD_FILE = "build.gradle";
    private static final String USER_CODE_FILTER_FILE = "user-code-filter.json";
    private static final String REQUIRED_DOCKER_IMAGES_FILE = "required-docker-images.txt";

    private void initializeWorkingDirectories(){
        testsDirectory = Path.of(getProject().file(CoordinateUtils.replace("tests/src/$group$/$artifact$/$version$", coordinates)).getAbsolutePath());
        metadataDirectory = Path.of(getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/$version$", coordinates)).getAbsolutePath());
    }


    @TaskAction
    void run() throws IOException {
        InteractiveTaskUtils.printUserInfo("Hello! This task will help you contributing to metadata repository." +
                " Please answer the following questions. In case you don't know the answer on the question, type \"help\" for more information");

        this.coordinates = getCoordinates();
        InteractiveTaskUtils.closeSection();

        Path testsLocation = getTestsLocation();
        InteractiveTaskUtils.closeSection();

        Path resourcesLocation = getResourcesLocation();
        InteractiveTaskUtils.closeSection();

        List<String> dockerImages = getDockerImages();
        InteractiveTaskUtils.closeSection();

        List<String> packages = getAllowedPackages();
        InteractiveTaskUtils.closeSection();

        List<Coordinates> additionalTestImplementationDependencies = getAdditionalDependencies();
        InteractiveTaskUtils.closeSection();

        // initialize project
        initializeWorkingDirectories();
        createStubs();

        // generate necessary infrastructure
        addTests(testsLocation);
        // TODO packages not accurate after move
        addResources(resourcesLocation);
        addDockerImages(dockerImages);
        addUserCodeFilterFile(packages);
        // TODO Update allowed-packages
        addAdditionalDependencies(additionalTestImplementationDependencies);
        addAgentConfigBlock();

        // run agent in conditional mode
        collectMetadata();

        // create a PR
        boolean shouldCreatePR = shouldCreatePullRequest();
        if (shouldCreatePR) {
            String branch = getBranchName();
            InteractiveTaskUtils.printUserInfo("After your pull requests gets generated, please update the pull request description to mention all places where your pull request" +
                    "accesses files, network, docker, or any other external service, and check if all checks in the description are correctly marked");

              // TODO disabled for local testing
//            createPullRequest(branch);
        }

        InteractiveTaskUtils.printSuccessfulStatement("Contribution successfully completed! Thank you!");
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

    private List<String> getDockerImages() {
        String question = "Do your tests use docker? Enter the next docker image name (to stop type \"-\"): ";
        String helpMessage = "Enter the docker images (press enter after each image name you enter) that you want to use in your tests. " +
                "Docker image declaration consists of two parts separated with \":\" in the following format: \"imageName:version\"." +
                "When you finish adding docker images, type \"-\" to terminate the inclusion process";

        List<String> images = new ArrayList<>();
        while (true) {
            String nextImage = InteractiveTaskUtils.askQuestion(question, helpMessage, answer -> {
                if (!answer.equalsIgnoreCase("-") && answer.split(":").length != 2) {
                    throw new IllegalStateException("Docker image name not provided in the correct format. Type help for explanation.");
                }

                return answer;
            });

            if (nextImage.trim().equalsIgnoreCase("-")) {
                break;
            }

            images.add(nextImage);
        }

        return images;
    }

    private List<String> getAllowedPackages() {
        String question = "What package you want to include? Enter the next package (to stop type \"-\")";
        String helpMessage = "Enter the packages (press enter after each package you entered) that you want to include in your metadata. " +
                "When you finish adding packages, type \"-\" to terminate the inclusion process";

        List<String> packages = new ArrayList<>();
        while (true) {
            String nextPackage = InteractiveTaskUtils.askQuestion(question, helpMessage, answer -> answer);
            if (nextPackage.trim().equalsIgnoreCase("-")) {
                if (packages.isEmpty()) {
                    InteractiveTaskUtils.printErrorMessage("At least one package must be provided. Type help for explanation.");
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

    private void createStubs(){
        InteractiveTaskUtils.printUserInfo("Generating stubs for: " + coordinates );
        invokeCommand("gradle scaffold --coordinates " + coordinates, "Cannot generate stubs for: " + coordinates);
    }

    private void addTests(Path originalTestsLocation){
        Path destination = testsDirectory.resolve("src")
                .resolve("test")
                .resolve("java")
                .resolve(coordinates.group().replace(".", "_").replace("-", "_"))
                .resolve(coordinates.artifact().replace(".", "_").replace("-", "_"));
        Path allTests = originalTestsLocation.resolve(".");

        InteractiveTaskUtils.printUserInfo("Removing dummy test stubs");
        invokeCommand("rm -r " + destination, "Cannot delete files from: " + destination);

        InteractiveTaskUtils.printUserInfo("Copying tests from: " + originalTestsLocation + " to " + destination);
        invokeCommand("cp -a " + allTests + " " + destination, "Cannot copy files to: " + destination);
    }

    private void addResources(Path originalResourcesDirectory){
        if (originalResourcesDirectory == null) {
            return;
        }

        Path destination = testsDirectory.resolve("src").resolve("test");
        InteractiveTaskUtils.printUserInfo("Copying resources from: " + originalResourcesDirectory + " to " + destination);

        invokeCommand("cp -r " + originalResourcesDirectory + " " + destination, "Cannot copy files to: " + destination);
    }

    private void addDockerImages(List<String> images) throws IOException {
        if (images.isEmpty()) {
            return;
        }

        InteractiveTaskUtils.printUserInfo("Adding following docker images to " + REQUIRED_DOCKER_IMAGES_FILE + ": " + images);
        Path destination = testsDirectory.resolve(REQUIRED_DOCKER_IMAGES_FILE);
        if (!Files.exists(destination)) {
            Files.createFile(destination);
        }

        for (var image : images) {
            writeToFile(destination, image.concat("\n"), StandardOpenOption.APPEND);
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
        sb.indent().append("mergeWithExisting").separateWithEquals().append("false").newLine();
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
        invokeCommand("gradle -Pagent test", "Cannot generate metadata", testsDirectory);

        InteractiveTaskUtils.printUserInfo("Performing metadata copy");
        invokeCommand("gradle metadataCopy", "Cannot perform metadata copy", testsDirectory);
    }

    private boolean shouldCreatePullRequest() {
        String question = "Do you want to create a pull request to the reachability metadata repository [Y/n]: ";
        String helpMessage = "If you want, we can create a pull request for you! " +
                "All you have to do is to provide necessary information for the GitHub CLI, and answer few questions regarding the pull request description.";

        return InteractiveTaskUtils.askYesNoQuestion(question, helpMessage, true);
    }

    private String getBranchName() {
        String question = "Branch that will be created for your pull request:";
        String helpMessage = "Branch name in format \"username/library-name\"";

        return InteractiveTaskUtils.askQuestion(question, helpMessage, answer -> answer);
    }

    private void createPullRequest(String branch) {
        InteractiveTaskUtils.printUserInfo("Creating new branch: " + branch);
        invokeCommand("git switch -C " + branch, "Cannot create a new branch");

        InteractiveTaskUtils.printUserInfo("Staging changes");
        invokeCommand("git add .", "Cannot add changes");

        InteractiveTaskUtils.printUserInfo("Committing changes");
        invokeCommand("git", List.of("commit", "-m", "Add metadata for " + coordinates), "Cannot commit changes", null);

        InteractiveTaskUtils.printUserInfo("Pushing changes");
        invokeCommand("git push origin " + branch, "Cannot push to origin");

        InteractiveTaskUtils.printUserInfo("Complete pull request creation on the above link");
    }

    private void writeToFile(Path path, String content, StandardOpenOption writeOption) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8, writeOption);
    }

    private void invokeCommand(String command, String errorMessage) {
        invokeCommand(command, errorMessage, null);
    }

    private void invokeCommand(String command, String errorMessage, Path workingDirectory) {
        String[] commandParts = command.split(" ");
        String executable = commandParts[0];

        List<String> args = List.of(Arrays.copyOfRange(commandParts, 1, commandParts.length));
        invokeCommand(executable, args, errorMessage, workingDirectory);
    }

    private void invokeCommand(String executable, List<String> args, String errorMessage, Path workingDirectory) {
        ByteArrayOutputStream execOutput = new ByteArrayOutputStream();
        var result = getExecOperations().exec(execSpec -> {
            if (workingDirectory != null) {
                execSpec.setWorkingDir(workingDirectory);
            }
            execSpec.setExecutable(executable);
            execSpec.setArgs(args);
            execSpec.setStandardOutput(execOutput);
        });

        if (result.getExitValue() != 0) {
            throw new RuntimeException(errorMessage + ". See: " + execOutput);
        }
    }
}
