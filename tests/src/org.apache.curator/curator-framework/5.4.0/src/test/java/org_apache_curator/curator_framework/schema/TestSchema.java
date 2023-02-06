/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_framework.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.transaction.CuratorOp;
import org.apache.curator.framework.schema.Schema;
import org.apache.curator.framework.schema.SchemaSet;
import org.apache.curator.framework.schema.SchemaSetLoader;
import org.apache.curator.framework.schema.SchemaValidator;
import org.apache.curator.framework.schema.SchemaViolation;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.CreateMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class TestSchema extends BaseClassForTests {
    @Test
    public void testBasics() throws Exception {
        SchemaSet schemaSet = loadSchemaSet("schema1.json", null);
        Schema schema = schemaSet.getNamedSchema("test");
        assertNotNull(schema);
        Map<String, String> expectedMetadata = Maps.newHashMap();
        expectedMetadata.put("one", "1");
        expectedMetadata.put("two", "2");
        assertEquals(schema.getMetadata(), expectedMetadata);
        CuratorFramework client = newClient(schemaSet);
        try {
            client.start();
            try {
                String rawPath = schema.getRawPath();
                assertEquals(rawPath, "/a/b/c");
                client.create().creatingParentsIfNeeded().forPath(rawPath);
                fail("Should've violated schema");
            } catch (SchemaViolation ignored) {
            }
            client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath("/a/b/c");
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testSchemaValidator() throws Exception {
        final SchemaValidator schemaValidator = (schema, path, data, acl) -> data.length > 0;
        SchemaSetLoader.SchemaValidatorMapper schemaValidatorMapper = name -> schemaValidator;
        SchemaSet schemaSet = loadSchemaSet("schema3.json", schemaValidatorMapper);
        CuratorFramework client = newClient(schemaSet);
        try {
            client.start();
            try {
                client.create().forPath("/test", new byte[0]);
                fail("Should've violated schema");
            } catch (SchemaViolation ignored) {
            }
            client.create().forPath("/test", "good".getBytes());
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testMulti() throws Exception {
        SchemaSet schemaSet = loadSchemaSet("schema2.json", null);
        CuratorFramework client = newClient(schemaSet);
        try {
            client.start();
            try {
                client.create().creatingParentsIfNeeded().forPath("/a/b/c");
                fail("Should've violated schema: test");
            } catch (SchemaViolation ignored) {
            }
            try {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath("/a/b/c/d/e");
                fail("Should've violated schema: test2");
            } catch (SchemaViolation ignored) {
            }
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testTransaction() throws Exception {
        final SchemaValidator schemaValidator = (schema, path, data, acl) -> data.length > 0;
        SchemaSetLoader.SchemaValidatorMapper schemaValidatorMapper = name -> schemaValidator;
        SchemaSet schemaSet = loadSchemaSet("schema4.json", schemaValidatorMapper);
        CuratorFramework client = newClient(schemaSet);
        try {
            client.start();
            CuratorOp createAPersistent = client.transactionOp().create().forPath("/a");
            CuratorOp createAEphemeral = client.transactionOp().create().withMode(CreateMode.EPHEMERAL).forPath("/a");
            CuratorOp deleteA = client.transactionOp().delete().forPath("/a");
            CuratorOp createBEmptyData = client.transactionOp().create().forPath("/b", new byte[0]);
            CuratorOp createBWithData = client.transactionOp().create().forPath("/b", new byte[10]);
            CuratorOp setBEmptyData = client.transactionOp().setData().forPath("/b", new byte[0]);
            CuratorOp setBWithData = client.transactionOp().setData().forPath("/b", new byte[10]);
            try {
                client.transaction().forOperations(createAPersistent, createAEphemeral);
                fail("Should've violated schema");
            } catch (SchemaViolation ignored) {
            }
            client.transaction().forOperations(createAEphemeral);
            try {
                client.transaction().forOperations(deleteA);
                fail("Should've violated schema");
            } catch (SchemaViolation ignored) {
            }
            try {
                client.transaction().forOperations(createBEmptyData);
                fail("Should've violated schema");
            } catch (SchemaViolation ignored) {
            }
            client.transaction().forOperations(createBWithData);
            try {
                client.transaction().forOperations(setBEmptyData);
                fail("Should've violated schema");
            } catch (SchemaViolation ignored) {
            }
            client.transaction().forOperations(setBWithData);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testYaml() throws Exception {
        String yaml = Resources.toString(Resources.getResource("schema.yaml"), Charsets.UTF_8);
        JsonNode root = new ObjectMapper(new YAMLFactory()).readTree(yaml);
        List<Schema> schemas = new SchemaSetLoader(root, null).getSchemas();
        assertEquals(schemas.size(), 2);
        assertEquals(schemas.get(0).getName(), "test");
        assertEquals(schemas.get(0).getMetadata().size(), 0);
        assertEquals(schemas.get(1).getName(), "test2");
        assertEquals(schemas.get(1).getMetadata().size(), 2);
        assertEquals(schemas.get(1).getMetadata().get("two"), "2");
    }

    @Test
    public void testOrdering() throws Exception {
        SchemaSet schemaSet = loadSchemaSet("schema5.json", null);
        CuratorFramework client = newClient(schemaSet);
        try {
            client.start();
            try {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath("/exact/match");
                fail("Should've violated schema");
            } catch (SchemaViolation ignored) {
            }
            try {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath("/exact/foo/bar");
                fail("Should've violated schema");
            } catch (SchemaViolation ignored) {
            }
            try {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT_SEQUENTIAL).forPath("/exact/other/bar");
                fail("Should've violated schema");
            } catch (SchemaViolation ignored) {
            }
            client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT_SEQUENTIAL).forPath("/exact/match");
            client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath("/exact/other/thing");
            client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath("/exact/foo/bar");
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    private CuratorFramework newClient(SchemaSet schemaSet) {
        return CuratorFrameworkFactory.builder()
                .connectString(server.getConnectString())
                .retryPolicy(new RetryOneTime(1))
                .schemaSet(schemaSet)
                .build();
    }

    private SchemaSet loadSchemaSet(String name, SchemaSetLoader.SchemaValidatorMapper schemaValidatorMapper) throws IOException {
        String json = Resources.toString(Resources.getResource(name), Charsets.UTF_8);
        return new SchemaSetLoader(json, schemaValidatorMapper).toSchemaSet(true);
    }
}
