package org.graalvm.internal.tck;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.graalvm.internal.tck.model.MetadataIndexEntry;
import org.graalvm.internal.tck.model.contributing.PredefinedClassesConfigModel;
import org.graalvm.internal.tck.model.contributing.ResourceConfigModel;
import org.graalvm.internal.tck.model.contributing.Question;
import org.graalvm.internal.tck.model.contributing.SerializationConfigModel;
import org.graalvm.internal.tck.utils.ConfigurationStringBuilder;
import org.graalvm.internal.tck.utils.FilesUtils;
import org.graalvm.internal.tck.utils.InteractiveTaskUtils;
import org.gradle.api.DefaultTask;
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
import java.util.*;

public abstract class ContributionTask extends DefaultTask {
    private static final String METADATA_INDEX = "metadata/index.json";
    private static final String BUILD_FILE = "build.gradle";
    private static final String USER_CODE_FILTER_FILE = "user-code-filter.json";
    private static final String REQUIRED_DOCKER_IMAGES_FILE = "required-docker-images.txt";
    @Inject
    protected abstract ExecOperations getExecOperations();

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private Path testsDirectory;
    private Path metadataDirectory;

    private Coordinates coordinates;

    private record ContributingQuestion(String question, String help) {}
    private final Map<String, ContributingQuestion> questions = new HashMap<>();

