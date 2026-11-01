/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TestBeans {
    private TestBeans() {
    }

    public interface Greeting {
        String greet(String name);
    }

    public static class GreetingService implements Greeting {
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    public static class Person {
        private String name = "initial";
        private int age;
        private Person child;
        private List<String> tags = new ArrayList<>();
        private Map<String, Integer> scores = new LinkedHashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public Person getChild() {
            return child;
        }

        public void setChild(Person child) {
            this.child = child;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public Map<String, Integer> getScores() {
            return scores;
        }

        public void setScores(Map<String, Integer> scores) {
            this.scores = scores;
        }
    }

    public static class Fields {
        public String label = "before";
        public int count = 1;
        public static final String CATEGORY = "sample";
    }

    public static class Lifecycle {
        private boolean started;
        private boolean stopped;

        public void start() {
            started = true;
        }

        public void stop() {
            stopped = true;
        }

        public boolean isStarted() {
            return started;
        }

        public boolean isStopped() {
            return stopped;
        }
    }
}
