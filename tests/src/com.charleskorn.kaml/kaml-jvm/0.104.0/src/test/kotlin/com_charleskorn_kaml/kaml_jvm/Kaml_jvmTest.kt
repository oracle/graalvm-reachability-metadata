/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com_charleskorn_kaml.kaml_jvm

import com.charleskorn.kaml.AmbiguousQuoteStyle
import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.DuplicateKeyException
import com.charleskorn.kaml.ForbiddenAnchorOrAliasException
import com.charleskorn.kaml.IncorrectTypeException
import com.charleskorn.kaml.MultiLineStringStyle
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.SequenceStyle
import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.UnknownPropertyException
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlComment
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlContentPolymorphicSerializer
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlMultiLineStringStyle
import com.charleskorn.kaml.YamlNamingStrategy
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalarFormatException
import com.charleskorn.kaml.YamlSingleLineStringStyle
import com.charleskorn.kaml.yamlMap
import com.charleskorn.kaml.yamlNull
import com.charleskorn.kaml.yamlScalar
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class Kaml_jvmTest {
    @Test
    fun parsesYamlNodesWithLocationsScalarsListsAndMaps(): Unit {
        val node: YamlNode = Yaml.default.parseToYamlNode(
            """
            name: kaml
            enabled: TRUE
            retries: 0x10
            thresholds:
              - 0o7
              - .inf
              - no
            nested:
              child: value
            """.trimIndent()
        )

        val root: YamlMap = node.yamlMap

        assertThat(root.getScalar("name")?.content).isEqualTo("kaml")
        assertThat(root.getScalar("enabled")?.toBoolean()).isTrue()
        assertThat(root.getScalar("retries")?.toInt()).isEqualTo(16)
        assertThat(root.getKey("nested")?.location?.line).isEqualTo(8)
        assertThat(root.path.toHumanReadableString()).isEqualTo("<root>")

        val thresholds: YamlList = root["thresholds"] ?: error("thresholds should be present")
        assertThat(thresholds.items).hasSize(3)
        assertThat(thresholds[0].yamlScalar.toInt()).isEqualTo(7)
        assertThat(thresholds[1].yamlScalar.toDouble()).isEqualTo(Double.POSITIVE_INFINITY)
        assertThat(thresholds[2].yamlScalar.content).isEqualTo("no")
        assertThat(thresholds[2].path.toHumanReadableString()).isEqualTo("thresholds[2]")

        val equivalent: YamlNode = Yaml.default.parseToYamlNode("{ enabled: TRUE, name: kaml, retries: 16, thresholds: [7, .inf, no], nested: { child: value } }")
        assertThat(root.equivalentContentTo(equivalent)).isFalse()
        assertThat(root.contentToString()).contains("'name': 'kaml'")
    }

    @Test
    fun decodesAndEncodesPrimitiveCollectionsWithBuiltInSerializers(): Unit {
        val numbersSerializer: KSerializer<List<Int>> = ListSerializer(Int.serializer())
        val mapSerializer: KSerializer<Map<String, List<Int>>> = MapSerializer(String.serializer(), numbersSerializer)
        val yaml: Yaml = Yaml(
            configuration = YamlConfiguration(
                sequenceStyle = SequenceStyle.Flow,
                singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous,
                ambiguousQuoteStyle = AmbiguousQuoteStyle.SingleQuoted
            )
        )
        val input: String = """
            alpha: [1, 2, 3]
            beta:
              - 0x10
              - 0o10
            """.trimIndent()

        val decoded: Map<String, List<Int>> = yaml.decodeFromString(mapSerializer, input)
        val encoded: String = yaml.encodeToString(mapSerializer, decoded)

        assertThat(decoded).isEqualTo(linkedMapOf("alpha" to listOf(1, 2, 3), "beta" to listOf(16, 8)))
        assertThat(encoded).contains("alpha: [1, 2, 3]")
        assertThat(encoded).contains("beta: [16, 8]")
        assertThat(yaml.decodeFromString(mapSerializer, encoded)).isEqualTo(decoded)
    }

    @Test
    fun classSerializersSupportDefaultsNamingStylesAndComments(): Unit {
        val yaml: Yaml = Yaml(
            configuration = YamlConfiguration(
                encodeDefaults = false,
                yamlNamingStrategy = YamlNamingStrategy.KebabCase,
                singleLineStringStyle = SingleLineStringStyle.Plain,
                multiLineStringStyle = MultiLineStringStyle.Literal
            )
        )
        val project: Project = Project(
            displayName = "Native metadata",
            retryCount = DEFAULT_RETRY_COUNT,
            enabled = true,
            notes = "first line\nsecond line"
        )

        val encoded: String = yaml.encodeToString(ProjectSerializer, project)
        val decoded: Project = yaml.decodeFromString(ProjectSerializer, encoded)

        assertThat(encoded).contains("# User visible project name")
        assertThat(encoded).contains("display-name: 'Native metadata'")
        assertThat(encoded).doesNotContain("retry-count")
        assertThat(encoded).contains("notes: |")
        assertThat(decoded).isEqualTo(project)

        val explicitRetry: Project = yaml.decodeFromString(
            ProjectSerializer,
            """
            display-name: Agent test
            retry-count: 5
            enabled: false
            notes: one line
            """.trimIndent()
        )
        assertThat(explicitRetry).isEqualTo(
            Project(displayName = "Agent test", retryCount = 5, enabled = false, notes = "one line")
        )
    }

    @Test
    fun strictModeRejectsUnknownPropertiesAndLenientModeIgnoresThem(): Unit {
        val input: String = """
            display-name: Strict project
            retry-count: 2
            enabled: true
            notes: checked
            unexpected: ignored only in lenient mode
            """.trimIndent()
        val strictYaml: Yaml = Yaml(configuration = YamlConfiguration(yamlNamingStrategy = YamlNamingStrategy.KebabCase))
        val lenientYaml: Yaml = Yaml(
            configuration = YamlConfiguration(strictMode = false, yamlNamingStrategy = YamlNamingStrategy.KebabCase)
        )

        assertThatThrownBy { strictYaml.decodeFromString(ProjectSerializer, input) }
            .isInstanceOf(UnknownPropertyException::class.java)
            .hasMessageContaining("unexpected")

        assertThat(lenientYaml.decodeFromString(ProjectSerializer, input)).isEqualTo(
            Project(displayName = "Strict project", retryCount = 2, enabled = true, notes = "checked")
        )
    }

    @Test
    fun configuredPolymorphismSupportsYamlTagsAndTypeProperties(): Unit {
        val module: SerializersModule = SerializersModule {
            polymorphic(DeploymentAction::class) {
                subclass(ScaleDeployment::class, ScaleDeploymentSerializer)
                subclass(RestartDeployment::class, RestartDeploymentSerializer)
            }
        }
        val actionsSerializer: KSerializer<List<DeploymentAction>> = ListSerializer(PolymorphicSerializer(DeploymentAction::class))
        val actions: List<DeploymentAction> = listOf(
            ScaleDeployment(replicas = 4),
            RestartDeployment(service = "api")
        )

        val tagYaml: Yaml = Yaml(serializersModule = module)
        val tagEncoded: String = tagYaml.encodeToString(actionsSerializer, actions)
        val propertyYaml: Yaml = Yaml(
            serializersModule = module,
            configuration = YamlConfiguration(
                polymorphismStyle = PolymorphismStyle.Property,
                polymorphismPropertyName = "kind"
            )
        )
        val propertyEncoded: String = propertyYaml.encodeToString(actionsSerializer, actions)

        assertThat(tagEncoded).contains("!<tests.ScaleDeployment>")
        assertThat(tagYaml.decodeFromString(actionsSerializer, tagEncoded)).isEqualTo(actions)
        assertThat(propertyEncoded).contains("kind: \"tests.ScaleDeployment\"")
        assertThat(propertyEncoded).contains("kind: \"tests.RestartDeployment\"")
        assertThat(propertyYaml.decodeFromString(actionsSerializer, propertyEncoded)).isEqualTo(actions)
    }

    @Test
    fun contentPolymorphicSerializerChoosesDeserializerFromYamlNodeShape(): Unit {
        val valuesSerializer: KSerializer<List<SettingValue>> = ListSerializer(SettingValueSerializer)
        val input: String = """
            - flag: true
            - text: hello
            - text: 123
            """.trimIndent()

        val values: List<SettingValue> = Yaml.default.decodeFromString(valuesSerializer, input)

        assertThat(values).containsExactly(
            BooleanSetting(enabled = true),
            TextSetting(text = "hello"),
            TextSetting(text = "123")
        )
    }

    @Test
    fun nullValuesRoundTripThroughYamlNullNodesAndNullableSerializers(): Unit {
        val input: String = """
            explicit: null
            empty:
            text: value
            """.trimIndent()
        val root: YamlMap = Yaml.default.parseToYamlNode(input).yamlMap
        val explicitNode: YamlNode = root["explicit"] ?: error("explicit should be present")
        val emptyNode: YamlNode = root["empty"] ?: error("empty should be present")
        val explicitNull: YamlNull = explicitNode.yamlNull
        val emptyNull: YamlNull = emptyNode.yamlNull
        val serializer: KSerializer<Map<String, String?>> = MapSerializer(String.serializer(), String.serializer().nullable)

        val decoded: Map<String, String?> = Yaml.default.decodeFromString(serializer, input)
        val encoded: String = Yaml.default.encodeToString(serializer, decoded)

        assertThat(explicitNull.path.toHumanReadableString()).isEqualTo("explicit")
        assertThat(emptyNull.path.toHumanReadableString()).isEqualTo("empty")
        assertThat(explicitNull.contentToString()).isEqualTo("null")
        assertThat(root.getScalar("text")?.content).isEqualTo("value")
        assertThat(decoded).isEqualTo(linkedMapOf("explicit" to null, "empty" to null, "text" to "value"))
        assertThat(Yaml.default.decodeFromString(serializer, encoded)).isEqualTo(decoded)
    }

    @Test
    fun sourceAndSinkApisRoundTripThroughOkioBuffers(): Unit {
        val serializer: KSerializer<Map<String, Int>> = MapSerializer(String.serializer(), Int.serializer())
        val yaml: Yaml = Yaml(configuration = YamlConfiguration(sequenceStyle = SequenceStyle.Block))
        val sink: Buffer = Buffer()
        val input: Map<String, Int> = linkedMapOf("first" to 1, "second" to 2)

        yaml.encodeToSink(serializer, input, sink)
        val encoded: String = sink.clone().readUtf8().trimEnd()
        val source: Buffer = Buffer().writeUtf8(encoded)
        val decoded: Map<String, Int> = yaml.decodeFromSource(serializer, source)

        assertThat(encoded).contains("\"first\": 1")
        assertThat(encoded).contains("\"second\": 2")
        assertThat(decoded).isEqualTo(input)
    }

    @Test
    fun yamlNodeApiCanDecodeFromParsedNodesAndRetainEquivalentContent(): Unit {
        val original: YamlNode = Yaml.default.parseToYamlNode(
            """
            displayName: Node backed
            retryCount: 4
            enabled: true
            notes: parsed first
            """.trimIndent()
        )
        val relocated: YamlNode = original.withPath(original.path)

        val decoded: Project = Yaml.default.decodeFromYamlNode(ProjectSerializer, relocated)

        assertThat(decoded).isEqualTo(Project(displayName = "Node backed", retryCount = 4, enabled = true, notes = "parsed first"))
        assertThat(original.equivalentContentTo(relocated)).isTrue()
        assertThat(relocated.yamlMap.getScalar("enabled")?.toBoolean()).isTrue()
    }

    @Test
    fun anchorsAliasesDuplicateKeysAndTypeErrorsReportPublicExceptions(): Unit {
        val anchoredInput: String = """
            base: &base
              url: https://example.test
              retries: 3
            copy: *base
            """.trimIndent()

        assertThatThrownBy { Yaml.default.parseToYamlNode(anchoredInput) }
            .isInstanceOf(ForbiddenAnchorOrAliasException::class.java)

        val aliasesYaml: Yaml = Yaml(configuration = YamlConfiguration(anchorsAndAliases = AnchorsAndAliases.Permitted(maxAliasCount = 2u)))
        val anchoredMap: YamlMap = aliasesYaml.parseToYamlNode(anchoredInput).yamlMap
        val base: YamlMap = anchoredMap["base"] ?: error("base should be present")
        val copy: YamlMap = anchoredMap["copy"] ?: error("copy should be present")
        assertThat(copy.equivalentContentTo(base)).isTrue()
        assertThat(copy.getScalar("retries")?.toInt()).isEqualTo(3)

        assertThatThrownBy { Yaml.default.parseToYamlNode("name: one\nname: two") }
            .isInstanceOf(DuplicateKeyException::class.java)
            .hasMessageContaining("Duplicate key 'name'")

        val mixedNode: YamlMap = Yaml.default.parseToYamlNode("items: [1, 2]\nanswer: not-a-number").yamlMap
        assertThatThrownBy { mixedNode.getScalar("items") }
            .isInstanceOf(IncorrectTypeException::class.java)
            .hasMessageContaining("items")
        assertThatThrownBy { mixedNode.getScalar("answer")?.toInt() }
            .isInstanceOf(YamlScalarFormatException::class.java)
            .hasMessageContaining("not-a-number")
    }
}

