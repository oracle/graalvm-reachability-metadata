/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import org.junit.jupiter.api.Test;
import org.modelmapper.Condition;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.modelmapper.Provider;
import org.modelmapper.Provider.ProvisionRequest;
import org.modelmapper.TypeToken;
import org.modelmapper.ValidationException;
import org.modelmapper.config.Configuration.AccessLevel;
import org.modelmapper.convention.MatchingStrategies;
import org.modelmapper.spi.MappingContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.*;

class ModelmapperTest {

    @Test
    void mapsSimpleProperties() {
        ModelMapper mm = new ModelMapper();

        PersonDTO source = new PersonDTO();
        source.setName("Alice");
        source.setAge(30);

        PersonEntity dest = mm.map(source, PersonEntity.class);

        assertThat(dest.getName()).isEqualTo("Alice");
        assertThat(dest.getAge()).isEqualTo(30);
    }

    @Test
    void automaticallyFlattensNestedProperties() {
        ModelMapper mm = new ModelMapper();

        Order order = new Order();
        order.setId("ORD-1");
        Customer customer = new Customer();
        customer.setName("Bob Buyer");
        Address addr = new Address();
        addr.setStreet("1 Main St");
        addr.setCity("Springfield");
        customer.setAddress(addr);
        order.setCustomer(customer);

        OrderDTO dto = mm.map(order, OrderDTO.class);

        assertThat(dto.getId()).isEqualTo("ORD-1");
        assertThat(dto.getCustomerName()).isEqualTo("Bob Buyer");
        assertThat(dto.getCustomerAddressCity()).isEqualTo("Springfield");
    }

    @Test
    void mapsCollectionsWithTypeToken() {
        ModelMapper mm = new ModelMapper();

        List<PersonDTO> src = new ArrayList<>();
        src.add(person("Ann", 20));
        src.add(person("Ben", 25));

        List<PersonEntity> result = mm.map(
            src,
            new TypeToken<List<PersonEntity>>() {}.getType()
        );

        assertThat(result)
            .hasSize(2)
            .extracting(PersonEntity::getName, PersonEntity::getAge)
            .containsExactly(
                tuple("Ann", 20),
                tuple("Ben", 25)
            );
    }

    @Test
    void appliesCustomConverterForTypeDifferences() {
        ModelMapper mm = new ModelMapper();

        // Register a global converter from String -> Integer
        Converter<String, Integer> stringToInteger = new Converter<String, Integer>() {
            @Override
            public Integer convert(MappingContext<String, Integer> ctx) {
                String s = ctx.getSource();
                return (s == null || s.isEmpty()) ? null : Integer.valueOf(s);
            }
        };
        mm.addConverter(stringToInteger);

        AgeStringSrc src = new AgeStringSrc();
        src.setAge("42");

        AgeDest dest = mm.map(src, AgeDest.class);

        assertThat(dest.getAge()).isEqualTo(42);
    }

    @Test
    void skipNullEnabledDoesNotOverwriteDestinationValues() {
        ModelMapper mm = new ModelMapper();
        mm.getConfiguration().setSkipNullEnabled(true);

        PersonDTO src = new PersonDTO();
        src.setName(null); // should be ignored
        src.setAge(99);    // should update

        PersonEntity dest = new PersonEntity();
        dest.setName("Existing");
        dest.setAge(1);

        mm.map(src, dest);

        assertThat(dest.getName()).isEqualTo("Existing"); // not overwritten
        assertThat(dest.getAge()).isEqualTo(99);          // updated
    }

    @Test
    void providerIsUsedWhenConstructingDestination() {
        ModelMapper mm = new ModelMapper();

        // Prepare a provider for AddressDTO that initializes the country
        Provider<AddressDTO> addressDtoProvider = new Provider<AddressDTO>() {
            @Override
            public AddressDTO get(ProvisionRequest<AddressDTO> request) {
                AddressDTO dto = new AddressDTO();
                dto.setCountry("US");
                return dto;
            }
        };

        // Create a TypeMap and set a provider so it is used during mapping
        mm.createTypeMap(Address.class, AddressDTO.class)
            .setProvider(addressDtoProvider);

        Address src = new Address();
        src.setStreet("2 Side St");
        src.setCity("Metropolis");

        AddressDTO dest = mm.map(src, AddressDTO.class);

        assertThat(dest.getStreet()).isEqualTo("2 Side St");
        assertThat(dest.getCity()).isEqualTo("Metropolis");
        assertThat(dest.getCountry()).isEqualTo("US"); // set by provider
    }

