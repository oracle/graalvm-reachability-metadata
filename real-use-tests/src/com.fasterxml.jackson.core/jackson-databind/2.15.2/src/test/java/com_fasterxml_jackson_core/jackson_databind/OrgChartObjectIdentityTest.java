/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.UnresolvedForwardReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Object identity and references in cyclic object graphs: `@JsonIdentityInfo` with property and
 * integer-sequence id generators, `@JsonIdentityReference(alwaysAsId = true)`, forward references
 * and `@JsonManagedReference`/`@JsonBackReference` parent links.
 *
 * An HR service exports its organisation chart. Employees point to their manager and their
 * reports, so the graph is cyclic; several employees share one office; teams link back to their
 * department and name their lead only by employee id.
 */
public class OrgChartObjectIdentityTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void writesCyclicGraphWithEachObjectInFullOnlyOnce() throws Exception {
        Organization organization = sampleOrganization();

        JsonNode tree = mapper.readTree(mapper.writeValueAsString(organization));

        JsonNode alice = tree.get("staff").get(0);
        assertEquals("alice", alice.get("id").asText());
        assertEquals("Alice", alice.get("name").asText());
        assertTrue(alice.get("office").isObject());
        assertEquals(1, alice.get("office").get("@officeId").asInt());
        assertEquals("Berlin HQ", alice.get("office").get("site").asText());

        JsonNode bob = alice.get("reports").get(0);
        assertEquals("bob", bob.get("id").asText());
        assertEquals(1, bob.get("office").asInt());
        assertEquals("alice", bob.get("manager").asText());
        JsonNode carol = alice.get("reports").get(1);
        assertTrue(carol.get("office").isObject());
        assertEquals(2, carol.get("office").get("@officeId").asInt());

        assertEquals("bob", tree.get("staff").get(1).asText());
        assertEquals("carol", tree.get("staff").get(2).asText());

        JsonNode team = tree.get("departments").get(0).get("teams").get(0);
        assertEquals("Payments", team.get("name").asText());
        assertEquals("alice", team.get("lead").asText());
        assertFalse(team.has("department"));
    }

    @Test
    void roundTripRestoresSharedInstancesAndCycles() throws Exception {
        String json = mapper.writeValueAsString(sampleOrganization());

        Organization restored = mapper.readValue(json, Organization.class);

        Employee alice = restored.staff.get(0);
        Employee bob = restored.staff.get(1);
        Employee carol = restored.staff.get(2);
        assertNull(alice.manager);
        assertSame(alice, bob.manager);
        assertSame(alice, carol.manager);
        assertSame(bob, alice.reports.get(0));
        assertSame(carol, alice.reports.get(1));
        assertSame(alice.office, bob.office);
        assertEquals("Lisbon Hub", carol.office.site);

        Department engineering = restored.departments.get(0);
        assertEquals(2, engineering.teams.size());
        for (Team team : engineering.teams) {
            assertSame(engineering, team.department);
        }
        assertSame(alice, engineering.teams.get(0).lead);
        assertSame(carol, engineering.teams.get(1).lead);
    }

    @Test
    void resolvesIdsThatAppearBeforeTheirObjects() throws Exception {
        String json = """
                {
                  "departments": [
                    {"name": "Support", "teams": [{"name": "Tier 1", "lead": "dave"}]}
                  ],
                  "staff": [
                    {"id": "erin", "name": "Erin", "office": 7, "manager": "dave", "reports": []},
                    {"id": "dave", "name": "Dave", "office": {"@officeId": 7, "site": "Remote"},
                     "manager": null, "reports": ["erin"]}
                  ]
                }
                """;

        Organization organization = mapper.readValue(json, Organization.class);

        Employee erin = organization.staff.get(0);
        Employee dave = organization.staff.get(1);
        assertSame(dave, erin.manager);
        assertSame(erin, dave.reports.get(0));
        assertSame(dave.office, erin.office);
        assertEquals("Remote", erin.office.site);
        Team tier1 = organization.departments.get(0).teams.get(0);
        assertSame(dave, tier1.lead);
        assertSame(organization.departments.get(0), tier1.department);
    }

    @Test
    void reportsIdsThatNeverResolve() {
        String json = """
                {
                  "staff": [{"id": "frank", "name": "Frank", "manager": "ghost", "reports": []}],
                  "departments": []
                }
                """;

        UnresolvedForwardReference failure = assertThrows(UnresolvedForwardReference.class,
                () -> mapper.readValue(json, Organization.class));

        assertEquals(1, failure.getUnresolvedIds().size());
        assertEquals("ghost", failure.getUnresolvedIds().get(0).getId());
        assertSame(Employee.class, failure.getUnresolvedIds().get(0).getType());
    }

    private static Organization sampleOrganization() {
        Office berlin = new Office("Berlin HQ");
        Office lisbon = new Office("Lisbon Hub");
        Employee alice = new Employee("alice", "Alice", berlin);
        Employee bob = new Employee("bob", "Bob", berlin);
        Employee carol = new Employee("carol", "Carol", lisbon);
        alice.addReport(bob);
        alice.addReport(carol);

        Department engineering = new Department("Engineering");
        engineering.addTeam(new Team("Payments", alice));
        engineering.addTeam(new Team("Search", carol));

        Organization organization = new Organization();
        organization.staff.addAll(List.of(alice, bob, carol));
        organization.departments.add(engineering);
        return organization;
    }

    /// The exported document: everyone on staff plus the department structure.
    @JsonPropertyOrder({"staff", "departments"})
    public static class Organization {
        public List<Employee> staff = new ArrayList<>();
        public List<Department> departments = new ArrayList<>();
    }

    /// A person identified by their own `id` property, linked to a manager and to their reports.
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    @JsonPropertyOrder({"id", "name", "office", "manager", "reports"})
    public static class Employee {
        public String id;
        public String name;
        public Office office;
        public Employee manager;
        public List<Employee> reports = new ArrayList<>();

        public Employee() {
        }

        Employee(String id, String name, Office office) {
            this.id = id;
            this.name = name;
            this.office = office;
        }

        void addReport(Employee report) {
            report.manager = this;
            reports.add(report);
        }
    }

    /// A shared office, numbered by Jackson in the order offices are first written.
    @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@officeId")
    public static class Office {
        public String site;

        public Office() {
        }

        Office(String site) {
            this.site = site;
        }
    }

    /// A department that owns its teams; the teams' parent link is restored on read.
    public static class Department {
        public String name;
        @JsonManagedReference
        public List<Team> teams = new ArrayList<>();

        public Department() {
        }

        Department(String name) {
            this.name = name;
        }

        void addTeam(Team team) {
            team.department = this;
            teams.add(team);
        }
    }

    /// A team inside a department whose lead is always written as an employee id.
    @JsonPropertyOrder({"name", "lead"})
    public static class Team {
        public String name;
        @JsonBackReference
        public Department department;
        @JsonIdentityReference(alwaysAsId = true)
        public Employee lead;

        public Team() {
        }

        Team(String name, Employee lead) {
            this.name = name;
            this.lead = lead;
        }
    }
}
