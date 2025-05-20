package org.graalvm.internal.tck;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.graalvm.internal.tck.exceptions.ContributingException;
import org.graalvm.internal.tck.model.MetadataIndexEntry;
import org.graalvm.internal.tck.model.contributing.Question;
import org.graalvm.internal.tck.utils.ConfigurationStringBuilder;
import org.graalvm.internal.tck.utils.FilesUtils;
import org.graalvm.internal.tck.utils.InteractiveTaskUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public abstract class ContributionTask extends DefaultTask {
    private static final String BRANCH_NAME_PREFIX = "add-support-for-";
    private static final String METADATA_INDEX = "metadata/index.json";
    private static final String BUILD_FILE = "build.gradle";
    private static final String USER_CODE_FILTER_FILE = "user-code-filter.json";
    private static final String REQUIRED_DOCKER_IMAGES_FILE = "required-docker-images.txt";

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private Path gradlew;
    private Path testsDirectory;
    private Path metadataDirectory;

    private Coordinates coordinates;

    private record ContributingQuestion(String question, String help) {}
    private final Map<String, ContributingQuestion> questions = new HashMap<>();

    private void initializeWorkingDirectories(){
        testsDirectory = getProject().file(CoordinateUtils.replace("tests/src/$group$/$artifact$/$version$", coordinates)).toPath();
        metadataDirectory = getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/$version$", coordinates)).toPath();
        gradlew = Path.of(getProject().file("gradlew").getAbsolutePath());
    }

    private void loadQuestions() throws IOException {
        File questionsJson = getProject().file("tests/tck-build-logic/src/main/resources/contributing/questions.json");
        List<Question> contributingQuestions = objectMapper.readValue(questionsJson, new TypeReference<>() {});
        for (var question : contributingQuestions) {
            questions.put(question.questionKey(), new ContributingQuestion(question.question(), question.help()));
        }
    }

    @TaskAction
    void run() throws IOException {
        InteractiveTaskUtils.printUserInfo("Hello! This task will help you contributing to metadata repository." +
                " Please answer the following questions. In case you don't know the answer on the question, type \"help\" for more information");

        loadQuestions();

        coordinates = getCoordinates();
        InteractiveTaskUtils.closeSection();

        Path coordinatesMetadataRoot = getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$", coordinates)).toPath();
        boolean isExistingLibrary = Files.exists(coordinatesMetadataRoot);

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
        createStubs(isExistingLibrary);
        updateAllowedPackages(packages, isExistingLibrary);

        // generate necessary boilerplate code
        addTests(testsLocation);
        addResources(resourcesLocation);
        addDockerImages(dockerImages);
        addUserCodeFilterFile(packages);
        addAdditionalDependencies(additionalTestImplementationDependencies);
        addAgentConfigBlock();

        // run agent in conditional mode
        collectMetadata();

        // create a PR
        boolean shouldCreatePR = shouldCreatePullRequest();
        if (shouldCreatePR) {
            String branch = BRANCH_NAME_PREFIX + coordinates.toString().replace(':', '-');
            createPullRequest(branch);

            InteractiveTaskUtils.printUserInfo("After your pull requests gets generated, please update the pull request description to mention all places where your pull request" +
                    "accesses files, network, docker, or any other external service, and check if all checks in the description are correctly marked.");
        }

        InteractiveTaskUtils.printSuccessfulStatement("Contribution successfully completed! Thank you!");
    }

    private Coordinates getCoordinates() {
        ContributingQuestion question = questions.get("coordinates");
        return InteractiveTaskUtils.askQuestion(question.question(), question.help(), (answer) -> {
            try {
                return CoordinateUtils.fromString(answer);
            } catch (IllegalArgumentException ex) {
                throw new ContributingException(ex.getMessage());
            }
        });
    }

    private Path getTestsLocation() {
        ContributingQuestion question = questions.get("testsLocation");
        return InteractiveTaskUtils.askQuestion(question.question(), question.help(), (answer) -> {
            Path testsLocation = Path.of(answer).toAbsolutePath();
            if (!Files.exists(testsLocation)) {
                throw new ContributingException("Cannot find tests directory on the given location: " + testsLocation + ". Type help for explanation.");
            }

            if (!Files.isDirectory(testsLocation)) {
                throw new ContributingException("Provided path does not represent a directory: " + testsLocation + ". Type help for explanation.");
            }

            checkPackages(testsLocation);

            return testsLocation;
        });
    }

    private void checkPackages(Path testsPath) throws ContributingException {
        List<Path> javaFiles = new ArrayList<>();
        try {
            FilesUtils.findJavaFiles(testsPath, javaFiles);
        } catch (IOException e) {
            throw new ContributingException("Cannot find Java files. Reason: " + e);
        }

        for (Path file : javaFiles) {
            try {
                Optional<String> packageLine = Files.readAllLines(file).stream().filter(line -> line.contains("package ")).findFirst();
                if (packageLine.isEmpty()) {
                    throw new ContributingException("Java file: " + file + " does not contain declared package");
                }

                String declaredPackage = packageLine.get().split(" ")[1].replace(";", "");
                String packagePath = declaredPackage.replace(".", File.separator);
                if (!Files.exists(testsPath.resolve(packagePath))) {
                    throw new ContributingException("File: " + file + " has package: " + declaredPackage +
                            " that cannot be found on tests location: " + testsPath +
                            ". Please make sure that the location you provided is a directory that contains packages with tests implementations");
                }
            } catch (IOException e) {
                throw new ContributingException(e.getMessage());
            }
        }
    }

    private Path getResourcesLocation(){
        ContributingQuestion question = questions.get("resourcesLocation");
        return InteractiveTaskUtils.askQuestion(question.question(), question.help(), (answer) -> {
            if (answer.equalsIgnoreCase("-")) {
                return null;
            }

            Path resourcesLocation = Path.of(answer).toAbsolutePath();
            if (!Files.exists(resourcesLocation)) {
                throw new ContributingException("Cannot find resources directory on the given location: " + resourcesLocation + ". Type help for explanation.");
            }

            if (!Files.isDirectory(resourcesLocation)) {
                throw new ContributingException("Provided path does not represent a directory: " + resourcesLocation + ". Type help for explanation.");
            }

            return resourcesLocation;
        });
    }

    private List<String> getDockerImages() {
        ContributingQuestion question = questions.get("docker");
        return InteractiveTaskUtils.askRecurringQuestions(question.question(), question.help(), 0, answer -> {
            if (answer.split(":").length != 2) {
                throw new ContributingException("Docker image name not provided in the correct format. Type help for explanation.");
            }

            return answer;
        });
    }

    private List<String> getAllowedPackages() {
        ContributingQuestion question = questions.get("allowedPackages");
        return InteractiveTaskUtils.askRecurringQuestions(question.question(), question.help(), 1, answer -> answer);
    }

    private List<Coordinates> getAdditionalDependencies() {
        ContributingQuestion question = questions.get("additionalDependencies");
        return InteractiveTaskUtils.askRecurringQuestions(question.question(), question.help(), 0, answer -> {
            try {
                return CoordinateUtils.fromString(answer);
            } catch (IllegalArgumentException ex) {
                throw new ContributingException(ex.getMessage());
            }
        });
    }

    private void createStubs(boolean shouldUpdate){
        InteractiveTaskUtils.printUserInfo("Generating stubs for: " + coordinates );
        if (shouldUpdate) {
            invokeCommand(gradlew + " scaffold --coordinates " + coordinates + " --skipTests --update ", "Cannot generate stubs for: " + coordinates);
        } else {
            invokeCommand(gradlew + " scaffold --coordinates " + coordinates + " --skipTests", "Cannot generate stubs for: " + coordinates);
        }
    }

    private void updateAllowedPackages(List<String> allowedPackages, boolean libraryAlreadyExists) throws IOException {
        InteractiveTaskUtils.printUserInfo("Updating allowed packages in: " + METADATA_INDEX);
        File metadataIndex = getProject().file(METADATA_INDEX);

        List<MetadataIndexEntry> entries = objectMapper.readValue(metadataIndex, new TypeReference<>() {});
        int replaceEntryIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).module().equals(coordinates.group() + ":" + coordinates.artifact())) {
                replaceEntryIndex = i;
            }
        }

        if (replaceEntryIndex != -1) {
            Set<String> extendedAllowedPackages = new HashSet<>();
            MetadataIndexEntry replacedEntry = entries.remove(replaceEntryIndex);

            if (libraryAlreadyExists) {
                // we don't want to break existing tests, so we must add existing allowed packages
                extendedAllowedPackages.addAll(replacedEntry.allowedPackages());
            }

            extendedAllowedPackages.addAll(allowedPackages);

            entries.add(new MetadataIndexEntry(replacedEntry.directory(), replacedEntry.module(), replacedEntry.requires(), new ArrayList<>(extendedAllowedPackages)));
        }

        List<MetadataIndexEntry> sortedEntries = entries.stream()
                .sorted(Comparator.comparing(MetadataIndexEntry::module))
                .toList();

        objectMapper.writeValue(metadataIndex, sortedEntries);
    }

    private void addTests(Path originalTestsLocation){
        Path destination = testsDirectory.resolve("src").resolve("test").resolve("java");
        Path allTests = originalTestsLocation.resolve(".");

        ensureFileBelongsToProject(destination);
        InteractiveTaskUtils.printUserInfo("Copying tests from: " + originalTestsLocation + " to " + destination);
        getFileSystemOperations().copy(copySpec -> {
            copySpec.from(allTests);
            copySpec.into(destination);
        });
    }

    private void addResources(Path originalResourcesDirectory){
        if (originalResourcesDirectory == null) {
            return;
        }

        Path destination = testsDirectory.resolve("src").resolve("test").resolve("resources");
        ensureFileBelongsToProject(destination);

        InteractiveTaskUtils.printUserInfo("Copying resources from: " + originalResourcesDirectory + " to " + destination);
        getFileSystemOperations().copy(copySpec -> {
           copySpec.from(originalResourcesDirectory);
           copySpec.into(destination);
        });
    }

    private void addDockerImages(List<String> images) throws IOException {
        if (images.isEmpty()) {
            return;
        }

        InteractiveTaskUtils.printUserInfo("Adding following docker images to " + REQUIRED_DOCKER_IMAGES_FILE + ": " + images);
        Path destination = testsDirectory.resolve(REQUIRED_DOCKER_IMAGES_FILE);
        ensureFileBelongsToProject(destination);

        if (!Files.exists(destination)) {
            Files.createFile(destination);
        }

        for (String image : images) {
            writeToFile(destination, image.concat(System.lineSeparator()), StandardOpenOption.APPEND);
        }
    }

    private void addUserCodeFilterFile(List<String> packages) throws IOException {
        InteractiveTaskUtils.printUserInfo("Generating " + USER_CODE_FILTER_FILE);
        List<Map<String, String>> filterFileRules = new ArrayList<>();

        // add exclude classes
        filterFileRules.add(Map.of("excludeClasses", "**"));

        // add include classes
        packages.forEach(p -> filterFileRules.add(Map.of("includeClasses", p + ".**")));

        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        objectMapper.writer(prettyPrinter).writeValue(testsDirectory.resolve(USER_CODE_FILTER_FILE).toFile(), Map.of("rules", filterFileRules));
    }

    private void addAdditionalDependencies(List<Coordinates> dependencies) throws IOException {
        if (dependencies == null) {
            return;
        }

        Path buildFilePath = testsDirectory.resolve(BUILD_FILE);
        InteractiveTaskUtils.printUserInfo("Adding following dependencies to " + BUILD_FILE + " file: " + dependencies);

        if (!Files.exists(buildFilePath) || !Files.isRegularFile(buildFilePath)) {
            throw new RuntimeException("Cannot add additional dependencies to " + buildFilePath + ". Please check if a " + BUILD_FILE + " exists on that location.");
        }

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
    }

    private void addAgentConfigBlock() throws IOException {
        Path buildFilePath = testsDirectory.resolve(BUILD_FILE);
        InteractiveTaskUtils.printUserInfo("Configuring agent block in: " + BUILD_FILE);

        if (!Files.exists(buildFilePath) || !Files.isRegularFile(buildFilePath)) {
            throw new RuntimeException("Cannot add agent block to " + buildFilePath + ". Please check if a " + BUILD_FILE + " exists on that location.");
        }

        try(InputStream stream = ContributionTask.class.getResourceAsStream("/contributing/agent.template")) {
            if (stream == null) {
                throw new RuntimeException("Cannot find template for the graalvm configuration block");
            }

            String content = System.lineSeparator() + (new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            writeToFile(buildFilePath, content, StandardOpenOption.APPEND);
        }
    }

    private void collectMetadata() {
        InteractiveTaskUtils.printUserInfo("Generating metadata");
        invokeCommand(gradlew + " -Pagent test", "Cannot generate metadata", testsDirectory);

        InteractiveTaskUtils.printUserInfo("Performing metadata copy");
        invokeCommand(gradlew + " metadataCopy --task test --dir " + metadataDirectory, "Cannot perform metadata copy", testsDirectory);
    }

    private boolean shouldCreatePullRequest() {
        ContributingQuestion question = questions.get("shouldCreatePullRequest");
        return InteractiveTaskUtils.askYesNoQuestion(question.question(), question.help(), true);
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

    private void ensureFileBelongsToProject(Path file) {
        if (!file.startsWith(getProject().getProjectDir().getAbsolutePath())) {
            throw new RuntimeException("The following file doesn't belong to the metadata repository: " + file);
        }
    }
}
