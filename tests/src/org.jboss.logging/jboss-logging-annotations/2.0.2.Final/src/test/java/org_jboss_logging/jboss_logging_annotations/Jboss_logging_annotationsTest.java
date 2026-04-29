/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logging.jboss_logging_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.ConstructType;
import org.jboss.logging.annotations.Field;
import org.jboss.logging.annotations.FormatWith;
import org.jboss.logging.annotations.LoggingClass;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.Message.Format;
import org.jboss.logging.annotations.MessageBundle;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.Once;
import org.jboss.logging.annotations.Param;
import org.jboss.logging.annotations.Pos;
import org.jboss.logging.annotations.Property;
import org.jboss.logging.annotations.Transform;
import org.jboss.logging.annotations.Transform.TransformType;
import org.jboss.logging.annotations.ValidIdRange;
import org.jboss.logging.annotations.ValidIdRanges;
import org.junit.jupiter.api.Test;

public class Jboss_logging_annotationsTest {
    @Test
    void exposesMessageIdSentinelConstants() {
        assertThat(Message.NONE).isZero();
        assertThat(Message.INHERIT).isEqualTo(-1);
    }

    @Test
    void exposesSupportedMessageFormatsInStableOrder() {
        assertThat(Format.values()).containsExactly(Format.PRINTF, Format.MESSAGE_FORMAT, Format.NO_FORMAT);
        assertThat(Format.valueOf("PRINTF")).isSameAs(Format.PRINTF);
        assertThat(Format.valueOf("MESSAGE_FORMAT")).isSameAs(Format.MESSAGE_FORMAT);
        assertThat(Format.valueOf("NO_FORMAT")).isSameAs(Format.NO_FORMAT);
    }

    @Test
    void exposesSupportedParameterTransformsInStableOrder() {
        assertThat(TransformType.values()).containsExactly(
                TransformType.GET_CLASS,
                TransformType.HASH_CODE,
                TransformType.IDENTITY_HASH_CODE,
                TransformType.SIZE);
        assertThat(TransformType.valueOf("GET_CLASS")).isSameAs(TransformType.GET_CLASS);
        assertThat(TransformType.valueOf("HASH_CODE")).isSameAs(TransformType.HASH_CODE);
        assertThat(TransformType.valueOf("IDENTITY_HASH_CODE")).isSameAs(TransformType.IDENTITY_HASH_CODE);
        assertThat(TransformType.valueOf("SIZE")).isSameAs(TransformType.SIZE);
    }

    @Test
    void loadsEveryAnnotationTypeUsedByMessageContracts() {
        List<Class<?>> annotationTypes = Arrays.asList(
                Cause.class,
                ConstructType.class,
                Field.class,
                FormatWith.class,
                LoggingClass.class,
                LogMessage.class,
                Message.class,
                MessageBundle.class,
                MessageLogger.class,
                Once.class,
                Param.class,
                Pos.class,
                Property.class,
                Transform.class,
                ValidIdRange.class,
                ValidIdRanges.class);

        assertThat(annotationTypes).doesNotContainNull();
        assertThat(Arrays.asList(SampleBundle.class, SampleLogger.class, AuditCategory.class, UppercaseFormatter.class))
                .doesNotContainNull();
    }

    @Test
    void formatWithTargetCanSupplyCustomStringConversion() {
        assertThat(new UppercaseFormatter("jboss logging").toString()).isEqualTo("JBOSS LOGGING");
    }

    @Test
    void supportsMappingOneArgumentToMultipleMessagePositions() {
        Pos position = new TestPosition(new int[] {1, 2}, new Transform[] {
                new TestTransform(TransformType.GET_CLASS, TransformType.HASH_CODE)
        });

        assertThat(position.value()).containsExactly(1, 2);
        assertThat(position.transform()).hasSize(1);
        assertThat(position.transform()[0].value()).containsExactly(TransformType.GET_CLASS, TransformType.HASH_CODE);
        assertThat(new RepeatedArgumentBundleImpl().sameValueTwice("metadata")).isEqualTo("metadata mirrors metadata");
    }

    @Test
    void supportsGroupingDisjointMessageIdRanges() {
        ValidIdRanges ranges = new TestValidIdRanges(
                new TestValidIdRange(1000, 1099),
                new TestValidIdRange(2000, 2099));

        assertThat(ranges.annotationType()).isSameAs(ValidIdRanges.class);
        assertThat(ranges.value()).hasSize(2);
        assertThat(ranges.value()[0].annotationType()).isSameAs(ValidIdRange.class);
        assertThat(ranges.value()[0].min()).isEqualTo(1000);
        assertThat(ranges.value()[0].max()).isEqualTo(1099);
        assertThat(messageIdAllowedBy(ranges, 1001)).isTrue();
        assertThat(messageIdAllowedBy(ranges, 1500)).isFalse();
        assertThat(messageIdAllowedBy(ranges, 2099)).isTrue();
    }

