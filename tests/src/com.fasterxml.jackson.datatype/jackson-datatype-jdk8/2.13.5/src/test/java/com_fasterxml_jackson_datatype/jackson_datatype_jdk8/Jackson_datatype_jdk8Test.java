/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_datatype.jackson_datatype_jdk8;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class Jackson_datatype_jdk8Test {
    @Test
    void serializesAndDeserializesPresentOptionalProperties() throws Exception {
        ObjectMapper mapper = jdk8Mapper();
        OptionalValues values = OptionalValues.presentSample();

        String json = mapper.writeValueAsString(values);

        JsonNode tree = mapper.readTree(json);
        assertThat(tree.get("name").asText()).isEqualTo("Ada");
        assertThat(tree.get("score").asInt()).isEqualTo(42);
        assertThat(tree.get("visits").asLong()).isEqualTo(1_234_567_890_123L);
        assertThat(tree.get("ratio").asDouble()).isEqualTo(0.625D);
        assertThat(tree.at("/address/city").asText()).isEqualTo("London");

        OptionalValues restored = mapper.readValue(json, OptionalValues.class);

        assertThat(restored.name).contains("Ada");
        assertThat(restored.score).hasValue(42);
        assertThat(restored.visits).hasValue(1_234_567_890_123L);
        assertThat(restored.ratio).hasValue(0.625D);
        assertThat(restored.address).contains(new Address("London", "SW1A"));
    }

    @Test
    void representsEmptyOptionalValuesAsJsonNullsAndReadsJsonNullAsEmpty() throws Exception {
        ObjectMapper mapper = jdk8Mapper();
        OptionalValues values = OptionalValues.emptySample();

        String json = mapper.writeValueAsString(values);

        JsonNode tree = mapper.readTree(json);
        assertThat(tree.get("name").isNull()).isTrue();
        assertThat(tree.get("score").isNull()).isTrue();
        assertThat(tree.get("visits").isNull()).isTrue();
        assertThat(tree.get("ratio").isNull()).isTrue();
        assertThat(tree.get("address").isNull()).isTrue();

        OptionalValues restored = mapper.readValue(
                "{\"name\":null,\"score\":null,\"visits\":null,\"ratio\":null,\"address\":null}",
                OptionalValues.class);

        assertThat(restored.name).isEmpty();
        assertThat(restored.score).isEmpty();
        assertThat(restored.visits).isEmpty();
        assertThat(restored.ratio).isEmpty();
        assertThat(restored.address).isEmpty();
    }

    @Test
    void writesAndReadsRootOptionalValues() throws Exception {
        ObjectMapper mapper = jdk8Mapper();
        TypeReference<Optional<Address>> optionalAddressType = new TypeReference<Optional<Address>>() { };

        String presentJson = mapper.writeValueAsString(Optional.of(new Address("Paris", "75001")));
        String emptyJson = mapper.writeValueAsString(Optional.empty());

        assertThat(mapper.readTree(presentJson).get("city").asText()).isEqualTo("Paris");
        assertThat(emptyJson).isEqualTo("null");
        assertThat(mapper.readValue(presentJson, optionalAddressType)).contains(new Address("Paris", "75001"));
        assertThat(mapper.readValue(emptyJson, optionalAddressType)).isEmpty();
    }

    @Test
    void excludesAbsentValuesWhenUsingNonAbsentInclusion() throws Exception {
        ObjectMapper mapper = jdk8Mapper();
        NonAbsentValues values = new NonAbsentValues();
        values.title = Optional.of("visible");
        values.missingTitle = Optional.empty();
        values.count = OptionalInt.of(7);
        values.missingCount = OptionalInt.empty();
        values.directNull = null;

        String json = mapper.writeValueAsString(values);

        JsonNode tree = mapper.readTree(json);
        assertThat(tree.has("title")).isTrue();
        assertThat(tree.get("title").asText()).isEqualTo("visible");
        assertThat(tree.has("count")).isTrue();
        assertThat(tree.get("count").asInt()).isEqualTo(7);
        assertThat(tree.has("missingTitle")).isFalse();
        assertThat(tree.has("missingCount")).isFalse();
        assertThat(tree.has("directNull")).isFalse();
    }

    @Test
    void supportsOptionalValuesInsideGenericCollectionsAndMaps() throws Exception {
        ObjectMapper mapper = jdk8Mapper();
        LinkedHashMap<String, Optional<Address>> offices = new LinkedHashMap<>();
        offices.put("primary", Optional.of(new Address("Prague", "11000")));
        offices.put("secondary", Optional.empty());
        GenericContainers containers = new GenericContainers(
                Arrays.asList(Optional.of("alpha"), Optional.empty(), Optional.of("omega")), offices);

        String json = mapper.writeValueAsString(containers);

        JsonNode tree = mapper.readTree(json);
        assertThat(tree.at("/labels/0").asText()).isEqualTo("alpha");
        assertThat(tree.at("/labels/1").isNull()).isTrue();
        assertThat(tree.at("/offices/primary/city").asText()).isEqualTo("Prague");
        assertThat(tree.at("/offices/secondary").isNull()).isTrue();

        GenericContainers restored = mapper.readValue(json, GenericContainers.class);

        assertThat(restored.labels).containsExactly(Optional.of("alpha"), Optional.empty(), Optional.of("omega"));
        assertThat(restored.offices).containsExactlyEntriesOf(offices);
    }

    @Test
    void supportsDeeplyNestedParameterizedOptionalContent() throws Exception {
        ObjectMapper mapper = jdk8Mapper();
        TypeReference<Optional<List<Optional<Address>>>> nestedType =
                new TypeReference<Optional<List<Optional<Address>>>>() { };
        Optional<List<Optional<Address>>> nested = Optional.of(Arrays.asList(
                Optional.of(new Address("Berlin", "10115")),
                Optional.empty(),
                Optional.of(new Address("Rome", "00100"))));

        String json = mapper.writeValueAsString(nested);

        assertThat(mapper.readTree(json).at("/0/city").asText()).isEqualTo("Berlin");
        assertThat(mapper.readTree(json).at("/1").isNull()).isTrue();
        assertThat(mapper.readValue(json, nestedType)).isEqualTo(nested);
    }

    @Test
    void supportsCreatorParametersWithOptionalValues() throws Exception {
        ObjectMapper mapper = jdk8Mapper();
        CreatorBackedValue original = new CreatorBackedValue(
                Optional.of("created"), OptionalInt.of(5), OptionalLong.of(55L), OptionalDouble.of(5.5D));

        String json = mapper.writeValueAsString(original);
        CreatorBackedValue restored = mapper.readValue(json, CreatorBackedValue.class);
        CreatorBackedValue nullRestored = mapper.readValue(
                "{\"name\":null,\"retries\":null,\"duration\":null,\"ratio\":null}",
                CreatorBackedValue.class);

        assertThat(restored.name).contains("created");
        assertThat(restored.retries).hasValue(5);
        assertThat(restored.duration).hasValue(55L);
        assertThat(restored.ratio).hasValue(5.5D);
        assertThat(nullRestored.name).isEmpty();
        assertThat(nullRestored.retries).isEmpty();
        assertThat(nullRestored.duration).isEmpty();
        assertThat(nullRestored.ratio).isEmpty();
    }

    @Test
    void supportsPolymorphicContentInsideOptionalValues() throws Exception {
        ObjectMapper mapper = jdk8Mapper();
        OptionalPetHolder holder = new OptionalPetHolder(Optional.of(new Dog("Rex", 8)), Optional.empty());

        String json = mapper.writeValueAsString(holder);

        JsonNode tree = mapper.readTree(json);
        assertThat(tree.at("/pet/type").asText()).isEqualTo("dog");
        assertThat(tree.at("/pet/name").asText()).isEqualTo("Rex");
        assertThat(tree.at("/missingPet").isNull()).isTrue();

        OptionalPetHolder restored = mapper.readValue(json, OptionalPetHolder.class);

        assertThat(restored.pet).contains(new Dog("Rex", 8));
        assertThat(restored.missingPet).isEmpty();
    }

    @Test
    void serializesUnwrappedOptionalBeanProperties() throws Exception {
        ObjectMapper mapper = jdk8Mapper();
        UnwrappedOptionalAddress present = new UnwrappedOptionalAddress(
                "office", Optional.of(new Address("Vienna", "1010")));
        UnwrappedOptionalAddress absent = new UnwrappedOptionalAddress("remote", Optional.empty());

        JsonNode presentTree = mapper.readTree(mapper.writeValueAsString(present));
        JsonNode absentTree = mapper.readTree(mapper.writeValueAsString(absent));

        assertThat(presentTree.get("id").asText()).isEqualTo("office");
        assertThat(presentTree.get("address_city").asText()).isEqualTo("Vienna");
        assertThat(presentTree.get("address_postalCode").asText()).isEqualTo("1010");
        assertThat(presentTree.has("address")).isFalse();
        assertThat(absentTree.get("id").asText()).isEqualTo("remote");
        assertThat(absentTree.has("address_city")).isFalse();
        assertThat(absentTree.has("address_postalCode")).isFalse();
        assertThat(absentTree.has("address")).isFalse();
    }

    @Test
    void supportsObjectMapperModuleAutoDiscovery() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Optional<String> value = Optional.of("auto-discovered");
        TypeReference<Optional<String>> optionalStringType = new TypeReference<Optional<String>>() { };

        String json = mapper.writeValueAsString(value);

        assertThat(json).isEqualTo("\"auto-discovered\"");
        assertThat(mapper.readValue(json, optionalStringType)).contains("auto-discovered");
        assertThat(mapper.readValue("null", optionalStringType)).isEmpty();
    }

    @Test
    void serializesJdk8StreamsAsJsonArrays() throws Exception {
        ObjectMapper mapper = jdk8Mapper();
        StreamValues values = new StreamValues(
                Stream.of("north", "south"),
                IntStream.of(1, 2, 3),
                LongStream.of(10L, 20L),
                DoubleStream.of(0.25D, 0.5D));

        String json = mapper.writeValueAsString(values);

        JsonNode tree = mapper.readTree(json);
        assertThat(tree.withArray("names").get(0).asText()).isEqualTo("north");
        assertThat(tree.withArray("names").get(1).asText()).isEqualTo("south");
        assertThat(tree.withArray("counts").get(0).asInt()).isEqualTo(1);
        assertThat(tree.withArray("counts").get(1).asInt()).isEqualTo(2);
        assertThat(tree.withArray("counts").get(2).asInt()).isEqualTo(3);
        assertThat(tree.withArray("sizes").get(0).asLong()).isEqualTo(10L);
        assertThat(tree.withArray("sizes").get(1).asLong()).isEqualTo(20L);
        assertThat(tree.withArray("ratios").get(0).asDouble()).isEqualTo(0.25D);
        assertThat(tree.withArray("ratios").get(1).asDouble()).isEqualTo(0.5D);
    }

    private static ObjectMapper jdk8Mapper() {
        return new ObjectMapper().registerModule(new Jdk8Module());
    }

    public static final class OptionalValues {
        public Optional<String> name;
        public OptionalInt score;
        public OptionalLong visits;
        public OptionalDouble ratio;
        public Optional<Address> address;

        public OptionalValues() { }

        static OptionalValues presentSample() {
            OptionalValues values = new OptionalValues();
            values.name = Optional.of("Ada");
            values.score = OptionalInt.of(42);
            values.visits = OptionalLong.of(1_234_567_890_123L);
            values.ratio = OptionalDouble.of(0.625D);
            values.address = Optional.of(new Address("London", "SW1A"));
            return values;
        }

        static OptionalValues emptySample() {
            OptionalValues values = new OptionalValues();
            values.name = Optional.empty();
            values.score = OptionalInt.empty();
            values.visits = OptionalLong.empty();
            values.ratio = OptionalDouble.empty();
            values.address = Optional.empty();
            return values;
        }
    }

    public static final class Address {
        public String city;
        public String postalCode;

        public Address() { }

        public Address(String city, String postalCode) {
            this.city = city;
            this.postalCode = postalCode;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Address)) {
                return false;
            }
            Address address = (Address) other;
            return city.equals(address.city) && postalCode.equals(address.postalCode);
        }

        @Override
        public int hashCode() {
            return 31 * city.hashCode() + postalCode.hashCode();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public static final class NonAbsentValues {
        public Optional<String> title;
        public Optional<String> missingTitle;
        public OptionalInt count;
        public OptionalInt missingCount;
        public String directNull;
    }

    public static final class GenericContainers {
        public List<Optional<String>> labels;
        public Map<String, Optional<Address>> offices;

        public GenericContainers() { }

        public GenericContainers(List<Optional<String>> labels, Map<String, Optional<Address>> offices) {
            this.labels = labels;
            this.offices = offices;
        }
    }

    public static final class UnwrappedOptionalAddress {
        public String id;

        @JsonUnwrapped(prefix = "address_")
        public Optional<Address> address;

        public UnwrappedOptionalAddress(String id, Optional<Address> address) {
            this.id = id;
            this.address = address;
        }
    }

    public static final class StreamValues {
        public Stream<String> names;
        public IntStream counts;
        public LongStream sizes;
        public DoubleStream ratios;

        public StreamValues(Stream<String> names, IntStream counts, LongStream sizes, DoubleStream ratios) {
            this.names = names;
            this.counts = counts;
            this.sizes = sizes;
            this.ratios = ratios;
        }
    }

    public static final class CreatorBackedValue {
        public final Optional<String> name;
        public final OptionalInt retries;
        public final OptionalLong duration;
        public final OptionalDouble ratio;

        @JsonCreator
        public CreatorBackedValue(
                @JsonProperty("name") Optional<String> name,
                @JsonProperty("retries") OptionalInt retries,
                @JsonProperty("duration") OptionalLong duration,
                @JsonProperty("ratio") OptionalDouble ratio) {
            this.name = name;
            this.retries = retries;
            this.duration = duration;
            this.ratio = ratio;
        }
    }

    public static final class OptionalPetHolder {
        public Optional<Pet> pet;
        public Optional<Pet> missingPet;

        public OptionalPetHolder() { }

        public OptionalPetHolder(Optional<Pet> pet, Optional<Pet> missingPet) {
            this.pet = pet;
            this.missingPet = missingPet;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes(@JsonSubTypes.Type(value = Dog.class, name = "dog"))
    public interface Pet { }

    public static final class Dog implements Pet {
        public String name;
        public int barkVolume;

        public Dog() { }

        public Dog(String name, int barkVolume) {
            this.name = name;
            this.barkVolume = barkVolume;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Dog)) {
                return false;
            }
            Dog dog = (Dog) other;
            return barkVolume == dog.barkVolume && name.equals(dog.name);
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + barkVolume;
        }
    }
}