private const val DEFAULT_RETRY_COUNT: Int = 3

private data class Project(
    val displayName: String,
    val retryCount: Int = DEFAULT_RETRY_COUNT,
    val enabled: Boolean,
    val notes: String
)

private object ProjectSerializer : KSerializer<Project> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tests.Project") {
        element<String>(
            "displayName",
            annotations = listOf(
                YamlComment("User visible project name"),
                YamlSingleLineStringStyle(SingleLineStringStyle.SingleQuoted)
            )
        )
        element<Int>("retryCount")
        element<Boolean>("enabled")
        element<String>("notes", annotations = listOf(YamlMultiLineStringStyle(MultiLineStringStyle.Literal)))
    }

    override fun serialize(encoder: Encoder, value: Project): Unit = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.displayName)
        if (value.retryCount != DEFAULT_RETRY_COUNT || shouldEncodeElementDefault(descriptor, 1)) {
            encodeIntElement(descriptor, 1, value.retryCount)
        }
        encodeBooleanElement(descriptor, 2, value.enabled)
        encodeStringElement(descriptor, 3, value.notes)
    }

    override fun deserialize(decoder: Decoder): Project = decoder.decodeStructure(descriptor) {
        var displayName: String? = null
        var retryCount: Int = DEFAULT_RETRY_COUNT
        var enabled: Boolean? = null
        var notes: String? = null

        while (true) {
            when (val index: Int = decodeElementIndex(descriptor)) {
                0 -> displayName = decodeStringElement(descriptor, 0)
                1 -> retryCount = decodeIntElement(descriptor, 1)
                2 -> enabled = decodeBooleanElement(descriptor, 2)
                3 -> notes = decodeStringElement(descriptor, 3)
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected element index $index")
            }
        }

        Project(
            displayName = requireNotNull(displayName) { "displayName is required" },
            retryCount = retryCount,
            enabled = requireNotNull(enabled) { "enabled is required" },
            notes = requireNotNull(notes) { "notes is required" }
        )
    }
}