    private static boolean messageIdAllowedBy(ValidIdRanges ranges, int messageId) {
        for (ValidIdRange range : ranges.value()) {
            if (messageId >= range.min() && messageId <= range.max()) {
                return true;
            }
        }
        return false;
    }

    @MessageBundle(projectCode = "BND", length = 4, rootLocale = "en")
    @ValidIdRanges({
            @ValidIdRange(min = 1000, max = 1099),
            @ValidIdRange(min = 2000, max = 2099)
    })
    private interface SampleBundle {
        @Message(id = 1001, value = "Hello %s")
        String greeting(String name);

        @Message(id = 1002, value = "Account {0} has {1} entries", format = Format.MESSAGE_FORMAT)
        String accountSummary(@Pos(1) String accountId, @Pos(2) int entries);

        @Message(id = Message.NONE, value = "constant text", format = Format.NO_FORMAT)
        String unnumberedMessage();

        @Message(id = Message.INHERIT, value = "Illegal state for %s")
        @ConstructType(IllegalStateException.class)
        IllegalStateException invalidState(@Param(String.class) String state, @Cause RuntimeException cause);
    }

    @MessageLogger(projectCode = "LOG", length = 5, rootLocale = "en")
    @ValidIdRange(min = 3000, max = 3999)
    private interface SampleLogger {
        @LogMessage(level = Logger.Level.WARN)
        @Once
        @Message(id = 3001, value = "User %s could not be processed")
        void invalidUser(@Pos(1) String username, @Cause IllegalArgumentException cause);

        @LogMessage(level = Logger.Level.ERROR, loggingClass = AuditCategory.class)
        @Message(id = 3002, value = "Audit entry {0}", format = Format.MESSAGE_FORMAT)
        void audit(
                @LoggingClass Class<?> loggingClass,
                @FormatWith(UppercaseFormatter.class) @Pos(1) String entry);

        @Message(id = 3003, value = "size=%s type=%s identity=%s hash=%s")
        String transformedValues(
                @Pos(value = 1, transform = @Transform(TransformType.SIZE)) Collection<String> entries,
                @Pos(value = 2, transform = @Transform(TransformType.GET_CLASS)) Object value,
                @Pos(value = 3, transform = @Transform(TransformType.IDENTITY_HASH_CODE)) Object identity,
                @Pos(value = 4, transform = @Transform(TransformType.HASH_CODE)) Object hashed);

        @Message(id = 3004, value = "Failed with code %d: %s")
        @ConstructType(IllegalArgumentException.class)
        IllegalArgumentException failure(
                @Field(name = "code") int code,
                @Property(name = "detail") String detail,
                @Param String message);
    }

    @MessageBundle(projectCode = "REP")
    private interface RepeatedArgumentBundle {
        @Message(id = 1, value = "%s mirrors %s")
        String sameValueTwice(@Pos({1, 2}) String value);
    }

    private static final class RepeatedArgumentBundleImpl implements RepeatedArgumentBundle {
        @Override
        public String sameValueTwice(String value) {
            return String.format("%s mirrors %s", value, value);
        }
    }

    private static final class TestPosition implements Pos {
        private final int[] value;
        private final Transform[] transform;

        private TestPosition(int[] value, Transform[] transform) {
            this.value = value;
            this.transform = transform;
        }

        @Override
        public int[] value() {
            return value;
        }

        @Override
        public Transform[] transform() {
            return transform;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Pos.class;
        }
    }

    private static final class TestTransform implements Transform {
        private final TransformType[] value;

        private TestTransform(TransformType... value) {
            this.value = value;
        }

        @Override
        public TransformType[] value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Transform.class;
        }
    }

    private static final class TestValidIdRange implements ValidIdRange {
        private final int min;
        private final int max;

        private TestValidIdRange(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public int min() {
            return min;
        }

        @Override
        public int max() {
            return max;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return ValidIdRange.class;
        }
    }

    private static final class TestValidIdRanges implements ValidIdRanges {
        private final ValidIdRange[] value;

        private TestValidIdRanges(ValidIdRange... value) {
            this.value = value;
        }

        @Override
        public ValidIdRange[] value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return ValidIdRanges.class;
        }
    }

    public static final class AuditCategory {
    }

    public static final class UppercaseFormatter {
        private final String value;

        public UppercaseFormatter(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value.toUpperCase(Locale.ROOT);
        }
    }
}
