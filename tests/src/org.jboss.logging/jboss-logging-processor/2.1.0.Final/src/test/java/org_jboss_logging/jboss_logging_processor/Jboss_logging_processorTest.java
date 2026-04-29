/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logging.jboss_logging_processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;

import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.ConstructType;
import org.jboss.logging.annotations.Field;
import org.jboss.logging.annotations.FormatWith;
import org.jboss.logging.annotations.LoggingClass;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.Once;
import org.jboss.logging.annotations.Param;
import org.jboss.logging.annotations.Pos;
import org.jboss.logging.annotations.Property;
import org.jboss.logging.annotations.Signature;
import org.jboss.logging.annotations.Transform;
import org.jboss.logging.annotations.ValidIdRange;
import org.jboss.logging.annotations.ValidIdRanges;
import org.jboss.logging.processor.apt.LoggingToolsProcessor;
import org.jboss.logging.processor.model.MessageInterface;
import org.jboss.logging.processor.model.MessageMethod;
import org.jboss.logging.processor.util.Objects;
import org.jboss.logging.processor.util.VersionComparator;
import org.jboss.logging.processor.validation.FormatValidator;
import org.jboss.logging.processor.validation.FormatValidatorFactory;
import org.jboss.logging.processor.validation.IdLengthValidator;
import org.jboss.logging.processor.validation.StringFormatValidator;
import org.jboss.logging.processor.validation.ValidationMessage;
import org.jboss.logging.processor.validation.ValidationMessageFactory;
import org.junit.jupiter.api.Test;

public class Jboss_logging_processorTest {
    @Test
    void processorAdvertisesAllSupportedAnnotationsAndOptions() {
        LoggingToolsProcessor processor = new LoggingToolsProcessor();

        assertThat(processor.getSupportedSourceVersion()).isEqualTo(SourceVersion.latest());
        assertThat(processor.getSupportedOptions()).containsExactlyInAnyOrder(
                LoggingToolsProcessor.DEBUG_OPTION,
                "org.jboss.logging.tools.addGeneratedAnnotation",
                "org.jboss.logging.tools.expressionProperties");
        assertThat(processor.getSupportedAnnotationTypes()).containsExactlyInAnyOrder(
                MessageBundle.class.getName(),
                MessageLogger.class.getName(),
                Cause.class.getName(),
                ConstructType.class.getName(),
                Field.class.getName(),
                FormatWith.class.getName(),
                LoggingClass.class.getName(),
                LogMessage.class.getName(),
                Message.class.getName(),
                Once.class.getName(),
                Param.class.getName(),
                Pos.class.getName(),
                Property.class.getName(),
                Signature.class.getName(),
                Transform.class.getName(),
                ValidIdRange.class.getName(),
                ValidIdRanges.class.getName());
    }

    @Test
    void factoryCreatesPrintfValidatorWithJavaFormatterSemantics() {
        FormatValidator validator = FormatValidatorFactory.create(
                Message.Format.PRINTF,
                "Progress %d%% for %s%n");

        assertThat(validator.isValid()).isTrue();
        assertThat(validator.format()).isEqualTo("Progress %d%% for %s%n");
        assertThat(validator.argumentCount()).isEqualTo(2);
        assertThat(validator.summaryMessage()).isEmpty();
        assertThat(validator.detailMessage()).isEmpty();
    }

    @Test
    void factoryCreatesMessageFormatValidatorWithIndexedArguments() {
        FormatValidator validator = FormatValidatorFactory.create(
                Message.Format.MESSAGE_FORMAT,
                "User {0} has {1,number,#} queued job(s)");

        assertThat(validator.isValid()).isTrue();
        assertThat(validator.format()).isEqualTo("User {0} has {1,number,#} queued job(s)");
        assertThat(validator.argumentCount()).isEqualTo(2);
    }

    @Test
    void factoryCreatesNoFormatValidatorForLiteralMessages() {
        FormatValidator validator = FormatValidatorFactory.create(
                Message.Format.NO_FORMAT,
                "Use {0} and %s literally");

        assertThat(validator.isValid()).isTrue();
        assertThat(validator.format()).isEqualTo("none");
        assertThat(validator.argumentCount()).isZero();
    }