private sealed interface DeploymentAction

private data class ScaleDeployment(val replicas: Int) : DeploymentAction

private data class RestartDeployment(val service: String) : DeploymentAction

private object ScaleDeploymentSerializer : KSerializer<ScaleDeployment> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tests.ScaleDeployment") {
        element<Int>("replicas")
    }

    override fun serialize(encoder: Encoder, value: ScaleDeployment): Unit = encoder.encodeStructure(descriptor) {
        encodeIntElement(descriptor, 0, value.replicas)
    }

    override fun deserialize(decoder: Decoder): ScaleDeployment = decoder.decodeStructure(descriptor) {
        var replicas: Int? = null
        while (true) {
            when (val index: Int = decodeElementIndex(descriptor)) {
                0 -> replicas = decodeIntElement(descriptor, 0)
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected element index $index")
            }
        }
        ScaleDeployment(replicas = requireNotNull(replicas) { "replicas is required" })
    }
}

private object RestartDeploymentSerializer : KSerializer<RestartDeployment> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tests.RestartDeployment") {
        element<String>("service")
    }

    override fun serialize(encoder: Encoder, value: RestartDeployment): Unit = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.service)
    }

    override fun deserialize(decoder: Decoder): RestartDeployment = decoder.decodeStructure(descriptor) {
        var service: String? = null
        while (true) {
            when (val index: Int = decodeElementIndex(descriptor)) {
                0 -> service = decodeStringElement(descriptor, 0)
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected element index $index")
            }
        }
        RestartDeployment(service = requireNotNull(service) { "service is required" })
    }
}

