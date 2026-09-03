/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.persist.EntityCursor;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.SecondaryIndex;
import com.sleepycat.persist.StoreConfig;
import com.sleepycat.persist.evolve.Converter;
import com.sleepycat.persist.evolve.Conversion;
import com.sleepycat.persist.evolve.Deleter;
import com.sleepycat.persist.evolve.EntityConverter;
import com.sleepycat.persist.evolve.EvolveConfig;
import com.sleepycat.persist.evolve.EvolveEvent;
import com.sleepycat.persist.evolve.EvolveInternal;
import com.sleepycat.persist.evolve.EvolveStats;
import com.sleepycat.persist.evolve.Mutations;
import com.sleepycat.persist.evolve.Renamer;
import com.sleepycat.persist.model.DeleteAction;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.Persistent;
import com.sleepycat.persist.model.SecondaryKey;
import com.sleepycat.persist.raw.RawObject;
import com.sleepycat.persist.raw.RawStore;
import com.sleepycat.persist.raw.RawType;
import com.sleepycat.persist.impl.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.sleepycat.persist.model.Relationship.MANY_TO_ONE;
import static org.assertj.core.api.Assertions.assertThat;

public class PersistenceApiCoverageTest {

    @Test
    void primaryAndSecondaryIndexesSupportRangeMapAndCursorOperations(@TempDir Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        EntityStore store = null;
        try {
            StoreConfig storeConfig = new StoreConfig();
            storeConfig.setAllowCreate(true);
            store = new EntityStore(environment, "people", storeConfig);
            PrimaryIndex<Integer, Person> primary = store.getPrimaryIndex(Integer.class, Person.class);
            SecondaryIndex<String, Integer, Person> secondary =
                    store.getSecondaryIndex(primary, String.class, "group");
            SecondaryIndex<String, Integer, Employee> employeeIndex =
                    store.getSubclassIndex(primary, Employee.class, String.class, "department");
            assertThat(employeeIndex).isNotNull();
            primary.put(new Person(1, "red", "Ada"));
            primary.put(new Person(2, "blue", "Bob"));
            PrimaryIndex<Integer, ScalarRecord> scalarIndex =
                    store.getPrimaryIndex(Integer.class, ScalarRecord.class);
            ScalarRecord scalar = new ScalarRecord();
            scalar.id = 7;
            scalar.bigInteger = java.math.BigInteger.TEN;
            scalar.booleanValue = true;
            scalar.byteValue = 3;
            scalar.character = 'x';
            scalar.date = new java.util.Date(1000L);
            scalar.doubleValue = 2.5d;
            scalar.floatValue = 1.5f;
            scalar.shortValue = 4;
            scalarIndex.put(scalar);
            ScalarRecord restoredScalar = scalarIndex.get(7);
            assertThat(restoredScalar.booleanValue).isTrue();
            assertThat(restoredScalar.bigInteger).isEqualTo(java.math.BigInteger.TEN);
            assertThat(restoredScalar.date).isEqualTo(new java.util.Date(1000L));
            assertThat(primary.putNoOverwrite(new Person(1, "other", "ignored"))).isFalse();
            assertThat(primary.count()).isEqualTo(2);
            assertThat(primary.getKeyClass()).isEqualTo(Integer.class);
            assertThat(primary.get(1).name).isEqualTo("Ada");
            assertThat(primary.contains(2)).isTrue();
            assertThat(primary.contains(null, 2, null)).isTrue();
            assertThat(store.getModel().getAllRawTypeVersions(Person.class.getName()))
                    .isNotEmpty();
            RawType personType = store.getModel().getRawType(Person.class.getName());
            assertThat(store.getModel().getRawTypeVersion(Person.class.getName(),
                    personType.getVersion())).isSameAs(personType);
            Object rawPerson = store.getModel().convertRawObject(new RawObject(personType,
                    Map.of("id", 3, "group", "green", "name", "Cara"), null));
            assertThat(rawPerson).isInstanceOf(Person.class);
            assertThat(((Person) rawPerson).name).isEqualTo("Cara");
            assertThat(primary.map()).containsKeys(1, 2);
            assertThat(primary.sortedMap().firstKey()).isEqualTo(1);

            EntityCursor<Person> entities = primary.entities();
            assertThat(entities.first().name).isEqualTo("Ada");
            entities.close();
            entities = primary.entities(null, null);
            assertThat(entities.next()).isNotNull();
            entities.close();
            entities = primary.entities(1, true, 2, true);
            assertThat(entities.first()).isNotNull();
            assertThat(entities.count()).isPositive();
            entities.close();
            entities = primary.entities(null, 1, true, 2, true, null);
            assertThat(entities.first()).isNotNull();
            entities.close();
            EntityCursor<Integer> keys = primary.keys();
            assertThat(keys.first()).isEqualTo(1);
            keys.close();
            keys = primary.keys(null, null);
            assertThat(keys.first()).isEqualTo(1);
            keys.close();
            keys = primary.keys(1, true, 2, true);
            assertThat(keys.next()).isEqualTo(1);
            keys.close();
            keys = primary.keys(null, 1, true, 2, true, null);
            assertThat(keys.next()).isEqualTo(1);
            keys.close();

            assertThat(secondary.getKeyClass()).isEqualTo(String.class);
            assertThat(secondary.contains("red")).isTrue();
            assertThat(secondary.contains(null, "red", null)).isTrue();
            assertThat(secondary.get("blue").name).isEqualTo("Bob");
            assertThat(secondary.count()).isEqualTo(2);
            EntityCursor<Person> secondaryRange = secondary.entities("blue", true,
                    "red", true);
            assertThat(secondaryRange.first()).isNotNull();
            secondaryRange.close();
            secondaryRange = secondary.entities(null, null);
            assertThat(secondaryRange.next()).isNotNull();
            secondaryRange.close();
            secondaryRange = secondary.entities(null, "blue", true, "red", true, null);
            assertThat(secondaryRange.first()).isNotNull();
            secondaryRange.close();
            EntityCursor<String> secondaryKeyRange = secondary.keys("blue", true,
                    "red", true);
            assertThat(secondaryKeyRange.first()).isNotNull();
            secondaryKeyRange.close();
            secondaryKeyRange = secondary.keys(null, null);
            assertThat(secondaryKeyRange.first()).isNotNull();
            secondaryKeyRange.close();
            secondaryKeyRange = secondary.keys(null, "blue", true, "red", true, null);
            assertThat(secondaryKeyRange.first()).isNotNull();
            secondaryKeyRange.close();
            assertThat(secondary.map()).containsKey("red");
            assertThat(secondary.keysIndex().contains("red")).isTrue();
            assertThat(secondary.subIndex("red").count()).isEqualTo(1);
            EntityCursor<String> secondaryKeys = secondary.keysIndex().keys();
            assertThat(secondaryKeys.first()).isIn("red", "blue");
            secondaryKeys.close();
            EntityCursor<Integer> secondaryEntities = secondary.keysIndex().entities();
            assertThat(secondaryEntities.first()).isIn(1, 2);
            secondaryEntities.close();
            assertThat(secondary.keysIndex().map()).containsKey("red");
            assertThat(secondary.keysIndex().sortedMap()).containsKey("red");
            com.sleepycat.persist.EntityJoin<Integer, Person> join =
                    new com.sleepycat.persist.EntityJoin<>(primary);
            join.addCondition(secondary, "red");
            com.sleepycat.persist.ForwardCursor<Person> joinedEntities = join.entities();
            assertThat(joinedEntities.next()).isNotNull();
            joinedEntities.close();
            com.sleepycat.persist.ForwardCursor<Integer> joinedKeys = join.keys();
            assertThat(joinedKeys.next()).isEqualTo(1);
            joinedKeys.close();
            assertThat(secondary.delete(null, "blue")).isTrue();
            assertThat(secondary.delete("missing")).isFalse();
            assertThat(primary.delete(null, 2)).isFalse();
            assertThat(primary.delete(2)).isFalse();
            assertThat(primary.count()).isEqualTo(1);
            assertThat(primary.delete(99)).isFalse();
        } finally {
            if (store != null) {
                try {
                    store.closeClass(Person.class);
                } catch (com.sleepycat.je.DatabaseException closeFailure) {
                    assertThat(closeFailure).isNotNull();
                }
                store.close();
            }
            environment.close();
        }
    }

