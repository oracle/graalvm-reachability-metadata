/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.util.Collections;
import java.util.List;

import org.apache.aries.blueprint.container.BeanRecipe;
import org.apache.aries.blueprint.container.BlueprintContainerImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanRecipeTest {
    @Test
    void constructsInjectsAndInitializesBean() {
        BlueprintContainerImpl container = RecipeSupport.container();
        BeanRecipe recipe = new BeanRecipe("worker", container, Worker.class, false);
        recipe.setArguments(List.of("queue"));
        recipe.setArgTypes(Collections.singletonList(null));
        recipe.setProperty("capacity", "16");
        recipe.setInitMethod("start");
        recipe.setDestroyMethod("stop");
        Worker worker = (Worker) RecipeSupport.create(container, recipe);
        assertThat(worker.name).isEqualTo("queue");
        assertThat(worker.getCapacity()).isEqualTo(16);
        assertThat(worker.running).isTrue();
        recipe.destroy(worker);
        assertThat(worker.running).isFalse();
    }

    @Test
    void createsBeanThroughStaticFactoryMethod() {
        BlueprintContainerImpl container = RecipeSupport.container();
        BeanRecipe recipe = new BeanRecipe("factoryWorker", container, Worker.class, false);
        recipe.setFactoryMethod("named");
        recipe.setArguments(List.of("archive"));
        recipe.setArgTypes(Collections.singletonList(null));
        Worker worker = (Worker) RecipeSupport.create(container, recipe);
        assertThat(worker.name).isEqualTo("factory:archive");
    }

    public static class Worker {
        private final String name;
        private int capacity;
        private boolean running;

        public Worker(String name) {
            this.name = name;
        }

        public static Worker named(String name) {
            return new Worker("factory:" + name);
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public void start() {
            running = capacity > 0;
        }

        public void stop() {
            running = false;
        }
    }
}
