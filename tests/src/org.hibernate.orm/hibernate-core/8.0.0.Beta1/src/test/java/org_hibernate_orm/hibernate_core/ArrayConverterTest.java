/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayConverterTest {

    @Test
    void convertedArrayElementsAreStoredInTheirRelationalForm() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Palette.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(
                    new Palette(1L, new Color[] {new Color(255, 0, 0), new Color(0, 128, 255)})));
            Color[] colors = sessionFactory.fromTransaction(session -> session.find(Palette.class, 1L).getColors());
            Object stored = sessionFactory.fromTransaction(session -> session.createNativeQuery(
                    "select colors from Palette").getSingleResult());

            assertThat(colors).containsExactly(new Color(255, 0, 0), new Color(0, 128, 255));
            assertThat((Object[]) stored).containsExactly("#ff0000", "#0080ff");
        }
    }

    public record Color(int red, int green, int blue) {
    }

    public static class HexColorConverter implements AttributeConverter<Color, String> {

        @Override
        public String convertToDatabaseColumn(Color color) {
            return String.format("#%02x%02x%02x", color.red(), color.green(), color.blue());
        }

        @Override
        public Color convertToEntityAttribute(String hex) {
            int rgb = Integer.parseInt(hex.substring(1), 16);
            return new Color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF);
        }
    }

    @Entity(name = "Palette")
    public static class Palette {

        @Id
        private Long id;

        @Convert(converter = HexColorConverter.class)
        private Color[] colors;

        public Palette() {
        }

        public Palette(Long id, Color[] colors) {
            this.id = id;
            this.colors = colors;
        }

        public Color[] getColors() {
            return colors;
        }
    }
}