    @Test
    void collectionAndForeignSecondaryKeysAreMaintainedByTheStore(@TempDir Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        EntityStore store = null;
        try {
            StoreConfig config = new StoreConfig();
            config.setAllowCreate(true);
            config.setTransactional(true);
            store = new EntityStore(environment, "relationships", config);

            PrimaryIndex<Integer, TaggedRecord> tagged =
                    store.getPrimaryIndex(Integer.class, TaggedRecord.class);
            SecondaryIndex<String, Integer, TaggedRecord> tags =
                    store.getSecondaryIndex(tagged, String.class, "tags");
            tagged.put(new TaggedRecord(1, new HashSet<>(Set.of("one", "two"))));
            assertThat(tags.count()).isEqualTo(2);
            EntityCursor<TaggedRecord> taggedEntities =
                    tags.entities("one", true, "two", true);
            try {
                assertThat(taggedEntities.first()).isNotNull();
                assertThat(taggedEntities.count()).isEqualTo(1);
            } finally {
                taggedEntities.close();
            }

            PrimaryIndex<String, ForeignParent> parents =
                    store.getPrimaryIndex(String.class, ForeignParent.class);
            PrimaryIndex<Integer, ForeignChild> children =
                    store.getPrimaryIndex(Integer.class, ForeignChild.class);
            parents.put(new ForeignParent("parent"));
            children.put(new ForeignChild(1, "parent"));
            assertThat(children.get(1).parent).isEqualTo("parent");
            assertThat(parents.delete("parent")).isTrue();
            assertThat(children.get(1).parent).isNull();

            parents.put(new ForeignParent("collection-parent"));
            PrimaryIndex<Integer, CollectionForeignRecord> collectionIndex =
                    store.getPrimaryIndex(Integer.class, CollectionForeignRecord.class);
            CollectionForeignRecord collectionRecord = new CollectionForeignRecord();
            collectionRecord.id = 2;
            collectionRecord.arrayList = new ArrayList<>(Set.of("collection-parent"));
            collectionRecord.hashSet = new HashSet<>(Set.of("collection-parent"));
            collectionRecord.linkedList = new LinkedList<>(Set.of("collection-parent"));
            collectionRecord.treeSet = new TreeSet<>(Set.of("collection-parent"));
            collectionIndex.put(collectionRecord);
            assertThat(parents.delete("collection-parent")).isTrue();
            CollectionForeignRecord cleared = collectionIndex.get(2);
            assertThat(cleared.arrayList).isEmpty();
            assertThat(cleared.hashSet).isEmpty();
            assertThat(cleared.linkedList).isEmpty();
            assertThat(cleared.treeSet).isEmpty();
        } finally {
            if (store != null) {
                store.close();
            }
            environment.close();
        }
    }

