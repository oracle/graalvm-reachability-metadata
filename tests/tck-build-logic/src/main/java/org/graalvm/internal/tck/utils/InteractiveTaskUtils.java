package org.graalvm.internal.tck.utils;

import org.graalvm.internal.tck.exceptions.ContributingException;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class InteractiveTaskUtils {

    @FunctionalInterface
    public interface ContributingHandler<V, R> {
        R apply(V v) throws ContributingException;
    }

    public static <R> R askQuestion(String question, String helpMessage, ContributingHandler<String, R> handleAnswer) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            printQuestion(question);

            String answer = scanner.next();
            if (answer.equalsIgnoreCase("help")) {
                printHelpMessage(helpMessage);
                continue;
            }

            try {
                return handleAnswer.apply(answer);
            } catch (ContributingException ex) {
                printErrorMessage(ex.getMessage());
            }
        }
    }

    public static <R> List<R> askRecurringQuestions(String question, String help, int minimalNumberOfAnswers, ContributingHandler<String, R> handleAnswer) {
        List<R> answers = new ArrayList<>();
        while (true) {
            String answer = InteractiveTaskUtils.askQuestion(question, help, a -> a);
            if (answer.equalsIgnoreCase("-")) {
                if (answers.size() < minimalNumberOfAnswers) {
                    printErrorMessage("At least " + minimalNumberOfAnswers + " should be provided. Currently provided: " + answers.size());
                    continue;
                }

                break;
            }

            try {
                R item = handleAnswer.apply(answer);
                answers.add(item);
            } catch (ContributingException e) {
                printErrorMessage(e.getMessage());
            }
        }

        return answers;
    }

    public static boolean askYesNoQuestion(String question, String helpMessage, boolean defaultAnswer) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            printQuestion(question);

            String answer = scanner.nextLine();
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
