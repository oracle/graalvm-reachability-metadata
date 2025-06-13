package org.graalvm.internal.tck.utils;

public class ColoredOutput {

    public enum OUTPUT_COLOR {
        RED("\u001B[31m"),
        GREEN("\u001B[32m"),
        BLUE("\u001B[34m"),
        YELLOW("\u001B[33m"),
        WHITE("\u001B[37m");

        private final String colorCode;

        OUTPUT_COLOR(String color) {
            this.colorCode = color;
        }

        @Override
        public String toString() {
            return this.colorCode;
        }
    }

    private static final String ANSI_RESET = "\u001B[0m";

    public static void println(String message, OUTPUT_COLOR color) {
        System.out.println(color + message + ANSI_RESET);
    }

}
