/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.pulsar.client.impl;

import com.google.protobuf.Message;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.pulsar.client.api.Authentication;
import org.apache.pulsar.client.api.BatcherBuilder;
import org.apache.pulsar.client.api.ClientBuilder;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.MessagePayloadFactory;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.PulsarClientSharedResourcesBuilder;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.TopicMessageId;
import org.apache.pulsar.client.api.schema.GenericRecord;
import org.apache.pulsar.client.api.schema.GenericSchema;
import org.apache.pulsar.client.api.schema.RecordSchemaBuilder;
import org.apache.pulsar.client.api.schema.SchemaDefinition;
import org.apache.pulsar.client.api.schema.SchemaDefinitionBuilder;
import org.apache.pulsar.client.internal.PulsarClientImplementationBinding;
import org.apache.pulsar.common.schema.KeyValue;
import org.apache.pulsar.common.schema.KeyValueEncodingType;
import org.apache.pulsar.common.schema.SchemaInfo;
import org.apache.pulsar.common.schema.SchemaInfoWithVersion;
import org.apache.pulsar.common.schema.SchemaType;

/**
 * Test implementation loaded by {@link org.apache.pulsar.client.internal.DefaultImplementation}.
 */
public class PulsarClientImplementationBindingImpl implements PulsarClientImplementationBinding {
    @Override
    public <T> SchemaDefinitionBuilder<T> newSchemaDefinitionBuilder() {
        throw unsupportedOperation();
    }

    @Override
    public ClientBuilder newClientBuilder() {
        throw unsupportedOperation();
    }

    @Override
    public MessageId newMessageId(long ledgerId, long entryId, int partitionIndex) {
        throw unsupportedOperation();
    }

    @Override
    public MessageId newMessageIdFromByteArray(byte[] data) throws IOException {
        throw unsupportedOperation();
    }

    @Override
    public MessageId newMessageIdFromByteArrayWithTopic(byte[] data, String topicName) throws IOException {
        throw unsupportedOperation();
    }

    @Override
    public Authentication newAuthenticationToken(String token) {
        throw unsupportedOperation();
    }

    @Override
    public Authentication newAuthenticationToken(Supplier<String> supplier) {
        throw unsupportedOperation();
    }

    @Override
    public Authentication newAuthenticationTLS(String certFilePath, String keyFilePath) {
        throw unsupportedOperation();
    }

    @Override
    public Authentication createAuthentication(String authPluginClassName, String authParamsString)
            throws PulsarClientException.UnsupportedAuthenticationException {
        throw unsupportedOperation();
    }

    @Override
    public Authentication createAuthentication(String authPluginClassName, Map<String, String> authParams)
            throws PulsarClientException.UnsupportedAuthenticationException {
        throw unsupportedOperation();
    }

    @Override
    public Schema<byte[]> newBytesSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<String> newStringSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<String> newStringSchema(Charset charset) {
        throw unsupportedOperation();
    }

    @Override
    public Schema<Byte> newByteSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<Short> newShortSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<Integer> newIntSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<Long> newLongSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<Boolean> newBooleanSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<ByteBuffer> newByteBufferSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<Float> newFloatSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<Double> newDoubleSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<Date> newDateSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<Time> newTimeSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<Timestamp> newTimestampSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<Instant> newInstantSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<LocalDate> newLocalDateSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<LocalTime> newLocalTimeSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<LocalDateTime> newLocalDateTimeSchema() {
        throw unsupportedOperation();
    }

    @Override
    public <T> Schema<T> newAvroSchema(SchemaDefinition schemaDefinition) {
        throw unsupportedOperation();
    }

    @Override
    public <T extends Message> Schema<T> newProtobufSchema(SchemaDefinition schemaDefinition) {
        throw unsupportedOperation();
    }

    @Override
    public <T extends Message> Schema<T> newProtobufNativeSchema(SchemaDefinition schemaDefinition) {
        throw unsupportedOperation();
    }

    @Override
    public <T> Schema<T> newJSONSchema(SchemaDefinition schemaDefinition) {
        throw unsupportedOperation();
    }

