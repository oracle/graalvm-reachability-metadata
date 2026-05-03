/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjfx.javafx_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ArrayChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableIntegerArray;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.Event;
import javafx.event.EventDispatchChain;
import javafx.event.EventDispatcher;
import javafx.event.EventTarget;
import javafx.event.EventType;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;

public class Javafx_baseTest {

    private static final EventType<Event> PROFILE_UPDATED_EVENT = new EventType<>(Event.ANY, "PROFILE_UPDATED_EVENT");

    @Test
    void propertiesAndBindingsStayInSyncAcrossBindingModes() {
        SimpleStringProperty primaryName = new SimpleStringProperty("Ada");
        SimpleStringProperty nickname = new SimpleStringProperty("Countess");
        SimpleStringProperty mirror = new SimpleStringProperty("Ada");
        SimpleBooleanProperty includeNickname = new SimpleBooleanProperty(true);
        StringBinding displayName = Bindings.createStringBinding(
                () -> includeNickname.get() ? primaryName.get() + " (" + nickname.get() + ")" : primaryName.get(),
                primaryName,
                nickname,
                includeNickname);

        try {
            List<String> displayChanges = new ArrayList<>();
            displayName.addListener((observable, oldValue, newValue) -> displayChanges.add(oldValue + "->" + newValue));
            Bindings.bindBidirectional(primaryName, mirror);

            assertThat(displayName.get()).isEqualTo("Ada (Countess)");

            mirror.set("Grace");
            assertThat(primaryName.get()).isEqualTo("Grace");
            assertThat(displayName.get()).isEqualTo("Grace (Countess)");

            includeNickname.set(false);
            assertThat(displayName.get()).isEqualTo("Grace");

            primaryName.set("Barbara");
            assertThat(mirror.get()).isEqualTo("Barbara");
            assertThat(displayName.get()).isEqualTo("Barbara");

            nickname.set("Compiler");
            includeNickname.set(true);
            assertThat(displayName.get()).isEqualTo("Barbara (Compiler)");

            Bindings.unbindBidirectional(primaryName, mirror);
            mirror.set("Detached");
            assertThat(primaryName.get()).isEqualTo("Barbara");
            assertThat(displayChanges).isNotEmpty();
            assertThat(displayChanges.get(displayChanges.size() - 1)).isEqualTo("Barbara->Barbara (Compiler)");
        } finally {
            displayName.dispose();
        }
    }

    @Test
    void observableIntegerArrayTracksChangesAndCopiesValues() {
        ObservableIntegerArray values = FXCollections.observableIntegerArray(1, 2, 3);
        List<String> arrayChanges = new ArrayList<>();

        values.addListener((ArrayChangeListener<ObservableIntegerArray>) (observableArray, sizeChanged, from, to) ->
                arrayChanges.add(sizeChanged + ":" + from + "-" + to));

        values.set(1, 5);
        values.addAll(8, 13);
        values.resize(4);

        ObservableIntegerArray copy = FXCollections.observableIntegerArray(values);
        values.set(0, 21);

        assertThat(values.toArray(null)).containsExactly(21, 5, 3, 8);
        assertThat(copy.toArray(null)).containsExactly(1, 5, 3, 8);
        assertThat(arrayChanges).hasSize(4);
        assertThat(arrayChanges.get(0)).startsWith("false:1-");
        assertThat(arrayChanges.get(1)).startsWith("true:3-");
    }

