package org.graalvm.internal.tck.utils;

import java.util.Scanner;
import java.util.function.Function;

public class InteractiveTaskUtils {

    private static final Scanner scanner = new Scanner(System.in);

    public static <R> R askQuestion(String question, String helpMessage, Function<String, R> handleAnswer) {
        while (true) {
            printQuestion(question);

            String answer = scanner.next();
            if (answer.equalsIgnoreCase("help")) {
                printHelpMessage(helpMessage);
                continue;
            }

            try {
                return handleAnswer.apply(answer);
            } catch (IllegalStateException ex) {
                printErrorMessage(ex.getMessage());
            }
        }
    }

    public static boolean askYesNoQuestion(String question, String helpMessage, boolean defaultAnswer) {
        while (true) {
            printQuestion(question);

            String answer = scanner.next();
            if (answer.equalsIgnoreCase("help")) {
                printHelpMessage(helpMessage);
                continue;
            }

            if (answer.isEmpty()) {
                return defaultAnswer;
            }

            if (answer.equalsIgnoreCase("y")) {
                return true;
            }

            if (answer.equalsIgnoreCase("n")) {
                return false;
            }

            printErrorMessage("Your answer must be either y (for yes) or n (for no)");
        }
    }

    public static void closeSection() {
        ColoredOutput.println("------------------------------------------------------------------------------------------", ColoredOutput.OUTPUT_COLOR.BLUE);
    }

    public static void printUserInfo(String message) {
        ColoredOutput.println("[INFO] " + message + "...", ColoredOutput.OUTPUT_COLOR.BLUE);
        waitForUserToReadMessage();
    }

    public static void printSuccessfulStatement(String message) {
        ColoredOutput.println("[SUCCESS] " + message, ColoredOutput.OUTPUT_COLOR.GREEN);
        waitForUserToReadMessage();
    }

    public static void printErrorMessage(String message) {
        ColoredOutput.println("[ERROR] " + message, ColoredOutput.OUTPUT_COLOR.RED);
        waitForUserToReadMessage();
    }

    public static void printHelpMessage(String helpMessage) {
        ColoredOutput.println("[HELP] " + helpMessage, ColoredOutput.OUTPUT_COLOR.YELLOW);
        waitForUserToReadMessage();
    }

    private static void printQuestion(String question) {
        ColoredOutput.println("[QUESTION] " + question, ColoredOutput.OUTPUT_COLOR.GREEN);
    }

    private static void waitForUserToReadMessage() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException("Waiting for user to read the message was interrupted. Reason: " + e.getMessage());
        }
    }

}
