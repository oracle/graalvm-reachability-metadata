/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_faces.jakarta_faces_api;

import jakarta.faces.application.ProjectStage;
import jakarta.faces.event.PhaseId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FacesContextTest {
    @Test
    void contextTracksPhaseAndEventProcessing() {
        try (TestFacesContext context = new TestFacesContext()) {
            assertTrue(context.isProcessingEvents());
            context.setProcessingEvents(false);
            assertFalse(context.isProcessingEvents());
            context.setCurrentPhaseId(PhaseId.PROCESS_VALIDATIONS);
            assertEquals(PhaseId.PROCESS_VALIDATIONS, context.getCurrentPhaseId());
            context.setCurrentPhaseId(PhaseId.RENDER_RESPONSE);
            assertEquals(PhaseId.RENDER_RESPONSE, context.getCurrentPhaseId());
            context.setProcessingEvents(true);
            assertTrue(context.isProcessingEvents());
            assertTrue(context.isProjectStage(ProjectStage.Production));
            assertFalse(context.isProjectStage(ProjectStage.Development));
        }
    }
}