    @Test
    void filteredAndSortedListsReactToSourceAndElementChanges() {
        ObservableList<TaskItem> tasks = FXCollections.observableArrayList(task -> new Observable[] {
                task.nameProperty(),
                task.priorityProperty()
        });
        TaskItem beta = new TaskItem("beta", 2);
        TaskItem alpha = new TaskItem("alpha", 1);
        TaskItem gamma = new TaskItem("gamma", 3);
        tasks.addAll(beta, alpha, gamma);

        FilteredList<TaskItem> prioritized = new FilteredList<>(tasks, task -> task.getPriority() >= 2);
        SortedList<TaskItem> sorted = new SortedList<>(prioritized, Comparator.comparing(TaskItem::getName));
        List<List<String>> snapshots = new ArrayList<>();
        sorted.addListener((ListChangeListener<TaskItem>) change -> {
            while (change.next()) {
                // exhaust change details so the transformation is fully applied
            }
            snapshots.add(taskNames(sorted));
        });

        assertThat(taskNames(prioritized)).containsExactly("beta", "gamma");
        assertThat(taskNames(sorted)).containsExactly("beta", "gamma");

        alpha.setPriority(4);
        assertThat(taskNames(prioritized)).containsExactly("beta", "alpha", "gamma");
        assertThat(taskNames(sorted)).containsExactly("alpha", "beta", "gamma");

        gamma.setName("aardvark");
        assertThat(taskNames(sorted)).containsExactly("aardvark", "alpha", "beta");
        assertThat(prioritized.getSourceIndex(1)).isEqualTo(1);
        assertThat(snapshots).contains(List.of("alpha", "beta", "gamma"), List.of("aardvark", "alpha", "beta"));
    }

    @Test
    void observableMapAndSetListenersReportMutations() {
        ObservableMap<String, Integer> counts = FXCollections.observableHashMap();
        ObservableSet<String> labels = FXCollections.observableSet("base");
        List<String> mapEvents = new ArrayList<>();
        List<String> setEvents = new ArrayList<>();

        counts.addListener((MapChangeListener<String, Integer>) change -> {
            if (change.wasAdded() && change.wasRemoved()) {
                mapEvents.add("replaced " + change.getKey() + ":" + change.getValueRemoved() + "->" + change.getValueAdded());
            } else if (change.wasAdded()) {
                mapEvents.add("added " + change.getKey() + ":" + change.getValueAdded());
            } else {
                mapEvents.add("removed " + change.getKey() + ":" + change.getValueRemoved());
            }
        });
        labels.addListener((SetChangeListener<String>) change -> {
            if (change.wasAdded()) {
                setEvents.add("added " + change.getElementAdded());
            }
            if (change.wasRemoved()) {
                setEvents.add("removed " + change.getElementRemoved());
            }
        });

        counts.put("listeners", 1);
        counts.put("listeners", 2);
        counts.remove("listeners");
        labels.add("bindings");
        labels.remove("base");

        assertThat(mapEvents).containsExactly(
                "added listeners:1",
                "replaced listeners:1->2",
                "removed listeners:2");
        assertThat(setEvents).containsExactly("added bindings", "removed base");
        assertThat(labels).containsExactly("bindings");
    }

    @Test
    void eventDispatchChainUsesCustomDispatchersAndSupportsCopyFor() {
        List<String> order = new ArrayList<>();
        RecordingEventTarget target = new RecordingEventTarget(
                new RecordingDispatcher("first", order, false),
                new RecordingDispatcher("second", order, true),
                new RecordingDispatcher("third", order, false));

        Event original = new Event("source", target, PROFILE_UPDATED_EVENT);
        original.consume();

        Event copied = original.copyFor("copy", target);
        assertThat(copied.getSource()).isEqualTo("copy");
        assertThat(copied.getTarget()).isSameAs(target);
        assertThat(copied.getEventType()).isSameAs(PROFILE_UPDATED_EVENT);
        assertThat(copied.isConsumed()).isFalse();

        Event.fireEvent(target, copied);

        assertThat(order).containsExactly("first:PROFILE_UPDATED_EVENT", "second:PROFILE_UPDATED_EVENT");
        assertThat(copied.isConsumed()).isTrue();
    }

