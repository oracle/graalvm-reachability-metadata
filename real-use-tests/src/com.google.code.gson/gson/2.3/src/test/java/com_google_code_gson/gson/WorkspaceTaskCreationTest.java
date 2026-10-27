/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Gson's instance-creation subsystem to import tasks with constructor-injected workspace context.
public class WorkspaceTaskCreationTest {

    @Test
    @Timeout(60)
    public void importsTasksIntoTheActiveWorkspaceAndExportsAssignedTasks() {
        Workspace workspace = new Workspace("design", Set.of("alice", "bob"));
        InstanceCreator<Task> taskCreator = type -> new Task(workspace);
        Gson gson = new GsonBuilder().registerTypeAdapter(Task.class, taskCreator).create();

        Task[] tasks = gson.fromJson("""
                [
                  {"id":"T-41","title":"Review cover","workspace":{"slug":"other"}},
                  {"id":"T-42","title":"Approve proof"}
                ]
                """, Task[].class);

        assertEquals(2, tasks.length);
        assertSame(workspace, tasks[0].workspace);
        assertSame(workspace, tasks[1].workspace);
        assertEquals("Review cover", tasks[0].title);
        assertEquals("Approve proof", tasks[1].title);
        assertEquals("unassigned", tasks[0].assignee);
        assertEquals("/workspaces/design/tasks/T-41", tasks[0].path());
        assertEquals("/workspaces/design/tasks/T-42", tasks[1].path());

        assertThrows(IllegalArgumentException.class, () -> tasks[0].assignTo("outsider"));
        assertEquals("unassigned", tasks[0].assignee);
        tasks[0].assignTo("alice");
        tasks[1].assignTo("bob");

        String exported = gson.toJson(tasks);
        assertEquals(new JsonParser().parse("""
                [
                  {"id":"T-41","title":"Review cover","assignee":"alice"},
                  {"id":"T-42","title":"Approve proof","assignee":"bob"}
                ]
                """), new JsonParser().parse(exported));
        assertFalse(exported.contains("workspace"));

        Task[] restored = gson.fromJson(exported, Task[].class);
        assertSame(workspace, restored[0].workspace);
        assertEquals("alice", restored[0].assignee);
        assertEquals("bob", restored[1].assignee);
        assertEquals("/workspaces/design/tasks/T-41", restored[0].path());
        assertThrows(IllegalArgumentException.class, () -> restored[1].assignTo("outsider"));
    }

    public record Workspace(String slug, Set<String> members) {
    }

    public static class Task {
        private final transient Workspace workspace;
        private String id;
        private String title;
        private String assignee;

        public Task(Workspace workspace) {
            this.workspace = workspace;
            this.assignee = "unassigned";
        }

        public void assignTo(String member) {
            if (!workspace.members().contains(member)) {
                throw new IllegalArgumentException("Assignee must belong to the active workspace");
            }
            assignee = member;
        }

        public String path() {
            return "/workspaces/" + workspace.slug() + "/tasks/" + id;
        }
    }
}