    private void initializeWorkingDirectories(){
        testsDirectory = Path.of(getProject().file(CoordinateUtils.replace("tests/src/$group$/$artifact$/$version$", coordinates)).getAbsolutePath());
        metadataDirectory = Path.of(getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/$version$", coordinates)).getAbsolutePath());
    }

    private void loadQuestions() throws IOException {
        File questionsJson = getProject().file("tests/tck-build-logic/src/main/resources/contributing/questions.json");
        List<Question> contributingQuestions = objectMapper.readValue(questionsJson, new TypeReference<>() {});
        for (var question : contributingQuestions) {
            this.questions.put(question.questionKey(), new ContributingQuestion(question.question(), question.help()));
        }
    }

    @TaskAction
    void run() throws IOException {
        InteractiveTaskUtils.printUserInfo("Hello! This task will help you contributing to metadata repository." +
                " Please answer the following questions. In case you don't know the answer on the question, type \"help\" for more information");

        loadQuestions();

        this.coordinates = getCoordinates();
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
        updateAllowedPackages(packages);

        // generate necessary infrastructure
        addTests(testsLocation);
        addResources(resourcesLocation);
        addDockerImages(dockerImages);
        addUserCodeFilterFile(packages);
        addAdditionalDependencies(additionalTestImplementationDependencies);
        addAgentConfigBlock();

        // run agent in conditional mode
        collectMetadata();

        // remove empty files
        removeEmptyConfigFiles();

        // create a PR
        boolean shouldCreatePR = shouldCreatePullRequest();
        if (shouldCreatePR) {
            String branch = "add-support-for-" + coordinates.toString().replace(':', '-');
            createPullRequest(branch);

            InteractiveTaskUtils.printUserInfo("After your pull requests gets generated, please update the pull request description to mention all places where your pull request" +
                    "accesses files, network, docker, or any other external service, and check if all checks in the description are correctly marked");
        }

        InteractiveTaskUtils.printSuccessfulStatement("Contribution successfully completed! Thank you!");
    }

    private Coordinates getCoordinates() {
        ContributingQuestion question = questions.get("coordinates");
        return InteractiveTaskUtils.askQuestion(question.question(), question.help(), (answer) -> {
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
        ContributingQuestion question = questions.get("testsLocation");
        return InteractiveTaskUtils.askQuestion(question.question(), question.help(), (answer) -> {
            Path testsLocation = Path.of(answer).toAbsolutePath();
            if (!Files.exists(testsLocation)) {
                throw new IllegalStateException("Cannot find tests directory on the given location: " + testsLocation + ". Type help for explanation.");
            }

            if (!Files.isDirectory(testsLocation)) {
                throw new IllegalStateException("Provided path does not represent a directory: " + testsLocation + ". Type help for explanation.");
            }

            checkPackages(testsLocation);

            return testsLocation;
        });
    }

    private void checkPackages(Path testsPath) {
        List<Path> javaFiles = new ArrayList<>();
        FilesUtils.findJavaFiles(testsPath, javaFiles);
        javaFiles.forEach(file -> {
            try {
                Optional<String> packageLine = Files.readAllLines(file).stream().filter(line -> line.contains("package ")).findFirst();
                if (packageLine.isEmpty()) {
                    throw new RuntimeException("Java file: " + file + " does not contain declared package");
                }

                String declaredPackage = packageLine.get().split(" ")[1].replace(";", "");
                String packagePath = declaredPackage.replace(".", File.separator);
                if (!Files.exists(testsPath.resolve(packagePath))) {
                    throw new IllegalStateException("File: " + file + " has package: " + declaredPackage +
                            " that cannot be found on tests location: " + testsPath +
                            ". Please make sure that the location you provided is a directory that contains packages with tests implementations");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Path getResourcesLocation(){
        ContributingQuestion question = questions.get("resourcesLocation");
        return InteractiveTaskUtils.askQuestion(question.question(), question.help(), (answer) -> {
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
        ContributingQuestion question = questions.get("docker");

        List<String> images = new ArrayList<>();
        while (true) {
            String nextImage = InteractiveTaskUtils.askQuestion(question.question(), question.help(), answer -> {
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
        ContributingQuestion question = questions.get("allowedPackages");
        List<String> packages = new ArrayList<>();
        while (true) {
            String nextPackage = InteractiveTaskUtils.askQuestion(question.question(), question.help(), answer -> answer);
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
        ContributingQuestion question = questions.get("additionalDependencies");
        List<Coordinates> dependencies = new ArrayList<>();
        while (true) {
            Coordinates dependency = InteractiveTaskUtils.askQuestion(question.question(), question.help(), answer -> {
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

    private void createStubs(boolean shouldUpdate){
        InteractiveTaskUtils.printUserInfo("Generating stubs for: " + coordinates );
        if (shouldUpdate) {
            invokeCommand("gradle scaffold --coordinates " + coordinates + " --update", "Cannot generate stubs for: " + coordinates);
        } else {
            invokeCommand("gradle scaffold --coordinates " + coordinates, "Cannot generate stubs for: " + coordinates);
        }
    }

    private void updateAllowedPackages(List<String> allowedPackages) throws IOException {
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
            MetadataIndexEntry replacedEntry = entries.remove(replaceEntryIndex);
            Set<String> extendedAllowedPackages = new HashSet<>(replacedEntry.allowedPackages());
            extendedAllowedPackages.addAll(allowedPackages);

            entries.add(new MetadataIndexEntry(replacedEntry.directory(), replacedEntry.module(), replacedEntry.requires(), new ArrayList<>(extendedAllowedPackages)));
        }

        List<MetadataIndexEntry> sortedEntries = entries.stream()
                .sorted(Comparator.comparing(MetadataIndexEntry::module))
                .toList();

        objectMapper.writeValue(metadataIndex, sortedEntries);
    }

    private void addTests(Path originalTestsLocation){
        Path destination = testsDirectory.resolve("src")
                .resolve("test")
                .resolve("java");
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


        try(InputStream stream = ContributionTask.class.getResourceAsStream("/contributing/agent.template")) {
            if (stream == null) {
                throw new RuntimeException("Cannot find template for the graalvm configuration block");
            }

            String content = "\n" + (new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            writeToFile(buildFilePath, content, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Cannot add agent block into: " + buildFilePath);
        }
    }

    private void collectMetadata() {
        InteractiveTaskUtils.printUserInfo("Generating metadata");
        invokeCommand("gradle -Pagent test", "Cannot generate metadata", testsDirectory);

        InteractiveTaskUtils.printUserInfo("Performing metadata copy");
        invokeCommand("gradle metadataCopy --task test --dir " + metadataDirectory, "Cannot perform metadata copy", testsDirectory);
    }

    private enum CONFIG_FILES {
        RESOURCE("resource-config.json"),
        REFLECTION("reflect-config.json"),
        SERIALIZATION("serialization-config.json"),
        JNI("jni-config.json"),
        PROXY("proxy-config.json"),
        PREDEFINED_CLASSES("predefined-classes-config.json");

        private final String value;
        public String get() {
            return value;
        }

        CONFIG_FILES(String val) {
            this.value = val;
        }
    }

    private void removeEmptyConfigFiles() throws IOException {
        Path indexFile = metadataDirectory.resolve("index.json");
        List<CONFIG_FILES> remainingFiles = new LinkedList<>(Arrays.asList(CONFIG_FILES.values()));

        Path resourceConfigPath = metadataDirectory.resolve(CONFIG_FILES.RESOURCE.get());
        ResourceConfigModel resourceConfig = objectMapper.readValue(new File(resourceConfigPath.toUri()), new TypeReference<>() {});
        if (resourceConfig.bundles().isEmpty() && resourceConfig.resources().toString().equalsIgnoreCase("{}")) {
            removeConfigFile(resourceConfigPath, CONFIG_FILES.RESOURCE, remainingFiles);
        }

        Path serializationConfigPath = metadataDirectory.resolve(CONFIG_FILES.SERIALIZATION.get());
        SerializationConfigModel serializationConfig = objectMapper.readValue(new File(serializationConfigPath.toUri()), new TypeReference<>() {});
        if (serializationConfig.lambdaCapturingTypes().isEmpty() && serializationConfig.types().isEmpty() && serializationConfig.proxies().isEmpty()) {
            removeConfigFile(serializationConfigPath, CONFIG_FILES.SERIALIZATION, remainingFiles);
        }

        Path jniConfigPath = metadataDirectory.resolve(CONFIG_FILES.JNI.get());
        List<Object> jniConfig = objectMapper.readValue(new File(jniConfigPath.toUri()), new TypeReference<>() {});
        if (jniConfig.isEmpty()) {
            removeConfigFile(jniConfigPath, CONFIG_FILES.JNI, remainingFiles);
        }

        Path proxyConfigPath = metadataDirectory.resolve(CONFIG_FILES.PROXY.get());
        List<Object> proxyConfig = objectMapper.readValue(new File(proxyConfigPath.toUri()), new TypeReference<>() {});
        if (proxyConfig.isEmpty()) {
            removeConfigFile(proxyConfigPath, CONFIG_FILES.PROXY, remainingFiles);
        }

        Path reflectConfigPath = metadataDirectory.resolve(CONFIG_FILES.REFLECTION.get());
        List<Object> reflectConfig = objectMapper.readValue(new File(reflectConfigPath.toUri()), new TypeReference<>() {});
        if (reflectConfig.isEmpty()) {
            removeConfigFile(reflectConfigPath, CONFIG_FILES.REFLECTION, remainingFiles);
        }

        Path predefinedClassesConfigPath = metadataDirectory.resolve(CONFIG_FILES.PREDEFINED_CLASSES.get());
        List<PredefinedClassesConfigModel> predefinedClassesConfig = objectMapper.readValue(new File(predefinedClassesConfigPath.toUri()), new TypeReference<>() {});
        if (predefinedClassesConfig.size() == 1) {
            if (predefinedClassesConfig.get(0).type().equalsIgnoreCase("agent-extracted") && predefinedClassesConfig.get(0).classes().isEmpty()) {
                removeConfigFile(predefinedClassesConfigPath, CONFIG_FILES.PREDEFINED_CLASSES, remainingFiles);
            }
        }

        Path agentExtractedPredefinedClasses = metadataDirectory.resolve("agent-extracted-predefined-classes");
        if (Files.exists(agentExtractedPredefinedClasses)) {
            File[] extractedPredefinedClasses = new File(agentExtractedPredefinedClasses.toUri()).listFiles();
            if (extractedPredefinedClasses == null || extractedPredefinedClasses.length == 0) {
                InteractiveTaskUtils.printUserInfo("Removing empty: agent-extracted-predefined-classes");
                invokeCommand("rm -r " + agentExtractedPredefinedClasses, "Cannot delete empty config file: " + agentExtractedPredefinedClasses);
            }
        }

        trimIndexFile(indexFile, remainingFiles);
    }

    private void removeConfigFile(Path path, CONFIG_FILES file, List<CONFIG_FILES> remainingFiles) {
        InteractiveTaskUtils.printUserInfo("Removing empty: " + file.get());
        invokeCommand("rm " + path, "Cannot delete empty config file: " + path);
        remainingFiles.remove(file);
    }

    private void trimIndexFile(Path index, List<CONFIG_FILES> remainingFiles) throws IOException {
        InteractiveTaskUtils.printUserInfo("Removing sufficient entries from: " + index);
        ConfigurationStringBuilder sb = new ConfigurationStringBuilder();
        sb.openArray().newLine();
        sb.indent();
        for (int i = 0; i < remainingFiles.size(); i++) {
            sb.quote(remainingFiles.get(i).get());

            if (i != remainingFiles.size() - 1) {
                sb.concat();
            }

            sb.newLine();
        }

        sb.unindent();
        sb.closeArray();

        writeToFile(index, sb.toString(), StandardOpenOption.TRUNCATE_EXISTING);
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
}
