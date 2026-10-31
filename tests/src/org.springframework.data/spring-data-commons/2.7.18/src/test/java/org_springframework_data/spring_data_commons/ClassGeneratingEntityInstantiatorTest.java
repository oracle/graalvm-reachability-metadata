/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.model.BasicPersistentEntity;
import org.springframework.data.mapping.model.EntityInstantiator;
import org.springframework.data.mapping.model.EntityInstantiators;
import org.springframework.data.util.ClassTypeInformation;

public class ClassGeneratingEntityInstantiatorTest {

    @Test
    void defaultInstantiatorCreatesIndependentEntityInstances() {
        BasicPersistentEntity<Message, ?> entity = new BasicPersistentEntity<>(ClassTypeInformation.from(Message.class));
        EntityInstantiator instantiator = new EntityInstantiators().getInstantiatorFor(entity);

        Message first = instantiator.createInstance(entity, null);
        Message second = instantiator.createInstance(entity, null);
        first.setText("updated");

        assertThat(first).isNotSameAs(second);
        assertThat(first.getText()).isEqualTo("updated");
        assertThat(second.getText()).isEqualTo("draft");
    }

    public static class Message {

        private String text = "draft";

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