    @Test
    void listPropertyTracksContentSizeAndAssignedLists() {
        SimpleListProperty<String> names = new SimpleListProperty<>(FXCollections.observableArrayList("Ada"));
        List<String> sizeChanges = new ArrayList<>();
        List<List<String>> snapshots = new ArrayList<>();
        List<Boolean> emptyStates = new ArrayList<>();

        names.sizeProperty().addListener((observable, oldValue, newValue) -> sizeChanges.add(oldValue + "->" + newValue));
        names.addListener((ListChangeListener<String>) change -> {
            while (change.next()) {
                // exhaust change details so the property state is fully applied
            }
            snapshots.add(List.copyOf(names));
        });
        names.emptyProperty().addListener((observable, oldValue, newValue) -> emptyStates.add(newValue));

        names.addAll("Grace", "Barbara");
        names.set(FXCollections.observableArrayList("Diana", "Evelyn"));
        names.remove("Diana");
        names.clear();

        assertThat(names).isEmpty();
        assertThat(sizeChanges).containsExactly("1->3", "3->2", "2->1", "1->0");
        assertThat(snapshots).containsExactly(
                List.of("Ada", "Grace", "Barbara"),
                List.of("Diana", "Evelyn"),
                List.of("Evelyn"),
                List.of());
        assertThat(emptyStates).containsExactly(true);
    }

    @Test
    void durationFactoriesParsingAndArithmeticRemainConsistent() {
        Duration parsedMilliseconds = Duration.valueOf("1500ms");
        Duration parsedSeconds = Duration.valueOf("2.5s");
        Duration scaledMinute = Duration.minutes(1).divide(4);
        Duration combined = parsedMilliseconds.add(parsedSeconds).subtract(Duration.seconds(1));

        assertThat(parsedMilliseconds).isEqualTo(Duration.millis(1500));
        assertThat(parsedSeconds).isEqualTo(Duration.millis(2500));
        assertThat(scaledMinute).isEqualTo(Duration.seconds(15));
        assertThat(combined).isEqualTo(Duration.seconds(3));
        assertThat(combined.greaterThan(Duration.seconds(2))).isTrue();
        assertThat(combined.lessThan(Duration.seconds(4))).isTrue();
    }

    @Test
    void durationSentinelValuesPreserveSpecialStateAcrossOperations() {
        Duration indefinite = Duration.INDEFINITE.add(Duration.seconds(5)).divide(3);
        Duration unknown = Duration.UNKNOWN.multiply(2).negate();

        assertThat(indefinite.isIndefinite()).isTrue();
        assertThat(indefinite.greaterThan(Duration.hours(1))).isTrue();
        assertThat(unknown.isUnknown()).isTrue();
        assertThat(unknown.compareTo(Duration.ZERO)).isEqualTo(1);
    }

    private static List<String> taskNames(List<TaskItem> tasks) {
        List<String> names = new ArrayList<>(tasks.size());
        for (TaskItem task : tasks) {
            names.add(task.getName());
        }
        return names;
    }

    public static final class TaskItem {
        private final StringProperty name = new SimpleStringProperty(this, "name");
        private final SimpleIntegerProperty priority = new SimpleIntegerProperty(this, "priority");

        public TaskItem(String name, int priority) {
            this.name.set(name);
            this.priority.set(priority);
        }

        public String getName() {
            return name.get();
        }

        public void setName(String name) {
            this.name.set(name);
        }

        public StringProperty nameProperty() {
            return name;
        }

        public int getPriority() {
            return priority.get();
        }

        public void setPriority(int priority) {
            this.priority.set(priority);
        }

        public SimpleIntegerProperty priorityProperty() {
            return priority;
        }
    }

    public static final class RecordingEventTarget implements EventTarget {
        private final List<EventDispatcher> dispatchers;

        public RecordingEventTarget(EventDispatcher... dispatchers) {
            this.dispatchers = List.of(dispatchers);
        }

        @Override
        public EventDispatchChain buildEventDispatchChain(EventDispatchChain tail) {
            EventDispatchChain chain = tail;
            for (EventDispatcher dispatcher : dispatchers) {
                chain = chain.append(dispatcher);
            }
            return chain;
        }
    }

    public static final class RecordingDispatcher implements EventDispatcher {
        private final String name;
        private final List<String> order;
        private final boolean consume;

        public RecordingDispatcher(String name, List<String> order, boolean consume) {
            this.name = name;
            this.order = order;
            this.consume = consume;
        }

        @Override
        public Event dispatchEvent(Event event, EventDispatchChain tail) {
            order.add(name + ":" + event.getEventType().getName());
            if (consume) {
                event.consume();
                return event;
            }
            return tail.dispatchEvent(event);
        }
    }
}
