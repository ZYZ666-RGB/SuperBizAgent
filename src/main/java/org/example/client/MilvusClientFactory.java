package org.example.client;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CollectionSchemaParam;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import org.example.config.MilvusProperties;
import org.example.constant.MilvusConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class MilvusClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(MilvusClientFactory.class);

    @Autowired
    private MilvusProperties milvusProperties;

    public MilvusServiceClient createClient() {
        MilvusServiceClient client = null;

        try {
            logger.info("Connecting to Milvus: {}:{}", milvusProperties.getHost(), milvusProperties.getPort());
            client = connectToMilvus();
            logger.info("Connected to Milvus");

            ensureBizCollection(client);
            ensureUserMemoryCollection(client);

            return client;
        } catch (Exception e) {
            logger.error("Failed to create Milvus client", e);
            if (client != null) {
                client.close();
            }
            throw new RuntimeException("Failed to create Milvus client: " + e.getMessage(), e);
        }
    }

    private MilvusServiceClient connectToMilvus() {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(milvusProperties.getHost())
                .withPort(milvusProperties.getPort())
                .withConnectTimeout(milvusProperties.getTimeout(), TimeUnit.MILLISECONDS);

        if (milvusProperties.getUsername() != null && !milvusProperties.getUsername().isEmpty()) {
            builder.withAuthorization(milvusProperties.getUsername(), milvusProperties.getPassword());
        }

        return new MilvusServiceClient(builder.build());
    }

    private void ensureBizCollection(MilvusServiceClient client) {
        if (collectionExists(client, MilvusConstants.MILVUS_COLLECTION_NAME)) {
            logger.info("Milvus collection '{}' already exists", MilvusConstants.MILVUS_COLLECTION_NAME);
            return;
        }

        logger.info("Creating Milvus collection '{}'", MilvusConstants.MILVUS_COLLECTION_NAME);
        createBizCollection(client);
        createVectorIndex(client, MilvusConstants.MILVUS_COLLECTION_NAME, MetricType.L2);
    }

    private void ensureUserMemoryCollection(MilvusServiceClient client) {
        if (collectionExists(client, MilvusConstants.USER_MEMORY_COLLECTION_NAME)) {
            logger.info("Milvus collection '{}' already exists", MilvusConstants.USER_MEMORY_COLLECTION_NAME);
            return;
        }

        logger.info("Creating Milvus collection '{}'", MilvusConstants.USER_MEMORY_COLLECTION_NAME);
        createUserMemoryCollection(client);
        createVectorIndex(client, MilvusConstants.USER_MEMORY_COLLECTION_NAME, MetricType.COSINE);
    }

    private boolean collectionExists(MilvusServiceClient client, String collectionName) {
        R<Boolean> response = client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());

        if (response.getStatus() != 0) {
            throw new RuntimeException("Failed to check collection: " + response.getMessage());
        }

        return response.getData();
    }

    private void createBizCollection(MilvusServiceClient client) {
        FieldType idField = FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.VarChar)
                .withMaxLength(MilvusConstants.ID_MAX_LENGTH)
                .withPrimaryKey(true)
                .build();

        FieldType vectorField = FieldType.newBuilder()
                .withName("vector")
                .withDataType(DataType.FloatVector)
                .withDimension(MilvusConstants.VECTOR_DIM)
                .build();

        FieldType contentField = FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(MilvusConstants.CONTENT_MAX_LENGTH)
                .build();

        FieldType metadataField = FieldType.newBuilder()
                .withName("metadata")
                .withDataType(DataType.JSON)
                .build();

        CollectionSchemaParam schema = CollectionSchemaParam.newBuilder()
                .withEnableDynamicField(false)
                .addFieldType(idField)
                .addFieldType(vectorField)
                .addFieldType(contentField)
                .addFieldType(metadataField)
                .build();

        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withDescription("Business knowledge collection")
                .withSchema(schema)
                .withShardsNum(MilvusConstants.DEFAULT_SHARD_NUMBER)
                .build();

        R<RpcStatus> response = client.createCollection(createParam);
        if (response.getStatus() != 0) {
            throw new RuntimeException("Failed to create biz collection: " + response.getMessage());
        }
    }

    private void createUserMemoryCollection(MilvusServiceClient client) {
        CollectionSchemaParam schema = CollectionSchemaParam.newBuilder()
                .withEnableDynamicField(false)
                .addFieldType(FieldType.newBuilder()
                        .withName("memory_id")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(MilvusConstants.MEMORY_FIELD_MAX_LENGTH)
                        .withPrimaryKey(true)
                        .build())
                .addFieldType(varcharField("user_id", 64))
                .addFieldType(varcharField("session_id", 64))
                .addFieldType(varcharField("task_id", 64))
                .addFieldType(varcharField("agent_id", 64))
                .addFieldType(varcharField("app_id", 64))
                .addFieldType(varcharField("memory_type", 30))
                .addFieldType(varcharField("scope_type", 30))
                .addFieldType(FieldType.newBuilder()
                        .withName("content")
                        .withDataType(DataType.VarChar)
                        .withMaxLength(MilvusConstants.CONTENT_MAX_LENGTH)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName("importance")
                        .withDataType(DataType.Double)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName("metadata")
                        .withDataType(DataType.JSON)
                        .build())
                .addFieldType(varcharField("created_at", 64))
                .addFieldType(FieldType.newBuilder()
                        .withName("enabled")
                        .withDataType(DataType.Bool)
                        .build())
                .addFieldType(FieldType.newBuilder()
                        .withName("vector")
                        .withDataType(DataType.FloatVector)
                        .withDimension(MilvusConstants.VECTOR_DIM)
                        .build())
                .build();

        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(MilvusConstants.USER_MEMORY_COLLECTION_NAME)
                .withDescription("User long-term memory collection")
                .withSchema(schema)
                .withShardsNum(MilvusConstants.DEFAULT_SHARD_NUMBER)
                .build();

        R<RpcStatus> response = client.createCollection(createParam);
        if (response.getStatus() != 0) {
            throw new RuntimeException("Failed to create user memory collection: " + response.getMessage());
        }
    }

    private FieldType varcharField(String name, int maxLength) {
        return FieldType.newBuilder()
                .withName(name)
                .withDataType(DataType.VarChar)
                .withMaxLength(maxLength)
                .build();
    }

    private void createVectorIndex(MilvusServiceClient client, String collectionName, MetricType metricType) {
        CreateIndexParam vectorIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName("vector")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(metricType)
                .withExtraParam("{\"nlist\":128}")
                .withSyncMode(Boolean.FALSE)
                .build();

        R<RpcStatus> response = client.createIndex(vectorIndexParam);
        if (response.getStatus() != 0) {
            throw new RuntimeException("Failed to create vector index: " + response.getMessage());
        }
        logger.info("Created vector index for collection '{}'", collectionName);
    }
}
