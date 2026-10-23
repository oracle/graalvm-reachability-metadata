/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.SessionFactory;
import org.hibernate.annotations.Immutable;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Exercises Hibernate attribute converters to persist retail monetary values as integer cents.
@Timeout(60)
public class RetailPriceConversionTest {

    @Test
    public void storesAndRevisesDomainPricesAndQueriesThemWithConvertedParameters() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(Listing.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:retail_price_conversion;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.cache.use_second_level_cache", "false")
                .setProperty("jakarta.persistence.validation.mode", "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            factory.inTransaction(session -> {
                session.persist(new Listing(1L, "Lunch box", new UsdAmount(1299L)));
                session.persist(new Listing(2L, "Picnic basket", new UsdAmount(4599L)));
                session.persist(new Listing(3L, "Travel cup", null));
            });

            factory.inTransaction(session -> {
                Listing lunchBox = session.find(Listing.class, 1L);
                assertNotNull(lunchBox);
                assertEquals(new UsdAmount(1299L), lunchBox.price);
                assertEquals(List.of("Lunch box"), session.createQuery(
                        "select l.name from ConvertedPriceListing l where l.price = :price", String.class)
                        .setParameter("price", new UsdAmount(1299L))
                        .getResultList());

                // The accounting export consumes cents, not a serialized monetary object.
                assertEquals(List.of(1299L, 4599L), session.createNativeQuery("""
                        select price_cents from converted_price_listing
                        where price_cents is not null order by id
                        """, Long.class).getResultList());

                lunchBox.price = new UsdAmount(1099L);
                Listing travelCup = session.find(Listing.class, 3L);
                assertNotNull(travelCup);
                assertNull(travelCup.price);
                travelCup.price = new UsdAmount(799L);
                Listing basket = session.find(Listing.class, 2L);
                assertNotNull(basket);
                basket.price = null;
            });

            factory.inTransaction(session -> {
                List<Listing> priced = session.createQuery("""
                        from ConvertedPriceListing l
                        where l.price is not null order by l.id
                        """, Listing.class).getResultList();
                assertEquals(List.of("Lunch box", "Travel cup"), priced.stream().map(l -> l.name).toList());
                assertEquals(List.of(new UsdAmount(1099L), new UsdAmount(799L)),
                        priced.stream().map(l -> l.price).toList());
                assertEquals(List.of(new UsdAmount(1099L)), session.createQuery("""
                        select l.price from ConvertedPriceListing l where l.id = :id
                        """, UsdAmount.class).setParameter("id", 1L).getResultList());
                assertEquals(List.of("Travel cup"), session.createQuery(
                        "select l.name from ConvertedPriceListing l where l.price = :price", String.class)
                        .setParameter("price", new UsdAmount(799L))
                        .getResultList());
                assertEquals(List.of(), session.createQuery(
                        "select l.name from ConvertedPriceListing l where l.price = :price", String.class)
                        .setParameter("price", new UsdAmount(1299L))
                        .getResultList());
                Listing basket = session.find(Listing.class, 2L);
                assertNotNull(basket);
                assertEquals("Picnic basket", basket.name);
                assertNull(basket.price);
                assertEquals(List.of(1099L, 799L), session.createNativeQuery("""
                        select price_cents from converted_price_listing
                        where price_cents is not null order by id
                        """, Long.class).getResultList());
            });
        }
    }

    @Immutable
    public record UsdAmount(long cents) {
    }

    @Converter
    public static final class UsdAmountConverter implements AttributeConverter<UsdAmount, Long> {
        @Override
        public Long convertToDatabaseColumn(UsdAmount amount) {
            return amount == null ? null : amount.cents();
        }

        @Override
        public UsdAmount convertToEntityAttribute(Long cents) {
            return cents == null ? null : new UsdAmount(cents);
        }
    }

    @Entity(name = "ConvertedPriceListing")
    @Table(name = "converted_price_listing")
    public static final class Listing {
        @Id
        private Long id;

        private String name;

        @Convert(converter = UsdAmountConverter.class)
        @Column(name = "price_cents")
        private UsdAmount price;

        public Listing() {
        }

        public Listing(Long id, String name, UsdAmount price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }
    }
}
