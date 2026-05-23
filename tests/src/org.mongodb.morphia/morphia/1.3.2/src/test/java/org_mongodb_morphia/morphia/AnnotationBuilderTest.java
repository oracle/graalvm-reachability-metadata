/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import static com.mongodb.client.model.ValidationAction.WARN;
import static com.mongodb.client.model.ValidationLevel.MODERATE;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoNamespace;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.ListCollectionsIterable;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MapReduceIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.CreateViewOptions;
import com.mongodb.client.model.DeleteOptions;
import com.mongodb.client.model.FindOneAndDeleteOptions;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.InsertOneOptions;
import com.mongodb.client.model.RenameCollectionOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.ValidationBuilder;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.Indexes;
import sun.misc.Unsafe;

@SuppressWarnings({"deprecation", "removal"})
public class AnnotationBuilderTest {
    private static final Unsafe UNSAFE = unsafe();

    @Test
    void validationBuilderReadsDefaultAnnotationMembersAndOverridesSelectedValues() {
        ValidationBuilder builder = new ValidationBuilder()
                .value("{ age: { $gte: 18 } }")
                .level(MODERATE)
                .action(WARN);

        assertThat(builder.value()).isEqualTo("{ age: { $gte: 18 } }");
        assertThat(builder.level()).isEqualTo(MODERATE);
        assertThat(builder.action()).isEqualTo(WARN);
        assertThat(builder.annotationType().getName()).isEqualTo("org.mongodb.morphia.annotations.Validation");
    }

    @Test
    void ensureIndexesMigratesDeprecatedIndexAnnotationIntoIndexOptions() {
        RecordingMongoClient client = newRecordingMongoClient("annotation_builder_test");
        try {
            Morphia morphia = new Morphia().map(DeprecatedIndexEntity.class);
            Datastore datastore = morphia.createDatastore(client, "annotation_builder_test");

            datastore.ensureIndexes(DeprecatedIndexEntity.class);

            assertThat(client.database.collection.createdIndexes).hasSize(1);
            CreatedIndex index = client.database.collection.createdIndexes.get(0);
            assertThat(index.keys.getInt32("name").getValue()).isEqualTo(1);
            assertThat(index.options.isUnique()).isTrue();
            assertThat(index.options.getName()).isEqualTo("name_idx");
            assertThat(index.options.getExpireAfter(TimeUnit.SECONDS)).isEqualTo(3600L);
        } finally {
            client.close();
        }
    }

    @Entity("deprecated_index_entities")
    @Indexes(@Index(value = "name", unique = true, name = "name_idx", expireAfterSeconds = 3600))
    public static class DeprecatedIndexEntity {
        @Id
        private String id;
        private String name;
    }

    private static final class CreatedIndex {
        private final BsonDocument keys;
        private final IndexOptions options;

        private CreatedIndex(BsonDocument keys, IndexOptions options) {
            this.keys = keys;
            this.options = options;
        }
    }

