/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.collections.CurrentTransaction;
import com.sleepycat.collections.MapEntryParameter;
import com.sleepycat.collections.StoredCollection;
import com.sleepycat.collections.StoredContainer;
import com.sleepycat.collections.StoredCollections;
import com.sleepycat.collections.PrimaryKeyAssigner;
import com.sleepycat.collections.StoredEntrySet;
import com.sleepycat.collections.StoredIterator;
import com.sleepycat.collections.StoredSortedEntrySet;
import com.sleepycat.collections.StoredKeySet;
import com.sleepycat.collections.StoredList;
import com.sleepycat.collections.StoredMap;
import com.sleepycat.collections.StoredSortedKeySet;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.collections.StoredSortedValueSet;
import com.sleepycat.collections.StoredValueSet;
import com.sleepycat.collections.TransactionRunner;
import com.sleepycat.collections.TransactionWorker;
import com.sleepycat.collections.TupleSerialFactory;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CollectionsApiCoverageTest {

    @Test
    void storedMapsAndCollectionsExposeDatabaseBackedCollectionSemantics(@TempDir java.nio.file.Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        Database mapDatabase = null;
        Database listDatabase = null;
        Database duplicateDatabase = null;
        try {
            mapDatabase = openDatabase(environment, "map", false);
            StoredMap map = new StoredMap(mapDatabase, new IntegerBinding(), new StringBinding(), true);
            map.put(1, "one");
            map.put(2, "two");
            assertThat(map.get(1)).isEqualTo("one");
            assertThat(map.containsKey(2)).isTrue();
            assertThat(map.containsValue("two")).isTrue();
            map.putAll(Map.of(3, "three"));
            assertThat(map.size()).isEqualTo(3);
            assertThat(map.entrySet()).isNotEmpty();
            assertThat(map.keySet()).contains(1, 2, 3);
            assertThat(map.values()).contains("one", "two", "three");
            assertThat(map.toString()).contains("one");
            assertThat(map.equals(map)).isTrue();
            assertThat(map.hashCode()).isNotZero();
            assertThat(map.isEmpty()).isFalse();
            assertThat(map.getCursorConfig()).isNotNull();
            assertThat(map.isWriteAllowed()).isTrue();
            assertThat(map.isTransactional()).isTrue();
            assertThat(map.areDuplicatesAllowed()).isFalse();
            assertThat(map.isOrdered()).isTrue();
            map.clear();
            assertThat(map.isEmpty()).isTrue();
            map.put(1, "one");
            map.put(2, "two");
            assertThat(StoredCollections.configuredMap(map, CursorConfig.READ_UNCOMMITTED)).isNotNull();
            assertThat(StoredCollections.dirtyReadMap(map)).isNotNull();
            assertThat(StoredCollections.configuredCollection(map.values(), null)).isNotNull();
            assertThat(StoredCollections.dirtyReadCollection(map.values())).isNotNull();
            java.util.Iterator valuesIterator = StoredCollections.iterator(map.values().iterator());
            assertThat(valuesIterator.hasNext()).isTrue();
            StoredIterator.close(valuesIterator);
            assertThat(StoredCollections.configuredSet(map.keySet(), null)).isNotNull();
            assertThat(StoredCollections.dirtyReadSet(map.keySet())).isNotNull();

            Collection values = map.values();
            StoredCollection storedValues = (StoredCollection) values;
            assertThat(values.containsAll(Arrays.asList("one", "two"))).isTrue();
            assertThat(values.toArray()).hasSize(2);
            assertThat(values.toArray(new String[0])).hasSize(2);
            assertThat(storedValues.toList()).hasSize(2);
            assertThat(values.toString()).contains("one");
            assertThat(values.size()).isEqualTo(2);
            assertThat(storedValues.getIteratorBlockSize()).isPositive();
            storedValues.setIteratorBlockSize(2);
            assertThat(storedValues.getIteratorBlockSize()).isEqualTo(2);
            assertThat(values.equals(values)).isTrue();
            assertThat(values.hashCode()).isNotZero();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> values.addAll(List.of("four")))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(values.removeAll(List.of("missing"))).isFalse();
            assertThat(values.retainAll(List.of("one", "two", "three"))).isFalse();
            StoredIterator iterator = ((StoredCollection) values).storedIterator();
            assertThat(iterator.hasNext()).isTrue();
            assertThat(iterator.next()).isNotNull();
            iterator.close();
            StoredIterator readModifyIterator = ((StoredCollection) values).storedIterator(true);
            assertThat(readModifyIterator.isReadModifyWrite()).isIn(true, false);
            readModifyIterator.setReadModifyWrite(false);
            readModifyIterator.close();

            SortedMap sorted = new StoredSortedMap(mapDatabase, new IntegerBinding(),
                    new StringBinding(), true);
            assertThat(sorted.headMap(3).keySet()).contains(1, 2);
            assertThat(StoredCollections.configuredSortedMap(sorted, null)).isNotNull();
            assertThat(StoredCollections.dirtyReadSortedMap(sorted)).isNotNull();
            assertThat(StoredCollections.configuredSortedSet((java.util.SortedSet) sorted.keySet(), null)).isNotNull();
            assertThat(StoredCollections.dirtyReadSortedSet((java.util.SortedSet) sorted.keySet())).isNotNull();

            duplicateDatabase = openDatabase(environment, "duplicates", true);
            StoredMap duplicateMap = new StoredMap(duplicateDatabase, new IntegerBinding(),
                    new StringBinding(), true);
            duplicateMap.put(1, "one");
            duplicateMap.put(1, "uno");
            StoredValueSet entityValues = new StoredValueSet(duplicateDatabase,
                    new EntityStringBinding(), true);
            assertThat(entityValues).isNotNull();
            Collection duplicateValues = duplicateMap.duplicates(1);
            assertThat(duplicateValues.add("dos")).isTrue();
            assertThat(duplicateValues).contains("one", "uno", "dos");
            assertThat(duplicateMap.remove(1)).isNotNull();
            assertThat(duplicateMap.containsKey(1)).isFalse();
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> duplicateMap.duplicatesMap(1, new StringBinding()))
                    .isInstanceOf(UnsupportedOperationException.class);

            final Database listForList = openDatabase(environment, "list", true);
            listDatabase = listForList;
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> new StoredList(listForList, new StringBinding(), true))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            if (listDatabase != null) {
                listDatabase.close();
            }
            if (duplicateDatabase != null) {
                duplicateDatabase.close();
            }
            if (mapDatabase != null) {
                try {
                    mapDatabase.close();
                } catch (com.sleepycat.je.DatabaseException closeFailure) {
                    assertThat(closeFailure).isNotNull();
                }
            }
            environment.close();
        }
    }

    @Test
    void collectionViewsAndIteratorsSupportRealMutations(@TempDir java.nio.file.Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        Database database = null;
        try {
            database = openDatabase(environment, "views", false);
            StoredMap map = new StoredMap(database, new IntegerBinding(), new StringBinding(), true);
            map.put(1, "one");
            map.put(2, "two");
            map.put(3, "three");

            StoredEntrySet entries = (StoredEntrySet) map.entrySet();
            Map.Entry firstEntry = (Map.Entry) entries.iterator().next();
            assertThat(firstEntry.setValue("updated")).isEqualTo("one");
            assertThat(map.get(1)).isEqualTo("updated");
            assertThat(entries.contains(new MapEntryParameter(1, "updated"))).isTrue();
            assertThat(entries.remove(new MapEntryParameter(2, "two"))).isTrue();
            assertThat(entries.add(new MapEntryParameter(2, "two"))).isTrue();
            assertThat(entries.contains(new MapEntryParameter(2, "two"))).isTrue();

            StoredKeySet keys = new StoredKeySet(database, new IntegerBinding(), true);
            assertThat(keys.contains(1)).isTrue();
            assertThat(keys.add(4)).isTrue();
            assertThat(keys.remove(4)).isTrue();
            assertThat(keys.areDuplicatesOrdered()).isFalse();
            assertThat(keys.areKeysRenumbered()).isFalse();
            assertThat(keys.isDirtyRead()).isFalse();
            assertThat(keys.isDirtyReadAllowed()).isTrue();

            StoredValueSet values = new StoredValueSet(database, new StringBinding(), true);
            assertThat(values.remove("three")).isTrue();

            assertThat(map.containsValue("three")).isFalse();
            map.put(3, "three");
            StoredIterator iterator = values.storedIterator(true);
            assertThat(iterator.getCollection()).isSameAs(values);
            assertThatThrownBy(() -> iterator.moveToIndex(0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(iterator::nextIndex).isInstanceOf(UnsupportedOperationException.class);
            Object currentValue = iterator.next();
            assertThat(currentValue).isNotNull();
            assertThat(iterator.count()).isEqualTo(1);
            assertThat(iterator.hasPrevious()).isTrue();
            assertThatThrownBy(iterator::previousIndex).isInstanceOf(UnsupportedOperationException.class);
            assertThat(iterator.isCurrentData(currentValue)).isTrue();
            java.util.ListIterator duplicateIterator = iterator.dup();
            assertThat(duplicateIterator).isNotNull();
            StoredIterator.close(duplicateIterator);
            assertThatThrownBy(() -> iterator.set("changed"))
                    .isInstanceOf(Exception.class);
            assertThat(iterator.previous()).isEqualTo("changed");
            assertThatThrownBy(iterator::remove).isInstanceOf(Exception.class);
            iterator.close();

            StoredSortedKeySet sortedKeys = new StoredSortedKeySet(database,
                    new IntegerBinding(), true);
            assertThat(sortedKeys.first()).isEqualTo(1);
            assertThat(sortedKeys.last()).isEqualTo(3);
            assertThat(sortedKeys.headSet(3)).contains(1, 2);
            assertThat(sortedKeys.headSet(3, true)).contains(3);
            assertThat(sortedKeys.tailSet(2)).contains(2, 3);
            assertThat(sortedKeys.tailSet(2, false)).contains(3);
            assertThat(sortedKeys.subSet(1, 3)).containsExactly(1, 2);
            assertThat(sortedKeys.subSet(1, true, 3, true)).contains(1, 2, 3);

            StoredSortedValueSet sortedValues = new StoredSortedValueSet(database,
                    new EntityStringBinding(), true);
            assertThat(sortedValues.comparator()).isNull();
            assertThat(sortedValues.first()).isNotNull();
            assertThat(sortedValues.last()).isNotNull();
            assertThat(sortedValues.headSet("three")).isNotNull();
            assertThat(sortedValues.headSet("three", true)).isNotNull();
            assertThat(sortedValues.tailSet("ONE")).isNotNull();
            assertThat(sortedValues.tailSet("ONE", true)).isNotNull();
            assertThat(sortedValues.subSet("ONE", "three")).isNotNull();
            assertThat(sortedValues.subSet("ONE", true, "three", true)).isNotNull();

            StoredSortedMap sortedMap = new StoredSortedMap(database, new IntegerBinding(),
                    new StringBinding(), true);
            assertThat(sortedMap.comparator()).isNull();
            StoredSortedEntrySet sortedEntries =
                    (StoredSortedEntrySet) sortedMap.entrySet();
            assertThat(((Map.Entry) sortedEntries.first()).getKey()).isEqualTo(1);
            assertThat(((Map.Entry) sortedEntries.last()).getKey()).isEqualTo(3);
            Map.Entry upperEntry = new MapEntryParameter(3, "three");
            Map.Entry lowerEntry = new MapEntryParameter(1, "one");
            assertThat(sortedEntries.headSet(upperEntry)).isNotEmpty();
            assertThat(sortedEntries.headSet(upperEntry, true)).isNotEmpty();
            assertThat(sortedEntries.tailSet(lowerEntry)).isNotEmpty();
            assertThat(sortedEntries.tailSet(lowerEntry, true)).isNotEmpty();
            assertThat(sortedEntries.subSet(lowerEntry, upperEntry)).isNotEmpty();
            assertThat(sortedEntries.subSet(lowerEntry, true, upperEntry, true))
                    .isNotEmpty();
            PrimaryKeyAssigner sortedAssigner = keyData -> IntegerBinding.intToEntry(11, keyData);
            StoredSortedMap assignedSortedMap = new StoredSortedMap(database,
                    new IntegerBinding(), new StringBinding(), sortedAssigner);
            assertThat(assignedSortedMap).isNotNull();
            Database entitySortedDatabase = openDatabase(environment, "entity-sorted", true);
            StoredSortedMap entitySortedMap = new StoredSortedMap(entitySortedDatabase,
                    new IntegerBinding(), new EntityStringBinding(), sortedAssigner);
            assertThat(entitySortedMap).isNotNull();
            entitySortedDatabase.close();
            assertThat(sortedMap.lastKey()).isEqualTo(3);
            assertThat(sortedMap.headMap(3, true)).containsKey(3);
            assertThat(sortedMap.tailMap(2)).containsKeys(2, 3);
            assertThat(sortedMap.tailMap(2, false)).containsKey(3);
            assertThat(sortedMap.subMap(2, 3)).containsKey(2);

            PrimaryKeyAssigner assigner = keyData -> IntegerBinding.intToEntry(9, keyData);
            final Database viewDatabase = database;
            StoredMap appendMap = new StoredMap(database, new IntegerBinding(), new StringBinding(),
                    assigner);
            assertThat(appendMap.append("nine")).isEqualTo(9);
            assertThat(appendMap.remove(9)).isEqualTo("nine");
            assertThatThrownBy(() -> new StoredList(viewDatabase, new StringBinding(), true))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> new StoredList(viewDatabase, (com.sleepycat.bind.EntityBinding) null,
                    true)).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> new StoredList(viewDatabase, new StringBinding(), assigner))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> new StoredList(viewDatabase, (com.sleepycat.bind.EntityBinding) null,
                    assigner)).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> new StoredSortedValueSet(viewDatabase,
                    (com.sleepycat.bind.EntityBinding) null, true))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> new StoredMap(viewDatabase, new IntegerBinding(),
                    (com.sleepycat.bind.EntityBinding) null, assigner))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> new StoredSortedMap(viewDatabase, new IntegerBinding(),
                    (com.sleepycat.bind.EntityBinding) null, assigner))
                    .isInstanceOf(Exception.class);
        } finally {
            if (database != null) {
                try {
                    database.close();
                } catch (com.sleepycat.je.DatabaseException closeFailure) {
                    assertThat(closeFailure).isNotNull();
                }
            }
            environment.close();
        }
    }

    @Test
    void deprecatedIteratorJoinAndListAdaptersFollowCollectionContracts(@TempDir java.nio.file.Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        Database database = null;
        try {
            database = openDatabase(environment, "collection-contracts", false);
            StoredMap map = new StoredMap(database, new IntegerBinding(), new StringBinding(), true);
            map.put(1, "one");
            map.put(2, "two");
            StoredCollection values = (StoredCollection) map.values();
            StoredIterator readOnly = values.iterator(false);
            assertThat(readOnly.hasNext()).isTrue();
            readOnly.close();
            StoredIterator readWrite = values.iterator(true);
            assertThat(readWrite.next()).isNotNull();
            assertThatThrownBy(() -> readWrite.add("three"))
                    .isInstanceOf(UnsupportedOperationException.class);
            readWrite.close();
            assertThatThrownBy(() -> values.join(
                    new StoredContainer[] {(StoredContainer) map.keySet()},
                    new Object[] {1}, null)).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> StoredCollections.configuredList(
                    java.util.Collections.emptyList(), CursorConfig.DEFAULT))
                    .isInstanceOf(ClassCastException.class);
            assertThatThrownBy(() -> StoredCollections.dirtyReadList(
                    java.util.Collections.emptyList()))
                    .isInstanceOf(ClassCastException.class);
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    @Test
    void storedListsImplementListSemanticsAndUseAllPublicConstructionModes(
            @TempDir java.nio.file.Path home) throws Exception {
        Environment environment = openEnvironment(home);
        Database listDatabase = null;
        Database entityDatabase = null;
        Database assignerDatabase = null;
        try {
            listDatabase = openDatabase(environment, "list-values", false);
            final Database valuesDatabase = listDatabase;
            assertThatThrownBy(() -> new StoredList(valuesDatabase, new StringBinding(), true))
                    .isInstanceOf(IllegalArgumentException.class);
            entityDatabase = openDatabase(environment, "list-entities", false);
            final Database entitiesDatabase = entityDatabase;
            assertThatThrownBy(() -> new StoredList(entitiesDatabase,
                    new EntityStringBinding(), true)).isInstanceOf(IllegalArgumentException.class);
            assignerDatabase = openDatabase(environment, "list-assigned", false);
            final Database assignedDatabase = assignerDatabase;
            PrimaryKeyAssigner assigner = keyData -> IntegerBinding.intToEntry(
                    keyData.getSize() + 1, keyData);
            assertThatThrownBy(() -> new StoredList(assignedDatabase, new StringBinding(), assigner))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new StoredList(assignedDatabase,
                    new EntityStringBinding(), assigner)).isInstanceOf(IllegalArgumentException.class);
        } finally {
            if (listDatabase != null) {
                listDatabase.close();
            }
            if (entityDatabase != null) {
                entityDatabase.close();
            }
            if (assignerDatabase != null) {
                assignerDatabase.close();
            }
            environment.close();
        }
    }

    @Test
    void storedListRejectsUnsupportedRecordNumberOperationsClearly(@TempDir java.nio.file.Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        Database database = null;
        try {
            database = openDatabase(environment, "list-api", false);
            final Database listDatabase = database;
            PrimaryKeyAssigner assigner = keyData -> IntegerBinding.intToEntry(1, keyData);
            assertThatThrownBy(() -> new StoredList(listDatabase, new StringBinding(), true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("RecordNumberBinding");
            assertThatThrownBy(() -> new StoredList(listDatabase, new EntityStringBinding(), true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("RecordNumberBinding");
            assertThatThrownBy(() -> new StoredList(listDatabase, new StringBinding(), assigner))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("RecordNumberBinding");
            assertThatThrownBy(() -> new StoredList(listDatabase, new EntityStringBinding(), assigner))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("RecordNumberBinding");
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    @Test
    void transactionRunnerConfigurationCanBeChangedBeforeWork(@TempDir java.nio.file.Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        try {
            TransactionRunner runner = new TransactionRunner(environment, 2,
                    new com.sleepycat.je.TransactionConfig());
            assertThat(runner.getMaxRetries()).isEqualTo(2);
            runner.setMaxRetries(4);
            assertThatThrownBy(() -> runner.setAllowNestedTransactions(true))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(runner.getAllowNestedTransactions()).isFalse();
            com.sleepycat.je.TransactionConfig config = new com.sleepycat.je.TransactionConfig();
            config.setNoWait(true);
            runner.setTransactionConfig(config);
            assertThat(runner.getTransactionConfig()).isSameAs(config);
        } finally {
            environment.close();
        }
    }

    @Test
    void transactionHelpersAndMapEntriesProvideUsefulLifecycleOperations(@TempDir java.nio.file.Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        Database database = null;
        Database catalogDatabase = null;
        StoredClassCatalog catalog = null;
        try {
            database = openDatabase(environment, "factory", false);
            catalogDatabase = openDatabase(environment, "catalog", false);
            catalog = new StoredClassCatalog(catalogDatabase);
            TupleSerialFactory factory = new TupleSerialFactory(catalog);
            assertThat(factory.getCatalog()).isSameAs(catalog);
            StoredMap map = factory.newMap(database, String.class, String.class, true);
            StoredSortedMap sortedMap = factory.newSortedMap(database, String.class, String.class, false);
            assertThat(map).isNotNull();
            assertThat(sortedMap).isNotNull();
            MapEntryParameter entry = new MapEntryParameter("key", "value");
            assertThat(entry.getKey()).isEqualTo("key");
            assertThat(entry.getValue()).isEqualTo("value");
            assertThat(entry.toString()).contains("key");
            assertThat(entry.equals(new MapEntryParameter("key", "value"))).isTrue();
            assertThat(entry.hashCode()).isEqualTo(new MapEntryParameter("key", "value").hashCode());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> entry.setValue("new"))
                    .isInstanceOf(UnsupportedOperationException.class);

            TransactionRunner runner = new TransactionRunner(environment);
            runner.run(new TransactionWorker() {
                @Override
                public void doWork() {
                    assertThat(map).isNotNull();
                }
            });
            CurrentTransaction current = CurrentTransaction.getInstance(environment);
            assertThat(current.getEnvironment()).isSameAs(environment);
            assertThat(current.getTransaction()).isNull();
            current.beginTransaction(null);
            assertThat(current.getTransaction()).isNotNull();
            current.abortTransaction();
            current.beginTransaction(null);
            assertThat(current.commitTransaction()).isNull();
            assertThat(current.getTransaction()).isNull();
            assertThat(factory.getKeyCreator(String.class, "missing")).isNotNull();
        } finally {
            if (catalog != null) {
                catalog.close();
                catalogDatabase = null;
            }
            if (database != null) {
                database.close();
            }
            if (catalogDatabase != null) {
                catalogDatabase.close();
            }
            environment.close();
        }
    }

    @Test
    void duplicateValueSetSupportsInsertionAndValueSearch(@TempDir java.nio.file.Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        Database database = null;
        try {
            database = openDatabase(environment, "value-set", true);
            StoredValueSet values = new StoredValueSet(database, new EntityStringBinding(), true);
            assertThat(values.add("alpha")).isTrue();
            assertThat(values.add("beta")).isTrue();
            assertThat(values.add("alpha")).isFalse();
            assertThat(values.contains("beta")).isTrue();
            assertThat(values.remove("alpha")).isTrue();
        } finally {
            if (database != null) {
                database.close();
            }
            environment.close();
        }
    }

    @Test
    void duplicateIteratorsInsertBeforeAndAfterTheirCurrentPosition(
            @TempDir java.nio.file.Path home) throws Exception {
        Environment environment = openEnvironment(home);
        Database database = null;
        try {
            database = openDatabase(environment, "iterator-duplicates", true);
            StoredMap map = new StoredMap(database, new IntegerBinding(), new StringBinding(), true);
            map.put(1, "one");
            map.put(1, "three");

            CurrentTransaction current = CurrentTransaction.getInstance(environment);
            current.beginTransaction(null);
            StoredCollection duplicates = (StoredCollection) map.duplicates(1);
            StoredIterator after = duplicates.storedIterator(true);
            assertThat(after.next()).isIn("one", "three");
            after.add("two");
            after.close();

            StoredIterator before = duplicates.storedIterator(true);
            before.add("zero");
            before.close();
            assertThat(duplicates).contains("zero", "one", "two", "three");
            current.commitTransaction();
        } finally {
            if (database != null) {
                try {
                    database.close();
                } catch (com.sleepycat.je.DatabaseException closeFailure) {
                    assertThat(closeFailure).isNotNull();
                }
            }
            environment.close();
        }
    }

    @Test
    void mapKeyBindingsEncodeAndRemoveAllSupportedScalarTypes(@TempDir java.nio.file.Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        try {
            Object[][] cases = {
                {new com.sleepycat.bind.tuple.BigIntegerBinding(), java.math.BigInteger.TEN},
                {new com.sleepycat.bind.tuple.BooleanBinding(), true},
                {new com.sleepycat.bind.tuple.ByteBinding(), (byte) 3},
                {new com.sleepycat.bind.tuple.CharacterBinding(), 'c'},
                {new com.sleepycat.bind.tuple.DoubleBinding(), 2.5d},
                {new com.sleepycat.bind.tuple.FloatBinding(), 1.5f},
                {new com.sleepycat.bind.tuple.LongBinding(), 7L},
                {new com.sleepycat.bind.tuple.ShortBinding(), (short) 4},
                {new com.sleepycat.bind.tuple.SortedDoubleBinding(), 3.5d},
                {new com.sleepycat.bind.tuple.SortedFloatBinding(), 4.5f}
            };
            for (int i = 0; i < cases.length; i++) {
                Database database = openDatabase(environment, "scalar-" + i, false);
                try {
                    StoredMap map = new StoredMap(database,
                            (com.sleepycat.bind.EntryBinding) cases[i][0],
                            new StringBinding(), true);
                    map.put(cases[i][1], "value");
                    assertThat(map.remove(cases[i][1])).isEqualTo("value");
                } finally {
                    database.close();
                }
            }
        } finally {
            environment.close();
        }
    }

    private static final class EntityStringBinding implements com.sleepycat.bind.EntityBinding {
        @Override
        public Object entryToObject(DatabaseEntry key, DatabaseEntry data) {
            return new String(data.getData(), data.getOffset(), data.getSize(),
                    java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public void objectToKey(Object object, DatabaseEntry key) {
            key.setData(new byte[0]);
        }

        @Override
        public void objectToData(Object object, DatabaseEntry data) {
            data.setData(String.valueOf(object).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static Environment openEnvironment(java.nio.file.Path home) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        return new Environment(home.toFile(), config);
    }

    private static Database openDatabase(Environment environment, String name, boolean duplicates)
            throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        config.setSortedDuplicates(duplicates);
        return environment.openDatabase(null, name, config);
    }
}
