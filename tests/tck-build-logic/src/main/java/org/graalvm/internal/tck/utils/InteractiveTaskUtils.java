package org.graalvm.internal.tck.utils;

import java.util.Scanner;
import java.util.function.Function;

public class InteractiveTaskUtils {

    private static final Scanner scanner = new Scanner(System.in);

    public static <R> R askQuestion(String question, String helpMessage, Function<String, R> handleAnswer) {
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

    public static void closeSection() {
        System.out.println("------------------------------------------------------------------------------------------");
    }

}