    @Override
    public Schema<GenericRecord> newAutoConsumeSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<byte[]> newAutoProduceSchema() {
        throw unsupportedOperation();
    }

    @Override
    public Schema<byte[]> newAutoProduceSchema(Schema<?> schema) {
        throw unsupportedOperation();
    }

    @Override
    public Schema<byte[]> newAutoProduceValidatedAvroSchema(Object schema) {
        throw unsupportedOperation();
    }

    @Override
    public Schema<KeyValue<byte[], byte[]>> newKeyValueBytesSchema() {
        throw unsupportedOperation();
    }

    @Override
    public <K, V> Schema<KeyValue<K, V>> newKeyValueSchema(Schema<K> keySchema, Schema<V> valueSchema,
            KeyValueEncodingType keyValueEncodingType) {
        throw unsupportedOperation();
    }

    @Override
    public <K, V> Schema<KeyValue<K, V>> newKeyValueSchema(Class<K> key, Class<V> value, SchemaType type) {
        throw unsupportedOperation();
    }

    @Override
    public Schema<?> getSchema(SchemaInfo schemaInfo) {
        throw unsupportedOperation();
    }

    @Override
    public GenericSchema<GenericRecord> getGenericSchema(SchemaInfo schemaInfo) {
        throw unsupportedOperation();
    }

    @Override
    public RecordSchemaBuilder newRecordSchemaBuilder(String name) {
        throw unsupportedOperation();
    }

    @Override
    public KeyValueEncodingType decodeKeyValueEncodingType(SchemaInfo schemaInfo) {
        throw unsupportedOperation();
    }

    @Override
    public <K, V> SchemaInfo encodeKeyValueSchemaInfo(Schema<K> keySchema, Schema<V> valueSchema,
            KeyValueEncodingType keyValueEncodingType) {
        throw unsupportedOperation();
    }

    @Override
    public <K, V> SchemaInfo encodeKeyValueSchemaInfo(String schemaName, Schema<K> keySchema,
            Schema<V> valueSchema, KeyValueEncodingType keyValueEncodingType) {
        throw unsupportedOperation();
    }

    @Override
    public KeyValue<SchemaInfo, SchemaInfo> decodeKeyValueSchemaInfo(SchemaInfo schemaInfo) {
        throw unsupportedOperation();
    }

    @Override
    public String jsonifySchemaInfo(SchemaInfo schemaInfo) {
        throw unsupportedOperation();
    }

    @Override
    public String jsonifySchemaInfoWithVersion(SchemaInfoWithVersion schemaInfoWithVersion) {
        throw unsupportedOperation();
    }

    @Override
    public String jsonifyKeyValueSchemaInfo(KeyValue<SchemaInfo, SchemaInfo> kvSchemaInfo) {
        throw unsupportedOperation();
    }

    @Override
    public String convertKeyValueSchemaInfoDataToString(KeyValue<SchemaInfo, SchemaInfo> kvSchemaInfo)
            throws IOException {
        throw unsupportedOperation();
    }

    @Override
    public byte[] convertKeyValueDataStringToSchemaInfoSchema(byte[] keyValueSchemaInfoDataJsonBytes)
            throws IOException {
        throw unsupportedOperation();
    }

    @Override
    public BatcherBuilder newDefaultBatcherBuilder() {
        throw unsupportedOperation();
    }

    @Override
    public BatcherBuilder newKeyBasedBatcherBuilder() {
        throw unsupportedOperation();
    }

    @Override
    public MessagePayloadFactory newDefaultMessagePayloadFactory() {
        throw unsupportedOperation();
    }

    @Override
    public SchemaInfo newSchemaInfoImpl(String name, byte[] schema, SchemaType type, long timestamp,
            Map<String, String> propertiesValue) {
        throw unsupportedOperation();
    }

    @Override
    public TopicMessageId newTopicMessageId(String topic, MessageId messageId) {
        throw unsupportedOperation();
    }

    @Override
    public PulsarClientSharedResourcesBuilder newSharedResourcesBuilder() {
        throw unsupportedOperation();
    }

    private static UnsupportedOperationException unsupportedOperation() {
        return new UnsupportedOperationException("Only DefaultImplementation construction is tested");
    }
}