private sealed interface SettingValue

private data class BooleanSetting(val enabled: Boolean) : SettingValue

private data class TextSetting(val text: String) : SettingValue

private object SettingValueSerializer : YamlContentPolymorphicSerializer<SettingValue>(SettingValue::class) {
    override fun selectDeserializer(node: YamlNode): DeserializationStrategy<SettingValue> {
        val map: YamlMap = node.yamlMap
        return when {
            map.getKey("flag") != null -> BooleanSettingSerializer
            map.getKey("text") != null -> TextSettingSerializer
            else -> error("Unknown setting shape: ${node.contentToString()}")
        }
    }
}

private object BooleanSettingSerializer : KSerializer<SettingValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tests.BooleanSetting") {
        element<Boolean>("flag")
    }

    override fun serialize(encoder: Encoder, value: SettingValue): Unit = encoder.encodeStructure(descriptor) {
        val setting: BooleanSetting = value as BooleanSetting
        encodeBooleanElement(descriptor, 0, setting.enabled)
    }

    override fun deserialize(decoder: Decoder): SettingValue = decoder.decodeStructure(descriptor) {
        var enabled: Boolean? = null
        while (true) {
            when (val index: Int = decodeElementIndex(descriptor)) {
                0 -> enabled = decodeBooleanElement(descriptor, 0)
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected element index $index")
            }
        }
        BooleanSetting(requireNotNull(enabled) { "flag is required" })
    }
}

private object TextSettingSerializer : KSerializer<SettingValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tests.TextSetting") {
        element("text", TextAsStringSerializer.descriptor)
    }

    override fun serialize(encoder: Encoder, value: SettingValue): Unit = encoder.encodeStructure(descriptor) {
        val setting: TextSetting = value as TextSetting
        encodeSerializableElement(descriptor, 0, TextAsStringSerializer, setting.text)
    }

    override fun deserialize(decoder: Decoder): SettingValue = decoder.decodeStructure(descriptor) {
        var text: String? = null
        while (true) {
            when (val index: Int = decodeElementIndex(descriptor)) {
                0 -> text = decodeSerializableElement(descriptor, 0, TextAsStringSerializer)
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected element index $index")
            }
        }
        TextSetting(requireNotNull(text) { "text is required" })
    }
}

private object TextAsStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("tests.TextAsString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String): Unit {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String = decoder.decodeString()
}