    @Test
    void customPropertyMapWithRenamesAndSkip() {
        ModelMapper mm = new ModelMapper();

        PropertyMap<PersonSrc, PersonDest> map = new PropertyMap<PersonSrc, PersonDest>() {
            @Override
            protected void configure() {
                map().setGivenName(source.getFirstName());
                map().setFamilyName(source.getLastName());
                skip(destination.getSsn()); // do not map SSN at all
            }
        };
        mm.addMappings(map);

        PersonSrc src = new PersonSrc();
        src.setFirstName("Clara");
        src.setLastName("Oswald");
        src.setAge(28);
        src.setSsn("123-45-6789");

        PersonDest dest = mm.map(src, PersonDest.class);

        assertThat(dest.getGivenName()).isEqualTo("Clara");
        assertThat(dest.getFamilyName()).isEqualTo("Oswald");
        assertThat(dest.getAge()).isEqualTo(28);
        assertThat(dest.getSsn()).isNull(); // skipped
    }

    @Test
    void strictMatchingValidateFailsWhenDestinationPropertyUnmapped() {
        ModelMapper mm = new ModelMapper();
        mm.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);

        // Create an explicit TypeMap and request validation
        mm.createTypeMap(CarMakeOnly.class, CarDTO.class);

        assertThatThrownBy(() -> mm.validate())
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void mapOntoExistingDestinationInstance() {
        ModelMapper mm = new ModelMapper();

        PersonDTO src = new PersonDTO();
        src.setName("New Name");
        src.setAge(50);

        PersonEntity existing = new PersonEntity();
        existing.setName("Old Name");
        existing.setAge(10);

        // map onto existing instance
        mm.map(src, existing);

        assertThat(existing.getName()).isEqualTo("New Name");
        assertThat(existing.getAge()).isEqualTo(50);
    }

    @Test
    void fieldMatchingAllowsSettingPrivateFieldsWithoutSetters() {
        ModelMapper mm = new ModelMapper();
        mm.getConfiguration()
            .setFieldMatchingEnabled(true)
            .setFieldAccessLevel(AccessLevel.PRIVATE);

        PrivateFieldSource src = new PrivateFieldSource("XYZ-123");
        PrivateFieldDest dest = mm.map(src, PrivateFieldDest.class);

        assertThat(dest.getCode()).isEqualTo("XYZ-123");
    }

    @Test
    void appliesCustomCondition() {
        ModelMapper mm = new ModelMapper();

        // Condition to ignore empty strings (but still map nulls unless skipNullEnabled is true)
        Condition<String, String> notEmpty = new Condition<String, String>() {
            @Override
            public boolean applies(MappingContext<String, String> context) {
                String s = context.getSource();
                return s != null && !s.isEmpty();
            }
        };
        mm.getConfiguration().setPropertyCondition(notEmpty);

        NameHolder src = new NameHolder();
        src.setName(""); // empty -> should be ignored

        NameHolder dest = new NameHolder();
        dest.setName("KeepMe");

        mm.map(src, dest);

        assertThat(dest.getName()).isEqualTo("KeepMe");
    }

    @Test
    void postConverterCanComputeDerivedDestinationFields() {
        ModelMapper mm = new ModelMapper();

        // Use a post-converter to compute a derived field after standard mapping
        mm.createTypeMap(NameParts.class, DisplayName.class)
            .setPostConverter(new Converter<NameParts, DisplayName>() {
                @Override
                public DisplayName convert(MappingContext<NameParts, DisplayName> ctx) {
                    NameParts s = ctx.getSource();
                    DisplayName d = ctx.getDestination();
                    d.setFullName(s.getFirstName() + " " + s.getLastName());
                    return d;
                }
            });

        NameParts src = new NameParts();
        src.setFirstName("Dana");
        src.setLastName("Scully");

        DisplayName dest = mm.map(src, DisplayName.class);

        assertThat(dest.getFullName()).isEqualTo("Dana Scully");
    }

    @Test
    void mapsFromMapToBean() {
        ModelMapper mm = new ModelMapper();

        Map<String, Object> src = new HashMap<>();
        src.put("name", "Eve");
        src.put("age", 33);

        PersonEntity dest = mm.map(src, PersonEntity.class);

        assertThat(dest.getName()).isEqualTo("Eve");
        assertThat(dest.getAge()).isEqualTo(33);
    }

    // -------- Helper data classes --------

    static class PersonDTO {
        private String name;
        private int age;

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public int getAge() {
            return age;
        }
        public void setAge(int age) {
            this.age = age;
        }
    }

