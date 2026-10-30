/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import net.sf.cglib.beans.ImmutableBean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class ImmutableBeanTest {
    @Test
    void constructsPrecompiledReadOnlyViewsForEachBean() {
        Message first = new Message();
        first.setText("first");
        Message second = new Message();
        second.setText("second");

        ImmutableBean.Generator generator = new ImmutableBean.Generator();
        generator.setAttemptLoad(true);
        generator.setNamingPolicy((prefix, source, key, names) -> ReadOnlyMessage.class.getName());
        generator.setStrategy(ignored -> {
            throw new AssertionError("The precompiled bean view must be reused");
        });

        generator.setBean(first);
        Message firstView = (Message) generator.create();
        generator.setBean(second);
        Message secondView = (Message) generator.create();

        assertThat(firstView).isInstanceOf(ReadOnlyMessage.class).isNotSameAs(first);
        assertThat(secondView).isInstanceOf(ReadOnlyMessage.class).isNotSameAs(firstView);
        assertThat(firstView.getText()).isEqualTo("first");
        assertThat(secondView.getText()).isEqualTo("second");
        first.setText("updated");
        assertThat(firstView.getText()).isEqualTo("updated");
        assertThat(secondView.getText()).isEqualTo("second");
        assertThatIllegalStateException().isThrownBy(() -> firstView.setText("replacement"));
        assertThat(first.getText()).isEqualTo("updated");
    }

    public static class Message {
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    public static class ReadOnlyMessage extends Message {
        private final Message bean;

        public ReadOnlyMessage(Object bean) {
            this.bean = (Message) bean;
        }

        @Override
        public String getText() {
            return bean.getText();
        }

        @Override
        public void setText(String text) {
            throw new IllegalStateException("Bean is immutable");
        }
    }
}