    @Test
    void sequenceKeysAndRichValuesRoundTripThroughPublicIndexes(@TempDir Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        EntityStore store = null;
        try {
            StoreConfig config = new StoreConfig();
            config.setAllowCreate(true);
            store = new EntityStore(environment, "rich", config);

            PrimaryIndex<Integer, RichRecord> richIndex =
                    store.getPrimaryIndex(Integer.class, RichRecord.class);
            RichRecord record = new RichRecord();
            record.id = 1;
            record.bigInteger = java.math.BigInteger.valueOf(123456L);
            record.booleanValue = true;
            record.byteValue = 7;
            record.character = 'z';
            record.date = new Date(1234L);
            record.doubleValue = 2.5d;
            record.floatValue = 1.5f;
            record.longValue = 9L;
            record.shortValue = 4;
            record.enumValue = RichEnum.SECOND;
            record.bigIntegers = new java.math.BigInteger[] {java.math.BigInteger.ONE};
            record.booleans = new Boolean[] {true, false};
            record.bytes = new byte[] {1, 2};
            record.boxedBytes = new Byte[] {3, 4};
            record.characters = new char[] {'a', 'b'};
            record.boxedCharacters = new Character[] {'c', 'd'};
            record.dates = new Date[] {new Date(5L)};
            record.doubles = new double[] {3.5d};
            record.boxedDoubles = new Double[] {6.5d};
            record.floats = new float[] {4.5f};
            record.boxedFloats = new Float[] {7.5f};
            record.integers = new int[] {6, 7};
            record.longs = new long[] {8L};
            record.shorts = new short[] {10};
            record.strings = new String[] {"first", "second"};
            record.arrayList = new ArrayList<>(Set.of("array"));
            record.hashSet = new HashSet<>(Set.of("hash"));
            record.linkedList = new LinkedList<>(Set.of("linked"));
            record.treeSet = new TreeSet<>(Set.of("tree"));
            record.hashMap = new HashMap<>(Map.of("hash", 1));
            record.treeMap = new TreeMap<>(Map.of("tree", 2));
            richIndex.put(record);

            RichRecord restored = richIndex.get(1);
            assertThat(restored.enumValue).isEqualTo(RichEnum.SECOND);
            assertThat(restored.bigIntegers).containsExactly(java.math.BigInteger.ONE);
            assertThat(restored.booleans).containsExactly(true, false);
            assertThat(restored.bytes).containsExactly((byte) 1, (byte) 2);
            assertThat(restored.boxedBytes).containsExactly((byte) 3, (byte) 4);
            assertThat(restored.characters).containsExactly('a', 'b');
            assertThat(restored.boxedCharacters).containsExactly('c', 'd');
            assertThat(restored.dates[0]).isEqualTo(new Date(5L));
            assertThat(restored.doubles).containsExactly(3.5d);
            assertThat(restored.boxedDoubles).containsExactly(6.5d);
            assertThat(restored.floats).containsExactly(4.5f);
            assertThat(restored.boxedFloats).containsExactly(7.5f);
            assertThat(restored.integers).containsExactly(6, 7);
            assertThat(restored.longs).containsExactly(8L);
            assertThat(restored.shorts).containsExactly((short) 10);
            assertThat(restored.strings).containsExactly("first", "second");
            assertThat(restored.arrayList).containsExactly("array");
            assertThat(restored.hashSet).containsExactly("hash");
            assertThat(restored.linkedList).containsExactly("linked");
            assertThat(restored.treeSet).containsExactly("tree");
            assertThat(restored.hashMap).containsEntry("hash", 1);
            assertThat(restored.treeMap).containsEntry("tree", 2);

            PrimaryIndex<Byte, ByteSequenceRecord> byteIndex =
                    store.getPrimaryIndex(Byte.class, ByteSequenceRecord.class);
            PrimaryIndex<Integer, IntegerSequenceRecord> integerIndex =
                    store.getPrimaryIndex(Integer.class, IntegerSequenceRecord.class);
            PrimaryIndex<Short, ShortSequenceRecord> shortIndex =
                    store.getPrimaryIndex(Short.class, ShortSequenceRecord.class);
            ByteSequenceRecord byteRecord = new ByteSequenceRecord();
            IntegerSequenceRecord integerRecord = new IntegerSequenceRecord();
            ShortSequenceRecord shortRecord = new ShortSequenceRecord();
            byteIndex.put(byteRecord);
            integerIndex.put(integerRecord);
            shortIndex.put(shortRecord);
            assertThat(byteRecord.id).isNotNull();
            assertThat(integerRecord.id).isNotZero();
            assertThat(shortRecord.id).isNotZero();
        } finally {
            if (store != null) {
                store.close();
            }
            environment.close();
        }
    }

