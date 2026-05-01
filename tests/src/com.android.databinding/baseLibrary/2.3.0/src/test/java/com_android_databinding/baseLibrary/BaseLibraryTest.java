/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_android_databinding.baseLibrary;

import android.databinding.Bindable;
import android.databinding.BindingAdapter;
import android.databinding.BindingBuildInfo;
import android.databinding.BindingConversion;
import android.databinding.BindingMethod;
import android.databinding.BindingMethods;
import android.databinding.CallbackRegistry;
import android.databinding.Observable;
import android.databinding.ObservableList;
import android.databinding.ObservableMap;
import android.databinding.Untaggable;

import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseLibraryTest {
    private static final CallbackRegistry.NotifierCallback<RecordingCallback, EventSource, String>
            EVENT_NOTIFIER = new CallbackRegistry.NotifierCallback<RecordingCallback, EventSource, String>() {
                @Override
                public void onNotifyCallback(RecordingCallback callback, EventSource sender, int arg,
                        String payload) {
                    callback.onEvent(sender, arg, payload);
                }
            };

    @Test
    void callbackRegistryNotifiesUniqueCallbacksInRegistrationOrder() {
        CallbackRegistry<RecordingCallback, EventSource, String> registry = newRegistry();
        EventSource sender = new EventSource("source");
        RecordingCallback first = new RecordingCallback("first");
        RecordingCallback second = new RecordingCallback("second");

        registry.add(first);
        registry.add(second);
        registry.add(first);
        registry.notifyCallbacks(sender, 7, "payload");

        assertThat(first.events).containsExactly("source:7:payload");
        assertThat(second.events).containsExactly("source:7:payload");
        assertThat(registry.copyCallbacks()).containsExactly(first, second);
        assertThat(registry.isEmpty()).isFalse();
    }

    @Test
    void callbackRegistryRemovesCallbacksAndCanBeCleared() {
        CallbackRegistry<RecordingCallback, EventSource, String> registry = newRegistry();
        RecordingCallback kept = new RecordingCallback("kept");
        RecordingCallback removed = new RecordingCallback("removed");
        List<RecordingCallback> callbacks = new ArrayList<RecordingCallback>();

        registry.add(removed);
        registry.add(kept);
        registry.remove(removed);
        registry.copyCallbacks(callbacks);
        registry.notifyCallbacks(new EventSource("source"), 3, "after-remove");

        assertThat(callbacks).containsExactly(kept);
        assertThat(removed.events).isEmpty();
        assertThat(kept.events).containsExactly("source:3:after-remove");

        registry.clear();
        assertThat(registry.isEmpty()).isTrue();
        assertThat(registry.copyCallbacks()).isEmpty();
    }

    @Test
    void callbackRegistryClearDuringNotificationHidesCallbacksFromRegistryCopies() {
        CallbackRegistry<RecordingCallback, EventSource, String> registry = newRegistry();
        List<Boolean> emptyStates = new ArrayList<Boolean>();
        List<List<RecordingCallback>> snapshots = new ArrayList<List<RecordingCallback>>();
        RecordingCallback clearing = new RecordingCallback("clearing") {
            @Override
            void onEvent(EventSource sender, int code, String payload) {
                super.onEvent(sender, code, payload);
                registry.clear();
                emptyStates.add(Boolean.valueOf(registry.isEmpty()));
                snapshots.add(registry.copyCallbacks());
            }
        };
        RecordingCallback notifiedAfterClear = new RecordingCallback("notified-after-clear");

        registry.add(clearing);
        registry.add(notifiedAfterClear);
        registry.notifyCallbacks(new EventSource("first"), 5, "clear-now");
        registry.notifyCallbacks(new EventSource("second"), 6, "already-cleared");

        assertThat(clearing.events).containsExactly("first:5:clear-now");
        assertThat(notifiedAfterClear.events).containsExactly("first:5:clear-now");
        assertThat(emptyStates).containsExactly(Boolean.TRUE);
        assertThat(snapshots).containsExactly(new ArrayList<RecordingCallback>());
        assertThat(registry.isEmpty()).isTrue();
        assertThat(registry.copyCallbacks()).isEmpty();
    }

    @Test
    void callbackRegistryDefersReentrantAddsAndRemovesUntilCurrentNotificationCompletes() {
        CallbackRegistry<RecordingCallback, EventSource, String> registry = newRegistry();
        RecordingCallback late = new RecordingCallback("late");
        RecordingCallback removing = new RecordingCallback("removing") {
            @Override
            void onEvent(EventSource sender, int code, String payload) {
                super.onEvent(sender, code, payload);
                registry.remove(this);
                registry.add(late);
            }
        };
        RecordingCallback stable = new RecordingCallback("stable");

        registry.add(removing);
        registry.add(stable);
        registry.notifyCallbacks(new EventSource("first"), 1, "initial");
        registry.notifyCallbacks(new EventSource("second"), 2, "next");

        assertThat(removing.events).containsExactly("first:1:initial");
        assertThat(stable.events).containsExactly("first:1:initial", "second:2:next");
        assertThat(late.events).containsExactly("second:2:next");
        assertThat(registry.copyCallbacks()).containsExactly(stable, late);
    }

    @Test
    void callbackRegistryHandlesDeferredRemovalBeyondFirstSixtyFourCallbacks() {
        CallbackRegistry<RecordingCallback, EventSource, String> registry = newRegistry();
        List<RecordingCallback> callbacks = new ArrayList<RecordingCallback>();
        for (int i = 0; i < 70; i++) {
            callbacks.add(new RecordingCallback("callback-" + i));
        }
        RecordingCallback remover = new RecordingCallback("remover") {
            @Override
            void onEvent(EventSource sender, int code, String payload) {
                super.onEvent(sender, code, payload);
                registry.remove(callbacks.get(65));
            }
        };
        callbacks.set(0, remover);
        for (RecordingCallback callback : callbacks) {
            registry.add(callback);
        }

        registry.notifyCallbacks(new EventSource("first"), 11, "all-present");
        registry.notifyCallbacks(new EventSource("second"), 12, "after-deferred-remove");

        assertThat(callbacks.get(65).events).containsExactly("first:11:all-present");
        assertThat(callbacks.get(66).events)
                .containsExactly("first:11:all-present", "second:12:after-deferred-remove");
        assertThat(registry.copyCallbacks()).hasSize(69).doesNotContain(callbacks.get(65));
    }

    @Test
    void clonedCallbackRegistryHasIndependentCallbackList() {
        CallbackRegistry<RecordingCallback, EventSource, String> original = newRegistry();
        RecordingCallback shared = new RecordingCallback("shared");
        RecordingCallback cloneOnly = new RecordingCallback("clone-only");

        original.add(shared);
        CallbackRegistry<RecordingCallback, EventSource, String> clone = original.clone();
        clone.add(cloneOnly);
        original.clear();

        original.notifyCallbacks(new EventSource("original"), 1, "ignored");
        clone.notifyCallbacks(new EventSource("clone"), 2, "delivered");

        assertThat(original.isEmpty()).isTrue();
        assertThat(clone.copyCallbacks()).containsExactly(shared, cloneOnly);
        assertThat(shared.events).containsExactly("clone:2:delivered");
        assertThat(cloneOnly.events).containsExactly("clone:2:delivered");
    }

    @Test
    void clonedCallbackRegistryExcludesCallbacksPendingDeferredRemoval() {
        CallbackRegistry<RecordingCallback, EventSource, String> registry = newRegistry();
        List<RecordingCallback> clonedCallbacks = new ArrayList<RecordingCallback>();
        RecordingCallback removed = new RecordingCallback("removed");
        RecordingCallback kept = new RecordingCallback("kept");
        RecordingCallback cloning = new RecordingCallback("cloning") {
            @Override
            void onEvent(EventSource sender, int code, String payload) {
                super.onEvent(sender, code, payload);
                if (code == 10) {
                    registry.remove(removed);
                    CallbackRegistry<RecordingCallback, EventSource, String> clone = registry.clone();
                    clone.copyCallbacks(clonedCallbacks);
                    clone.notifyCallbacks(new EventSource("clone"), 20, "after-deferred-remove");
                }
            }
        };

        registry.add(cloning);
        registry.add(removed);
        registry.add(kept);
        registry.notifyCallbacks(new EventSource("original"), 10, "first");
        registry.notifyCallbacks(new EventSource("original"), 11, "second");

        assertThat(clonedCallbacks).containsExactly(cloning, kept);
        assertThat(cloning.events).containsExactly(
                "original:10:first",
                "clone:20:after-deferred-remove",
                "original:11:second");
        assertThat(removed.events).containsExactly("original:10:first");
        assertThat(kept.events).containsExactly(
                "clone:20:after-deferred-remove",
                "original:10:first",
                "original:11:second");
        assertThat(registry.copyCallbacks()).containsExactly(cloning, kept);
    }

    @Test
    void observableCallbacksReceivePropertyChangesAndCanBeRemoved() {
        ObservableModel model = new ObservableModel("initial");
        List<String> changes = new ArrayList<String>();
        Observable.OnPropertyChangedCallback callback = new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable sender, int propertyId) {
                changes.add(((ObservableModel) sender).getName() + ':' + propertyId);
            }
        };

        model.addOnPropertyChangedCallback(callback);
        model.setName("updated");
        model.removeOnPropertyChangedCallback(callback);
        model.setName("ignored");

        assertThat(changes).containsExactly("updated:1");
    }

    @Test
    void observableListCallbacksDescribeInsertChangeMoveRemoveAndWholeListChanges() {
        ObservableStringList list = new ObservableStringList();
        List<String> changes = new ArrayList<String>();
        ObservableList.OnListChangedCallback<ObservableStringList> callback =
                new ObservableList.OnListChangedCallback<ObservableStringList>() {
                    @Override
                    public void onChanged(ObservableStringList sender) {
                        changes.add("changed:" + sender.size());
                    }

                    @Override
                    public void onItemRangeChanged(ObservableStringList sender, int positionStart,
                            int itemCount) {
                        changes.add("rangeChanged:" + positionStart + ':' + itemCount + ':'
                                + sender.get(positionStart));
                    }

                    @Override
                    public void onItemRangeInserted(ObservableStringList sender, int positionStart,
                            int itemCount) {
                        changes.add("inserted:" + positionStart + ':' + itemCount);
                    }

                    @Override
                    public void onItemRangeMoved(ObservableStringList sender, int fromPosition,
                            int toPosition, int itemCount) {
                        changes.add("moved:" + fromPosition + ':' + toPosition + ':' + itemCount);
                    }

                    @Override
                    public void onItemRangeRemoved(ObservableStringList sender, int positionStart,
                            int itemCount) {
                        changes.add("removed:" + positionStart + ':' + itemCount);
                    }
                };

        list.addOnListChangedCallback(callback);
        list.add("alpha");
        list.add("bravo");
        list.set(1, "beta");
        list.move(0, 1);
        list.remove(0);
        list.replaceAllWith("gamma", "delta");
        list.removeOnListChangedCallback(callback);
        list.add("ignored");

        assertThat(changes).containsExactly(
                "inserted:0:1",
                "inserted:1:1",
                "rangeChanged:1:1:beta",
                "moved:0:1:1",
                "removed:0:1",
                "changed:2");
    }

    @Test
    void observableMapCallbacksReceiveChangedKeysForPutRemoveAndClear() {
        ObservableStringIntegerMap map = new ObservableStringIntegerMap();
        List<String> changes = new ArrayList<String>();
        ObservableMap.OnMapChangedCallback<ObservableStringIntegerMap, String, Integer> callback =
                new ObservableMap.OnMapChangedCallback<ObservableStringIntegerMap, String, Integer>() {
                    @Override
                    public void onMapChanged(ObservableStringIntegerMap sender, String key) {
                        changes.add(key + '=' + sender.get(key));
                    }
                };

        map.addOnMapChangedCallback(callback);
        map.put("one", 1);
        map.put("one", 11);
        map.put("two", 2);
        Map<String, Integer> asMap = map;
        assertThat(asMap).containsEntry("one", 11).containsEntry("two", 2);
        map.remove("one");
        map.clear();
        map.removeOnMapChangedCallback(callback);
        map.put("ignored", 99);

        assertThat(changes).containsExactly("one=1", "one=11", "two=2", "one=null", "two=null");
    }

    @Test
    void bindingAnnotationsCanDecorateApplicationAdaptersAndBuildMetadata() {
        BindingTarget target = new BindingTarget();

        setTextAndEnabled(target, "hello", true);
        setCount(target, convertToInteger("42"));

        assertThat(target.text).isEqualTo("hello");
        assertThat(target.enabled).isTrue();
        assertThat(target.count).isEqualTo(42);
        assertThat(new AdapterCatalog()).isNotNull();
        assertThat(new GeneratedBindingBuildInfo()).isNotNull();
    }

    private static CallbackRegistry<RecordingCallback, EventSource, String> newRegistry() {
        return new CallbackRegistry<RecordingCallback, EventSource, String>(EVENT_NOTIFIER);
    }

    @BindingAdapter(value = {"text", "enabled"}, requireAll = false)
    public static void setTextAndEnabled(BindingTarget target, String text, boolean enabled) {
        target.text = text;
        target.enabled = enabled;
    }

    @BindingAdapter("count")
    public static void setCount(BindingTarget target, Integer count) {
        target.count = count;
    }

    @BindingConversion
    public static Integer convertToInteger(String value) {
        return Integer.valueOf(value);
    }

    private static final class EventSource {
        private final String name;

        private EventSource(String name) {
            this.name = name;
        }
    }

    private static class RecordingCallback {
        private final String name;
        private final List<String> events = new ArrayList<String>();

        private RecordingCallback(String name) {
            this.name = name;
        }

        void onEvent(EventSource sender, int code, String payload) {
            events.add(sender.name + ':' + code + ':' + payload);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class ObservableModel implements Observable {
        private static final int NAME_PROPERTY_ID = 1;

        private final CallbackRegistry<OnPropertyChangedCallback, Observable, Void> callbacks =
                new CallbackRegistry<OnPropertyChangedCallback, Observable, Void>(
                        new CallbackRegistry.NotifierCallback<OnPropertyChangedCallback, Observable, Void>() {
                            @Override
                            public void onNotifyCallback(OnPropertyChangedCallback callback,
                                    Observable sender, int arg, Void ignored) {
                                callback.onPropertyChanged(sender, arg);
                            }
                        });
        private String name;

        private ObservableModel(String name) {
            this.name = name;
        }

        @Bindable
        public String getName() {
            return name;
        }

        private void setName(String name) {
            this.name = name;
            callbacks.notifyCallbacks(this, NAME_PROPERTY_ID, null);
        }

        @Override
        public void addOnPropertyChangedCallback(OnPropertyChangedCallback callback) {
            callbacks.add(callback);
        }

        @Override
        public void removeOnPropertyChangedCallback(OnPropertyChangedCallback callback) {
            callbacks.remove(callback);
        }
    }

    private static final class ObservableStringList extends AbstractList<String>
            implements ObservableList<String> {
        private final List<String> values = new ArrayList<String>();
        private final List<OnListChangedCallback<? extends ObservableList<String>>> callbacks =
                new ArrayList<OnListChangedCallback<? extends ObservableList<String>>>();

        @Override
        public void addOnListChangedCallback(
                OnListChangedCallback<? extends ObservableList<String>> callback) {
            callbacks.add(callback);
        }

        @Override
        public void removeOnListChangedCallback(
                OnListChangedCallback<? extends ObservableList<String>> callback) {
            callbacks.remove(callback);
        }

        @Override
        public String get(int index) {
            return values.get(index);
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public void add(int index, String element) {
            values.add(index, element);
            notifyInserted(index, 1);
        }

        @Override
        public String set(int index, String element) {
            String previous = values.set(index, element);
            notifyRangeChanged(index, 1);
            return previous;
        }

        @Override
        public String remove(int index) {
            String removed = values.remove(index);
            notifyRemoved(index, 1);
            return removed;
        }

        private void move(int fromPosition, int toPosition) {
            String value = values.remove(fromPosition);
            values.add(toPosition, value);
            notifyMoved(fromPosition, toPosition, 1);
        }

        private void replaceAllWith(String first, String second) {
            values.clear();
            values.add(first);
            values.add(second);
            notifyChanged();
        }

        private void notifyChanged() {
            for (OnListChangedCallback<? extends ObservableList<String>> callback : snapshot()) {
                notifyChanged(callback);
            }
        }

        private void notifyRangeChanged(int positionStart, int itemCount) {
            for (OnListChangedCallback<? extends ObservableList<String>> callback : snapshot()) {
                notifyRangeChanged(callback, positionStart, itemCount);
            }
        }

        private void notifyInserted(int positionStart, int itemCount) {
            for (OnListChangedCallback<? extends ObservableList<String>> callback : snapshot()) {
                notifyInserted(callback, positionStart, itemCount);
            }
        }

        private void notifyMoved(int fromPosition, int toPosition, int itemCount) {
            for (OnListChangedCallback<? extends ObservableList<String>> callback : snapshot()) {
                notifyMoved(callback, fromPosition, toPosition, itemCount);
            }
        }

        private void notifyRemoved(int positionStart, int itemCount) {
            for (OnListChangedCallback<? extends ObservableList<String>> callback : snapshot()) {
                notifyRemoved(callback, positionStart, itemCount);
            }
        }

        private List<OnListChangedCallback<? extends ObservableList<String>>> snapshot() {
            return new ArrayList<OnListChangedCallback<? extends ObservableList<String>>>(callbacks);
        }

        @SuppressWarnings("unchecked")
        private void notifyChanged(OnListChangedCallback<? extends ObservableList<String>> callback) {
            ((OnListChangedCallback<ObservableStringList>) callback).onChanged(this);
        }

        @SuppressWarnings("unchecked")
        private void notifyRangeChanged(OnListChangedCallback<? extends ObservableList<String>> callback,
                int positionStart, int itemCount) {
            ((OnListChangedCallback<ObservableStringList>) callback)
                    .onItemRangeChanged(this, positionStart, itemCount);
        }

        @SuppressWarnings("unchecked")
        private void notifyInserted(OnListChangedCallback<? extends ObservableList<String>> callback,
                int positionStart, int itemCount) {
            ((OnListChangedCallback<ObservableStringList>) callback)
                    .onItemRangeInserted(this, positionStart, itemCount);
        }

        @SuppressWarnings("unchecked")
        private void notifyMoved(OnListChangedCallback<? extends ObservableList<String>> callback,
                int fromPosition, int toPosition, int itemCount) {
            ((OnListChangedCallback<ObservableStringList>) callback)
                    .onItemRangeMoved(this, fromPosition, toPosition, itemCount);
        }

        @SuppressWarnings("unchecked")
        private void notifyRemoved(OnListChangedCallback<? extends ObservableList<String>> callback,
                int positionStart, int itemCount) {
            ((OnListChangedCallback<ObservableStringList>) callback)
                    .onItemRangeRemoved(this, positionStart, itemCount);
        }
    }

    private static final class ObservableStringIntegerMap extends HashMap<String, Integer>
            implements ObservableMap<String, Integer> {
        private final List<OnMapChangedCallback<? extends ObservableMap<String, Integer>, String, Integer>>
                callbacks =
                        new ArrayList<OnMapChangedCallback<? extends ObservableMap<String, Integer>, String,
                                Integer>>();

        @Override
        public void addOnMapChangedCallback(
                OnMapChangedCallback<? extends ObservableMap<String, Integer>, String, Integer> callback) {
            callbacks.add(callback);
        }

        @Override
        public void removeOnMapChangedCallback(
                OnMapChangedCallback<? extends ObservableMap<String, Integer>, String, Integer> callback) {
            callbacks.remove(callback);
        }

        @Override
        public Integer put(String key, Integer value) {
            Integer previous = super.put(key, value);
            notifyMapChanged(key);
            return previous;
        }

        @Override
        public Integer remove(Object key) {
            Integer previous = super.remove(key);
            if (previous != null) {
                notifyMapChanged((String) key);
            }
            return previous;
        }

        @Override
        public void clear() {
            List<String> keys = new ArrayList<String>(keySet());
            super.clear();
            for (String key : keys) {
                notifyMapChanged(key);
            }
        }

        private void notifyMapChanged(String key) {
            List<OnMapChangedCallback<? extends ObservableMap<String, Integer>, String, Integer>> snapshot =
                    new ArrayList<OnMapChangedCallback<? extends ObservableMap<String, Integer>, String,
                            Integer>>(callbacks);
            for (OnMapChangedCallback<? extends ObservableMap<String, Integer>, String, Integer> callback
                    : snapshot) {
                notifyMapChanged(callback, key);
            }
        }

        @SuppressWarnings("unchecked")
        private void notifyMapChanged(
                OnMapChangedCallback<? extends ObservableMap<String, Integer>, String, Integer> callback,
                String key) {
            ((OnMapChangedCallback<ObservableStringIntegerMap, String, Integer>) callback)
                    .onMapChanged(this, key);
        }
    }

    private static final class BindingTarget {
        private String text;
        private boolean enabled;
        private Integer count;

        public void applyTitle(String title) {
            text = title;
        }
    }

    @BindingMethods({
            @BindingMethod(type = BindingTarget.class, attribute = "title", method = "applyTitle")
    })
    private static final class AdapterCatalog {
    }

    @Untaggable({"transientBindingTag"})
    @BindingBuildInfo(buildId = "base-library-test")
    private static final class GeneratedBindingBuildInfo {
    }

}