    private static RecordingMongoClient newRecordingMongoClient(String databaseName) {
        try {
            RecordingMongoClient client = (RecordingMongoClient) UNSAFE.allocateInstance(RecordingMongoClient.class);
            client.database = new RecordingMongoDatabase(databaseName);
            return client;
        } catch (InstantiationException exception) {
            throw new IllegalStateException("Failed to allocate RecordingMongoClient", exception);
        }
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static final class RecordingMongoClient extends MongoClient {
        private RecordingMongoDatabase database;

        @Override
        public MongoDatabase getDatabase(String databaseName) {
            assertThat(databaseName).isEqualTo(database.name);
            return database;
        }

        @Override
        public DB getDB(String databaseName) {
            assertThat(databaseName).isEqualTo(database.name);
            return null;
        }

        @Override
        public WriteConcern getWriteConcern() {
            return WriteConcern.ACKNOWLEDGED;
        }

        @Override
        public void close() {
        }
    }

    private static final class RecordingMongoDatabase implements MongoDatabase {
        private final String name;
        private final RecordingMongoCollection<Document> collection = new RecordingMongoCollection<Document>();

        private RecordingMongoDatabase(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public CodecRegistry getCodecRegistry() {
            return MongoClient.getDefaultCodecRegistry();
        }

        @Override
        public ReadPreference getReadPreference() {
            return ReadPreference.primary();
        }

        @Override
        public WriteConcern getWriteConcern() {
            return WriteConcern.ACKNOWLEDGED;
        }

        @Override
        public ReadConcern getReadConcern() {
            return ReadConcern.DEFAULT;
        }

        @Override
        public MongoDatabase withCodecRegistry(CodecRegistry codecRegistry) {
            return this;
        }

        @Override
        public MongoDatabase withReadPreference(ReadPreference readPreference) {
            return this;
        }

        @Override
        public MongoDatabase withWriteConcern(WriteConcern writeConcern) {
            return this;
        }

        @Override
        public MongoDatabase withReadConcern(ReadConcern readConcern) {
            return this;
        }

        @Override
        public MongoCollection<Document> getCollection(String collectionName) {
            return collection;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <TDocument> MongoCollection<TDocument> getCollection(String collectionName,
                                                                    Class<TDocument> documentClass) {
            return (MongoCollection<TDocument>) collection;
        }

        @Override
        public Document runCommand(Bson command) {
            throw unsupported();
        }

        @Override
        public Document runCommand(Bson command, ReadPreference readPreference) {
            throw unsupported();
        }

        @Override
        public <TResult> TResult runCommand(Bson command, Class<TResult> resultClass) {
            throw unsupported();
        }

        @Override
        public <TResult> TResult runCommand(Bson command, ReadPreference readPreference, Class<TResult> resultClass) {
            throw unsupported();
        }

        @Override
        public void drop() {
            throw unsupported();
        }

        @Override
        public MongoIterable<String> listCollectionNames() {
            throw unsupported();
        }

        @Override
        public ListCollectionsIterable<Document> listCollections() {
            throw unsupported();
        }

        @Override
        public <TResult> ListCollectionsIterable<TResult> listCollections(Class<TResult> resultClass) {
            throw unsupported();
        }

        @Override
        public void createCollection(String collectionName) {
            throw unsupported();
        }

        @Override
        public void createCollection(String collectionName, CreateCollectionOptions createCollectionOptions) {
            throw unsupported();
        }

        @Override
        public void createView(String viewName, String viewOn, List<? extends Bson> pipeline) {
            throw unsupported();
        }

        @Override
        public void createView(String viewName, String viewOn, List<? extends Bson> pipeline,
                               CreateViewOptions createViewOptions) {
            throw unsupported();
        }
    }

    private static final class RecordingMongoCollection<TDocument> implements MongoCollection<TDocument> {
        private final List<CreatedIndex> createdIndexes = new ArrayList<CreatedIndex>();

        @Override
        public MongoNamespace getNamespace() {
            return new MongoNamespace("annotation_builder_test.deprecated_index_entities");
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<TDocument> getDocumentClass() {
            return (Class<TDocument>) Document.class;
        }

        @Override
        public CodecRegistry getCodecRegistry() {
            return MongoClient.getDefaultCodecRegistry();
        }

        @Override
        public ReadPreference getReadPreference() {
            return ReadPreference.primary();
        }

        @Override
        public WriteConcern getWriteConcern() {
            return WriteConcern.ACKNOWLEDGED;
        }

        @Override
        public ReadConcern getReadConcern() {
            return ReadConcern.DEFAULT;
        }

        @Override
        public <NewTDocument> MongoCollection<NewTDocument> withDocumentClass(Class<NewTDocument> clazz) {
            throw unsupported();
        }

        @Override
        public MongoCollection<TDocument> withCodecRegistry(CodecRegistry codecRegistry) {
            return this;
        }

        @Override
        public MongoCollection<TDocument> withReadPreference(ReadPreference readPreference) {
            return this;
        }

        @Override
        public MongoCollection<TDocument> withWriteConcern(WriteConcern writeConcern) {
            return this;
        }

        @Override
        public MongoCollection<TDocument> withReadConcern(ReadConcern readConcern) {
            return this;
        }

        @Override
        public long count() {
            throw unsupported();
        }

        @Override
        public long count(Bson filter) {
            throw unsupported();
        }

        @Override
        public long count(Bson filter, CountOptions options) {
            throw unsupported();
        }

        @Override
        public <TResult> DistinctIterable<TResult> distinct(String fieldName, Class<TResult> resultClass) {
            throw unsupported();
        }

        @Override
        public <TResult> DistinctIterable<TResult> distinct(String fieldName, Bson filter, Class<TResult> resultClass) {
            throw unsupported();
        }

        @Override
        public FindIterable<TDocument> find() {
            throw unsupported();
        }

        @Override
        public <TResult> FindIterable<TResult> find(Class<TResult> resultClass) {
            throw unsupported();
        }

        @Override
        public FindIterable<TDocument> find(Bson filter) {
            throw unsupported();
        }

        @Override
        public <TResult> FindIterable<TResult> find(Bson filter, Class<TResult> resultClass) {
            throw unsupported();
        }

        @Override
        public AggregateIterable<TDocument> aggregate(List<? extends Bson> pipeline) {
            throw unsupported();
        }

        @Override
        public <TResult> AggregateIterable<TResult> aggregate(List<? extends Bson> pipeline,
                                                              Class<TResult> resultClass) {
            throw unsupported();
        }

        @Override
        public MapReduceIterable<TDocument> mapReduce(String mapFunction, String reduceFunction) {
            throw unsupported();
        }

        @Override
        public <TResult> MapReduceIterable<TResult> mapReduce(String mapFunction, String reduceFunction,
                                                              Class<TResult> resultClass) {
            throw unsupported();
        }

        @Override
        public BulkWriteResult bulkWrite(List<? extends WriteModel<? extends TDocument>> requests) {
            throw unsupported();
        }

        @Override
        public BulkWriteResult bulkWrite(List<? extends WriteModel<? extends TDocument>> requests,
                                         BulkWriteOptions options) {
            throw unsupported();
        }

        @Override
        public void insertOne(TDocument document) {
            throw unsupported();
        }

        @Override
        public void insertOne(TDocument document, InsertOneOptions options) {
            throw unsupported();
        }

        @Override
        public void insertMany(List<? extends TDocument> documents) {
            throw unsupported();
        }

        @Override
        public void insertMany(List<? extends TDocument> documents, InsertManyOptions options) {
            throw unsupported();
        }

        @Override
        public DeleteResult deleteOne(Bson filter) {
            throw unsupported();
        }

        @Override
        public DeleteResult deleteOne(Bson filter, DeleteOptions options) {
            throw unsupported();
        }

        @Override
        public DeleteResult deleteMany(Bson filter) {
            throw unsupported();
        }

        @Override
        public DeleteResult deleteMany(Bson filter, DeleteOptions options) {
            throw unsupported();
        }

        @Override
        public UpdateResult replaceOne(Bson filter, TDocument replacement) {
            throw unsupported();
        }

        @Override
        public UpdateResult replaceOne(Bson filter, TDocument replacement, UpdateOptions options) {
            throw unsupported();
        }

        @Override
        public UpdateResult updateOne(Bson filter, Bson update) {
            throw unsupported();
        }

        @Override
        public UpdateResult updateOne(Bson filter, Bson update, UpdateOptions options) {
            throw unsupported();
        }

        @Override
        public UpdateResult updateMany(Bson filter, Bson update) {
            throw unsupported();
        }

        @Override
        public UpdateResult updateMany(Bson filter, Bson update, UpdateOptions options) {
            throw unsupported();
        }

        @Override
        public TDocument findOneAndDelete(Bson filter) {
            throw unsupported();
        }

        @Override
        public TDocument findOneAndDelete(Bson filter, FindOneAndDeleteOptions options) {
            throw unsupported();
        }

        @Override
        public TDocument findOneAndReplace(Bson filter, TDocument replacement) {
            throw unsupported();
        }

        @Override
        public TDocument findOneAndReplace(Bson filter, TDocument replacement, FindOneAndReplaceOptions options) {
            throw unsupported();
        }

        @Override
        public TDocument findOneAndUpdate(Bson filter, Bson update) {
            throw unsupported();
        }

        @Override
        public TDocument findOneAndUpdate(Bson filter, Bson update, FindOneAndUpdateOptions options) {
            throw unsupported();
        }

        @Override
        public void drop() {
            throw unsupported();
        }

        @Override
        public String createIndex(Bson keys) {
            return createIndex(keys, new IndexOptions());
        }

        @Override
        public String createIndex(Bson keys, IndexOptions indexOptions) {
            createdIndexes.add(new CreatedIndex((BsonDocument) keys, indexOptions));
            return "recorded-index";
        }

        @Override
        public List<String> createIndexes(List<IndexModel> indexes) {
            throw unsupported();
        }

        @Override
        public ListIndexesIterable<Document> listIndexes() {
            throw unsupported();
        }

        @Override
        public <TResult> ListIndexesIterable<TResult> listIndexes(Class<TResult> resultClass) {
            throw unsupported();
        }

        @Override
        public void dropIndex(String indexName) {
            throw unsupported();
        }

        @Override
        public void dropIndex(Bson keys) {
            throw unsupported();
        }

        @Override
        public void dropIndexes() {
            throw unsupported();
        }

        @Override
        public void renameCollection(MongoNamespace newCollectionNamespace) {
            throw unsupported();
        }

        @Override
        public void renameCollection(MongoNamespace newCollectionNamespace, RenameCollectionOptions options) {
            throw unsupported();
        }
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("This test double only supports index creation");
    }
}