    @Test
    void classRenamingEvolutionReadsExistingEntities(@TempDir Path home) throws Exception {
        Environment environment = openEnvironment(home);
        EntityStore initialStore = null;
        EntityStore evolvedStore = null;
        try {
            StoreConfig initialConfig = new StoreConfig();
            initialConfig.setAllowCreate(true);
            initialStore = new EntityStore(environment, "evolution", initialConfig);
            PrimaryIndex<Integer, OldRecord> oldIndex =
                    initialStore.getPrimaryIndex(Integer.class, OldRecord.class);
            OldRecord oldRecord = new OldRecord();
            oldRecord.id = 1;
            oldRecord.name = "before";
            oldRecord.oldNumber = 7;
            oldRecord.removed = "discarded";
            oldIndex.put(oldRecord);
            initialStore.close();
            initialStore = null;

            Mutations mutations = new Mutations();
            mutations.addRenamer(new Renamer(OldRecord.class.getName(), 0,
                    EvolvedRecord.class.getName()));
            mutations.addRenamer(new Renamer(OldRecord.class.getName(), 0,
                    "oldNumber", "longNumber"));
            mutations.addDeleter(new Deleter(OldRecord.class.getName(), 0, "removed"));
            StoreConfig evolvedConfig = new StoreConfig();
            evolvedConfig.setAllowCreate(true);
            evolvedConfig.setMutations(mutations);
            evolvedStore = new EntityStore(environment, "evolution", evolvedConfig);
            PrimaryIndex<Integer, EvolvedRecord> evolvedIndex =
                    evolvedStore.getPrimaryIndex(Integer.class, EvolvedRecord.class);
            EvolvedRecord evolved = evolvedIndex.get(1);
            assertThat(evolved).isNotNull();
            assertThat(evolved.name).isEqualTo("before");
            assertThat(evolved.longNumber).isEqualTo(7L);
            assertThat(evolved.added).isNull();
            assertThat(evolvedStore.evolve(new EvolveConfig()).getNRead()).isGreaterThanOrEqualTo(0);
        } finally {
            if (initialStore != null) {
                initialStore.close();
            }
            if (evolvedStore != null) {
                evolvedStore.close();
            }
            environment.close();
        }
    }

