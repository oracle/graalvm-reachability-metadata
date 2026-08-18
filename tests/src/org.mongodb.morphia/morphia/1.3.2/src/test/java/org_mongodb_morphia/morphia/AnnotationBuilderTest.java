/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import java.util.ArrayList;
import java.util.List;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoNamespace;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
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
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
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

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class AnnotationBuilderTest {
    @Test
    void validationBuilderReadsAnnotationDefaults() {
        ValidationBuilder builder = new ValidationBuilder()
                .value("{ status: { $exists: true } }");

        assertThat(builder.annotationType().getName()).isEqualTo("org.mongodb.morphia.annotations.Validation");
        assertThat(builder.value()).isEqualTo("{ status: { $exists: true } }");
        assertThat(builder.level()).isEqualTo(ValidationLevel.STRICT);
        assertThat(builder.action()).isEqualTo(ValidationAction.ERROR);
    }

    @Test
    void datastoreMigratesLegacyIndexAnnotationValues() {
        RecordingMongoClient client = new RecordingMongoClient();
        try {
            Morphia morphia = new Morphia();
            morphia.map(AnnotationBuilderLegacyIndexEntity.class);
            Datastore datastore = morphia.createDatastore(client, "annotation_builder_coverage");

            datastore.ensureIndexes(AnnotationBuilderLegacyIndexEntity.class);

            assertThat(client.createdIndexes).hasSize(1);
            CreatedIndex createdIndex = client.createdIndexes.get(0);
            BsonDocument keys = (BsonDocument) createdIndex.keys;
            assertThat(keys.getInt32("legacyField").getValue()).isEqualTo(1);
            assertThat(createdIndex.options.getName()).isEqualTo("legacy_index");
            assertThat(createdIndex.options.isUnique()).isTrue();
            assertThat(createdIndex.options.isSparse()).isTrue();
        } finally {
            client.close();
        }
    }

    private static UnsupportedOperationException unsupported(final String methodName) {
        return new UnsupportedOperationException("Unexpected test double call: " + methodName);
    }

    private static final class RecordingMongoClient extends MongoClient {
        private final List<CreatedIndex> createdIndexes = new ArrayList<CreatedIndex>();

        RecordingMongoClient() {
            super(new ServerAddress("localhost", 1), MongoClientOptions.builder()
                    .serverSelectionTimeout(1)
                    .connectTimeout(1)
                    .socketTimeout(1)
                    .build());
        }

        @Override
        public MongoDatabase getDatabase(final String databaseName) {
            return new RecordingMongoDatabase(databaseName, new RecordingMongoCollection<Document>(createdIndexes));
        }

        @Override
        public DB getDB(final String databaseName) {
            return null;
        }

        @Override
        public WriteConcern getWriteConcern() {
            return WriteConcern.ACKNOWLEDGED;
        }
    }

    private static final class RecordingMongoDatabase implements MongoDatabase {
        private final String databaseName;
        private final RecordingMongoCollection<?> collection;

        RecordingMongoDatabase(final String databaseName, final RecordingMongoCollection<?> collection) {
            this.databaseName = databaseName;
            this.collection = collection;
        }

        @Override
        public String getName() {
            return databaseName;
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
        public MongoDatabase withCodecRegistry(final CodecRegistry codecRegistry) {
            return this;
        }

        @Override
        public MongoDatabase withReadPreference(final ReadPreference readPreference) {
            return this;
        }

        @Override
        public MongoDatabase withWriteConcern(final WriteConcern writeConcern) {
            return this;
        }

        @Override
        public MongoDatabase withReadConcern(final ReadConcern readConcern) {
            return this;
        }

        @Override
        public MongoCollection<Document> getCollection(final String collectionName) {
            return new RecordingMongoCollection<Document>(collection.createdIndexes);
        }

        @Override
        public <TDocument> MongoCollection<TDocument> getCollection(final String collectionName,
                final Class<TDocument> documentClass) {
            return new RecordingMongoCollection<TDocument>(collection.createdIndexes, documentClass);
        }

        @Override
        public Document runCommand(final Bson command) {
            throw unsupported("runCommand");
        }

        @Override
        public Document runCommand(final Bson command, final ReadPreference readPreference) {
            throw unsupported("runCommand");
        }

        @Override
        public <TResult> TResult runCommand(final Bson command, final Class<TResult> resultClass) {
            throw unsupported("runCommand");
        }

        @Override
        public <TResult> TResult runCommand(final Bson command, final ReadPreference readPreference,
                final Class<TResult> resultClass) {
            throw unsupported("runCommand");
        }

        @Override
        public void drop() {
            throw unsupported("drop");
        }

        @Override
        public MongoIterable<String> listCollectionNames() {
            throw unsupported("listCollectionNames");
        }

        @Override
        public ListCollectionsIterable<Document> listCollections() {
            throw unsupported("listCollections");
        }

        @Override
        public <TResult> ListCollectionsIterable<TResult> listCollections(final Class<TResult> resultClass) {
            throw unsupported("listCollections");
        }

        @Override
        public void createCollection(final String collectionName) {
            throw unsupported("createCollection");
        }

        @Override
        public void createCollection(final String collectionName, final CreateCollectionOptions createCollectionOptions) {
            throw unsupported("createCollection");
        }

        @Override
        public void createView(final String viewName, final String viewOn, final List<? extends Bson> pipeline) {
            throw unsupported("createView");
        }

        @Override
        public void createView(final String viewName, final String viewOn, final List<? extends Bson> pipeline,
                final CreateViewOptions createViewOptions) {
            throw unsupported("createView");
        }
    }

    private static final class RecordingMongoCollection<TDocument> implements MongoCollection<TDocument> {
        private final List<CreatedIndex> createdIndexes;
        private final Class<TDocument> documentClass;

        RecordingMongoCollection(final List<CreatedIndex> createdIndexes) {
            this(createdIndexes, null);
        }

        RecordingMongoCollection(final List<CreatedIndex> createdIndexes, final Class<TDocument> documentClass) {
            this.createdIndexes = createdIndexes;
            this.documentClass = documentClass;
        }

        @Override
        public MongoNamespace getNamespace() {
            return new MongoNamespace("annotation_builder_coverage.annotation_builder_legacy_index_entities");
        }

        @Override
        public Class<TDocument> getDocumentClass() {
            return documentClass;
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
        public <NewTDocument> MongoCollection<NewTDocument> withDocumentClass(final Class<NewTDocument> newDocumentClass) {
            return new RecordingMongoCollection<NewTDocument>(createdIndexes, newDocumentClass);
        }

        @Override
        public MongoCollection<TDocument> withCodecRegistry(final CodecRegistry codecRegistry) {
            return this;
        }

        @Override
        public MongoCollection<TDocument> withReadPreference(final ReadPreference readPreference) {
            return this;
        }

        @Override
        public MongoCollection<TDocument> withWriteConcern(final WriteConcern writeConcern) {
            return this;
        }

        @Override
        public MongoCollection<TDocument> withReadConcern(final ReadConcern readConcern) {
            return this;
        }

        @Override
        public long count() {
            throw unsupported("count");
        }

        @Override
        public long count(final Bson filter) {
            throw unsupported("count");
        }

        @Override
        public long count(final Bson filter, final CountOptions options) {
            throw unsupported("count");
        }

        @Override
        public <TResult> DistinctIterable<TResult> distinct(final String fieldName, final Class<TResult> resultClass) {
            throw unsupported("distinct");
        }

        @Override
        public <TResult> DistinctIterable<TResult> distinct(final String fieldName, final Bson filter,
                final Class<TResult> resultClass) {
            throw unsupported("distinct");
        }

        @Override
        public FindIterable<TDocument> find() {
            throw unsupported("find");
        }

        @Override
        public <TResult> FindIterable<TResult> find(final Class<TResult> resultClass) {
            throw unsupported("find");
        }

        @Override
        public FindIterable<TDocument> find(final Bson filter) {
            throw unsupported("find");
        }

        @Override
        public <TResult> FindIterable<TResult> find(final Bson filter, final Class<TResult> resultClass) {
            throw unsupported("find");
        }

        @Override
        public AggregateIterable<TDocument> aggregate(final List<? extends Bson> pipeline) {
            throw unsupported("aggregate");
        }

        @Override
        public <TResult> AggregateIterable<TResult> aggregate(final List<? extends Bson> pipeline,
                final Class<TResult> resultClass) {
            throw unsupported("aggregate");
        }

        @Override
        public MapReduceIterable<TDocument> mapReduce(final String mapFunction, final String reduceFunction) {
            throw unsupported("mapReduce");
        }

        @Override
        public <TResult> MapReduceIterable<TResult> mapReduce(final String mapFunction, final String reduceFunction,
                final Class<TResult> resultClass) {
            throw unsupported("mapReduce");
        }

        @Override
        public BulkWriteResult bulkWrite(final List<? extends WriteModel<? extends TDocument>> requests) {
            throw unsupported("bulkWrite");
        }

        @Override
        public BulkWriteResult bulkWrite(final List<? extends WriteModel<? extends TDocument>> requests,
                final BulkWriteOptions options) {
            throw unsupported("bulkWrite");
        }

        @Override
        public void insertOne(final TDocument document) {
            throw unsupported("insertOne");
        }

        @Override
        public void insertOne(final TDocument document, final InsertOneOptions options) {
            throw unsupported("insertOne");
        }

        @Override
        public void insertMany(final List<? extends TDocument> documents) {
            throw unsupported("insertMany");
        }

        @Override
        public void insertMany(final List<? extends TDocument> documents, final InsertManyOptions options) {
            throw unsupported("insertMany");
        }

        @Override
        public DeleteResult deleteOne(final Bson filter) {
            throw unsupported("deleteOne");
        }

        @Override
        public DeleteResult deleteOne(final Bson filter, final DeleteOptions options) {
            throw unsupported("deleteOne");
        }

        @Override
        public DeleteResult deleteMany(final Bson filter) {
            throw unsupported("deleteMany");
        }

        @Override
        public DeleteResult deleteMany(final Bson filter, final DeleteOptions options) {
            throw unsupported("deleteMany");
        }

        @Override
        public UpdateResult replaceOne(final Bson filter, final TDocument replacement) {
            throw unsupported("replaceOne");
        }

        @Override
        public UpdateResult replaceOne(final Bson filter, final TDocument replacement, final UpdateOptions updateOptions) {
            throw unsupported("replaceOne");
        }

        @Override
        public UpdateResult updateOne(final Bson filter, final Bson update) {
            throw unsupported("updateOne");
        }

        @Override
        public UpdateResult updateOne(final Bson filter, final Bson update, final UpdateOptions updateOptions) {
            throw unsupported("updateOne");
        }

        @Override
        public UpdateResult updateMany(final Bson filter, final Bson update) {
            throw unsupported("updateMany");
        }

        @Override
        public UpdateResult updateMany(final Bson filter, final Bson update, final UpdateOptions updateOptions) {
            throw unsupported("updateMany");
        }

        @Override
        public TDocument findOneAndDelete(final Bson filter) {
            throw unsupported("findOneAndDelete");
        }

        @Override
        public TDocument findOneAndDelete(final Bson filter, final FindOneAndDeleteOptions options) {
            throw unsupported("findOneAndDelete");
        }

        @Override
        public TDocument findOneAndReplace(final Bson filter, final TDocument replacement) {
            throw unsupported("findOneAndReplace");
        }

        @Override
        public TDocument findOneAndReplace(final Bson filter, final TDocument replacement,
                final FindOneAndReplaceOptions options) {
            throw unsupported("findOneAndReplace");
        }

        @Override
        public TDocument findOneAndUpdate(final Bson filter, final Bson update) {
            throw unsupported("findOneAndUpdate");
        }

        @Override
        public TDocument findOneAndUpdate(final Bson filter, final Bson update, final FindOneAndUpdateOptions options) {
            throw unsupported("findOneAndUpdate");
        }

        @Override
        public void drop() {
            throw unsupported("drop");
        }

        @Override
        public String createIndex(final Bson keys) {
            return createIndex(keys, new IndexOptions());
        }

        @Override
        public String createIndex(final Bson keys, final IndexOptions options) {
            createdIndexes.add(new CreatedIndex(keys, options));
            return "created_index";
        }

        @Override
        public List<String> createIndexes(final List<IndexModel> indexes) {
            throw unsupported("createIndexes");
        }

        @Override
        public ListIndexesIterable<Document> listIndexes() {
            throw unsupported("listIndexes");
        }

        @Override
        public <TResult> ListIndexesIterable<TResult> listIndexes(final Class<TResult> resultClass) {
            throw unsupported("listIndexes");
        }

        @Override
        public void dropIndex(final String indexName) {
            throw unsupported("dropIndex");
        }

        @Override
        public void dropIndex(final Bson keys) {
            throw unsupported("dropIndex");
        }

        @Override
        public void dropIndexes() {
            throw unsupported("dropIndexes");
        }

        @Override
        public void renameCollection(final MongoNamespace newCollectionNamespace) {
            throw unsupported("renameCollection");
        }

        @Override
        public void renameCollection(final MongoNamespace newCollectionNamespace, final RenameCollectionOptions options) {
            throw unsupported("renameCollection");
        }
    }

    private static final class CreatedIndex {
        private final Bson keys;
        private final IndexOptions options;

        CreatedIndex(final Bson keys, final IndexOptions options) {
            this.keys = keys;
            this.options = options;
        }
    }
}

@Entity("annotation_builder_legacy_index_entities")
@Indexes(@Index(value = "legacyField", name = "legacy_index", unique = true, sparse = true))
class AnnotationBuilderLegacyIndexEntity {
    @Id
    private String id;
    private String legacyField;
}
