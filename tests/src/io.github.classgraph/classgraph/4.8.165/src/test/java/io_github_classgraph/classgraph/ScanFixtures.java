/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class ScanFixtures {
    public enum Mode {
        FAST, SAFE
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Tag {
        String value() default "catalog";
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Details {
        Mode mode() default Mode.SAFE;
        Mode[] alternatives() default {Mode.FAST, Mode.SAFE};
        Class<?> type() default String.class;
        Class<?>[] types() default {String.class, Integer.class};
        Tag nested() default @Tag("inner");
        Tag[] tags() default {@Tag("first"), @Tag("second")};
        boolean enabled() default true;
        byte small() default 7;
        short count() default 12;
        char initial() default 'C';
        int size() default 42;
        long total() default 5000000000L;
        float ratio() default 1.25f;
        double weight() default 2.5;
        int[] numbers() default {3, 5, 8};
    }

    @Tag
    @Details
    public static class Subject implements Runnable {
        public static final String TITLE = "catalog";
        public String name = "reader";
        public String[][] matrix;
        public int[] numbers;
        private int hidden = 17;

        public Subject() {
        }

        private Subject(int hidden) {
            this.hidden = hidden;
        }

        public String greet(String prefix) {
            return prefix + name;
        }

        private int secret() {
            return hidden;
        }

        @Override
        public void run() {
            name = "finished";
        }
    }

    @Tag
    public static class Child extends Subject {
    }
}