    @Test
    void secondaryKeyRenamingPreservesEntitiesAndIndexes(@TempDir Path home) throws Exception {
        Environment environment = openEnvironment(home);
        EntityStore initialStore = null;
        EntityStore evolvedStore = null;
        try {
            StoreConfig initialConfig = new StoreConfig();
            initialConfig.setAllowCreate(true);
            initialStore = new EntityStore(environment, "secondary-evolution", initialConfig);
            PrimaryIndex<Integer, OldSecondaryRecord> oldIndex =
                    initialStore.getPrimaryIndex(Integer.class, OldSecondaryRecord.class);
            oldIndex.put(new OldSecondaryRecord(1, "before", "old-group"));
            initialStore.close();
            initialStore = null;

            Mutations mutations = new Mutations();
            mutations.addRenamer(new Renamer(OldSecondaryRecord.class.getName(), 0,
                    EvolvedSecondaryRecord.class.getName()));
            mutations.addRenamer(new Renamer(OldSecondaryRecord.class.getName(), 0,
                    "oldGroup", "newGroup"));
            StoreConfig evolvedConfig = new StoreConfig();
            evolvedConfig.setAllowCreate(true);
            evolvedConfig.setMutations(mutations);
            evolvedStore = new EntityStore(environment, "secondary-evolution", evolvedConfig);
            PrimaryIndex<Integer, EvolvedSecondaryRecord> evolvedIndex =
                    evolvedStore.getPrimaryIndex(Integer.class, EvolvedSecondaryRecord.class);
            EvolvedSecondaryRecord record = evolvedIndex.get(1);
            assertThat(record).isNotNull();
            assertThat(record.newGroup).isEqualTo("old-group");
            SecondaryIndex<String, Integer, EvolvedSecondaryRecord> secondary =
                    evolvedStore.getSecondaryIndex(evolvedIndex, String.class, "newGroup");
            assertThat(secondary.get("old-group").id).isEqualTo(1);
            assertThat(evolvedStore.evolve(new EvolveConfig()).getNRead()).isGreaterThanOrEqualTo(0);
        } finally {
            if (initialStore != null) {
                initialStore.close();
            }
            if (evolvedStore != null) {
                evolvedStore.close();
            }
            environment.close();
        }
    }

    @Test
    void storeConfigurationAndRawStoreExposeConfiguredModel(@TempDir Path home) throws Exception {
        Environment environment = openEnvironment(home);
        EntityStore store = null;
        EntityStore rawBase = null;
        RawStore rawStore = null;
        try {
            StoreConfig config = new StoreConfig();
            config.setAllowCreate(true);
            config.setTransactional(true);
            Mutations mutations = new Mutations();
            config.setMutations(mutations);
            store = new EntityStore(environment, "config", config);
            assertThat(store.getEnvironment()).isSameAs(environment);
            assertThat(store.getStoreName()).isEqualTo("config");
            assertThat(store.getConfig().getTransactional()).isTrue();
            assertThat(store.getMutations()).isNotNull();
            assertThat(store.getModel().isOpen()).isTrue();
            assertThat(EntityStore.getStoreNames(environment)).contains("config");
            assertThat(store.getPrimaryConfig(Person.class)).isNotNull();
            DatabaseConfig primaryConfig = new DatabaseConfig();
            primaryConfig.setAllowCreate(true);
            store.setPrimaryConfig(Person.class, primaryConfig);
            assertThat(store.getPrimaryConfig(Person.class)).isNotNull();
            SecondaryConfig secondaryConfig = store.getSecondaryConfig(Person.class, "group");
            assertThat(secondaryConfig).isNotNull();
            store.setSecondaryConfig(Person.class, "group", secondaryConfig);
            com.sleepycat.je.SequenceConfig idsConfig = new com.sleepycat.je.SequenceConfig();
            idsConfig.setAllowCreate(true);
            store.setSequenceConfig("ids", idsConfig);
            assertThat(store.getSequenceConfig("ids")).isNotNull();
            assertThat(store.getSequence("ids")).isNotNull();
            store.getPrimaryIndex(Integer.class, Person.class).put(new Person(1, "group", "name"));
            EvolveConfig evolveConfig = new EvolveConfig();
            evolveConfig.addClassToEvolve(Person.class.getName());
            assertThat(store.evolve(evolveConfig)).isNotNull();
            store.sync();
            Store directStore = new Store(environment, "direct", config, false);
            directStore.dumpCatalog();
            directStore.close();
            Store.setSyncHook((database, beforeCommit) -> {
            });
            store.truncateClass(null, Person.class);
            store.truncateClass(Person.class);

            StoreConfig rawConfig = new StoreConfig();
            rawConfig.setAllowCreate(true);
            rawConfig.setTransactional(true);
            rawConfig.setMutations(mutations);
            rawBase = new EntityStore(environment, "raw", rawConfig);
            rawBase.getPrimaryIndex(Integer.class, Person.class);
            rawBase.close();
            rawBase = null;
            rawStore = new RawStore(environment, "raw", rawConfig);
            assertThat(rawStore.getEnvironment()).isSameAs(environment);
            assertThat(rawStore.getConfig()).isNotNull();
            assertThat(rawStore.getStoreName()).isEqualTo("raw");
            assertThat(rawStore.getModel()).isNotNull();
            assertThat(rawStore.getMutations()).isNotNull();
            assertThat(rawStore.getPrimaryIndex(Person.class.getName())).isNotNull();
            assertThat(rawStore.getSecondaryIndex(Person.class.getName(), "group")).isNotNull();
        } finally {
            if (rawStore != null) {
                rawStore.close();
            }
            if (rawBase != null) {
                rawBase.close();
            }
            if (store != null) {
                store.closeClass(Person.class);
                store.close();
            }
            environment.close();
        }
    }

