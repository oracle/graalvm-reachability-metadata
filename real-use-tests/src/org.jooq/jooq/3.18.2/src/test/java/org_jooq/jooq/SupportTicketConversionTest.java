/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jooq.jooq;

import org.jooq.Converter;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Records;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises jOOQ's type-conversion subsystem to persist domain-typed support tickets in a legacy schema.
 */
public class SupportTicketConversionTest {
    private static final Converter<String, TicketState> STATE_CONVERTER = Converter.ofNullable(
            String.class, TicketState.class, TicketState::fromCode, state -> state.code);
    private static final Converter<String, Email> EMAIL_CONVERTER = Converter.ofNullable(
            String.class, Email.class, Email::new, Email::value);
    private static final Table<Record> TICKETS = DSL.table(DSL.name("support_ticket"));
    private static final Field<Integer> ID = DSL.field(DSL.name("ticket_id"), SQLDataType.INTEGER);
    private static final Field<TicketState> STATE = DSL.field(DSL.name("state_code"),
            SQLDataType.VARCHAR(1).asConvertedDataType(STATE_CONVERTER));
    private static final Field<Email> ASSIGNEE = DSL.field(DSL.name("assignee"),
            SQLDataType.VARCHAR(120).asConvertedDataType(EMAIL_CONVERTER));

    @Test
    @Timeout(60)
    public void claimsAndClosesTicketsUsingDomainValues() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:support_ticket_conversion", "sa", "")) {
            DSLContext database = DSL.using(connection, SQLDialect.H2);
            database.createTable(TICKETS)
                    .column(ID, ID.getDataType().nullable(false))
                    .column(STATE, STATE.getDataType().nullable(false))
                    .column(ASSIGNEE)
                    .primaryKey(ID)
                    .execute();

            Email ada = new Email("ada@example.test");
            Email grace = new Email("grace@example.test");
            database.insertInto(TICKETS, ID, STATE, ASSIGNEE)
                    .values(101, TicketState.OPEN, ada)
                    .values(102, TicketState.OPEN, (Email) null)
                    .values(103, TicketState.CLOSED, grace)
                    .execute();

            assertEquals(List.of(
                    new Ticket(101, TicketState.OPEN, ada),
                    new Ticket(102, TicketState.OPEN, null),
                    new Ticket(103, TicketState.CLOSED, grace)), tickets(database));

            // The queue predicate and assignment both use domain values, not storage strings.
            assertEquals(1, database.update(TICKETS)
                    .set(ASSIGNEE, grace)
                    .where(STATE.eq(TicketState.OPEN).and(ASSIGNEE.isNull()))
                    .execute());
            assertEquals(List.of(102, 103), database.select(ID).from(TICKETS)
                    .where(ASSIGNEE.eq(grace)).orderBy(ID).fetch(ID));

            // Closing a ticket releases its assignee; nullable conversion works on writes and reads.
            assertEquals(1, database.update(TICKETS)
                    .set(STATE, TicketState.CLOSED)
                    .setNull(ASSIGNEE)
                    .where(ID.eq(101))
                    .execute());
            assertEquals(List.of(
                    new Ticket(101, TicketState.CLOSED, null),
                    new Ticket(102, TicketState.OPEN, grace),
                    new Ticket(103, TicketState.CLOSED, grace)), tickets(database));
            assertEquals(List.of(102), database.select(ID).from(TICKETS)
                    .where(STATE.eq(TicketState.OPEN)).orderBy(ID).fetch(ID));

            // Existing consumers of the legacy schema still see its original column representation.
            Field<String> storedState = DSL.field(DSL.name("state_code"), String.class);
            Field<String> storedAssignee = DSL.field(DSL.name("assignee"), String.class);
            assertEquals(List.of("C", "O", "C"), database.select(storedState).from(TICKETS)
                    .orderBy(ID).fetch(storedState));
            assertEquals("grace@example.test", database.select(storedAssignee).from(TICKETS)
                    .where(ID.eq(102)).fetchSingle(storedAssignee));
        }
    }

    private static List<Ticket> tickets(DSLContext database) {
        return database.select(ID, STATE, ASSIGNEE).from(TICKETS)
                .orderBy(ID).fetch(Records.mapping(Ticket::new));
    }

    private enum TicketState {
        OPEN("O"), CLOSED("C");

        private final String code;

        TicketState(String code) {
            this.code = code;
        }

        private static TicketState fromCode(String code) {
            return switch (code) {
                case "O" -> OPEN;
                case "C" -> CLOSED;
                default -> throw new IllegalArgumentException("Unknown ticket state: " + code);
            };
        }
    }

    private record Email(String value) {
    }

    private record Ticket(int id, TicketState state, Email assignee) {
    }
}
