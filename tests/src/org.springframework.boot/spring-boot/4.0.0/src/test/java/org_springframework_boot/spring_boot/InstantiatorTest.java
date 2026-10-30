/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.util.Instantiator;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

public class InstantiatorTest {
    @Test
    void injectsAvailableArgumentsAndOrdersCreatedImplementations() {
        Instantiator<Task> instantiator = new Instantiator<>(Task.class,
                parameters -> parameters.add(String.class, "injected"));

        List<Task> tasks = instantiator.instantiateTypes(List.of(LaterTask.class, FirstTask.class));

        assertThat(tasks).extracting(Task::value).containsExactly("injected:first", "injected:later");
        assertThat(instantiator.instantiate(FirstTask.class.getName()).value()).isEqualTo("injected:first");
    }

    public interface Task extends Ordered {
        String value();
    }

    public static class FirstTask implements Task {
        private final String prefix;

        public FirstTask(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String value() {
            return prefix + ":first";
        }

        @Override
        public int getOrder() {
            return 1;
        }
    }

    public static class LaterTask implements Task {
        private final String prefix;

        public LaterTask() {
            this("default");
        }

        public LaterTask(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String value() {
            return prefix + ":later";
        }

        @Override
        public int getOrder() {
            return 2;
        }
    }
}