    @Test
    void compositeSecondaryKeysAreCreatedThroughThePublicIndex(@TempDir Path home)
            throws Exception {
        Environment environment = openEnvironment(home);
        EntityStore store = null;
        try {
            StoreConfig config = new StoreConfig();
            config.setAllowCreate(true);
            store = new EntityStore(environment, "composite-secondary", config);
            PrimaryIndex<Integer, CompositeSecondaryRecord> primary = store.getPrimaryIndex(
                    Integer.class, CompositeSecondaryRecord.class);
            SecondaryIndex<CompositeSecondaryKey, Integer, CompositeSecondaryRecord> secondary =
                    store.getSecondaryIndex(primary, CompositeSecondaryKey.class, "compositeKey");
            CompositeSecondaryRecord record = new CompositeSecondaryRecord();
            record.id = 1;
            record.compositeKey = new CompositeSecondaryKey("group", 7);
            primary.put(record);
            assertThat(secondary.get(record.compositeKey).id).isEqualTo(1);
        } finally {
            if (store != null) {
                store.close();
            }
            environment.close();
        }
    }

    @Test
    void evolutionAndStoreConfigurationObjectsExposeTheirState() {
        StoreConfig storeConfig = new StoreConfig();
        storeConfig.setExclusiveCreate(true);
        storeConfig.setReadOnly(true);
        storeConfig.setSecondaryBulkLoad(true);
        assertThat(storeConfig.getExclusiveCreate()).isTrue();
        assertThat(storeConfig.getReadOnly()).isTrue();
        assertThat(storeConfig.getSecondaryBulkLoad()).isTrue();

        Renamer classRenamer = new Renamer("Person", 1, "Employee");
        assertThat(classRenamer.getNewName()).isEqualTo("Employee");
        Renamer renamer = new Renamer("Person", 1, "name", "label");
        assertThat(renamer.getNewName()).isEqualTo("label");
        assertThat(renamer).isEqualTo(new Renamer("Person", 1, "name", "label"));
        assertThat(renamer.hashCode()).isEqualTo(
                new Renamer("Person", 1, "name", "label").hashCode());
        assertThat(renamer.toString()).contains("label");
        assertThat(new Deleter("Person", 1).toString()).contains("Person");
        assertThat(new Deleter("Person", 1, "name").toString()).contains("name");

        Conversion conversion = new Conversion() {
            @Override
            public void initialize(com.sleepycat.persist.model.EntityModel model) {
            }

            @Override
            public Object convert(Object oldValue) {
                return String.valueOf(oldValue);
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof Conversion;
            }
        };
        EntityConverter converter = new EntityConverter("Person", 1, conversion,
                Set.of("oldField"));
        assertThat(converter.getDeletedKeys()).containsExactly("oldField");
        assertThat(converter).isEqualTo(new EntityConverter("Person", 1, conversion,
                Set.of("oldField")));
        assertThat(converter.hashCode()).isNotZero();
        assertThat(converter.toString()).contains("oldField");
        assertThat(new Converter("Person", 1, conversion)).isNotNull();

        EvolveEvent event = EvolveInternal.newEvent();
        EvolveInternal.updateEvent(event, Person.class.getName(), 2, 1);
        assertThat(event.getEntityClassName()).isEqualTo(Person.class.getName());
        EvolveStats stats = event.getStats();
        assertThat(stats.getNRead()).isEqualTo(2);
        assertThat(stats.getNConverted()).isEqualTo(1);
    }

    @Test
    void entityConfigurationObjectsCloneAndRetainValues() {
        StoreConfig storeConfig = new StoreConfig();
        storeConfig.setAllowCreate(true);
        storeConfig.setDeferredWrite(true);
        assertThat(storeConfig.cloneConfig().getDeferredWrite()).isTrue();
        EvolveConfig evolveConfig = new EvolveConfig();
        evolveConfig.addClassToEvolve(Person.class.getName());
        assertThat(evolveConfig.cloneConfig().getClassesToEvolve()).contains(Person.class.getName());
        com.sleepycat.persist.evolve.EvolveListener listener = event -> true;
        evolveConfig.setEvolveListener(listener);
        assertThat(evolveConfig.getEvolveListener()).isSameAs(listener);
        Mutations mutations = new Mutations();
        mutations.addDeleter(new Deleter("Person", 1));
        mutations.addRenamer(new Renamer("Person", 1, "old", "new"));
        assertThat(mutations.isEmpty()).isFalse();
        assertThat(mutations.getDeleters()).hasSize(1);
        assertThat(mutations.getRenamers()).hasSize(1);
        assertThat(mutations.hashCode()).isNotZero();
        assertThat(mutations.toString()).contains("Person");
        mutations.addConverter(new Converter("Person", 1, "name", new Conversion() {
            @Override
            public void initialize(com.sleepycat.persist.model.EntityModel model) {
            }

            @Override
            public Object convert(Object oldValue) {
                return String.valueOf(oldValue);
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof Conversion;
            }
        }));
        assertThat(mutations.getConverters()).hasSize(1);
        assertThat(mutations.getConverter("Person", 1, "name")).isNotNull();
        assertThat(new Mutations()).isNotNull();
    }

