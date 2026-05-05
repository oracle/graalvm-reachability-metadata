/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.parser.core.ParsedNode;
import liquibase.parser.core.ParsedNodeException;
import liquibase.resource.ResourceAccessor;
import liquibase.serializer.LiquibaseSerializable;
import liquibase.serializer.core.yaml.YamlSerializer;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class YamlSerializerInnerLiquibaseRepresenterTest implements LiquibaseSerializable {

    private String serializedMessage;

    @Test
    void dumpsLiquibaseSerializableWithRepresenterDiscoveredProperties() {
        serializedMessage = "represented through LiquibaseSerializable properties";
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(new YamlSerializer.LiquibaseRepresenter(options), options);

        String document = yaml.dumpAsMap(this);

        assertThat(document)
                .contains("serializedMessage")
                .contains(serializedMessage);
    }

    @Override
    public String getSerializedObjectName() {
        return "representerTestObject";
    }

    @Override
    public Set<String> getSerializableFields() {
        return Set.of("serializedMessage");
    }

    @Override
    public Object getSerializableFieldValue(String field) {
        if ("serializedMessage".equals(field)) {
            return serializedMessage;
        }
        return null;
    }

    @Override
    public SerializationType getSerializableFieldType(String field) {
        return SerializationType.NAMED_FIELD;
    }

    @Override
    public String getSerializableFieldNamespace(String field) {
        return STANDARD_CHANGELOG_NAMESPACE;
    }

    @Override
    public String getSerializedObjectNamespace() {
        return STANDARD_CHANGELOG_NAMESPACE;
    }

    @Override
    public void load(ParsedNode parsedNode, ResourceAccessor resourceAccessor) throws ParsedNodeException {
        serializedMessage = parsedNode.getChildValue(null, "serializedMessage", String.class);
    }

    @Override
    public ParsedNode serialize() throws ParsedNodeException {
        return new ParsedNode(null, getSerializedObjectName())
                .addChild(null, "serializedMessage", serializedMessage);
    }
}
