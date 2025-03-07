package org.graalvm.internal.tck.utils;

import java.util.Scanner;
import java.util.function.Function;

public class InteractiveTaskUtils {

    private static final Scanner scanner = new Scanner(System.in);

    public static <R> R askQuestion(String question, String helpMessage, Function<String, R> handleAnswer) {
        while (true) {
            ColoredOutput.print("[QUESTION] ", ColoredOutput.OUTPUT_COLOR.GREEN);
            System.out.println(question);

            String answer = scanner.next();
            if (answer.equalsIgnoreCase("help")) {
                ColoredOutput.println("[HELP] " + helpMessage, ColoredOutput.OUTPUT_COLOR.YELLOW);
                continue;
            }

            try {
                return handleAnswer.apply(answer);
            } catch (IllegalStateException ex) {
                ColoredOutput.println("[ERROR] " + ex.getMessage(), ColoredOutput.OUTPUT_COLOR.RED);
            }
        }
    }

    public static boolean askYesNoQuestion(String question, String helpMessage, boolean defaultAnswer) {
        while (true) {
            ColoredOutput.print("[QUESTION] ", ColoredOutput.OUTPUT_COLOR.GREEN);
            System.out.println(question);

            String answer = scanner.next();
            if (answer.equalsIgnoreCase("help")) {
                ColoredOutput.println("[HELP] " + helpMessage, ColoredOutput.OUTPUT_COLOR.YELLOW);
                continue;
            }

            if (answer.isBlank()) {
                return defaultAnswer;
            }

            if (answer.equalsIgnoreCase("y")) {
                return true;
            }

            if (answer.equalsIgnoreCase("n")) {
                return false;
            }

            ColoredOutput.println("[ERROR] Your answer must be either y (for yes) or n (for no)", ColoredOutput.OUTPUT_COLOR.RED);
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

    private static void waitForUserToReadMessage() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException("Waiting for user to read the message was interrupted. Reason: " + e.getMessage());
        }
    }

}