    static class PersonEntity {
        private String name;
        private int age;

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public int getAge() {
            return age;
        }
        public void setAge(int age) {
            this.age = age;
        }
    }

    static class Order {
        private String id;
        private Customer customer;

        public String getId() {
            return id;
        }
        public void setId(String id) {
            this.id = id;
        }
        public Customer getCustomer() {
            return customer;
        }
        public void setCustomer(Customer customer) {
            this.customer = customer;
        }
    }

    static class OrderDTO {
        private String id;
        private String customerName;
        private String customerAddressCity;

        public String getId() {
            return id;
        }
        public void setId(String id) {
            this.id = id;
        }
        public String getCustomerName() {
            return customerName;
        }
        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }
        public String getCustomerAddressCity() {
            return customerAddressCity;
        }
        public void setCustomerAddressCity(String customerAddressCity) {
            this.customerAddressCity = customerAddressCity;
        }
    }

    static class Customer {
        private String name;
        private Address address;

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public Address getAddress() {
            return address;
        }
        public void setAddress(Address address) {
            this.address = address;
        }
    }

    static class Address {
        private String street;
        private String city;

        public String getStreet() {
            return street;
        }
        public void setStreet(String street) {
            this.street = street;
        }
        public String getCity() {
            return city;
        }
        public void setCity(String city) {
            this.city = city;
        }
    }

    static class AddressDTO {
        private String street;
        private String city;
        private String country;

        public String getStreet() {
            return street;
        }
        public void setStreet(String street) {
            this.street = street;
        }
        public String getCity() {
            return city;
        }
        public void setCity(String city) {
            this.city = city;
        }
        public String getCountry() {
            return country;
        }
        public void setCountry(String country) {
            this.country = country;
        }
    }

    static class AgeStringSrc {
        private String age;

        public String getAge() {
            return age;
        }
        public void setAge(String age) {
            this.age = age;
        }
    }

    static class AgeDest {
        private Integer age;

        public Integer getAge() {
            return age;
        }
        public void setAge(Integer age) {
            this.age = age;
        }
    }

    static class PersonSrc {
        private String firstName;
        private String lastName;
        private int age;
        private String ssn;

        public String getFirstName() {
            return firstName;
        }
        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }
        public String getLastName() {
            return lastName;
        }
        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
        public int getAge() {
            return age;
        }
        public void setAge(int age) {
            this.age = age;
        }
        public String getSsn() {
            return ssn;
        }
        public void setSsn(String ssn) {
            this.ssn = ssn;
        }
    }

    static class PersonDest {
        private String givenName;
        private String familyName;
        private int age;
        private String ssn;

        public String getGivenName() {
            return givenName;
        }
        public void setGivenName(String givenName) {
            this.givenName = givenName;
        }
        public String getFamilyName() {
            return familyName;
        }
        public void setFamilyName(String familyName) {
            this.familyName = familyName;
        }
        public int getAge() {
            return age;
        }
        public void setAge(int age) {
            this.age = age;
        }
        public String getSsn() {
            return ssn;
        }
        public void setSsn(String ssn) {
            this.ssn = ssn;
        }
    }

    static class CarMakeOnly {
        private String make;

        public String getMake() {
            return make;
        }
        public void setMake(String make) {
            this.make = make;
        }
    }

    static class CarDTO {
        private String make;
        private String model; // intentionally has no source mapping

        public String getMake() {
            return make;
        }
        public void setMake(String make) {
            this.make = make;
        }
        public String getModel() {
            return model;
        }
        public void setModel(String model) {
            this.model = model;
        }
    }

    static class PrivateFieldSource {
        private final String code;

        PrivateFieldSource(String code) {
            this.code = code;
        }

        // no getter on purpose to test field matching
    }

    static class PrivateFieldDest {
        private String code;

        // No setter on purpose; field will be set directly
        public String getCode() {
            return code;
        }
    }

    static class NameHolder {
        private String name;

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
    }

    static class NameParts {
        private String firstName;
        private String lastName;

        public String getFirstName() {
            return firstName;
        }
        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }
        public String getLastName() {
            return lastName;
        }
        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
    }

    static class DisplayName {
        private String fullName;

        public String getFullName() {
            return fullName;
        }
        public void setFullName(String fullName) {
            this.fullName = fullName;
        }
    }

    private static PersonDTO person(String name, int age) {
        PersonDTO p = new PersonDTO();
        p.setName(name);
        p.setAge(age);
        return p;
    }
}