    @Test
    void factoryReportsMissingFormatAndMissingMessageAsInvalidValidators() {
        FormatValidator missingFormat = FormatValidatorFactory.create(null, "message");
        FormatValidator missingMessage = FormatValidatorFactory.create(Message.Format.PRINTF, null);

        assertThat(missingFormat.isValid()).isFalse();
        assertThat(missingFormat.argumentCount()).isZero();
        assertThat(missingFormat.summaryMessage()).contains("format is required");

        assertThat(missingMessage.isValid()).isFalse();
        assertThat(missingMessage.argumentCount()).isZero();
        assertThat(missingMessage.summaryMessage()).contains("message is required");
    }

    @Test
    void printfValidatorDetectsMalformedConversion() {
        FormatValidator validator = FormatValidatorFactory.create(Message.Format.PRINTF, "Broken %q conversion");

        assertThat(validator.isValid()).isFalse();
        assertThat(validator.format()).isEqualTo("Broken %q conversion");
        assertThat(validator.summaryMessage()).isEmpty();
        assertThat(validator.detailMessage()).contains("Broken %q conversion");
    }

    @Test
    void printfTranslationValidationAcceptsEquivalentConversions() {
        StringFormatValidator translation = StringFormatValidator.withTranslation(
                "Processing %1$s took %2$d ms",
                "Traitement de %1$s termin\u00e9 en %2$d ms");

        assertThat(translation.isValid()).isTrue();
        assertThat(translation.argumentCount()).isEqualTo(2);
        assertThat(translation.format()).isEqualTo("Traitement de %1$s termin\u00e9 en %2$d ms");
    }

    @Test
    void printfTranslationValidationRejectsChangedConversionTypes() {
        StringFormatValidator translation = StringFormatValidator.withTranslation(
                "Processing %1$s took %2$d ms",
                "Traitement de %1$d termin\u00e9 en %2$s ms");

        assertThat(translation.isValid()).isFalse();
        assertThat(translation.summaryMessage()).contains("does not match the initial message format");
        assertThat(translation.detailMessage()).contains("Processing %1$s took %2$d ms");
    }

    @Test
    void validationMessageFactoryCreatesTypedMessagesForElements() {
        MessageInterface messageInterface = new TestMessageInterface("org.example.BundleMessages", "EX", 4);

        ValidationMessage error = ValidationMessageFactory.createError(messageInterface, "Invalid id %d", 42);
        ValidationMessage warning = ValidationMessageFactory.createWarning(messageInterface, "Range may overlap");

        assertThat(error.type()).isEqualTo(ValidationMessage.Type.ERROR);
        assertThat(error.getElement()).isSameAs(messageInterface);
        assertThat(error.getMessage()).isEqualTo("Invalid id 42");

        assertThat(warning.type()).isEqualTo(ValidationMessage.Type.WARN);
        assertThat(warning.getElement()).isSameAs(messageInterface);
        assertThat(warning.getMessage()).isEqualTo("Range may overlap");
    }

    @Test
    void objectsUtilityPerformsNullChecksAndPrimitiveEquality() {
        String value = "message";

        assertThat(Objects.checkNonNull(value)).isSameAs(value);
        assertThat(Objects.areEqual(true, true)).isTrue();
        assertThat(Objects.areEqual('a', 'b')).isFalse();
        assertThat(Objects.areEqual(42L, 42L)).isTrue();
        assertThat(Objects.areEqual(1.5F, 1.5F)).isTrue();
        assertThat(Objects.areEqual(2.5D, 3.5D)).isFalse();
        assertThat(Objects.areEqual("same", new String("same"))).isTrue();
        assertThat(Objects.areEqual("left", null)).isFalse();
    }

    @Test
    void versionComparatorOrdersDottedNumericVersions() {
        Set<String> versions = Set.of("2.0.10", "1.9", "2.0.2", "2.0.2.1", "2.0");

        assertThat(versions.stream().sorted(VersionComparator.INSTANCE).toList())
                .containsExactly("1.9", "2.0", "2.0.2", "2.0.2.1", "2.0.10");
        assertThat(VersionComparator.compareVersion("2.0.2", "2.0.10")).isLessThan(0);
        assertThat(VersionComparator.compareVersion("2.0.2", "2.0.2")).isZero();
        assertThat(VersionComparator.compareVersion("3.0", "2.99")).isGreaterThan(0);
    }

