/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_validator.hibernate_validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.GroupSequence;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.GroupConversionDescriptor;
import jakarta.validation.metadata.PropertyDescriptor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.group.GroupSequenceProvider;
import org.hibernate.validator.spi.group.DefaultGroupSequenceProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Validation groups and group sequences: constraints assigned to application groups, a group
 * sequence that stops at the first failing group, a class that redefines its default group
 * sequence, a Hibernate `@GroupSequenceProvider` that picks the default sequence from the bean's
 * state, `@ConvertGroup` on a cascaded property, group-restricted property and value validation,
 * and group-aware constraint metadata.
 *
 * A lender's back office saves loan applications as drafts, checks them in full before they
 * are submitted and only then runs the costly credit check. Collateral is appraised only once
 * it is described, and company borrowers must give a registration number that private
 * borrowers do not have. The scenario asserts which rules each workflow step reports.
 */
public class LoanApplicationValidationGroupsTest {

    private ValidatorFactory factory;
    private Validator validator;

    @BeforeEach
    void createValidator() {
        factory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .defaultLocale(Locale.ENGLISH)
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterEach
    void closeFactory() {
        factory.close();
    }

    @Test
    void checksOnlyTheRequestedGroup() {
        LoanApplication draft = new LoanApplication("", null, 3, 520);

        Set<ConstraintViolation<LoanApplication>> defaultViolations = validator.validate(draft);
        Set<ConstraintViolation<LoanApplication>> submissionViolations = validator.validate(draft, Submission.class);
        Set<ConstraintViolation<LoanApplication>> bothViolations =
                validator.validate(draft, Default.class, Submission.class);

        assertEquals(Map.of("applicantName", "must not be blank"), describe(defaultViolations));
        Map<String, String> submission = new TreeMap<>();
        submission.put("amount", "must not be null");
        submission.put("termMonths", "must be greater than or equal to 6");
        assertEquals(submission, describe(submissionViolations));
        assertEquals(Set.of("applicantName", "amount", "termMonths"), describe(bothViolations).keySet());
    }

    @Test
    void groupSequenceStopsAtTheFirstFailingGroup() {
        LoanApplication incomplete = new LoanApplication("Ada Lovelace", null, 24, 520);
        LoanApplication complete = new LoanApplication("Ada Lovelace", new BigDecimal("25000"), 24, 520);
        LoanApplication creditworthy = new LoanApplication("Ada Lovelace", new BigDecimal("25000"), 24, 710);

        Set<ConstraintViolation<LoanApplication>> incompleteViolations =
                validator.validate(incomplete, SubmissionSequence.class);
        Set<ConstraintViolation<LoanApplication>> completeViolations =
                validator.validate(complete, SubmissionSequence.class);
        Set<ConstraintViolation<LoanApplication>> creditworthyViolations =
                validator.validate(creditworthy, SubmissionSequence.class);

        assertEquals(Map.of("amount", "must not be null"), describe(incompleteViolations));
        assertEquals(Map.of("creditworthy", "credit score is below the minimum of 600"),
                describe(completeViolations));
        assertTrue(creditworthyViolations.isEmpty(), () -> "unexpected violations: " + describe(creditworthyViolations));
    }

    @Test
    void redefinedDefaultSequenceAppraisesOnlyDescribedCollateral() {
        Collateral undescribed = new Collateral(" ", BigDecimal.ZERO);
        Collateral unappraised = new Collateral("Apartment, 3 rooms", BigDecimal.ZERO);
        Collateral appraised = new Collateral("Apartment, 3 rooms", new BigDecimal("180000"));

        assertEquals(Map.of("description", "must not be blank"), describe(validator.validate(undescribed)));
        assertEquals(Map.of("appraisedValue", "must be greater than or equal to 1000"),
                describe(validator.validate(unappraised)));
        assertTrue(validator.validate(appraised).isEmpty());
    }

    @Test
    void sequenceProviderAddsCompanyRulesForCompanyBorrowers() {
        Borrower person = new Borrower("Grace Hopper", BorrowerKind.PERSON, null);
        Borrower unregisteredCompany = new Borrower("Analytical Engines Ltd", BorrowerKind.COMPANY, "");
        Borrower registeredCompany = new Borrower("Analytical Engines Ltd", BorrowerKind.COMPANY, "HRB 12345");

        Set<ConstraintViolation<Borrower>> companyViolations = validator.validate(unregisteredCompany);

        assertTrue(validator.validate(person).isEmpty());
        assertEquals(Map.of("registrationNumber", "companies must give their registration number"),
                describe(companyViolations));
        assertEquals(Company.class, companyViolations.iterator().next()
                .getConstraintDescriptor().getGroups().iterator().next());
        assertTrue(validator.validate(registeredCompany).isEmpty());
    }

    @Test
    void convertsTheDefaultGroupWhenCascadingIntoTheApplication() {
        LoanFile file = new LoanFile("", new LoanApplication("", null, 12, 700));

        Set<ConstraintViolation<LoanFile>> violations = validator.validate(file);

        Map<String, String> expected = new TreeMap<>();
        expected.put("fileNumber", "must not be blank");
        expected.put("application.amount", "must not be null");
        assertEquals(expected, describe(violations));
    }

    @Test
    void validatesSinglePropertiesAndCandidateValuesPerGroup() {
        LoanApplication application = new LoanApplication("Ada Lovelace", new BigDecimal("25000"), 3, 700);

        Set<ConstraintViolation<LoanApplication>> defaultTerm = validator.validateProperty(application, "termMonths");
        Set<ConstraintViolation<LoanApplication>> submissionTerm =
                validator.validateProperty(application, "termMonths", Submission.class);
        Set<ConstraintViolation<LoanApplication>> shortCandidate =
                validator.validateValue(LoanApplication.class, "termMonths", 4, Submission.class);
        Set<ConstraintViolation<LoanApplication>> longCandidate =
                validator.validateValue(LoanApplication.class, "termMonths", 36, Submission.class);

        assertTrue(defaultTerm.isEmpty());
        assertEquals(Map.of("termMonths", "must be greater than or equal to 6"), describe(submissionTerm));
        assertEquals(4, shortCandidate.iterator().next().getInvalidValue());
        assertEquals(1, shortCandidate.size());
        assertTrue(longCandidate.isEmpty());
    }

    @Test
    void describesGroupsAndConversionsThroughMetadataApi() {
        PropertyDescriptor term = validator.getConstraintsForClass(LoanApplication.class)
                .getConstraintsForProperty("termMonths");
        PropertyDescriptor application = validator.getConstraintsForClass(LoanFile.class)
                .getConstraintsForProperty("application");

        Set<ConstraintDescriptor<?>> defaultConstraints =
                term.findConstraints().unorderedAndMatchingGroups(Default.class).getConstraintDescriptors();
        Set<ConstraintDescriptor<?>> submissionConstraints =
                term.findConstraints().unorderedAndMatchingGroups(Submission.class).getConstraintDescriptors();
        Set<GroupConversionDescriptor> conversions = application.getGroupConversions();
        GroupConversionDescriptor conversion = conversions.iterator().next();

        assertTrue(defaultConstraints.isEmpty());
        assertEquals(1, submissionConstraints.size());
        assertEquals(6L, submissionConstraints.iterator().next().getAttributes().get("value"));
        assertTrue(application.isCascaded());
        assertEquals(1, conversions.size());
        assertEquals(Default.class, conversion.getFrom());
        assertEquals(Submission.class, conversion.getTo());
    }

    private static <T> Map<String, String> describe(Set<ConstraintViolation<T>> violations) {
        Map<String, String> byPath = new TreeMap<>();
        for (ConstraintViolation<T> violation : violations) {
            byPath.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return byPath;
    }

    /** Rules an application must meet before it is submitted. */
    public interface Submission {
    }

    /** The costly credit bureau check, run only on complete applications. */
    public interface CreditCheck {
    }

    /** Company-only borrower rules. */
    public interface Company {
    }

    /** Checks a submission in full before it pays for a credit check. */
    @GroupSequence({Submission.class, CreditCheck.class})
    public interface SubmissionSequence {
    }

    public static class LoanApplication {

        @NotBlank
        final String applicantName;

        @NotNull(groups = Submission.class)
        final BigDecimal amount;

        @Min(value = 6, groups = Submission.class)
        final int termMonths;

        final int creditScore;

        LoanApplication(String applicantName, BigDecimal amount, int termMonths, int creditScore) {
            this.applicantName = applicantName;
            this.amount = amount;
            this.termMonths = termMonths;
            this.creditScore = creditScore;
        }

        @AssertTrue(groups = CreditCheck.class, message = "credit score is below the minimum of 600")
        public boolean isCreditworthy() {
            return creditScore >= 600;
        }
    }

    /** Collateral is appraised only after the default checks on it pass. */
    @GroupSequence({Collateral.class, Collateral.Appraisal.class})
    public static class Collateral {

        /** Rules checked against an appraiser's valuation. */
        public interface Appraisal {
        }

        @NotBlank
        final String description;

        @DecimalMin(value = "1000", groups = Appraisal.class)
        final BigDecimal appraisedValue;

        Collateral(String description, BigDecimal appraisedValue) {
            this.description = description;
            this.appraisedValue = appraisedValue;
        }
    }

    public enum BorrowerKind {
        PERSON,
        COMPANY
    }

    /** Adds the company rules to the default sequence of company borrowers. */
    public static class BorrowerSequenceProvider implements DefaultGroupSequenceProvider<Borrower> {

        @Override
        public List<Class<?>> getValidationGroups(Borrower borrower) {
            List<Class<?>> groups = new ArrayList<>();
            groups.add(Borrower.class);
            if (borrower != null && borrower.kind == BorrowerKind.COMPANY) {
                groups.add(Company.class);
            }
            return groups;
        }
    }

    @GroupSequenceProvider(BorrowerSequenceProvider.class)
    public static class Borrower {

        @NotBlank
        final String name;

        @NotNull
        final BorrowerKind kind;

        @NotBlank(groups = Company.class, message = "companies must give their registration number")
        final String registrationNumber;

        Borrower(String name, BorrowerKind kind, String registrationNumber) {
            this.name = name;
            this.kind = kind;
            this.registrationNumber = registrationNumber;
        }
    }

    /** A case file whose default check holds the application to the submission rules. */
    public static class LoanFile {

        @NotBlank
        final String fileNumber;

        @Valid
        @ConvertGroup(from = Default.class, to = Submission.class)
        final LoanApplication application;

        LoanFile(String fileNumber, LoanApplication application) {
            this.fileNumber = fileNumber;
            this.application = application;
        }
    }
}