    @Test
    void secondaryIndexesCopyEachSupportedScalarKey(@TempDir Path home) throws Exception {
        Environment environment = openEnvironment(home);
        EntityStore store = null;
        try {
            StoreConfig config = new StoreConfig();
            config.setAllowCreate(true);
            store = new EntityStore(environment, "scalar-secondary", config);
            PrimaryIndex<Integer, ScalarSecondaryRecord> primary = store.getPrimaryIndex(
                    Integer.class, ScalarSecondaryRecord.class);
            Class<?>[] keyTypes = {
                java.math.BigInteger.class, Boolean.class, Byte.class, Character.class,
                java.util.Date.class, Double.class, Float.class, Integer.class, Long.class,
                Short.class
            };
            String[] keyNames = {"bigInteger", "booleanValue", "byteValue", "character",
                "date", "doubleValue", "floatValue", "integerValue", "longValue", "shortValue"};
            for (int i = 0; i < keyTypes.length; i++) {
                assertThat(store.getSecondaryIndex((PrimaryIndex) primary, keyTypes[i], keyNames[i]))
                        .isNotNull();
            }
            primary.put(new ScalarSecondaryRecord());
            assertThat(primary.get(1)).isNotNull();
        } finally {
            if (store != null) {
                store.close();
            }
            environment.close();
        }
    }

    @Persistent
    public static class CompositeSecondaryKey {
        @com.sleepycat.persist.model.KeyField(1)
        public String group;
        @com.sleepycat.persist.model.KeyField(2)
        public int number;

        public CompositeSecondaryKey() {
        }

        CompositeSecondaryKey(String group, int number) {
            this.group = group;
            this.number = number;
        }
    }

    @Entity
    public static class CompositeSecondaryRecord {
        @PrimaryKey
        public int id;
        @SecondaryKey(relate = MANY_TO_ONE)
        public CompositeSecondaryKey compositeKey;

        public CompositeSecondaryRecord() {
        }
    }

    @Entity
    public static class OldSecondaryRecord {
        @PrimaryKey
        public int id;
        @SecondaryKey(relate = MANY_TO_ONE)
        public String oldGroup;
        public String name;

        public OldSecondaryRecord() {
        }

        OldSecondaryRecord(int id, String name, String oldGroup) {
            this.id = id;
            this.name = name;
            this.oldGroup = oldGroup;
        }
    }

    @Entity(version = 1)
    public static class EvolvedSecondaryRecord {
        @PrimaryKey
        public int id;
        @SecondaryKey(relate = MANY_TO_ONE)
        public String newGroup;
        public String name;

        public EvolvedSecondaryRecord() {
        }
    }

    @Entity
    public static class OldRecord {
        @PrimaryKey
        public int id;
        public String name;
        public int oldNumber;
        public String removed;

        public OldRecord() {
        }
    }

    @Entity(version = 1)
    public static class EvolvedRecord {
        @PrimaryKey
        public int id;
        public String name;
        public long longNumber;
        public String added;

        public EvolvedRecord() {
        }
    }

    @Entity
    public static class RichRecord {
        @PrimaryKey
        public int id;
        public java.math.BigInteger bigInteger;
        public boolean booleanValue;
        public byte byteValue;
        public char character;
        public Date date;
        public double doubleValue;
        public float floatValue;
        public long longValue;
        public short shortValue;
        public RichEnum enumValue;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public java.math.BigInteger[] bigIntegers;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public Boolean[] booleans;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public byte[] bytes;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public Byte[] boxedBytes;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public char[] characters;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public Character[] boxedCharacters;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public Date[] dates;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public double[] doubles;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public Double[] boxedDoubles;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public float[] floats;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public Float[] boxedFloats;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public int[] integers;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public long[] longs;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public short[] shorts;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public String[] strings;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public ArrayList<String> arrayList;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public HashSet<String> hashSet;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public LinkedList<String> linkedList;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        public TreeSet<String> treeSet;
        public HashMap<String, Integer> hashMap;
        public TreeMap<String, Integer> treeMap;

