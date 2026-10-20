/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_baomidou.mybatis_plus;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.incrementer.H2KeyGenerator;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Exercises primary key generation: `@KeySequence` ids drawn from a database sequence through the
 * `H2KeyGenerator` registered on `GlobalConfig.DbConfig`, and `ASSIGN_ID` / `ASSIGN_UUID` ids
 * produced by an application `IdentifierGenerator` set on `GlobalConfig`, including the global
 * default `IdType` for entities whose `@TableId` names none.
 *
 * A billing desk numbers invoices from an H2 sequence before they are inserted, gives payments
 * ids derived from the invoice they settle, stamps receipts with readable string ids, and keeps
 * ids the caller already chose instead of generating new ones.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceNumberingKeyGenerationTest {

    private JdbcDataSource dataSource;

    private BillingIdGenerator idGenerator;

    private SqlSessionFactory sessionFactory;

    private SqlSession session;

    private InvoiceMapper invoices;

    private PaymentMapper payments;

    private ReceiptMapper receipts;

    @BeforeAll
    void buildSessionFactory() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:invoice_numbering;DB_CLOSE_DELAY=-1");
        Environment environment = new Environment("billing", new JdbcTransactionFactory(), dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration(environment);
        idGenerator = new BillingIdGenerator();
        GlobalConfig globalConfig = GlobalConfigUtils.defaults()
                .setIdentifierGenerator(idGenerator);
        globalConfig.getDbConfig()
                .setIdType(IdType.ASSIGN_UUID)
                .setKeyGenerators(List.of(new H2KeyGenerator()));
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.addMapper(InvoiceMapper.class);
        configuration.addMapper(PaymentMapper.class);
        configuration.addMapper(ReceiptMapper.class);
        sessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void openEmptyLedger() throws SQLException {
        execute("DROP TABLE IF EXISTS billing_invoice");
        execute("DROP TABLE IF EXISTS billing_payment");
        execute("DROP TABLE IF EXISTS billing_receipt");
        execute("DROP SEQUENCE IF EXISTS invoice_number_seq");
        execute("CREATE SEQUENCE invoice_number_seq START WITH 1000 INCREMENT BY 1");
        execute("""
                CREATE TABLE billing_invoice (
                    id BIGINT PRIMARY KEY,
                    customer VARCHAR(80) NOT NULL,
                    amount DECIMAL(10, 2) NOT NULL
                )
                """);
        execute("""
                CREATE TABLE billing_payment (
                    id BIGINT PRIMARY KEY,
                    invoice_id BIGINT NOT NULL,
                    amount DECIMAL(10, 2) NOT NULL
                )
                """);
        execute("""
                CREATE TABLE billing_receipt (
                    id VARCHAR(40) PRIMARY KEY,
                    payment_id BIGINT NOT NULL,
                    issued_to VARCHAR(80) NOT NULL
                )
                """);
        idGenerator.reset();
        session = sessionFactory.openSession(true);
        invoices = session.getMapper(InvoiceMapper.class);
        payments = session.getMapper(PaymentMapper.class);
        receipts = session.getMapper(ReceiptMapper.class);
    }

    @AfterEach
    void closeSession() {
        session.close();
    }

    @AfterAll
    void shutDownDatabase() throws SQLException {
        execute("SHUTDOWN");
    }

    @Test
    void numbersInvoicesFromTheDatabaseSequenceBeforeInserting() throws SQLException {
        Invoice northwind = Invoice.of("Northwind Traders", "1250.00");
        Invoice contoso = Invoice.of("Contoso Ltd", "980.50");
        Invoice fabrikam = Invoice.of("Fabrikam Inc", "42.00");

        assertThat(invoices.insert(northwind)).isEqualTo(1);
        assertThat(invoices.insert(contoso)).isEqualTo(1);
        assertThat(invoices.insert(fabrikam)).isEqualTo(1);

        assertThat(northwind.getId()).isEqualTo(1000L);
        assertThat(contoso.getId()).isEqualTo(1001L);
        assertThat(fabrikam.getId()).isEqualTo(1002L);
        assertThat(nextSequenceValue()).isEqualTo(1003L);

        assertThat(invoices.selectById(1001L).getCustomer()).isEqualTo("Contoso Ltd");
        assertThat(invoices.selectList(new LambdaQueryWrapper<Invoice>().orderByAsc(Invoice::getId)))
                .extracting(Invoice::getId, Invoice::getCustomer)
                .containsExactly(
                        tuple(1000L, "Northwind Traders"),
                        tuple(1001L, "Contoso Ltd"),
                        tuple(1002L, "Fabrikam Inc"));
        assertThat(idGenerator.numericRequests()).isEmpty();
    }

    @Test
    void derivesPaymentIdsFromTheSettledInvoiceThroughTheIdentifierGenerator() {
        Invoice invoice = Invoice.of("Northwind Traders", "1250.00");
        invoices.insert(invoice);

        Payment deposit = Payment.of(invoice.getId(), "250.00");
        Payment balance = Payment.of(invoice.getId(), "1000.00");
        assertThat(payments.insert(deposit)).isEqualTo(1);
        assertThat(payments.insert(balance)).isEqualTo(1);

        assertThat(deposit.getId()).isEqualTo(1000_0001L);
        assertThat(balance.getId()).isEqualTo(1000_0002L);
        assertThat(idGenerator.numericRequests()).containsExactly(deposit, balance);

        BigDecimal settled = payments.selectList(new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getInvoiceId, invoice.getId()))
                .stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(settled).isEqualByComparingTo(invoice.getAmount());
        assertThat(payments.selectById(1000_0002L).getAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void stampsReceiptsWithStringIdsFromTheGlobalDefaultIdType() {
        Receipt first = Receipt.of(1000_0001L, "Northwind Traders");
        Receipt second = Receipt.of(1000_0002L, "Northwind Traders");

        assertThat(receipts.insert(first)).isEqualTo(1);
        assertThat(receipts.insert(second)).isEqualTo(1);

        assertThat(first.getId()).isEqualTo("RCPT-10000001-1");
        assertThat(second.getId()).isEqualTo("RCPT-10000002-2");
        assertThat(idGenerator.uuidRequests()).containsExactly(first, second);
        assertThat(idGenerator.numericRequests()).isEmpty();

        Receipt found = receipts.selectById("RCPT-10000002-2");
        assertThat(found.getPaymentId()).isEqualTo(1000_0002L);
        assertThat(found.getIssuedTo()).isEqualTo("Northwind Traders");
    }

    @Test
    void keepsIdsTheCallerAlreadyAssigned() {
        Payment imported = Payment.of(999L, "75.00");
        imported.setId(77L);
        Receipt migrated = Receipt.of(77L, "Legacy Ledger");
        migrated.setId("LEGACY-0077");

        assertThat(payments.insert(imported)).isEqualTo(1);
        assertThat(receipts.insert(migrated)).isEqualTo(1);

        assertThat(imported.getId()).isEqualTo(77L);
        assertThat(migrated.getId()).isEqualTo("LEGACY-0077");
        assertThat(idGenerator.numericRequests()).isEmpty();
        assertThat(idGenerator.uuidRequests()).isEmpty();
        assertThat(payments.selectById(77L).getInvoiceId()).isEqualTo(999L);
        assertThat(receipts.selectById("LEGACY-0077").getIssuedTo()).isEqualTo("Legacy Ledger");

        Payment generated = Payment.of(999L, "25.00");
        payments.insert(generated);
        assertThat(generated.getId()).isEqualTo(999_0001L);
        assertThat(payments.selectCount(new LambdaQueryWrapper<Payment>().eq(Payment::getInvoiceId, 999L)))
                .isEqualTo(2L);
    }

    private long nextSequenceValue() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT NEXT VALUE FOR invoice_number_seq")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /// Builds payment ids as `invoiceId * 10000 + n` and receipt ids as `RCPT-<payment>-<n>`.
    static final class BillingIdGenerator implements IdentifierGenerator {

        private final AtomicLong paymentCounter = new AtomicLong();

        private final AtomicLong receiptCounter = new AtomicLong();

        private final List<Object> numericRequests = new ArrayList<>();

        private final List<Object> uuidRequests = new ArrayList<>();

        @Override
        public synchronized Number nextId(Object entity) {
            numericRequests.add(entity);
            Payment payment = (Payment) entity;
            return payment.getInvoiceId() * 10_000L + paymentCounter.incrementAndGet();
        }

        @Override
        public synchronized String nextUUID(Object entity) {
            uuidRequests.add(entity);
            Receipt receipt = (Receipt) entity;
            return "RCPT-" + receipt.getPaymentId() + "-" + receiptCounter.incrementAndGet();
        }

        synchronized List<Object> numericRequests() {
            return List.copyOf(numericRequests);
        }

        synchronized List<Object> uuidRequests() {
            return List.copyOf(uuidRequests);
        }

        synchronized void reset() {
            paymentCounter.set(0);
            receiptCounter.set(0);
            numericRequests.clear();
            uuidRequests.clear();
        }
    }

    interface InvoiceMapper extends BaseMapper<Invoice> {
    }

    interface PaymentMapper extends BaseMapper<Payment> {
    }

    interface ReceiptMapper extends BaseMapper<Receipt> {
    }

    @TableName("billing_invoice")
    @KeySequence(value = "invoice_number_seq", dbType = DbType.H2)
    public static class Invoice {

        @TableId(type = IdType.INPUT)
        private Long id;

        private String customer;

        private BigDecimal amount;

        static Invoice of(String customer, String amount) {
            Invoice invoice = new Invoice();
            invoice.setCustomer(customer);
            invoice.setAmount(new BigDecimal(amount));
            return invoice;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getCustomer() {
            return customer;
        }

        public void setCustomer(String customer) {
            this.customer = customer;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }

    @TableName("billing_payment")
    public static class Payment {

        @TableId(type = IdType.ASSIGN_ID)
        private Long id;

        private Long invoiceId;

        private BigDecimal amount;

        static Payment of(Long invoiceId, String amount) {
            Payment payment = new Payment();
            payment.setInvoiceId(invoiceId);
            payment.setAmount(new BigDecimal(amount));
            return payment;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getInvoiceId() {
            return invoiceId;
        }

        public void setInvoiceId(Long invoiceId) {
            this.invoiceId = invoiceId;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }

    @TableName("billing_receipt")
    public static class Receipt {

        @TableId
        private String id;

        private Long paymentId;

        private String issuedTo;

        static Receipt of(Long paymentId, String issuedTo) {
            Receipt receipt = new Receipt();
            receipt.setPaymentId(paymentId);
            receipt.setIssuedTo(issuedTo);
            return receipt;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Long getPaymentId() {
            return paymentId;
        }

        public void setPaymentId(Long paymentId) {
            this.paymentId = paymentId;
        }

        public String getIssuedTo() {
            return issuedTo;
        }

        public void setIssuedTo(String issuedTo) {
            this.issuedTo = issuedTo;
        }
    }
}