    @Test
    void idLengthValidatorEnforcesPaddingRulesPerProjectCode() {
        IdLengthValidator validator = new IdLengthValidator();
        MessageInterface firstInterface = new TestMessageInterface("org.example.Messages", "EX", 4);
        MessageInterface matchingInterface = new TestMessageInterface("org.example.MoreMessages", "EX", 4);
        MessageInterface conflictingInterface = new TestMessageInterface("org.example.ConflictingMessages", "EX", 5);
        MessageInterface tooShortInterface = new TestMessageInterface("org.example.ShortMessages", "SHORT", 2);

        assertThat(validator.validate(firstInterface)).isEmpty();
        assertThat(validator.validate(matchingInterface)).isEmpty();

        Collection<ValidationMessage> conflictingMessages = validator.validate(conflictingInterface);
        Collection<ValidationMessage> tooShortMessages = validator.validate(tooShortInterface);

        assertThat(conflictingMessages)
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.type()).isEqualTo(ValidationMessage.Type.ERROR);
                    assertThat(message.getMessage()).contains("A length of 4 was already used for project code 'EX'");
                });
        assertThat(tooShortMessages)
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.type()).isEqualTo(ValidationMessage.Type.ERROR);
                    assertThat(message.getMessage()).contains("must be between 3 and 8");
                });
    }

    private record TestMessageInterface(String name, String projectCode, int getIdLength) implements MessageInterface {
        @Override
        public TypeElement getDelegate() {
            return null;
        }

        @Override
        public TypeMirror asType() {
            return null;
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.INTERFACE;
        }

        @Override
        public Set<Modifier> getModifiers() {
            return Set.of(Modifier.PUBLIC);
        }

        @Override
        public Name getSimpleName() {
            return new TestName(simpleName());
        }

        @Override
        public Element getEnclosingElement() {
            return null;
        }

        @Override
        public List<? extends Element> getEnclosedElements() {
            return List.of();
        }

        @Override
        public NestingKind getNestingKind() {
            return NestingKind.TOP_LEVEL;
        }

        @Override
        public Name getQualifiedName() {
            return new TestName(name);
        }

        @Override
        public TypeMirror getSuperclass() {
            return null;
        }

        @Override
        public List<? extends TypeMirror> getInterfaces() {
            return List.of();
        }

        @Override
        public List<? extends TypeParameterElement> getTypeParameters() {
            return List.of();
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            return List.of();
        }

        @Override
        public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotation) {
            return null;
        }

        @Override
        public <R, P> R accept(ElementVisitor<R, P> visitor, P parameter) {
            return null;
        }

        @Override
        public boolean extendsLoggerInterface() {
            return false;
        }

        @Override
        public Set<MessageInterface> extendedInterfaces() {
            return Set.of();
        }

        @Override
        public Collection<MessageMethod> methods() {
            return List.of();
        }

        @Override
        public String packageName() {
            int lastDot = name.lastIndexOf('.');
            return lastDot < 0 ? "" : name.substring(0, lastDot);
        }

        @Override
        public String simpleName() {
            int lastDot = name.lastIndexOf('.');
            return lastDot < 0 ? name : name.substring(lastDot + 1);
        }

        @Override
        public String loggingFQCN() {
            return name;
        }

        @Override
        public List<ValidIdRange> validIdRanges() {
            return List.of();
        }

        @Override
        public boolean isAnnotatedWith(Class<? extends Annotation> annotation) {
            return false;
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotation) {
            return null;
        }

        @Override
        public boolean isAssignableFrom(Class<?> type) {
            return false;
        }

        @Override
        public boolean isSubtypeOf(Class<?> type) {
            return false;
        }

        @Override
        public boolean isSameAs(Class<?> type) {
            return false;
        }

        @Override
        public String getComment() {
            return "";
        }

        @Override
        public int compareTo(MessageInterface other) {
            return name.compareTo(other.name());
        }
    }

    private record TestName(String value) implements Name {
        @Override
        public boolean contentEquals(CharSequence characterSequence) {
            return value.contentEquals(characterSequence);
        }

        @Override
        public int length() {
            return value.length();
        }

        @Override
        public char charAt(int index) {
            return value.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return value.subSequence(start, end);
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