        public RichRecord() {
        }
    }

    public enum RichEnum {
        FIRST,
        SECOND
    }

    @Entity
    public static class ByteSequenceRecord {
        @PrimaryKey(sequence = "byte-sequence")
        public Byte id;

        public ByteSequenceRecord() {
        }
    }

    @Entity
    public static class IntegerSequenceRecord {
        @PrimaryKey(sequence = "integer-sequence")
        public int id;

        public IntegerSequenceRecord() {
        }
    }

    @Entity
    public static class ShortSequenceRecord {
        @PrimaryKey(sequence = "short-sequence")
        public short id;

        public ShortSequenceRecord() {
        }
    }

    @Entity
    public static class ScalarSecondaryRecord {
        @PrimaryKey
        private int id = 1;
        @SecondaryKey(relate = MANY_TO_ONE)
        private java.math.BigInteger bigInteger = java.math.BigInteger.TEN;
        @SecondaryKey(relate = MANY_TO_ONE)
        private boolean booleanValue = true;
        @SecondaryKey(relate = MANY_TO_ONE)
        private byte byteValue = 2;
        @SecondaryKey(relate = MANY_TO_ONE)
        private char character = 'c';
        @SecondaryKey(relate = MANY_TO_ONE)
        private java.util.Date date = new java.util.Date(1000L);
        @SecondaryKey(relate = MANY_TO_ONE)
        private double doubleValue = 2.5d;
        @SecondaryKey(relate = MANY_TO_ONE)
        private float floatValue = 1.5f;
        @SecondaryKey(relate = MANY_TO_ONE)
        private int integerValue = 4;
        @SecondaryKey(relate = MANY_TO_ONE)
        private long longValue = 5L;
        @SecondaryKey(relate = MANY_TO_ONE)
        private short shortValue = 6;

        public ScalarSecondaryRecord() {
        }
    }

    @Entity
    public static class TaggedRecord {
        @PrimaryKey
        private int id;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY)
        private Set<String> tags;

        public TaggedRecord() {
        }

        TaggedRecord(int id, Set<String> tags) {
            this.id = id;
            this.tags = tags;
        }
    }

    @Entity
    public static class ForeignParent {
        @PrimaryKey
        private String id;

        public ForeignParent() {
        }

        ForeignParent(String id) {
            this.id = id;
        }
    }

    @Entity
    public static class CollectionForeignRecord {
        @PrimaryKey
        public int id;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY,
                relatedEntity = ForeignParent.class, onRelatedEntityDelete = DeleteAction.NULLIFY)
        public ArrayList<String> arrayList;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY,
                relatedEntity = ForeignParent.class, onRelatedEntityDelete = DeleteAction.NULLIFY)
        public HashSet<String> hashSet;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY,
                relatedEntity = ForeignParent.class, onRelatedEntityDelete = DeleteAction.NULLIFY)
        public LinkedList<String> linkedList;
        @SecondaryKey(relate = com.sleepycat.persist.model.Relationship.MANY_TO_MANY,
                relatedEntity = ForeignParent.class, onRelatedEntityDelete = DeleteAction.NULLIFY)
        public TreeSet<String> treeSet;

        public CollectionForeignRecord() {
        }
    }

    @Entity
    public static class ForeignChild {
        @PrimaryKey
        private int id;
        @SecondaryKey(relate = MANY_TO_ONE, relatedEntity = ForeignParent.class,
                onRelatedEntityDelete = DeleteAction.NULLIFY)
        private String parent;

        public ForeignChild() {
        }

        ForeignChild(int id, String parent) {
            this.id = id;
            this.parent = parent;
        }
    }

    @com.sleepycat.persist.model.Persistent
    public static class Employee extends Person {
        @SecondaryKey(relate = MANY_TO_ONE)
        private String department;

        public Employee() {
        }
    }

    private static Environment openEnvironment(Path home) throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        return new Environment(home.toFile(), config);
    }

    @Entity
    public static class ScalarRecord {
        @PrimaryKey
        private int id;
        private java.math.BigInteger bigInteger;
        private boolean booleanValue;
        private byte byteValue;
        private char character;
        private java.util.Date date;
        private double doubleValue;
        private float floatValue;
        private short shortValue;

        public ScalarRecord() {
        }
    }

    @Entity
    public static class Person {
        @PrimaryKey
        private int id;
        @SecondaryKey(relate = MANY_TO_ONE)
        private String group;
        private String name;

        public Person() {
        }

        Person(int id, String group, String name) {
            this.id = id;
            this.group = group;
            this.name = name;
        }
    }
}
