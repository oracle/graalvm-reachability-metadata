package org.graalvm.internal.tck;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;

public abstract class ContributionTask extends DefaultTask {

    @Inject
    protected abstract ExecOperations getExecOperations();

    private Path testsDirectory;
    private Path metadataDirectory;

    private final Scanner scanner = new Scanner(System.in);

    private void initializeWorkingDirectories(Coordinates coordinates){
        testsDirectory = Path.of(getProject().file(CoordinateUtils.replace("tests/src/$group$/$artifact$/$version$", coordinates)).getAbsolutePath());
        metadataDirectory = Path.of(getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/$version$", coordinates)).getAbsolutePath());
    }

    @TaskAction
    void run() throws IOException {
        System.out.println("*********************************CONTRIBUTING*********************************");
        System.out.println("Hello! This task will help you to contribute to metadata repository." +
                " Please answer the following questions. In case you don't know the answer on the question, type \"help\" for more information.");

        Coordinates coordinates = getCoordinates();
        closeSection();

        Path testsLocation = getTestsLocation();
        closeSection();

        Path resourcesLocation = getResourcesLocation();
        closeSection();

        List<String> packages = getAllowedPackages();
        closeSection();

        // ask for additional testImplementation dependencies
//        List<Coordinates> additionalTestImplementationDependencies = getAdditionalDependencies();
//        closeSection();


        initializeWorkingDirectories(coordinates);
        createStubs(coordinates);
        addTests(testsLocation, coordinates);
        addResources(resourcesLocation, coordinates);
        addUserCodeFilterFile(packages);

        // run agent in conditional mode

        // TODO ask user to check metadata. If everything is okay, ask user if the task should create a PR for him

    }

    private Coordinates getCoordinates() {
        String question = "What library you want to support? Maven coordinates: ";
        String helpMessage = "Maven coordinates consists of three parts in the following format \"groupId:artifactId:version\". " +
                "For more information visit: https://maven.apache.org/repositories/artifacts.html";

        return askQuestion(question, helpMessage, (answer) -> {
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

        return askQuestion(question, helpMessage, (answer) -> {
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

        return askQuestion(question, helpMessage, (answer) -> {
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
            String nextPackage = askQuestion(question, helpMessage, answer -> answer);
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
                "When you finish adding dependencies, type \"-\" to terminate the inclusion process";

        List<Coordinates> dependencies = new ArrayList<>();
        while (true) {
            Coordinates dependency = askQuestion(question, helpMessage, answer -> {
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
        System.out.println("Generating stubs for: " + coordinates + "...");

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
        Path destination = testsDirectory.resolve("src").resolve("test").resolve("java").resolve(coordinates.group()).resolve(coordinates.artifact());
        Path allTests = originalTestsLocation.resolve(".");

        System.out.println("Removing dummy test stubs...");
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

        System.out.println("Copying tests from: " + originalTestsLocation + " to " + destination + "...");
        ByteArrayOutputStream copyExecCommand = new ByteArrayOutputStream();
        String[] copyCommand = { "-a", allTests.toString(), destination.toString()};
        var copyResult = getExecOperations().exec(execSpec -> {
            execSpec.setExecutable("cp");
            execSpec.setArgs(List.of(copyCommand));
            execSpec.setStandardOutput(copyExecCommand);
        });

        if (copyResult.getExitValue() != 0) {
            throw new RuntimeException("Cannot copy files to: " + destination);
        }
    }

    private void addResources(Path originalResourcesDirectory, Coordinates coordinates){
        if (originalResourcesDirectory == null) {
            return;
        }

        Path destination = testsDirectory.resolve("src").resolve("test");
        System.out.println("Copying resources from: " + originalResourcesDirectory + " to " + destination + "...");

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
        System.out.println("Generating user-code-filter.json...");

        StringBuilder userCodeFilterBuilder = new StringBuilder();
        userCodeFilterBuilder.append("{").append("\n");
        userCodeFilterBuilder.append("\t \"rules\": [").append("\n");
        userCodeFilterBuilder.append("\t\t{\"excludeClasses\": \"**\"},").append("\n");
        for (int i = 0; i < packages.size(); i++) {
            String nextPackage = packages.get(i);
            userCodeFilterBuilder.append("\t\t{\"includeClasses\": \"").append(nextPackage).append(".**\"}");
            if (i < packages.size() - 1) {
                userCodeFilterBuilder.append(",");
            }

            userCodeFilterBuilder.append("\n");
        }
        userCodeFilterBuilder.append("\t]").append("\n");
        userCodeFilterBuilder.append("}");

        writeToFile(testsDirectory.resolve("user-code-filter.json"), userCodeFilterBuilder.toString());
    }

    private <R> R askQuestion(String question, String helpMessage, Function<String, R> handleAnswer) {
        while (true) {
            System.out.println("[QUESTION] " + question);

            String answer = scanner.next();
            if (answer.equalsIgnoreCase("help")) {
                System.out.println("[HELP] " + helpMessage);
                continue;
            }

            try {
                return handleAnswer.apply(answer);
            } catch (IllegalStateException ex) {
                System.out.println("[ERROR] " + ex.getMessage());
            }
        }
    }


    private void writeToFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private void closeSection() {
        System.out.println("------------------------------------------------------------------------------------------");
    }

}
