/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_faces.jakarta_faces_api;

import jakarta.faces.application.ApplicationWrapper;
import jakarta.faces.application.ProjectStage;
import jakarta.faces.convert.Converter;
import jakarta.faces.convert.NumberConverter;

final class TestApplication extends ApplicationWrapper {
    private String messageBundle;

    TestApplication() {
        super(null);
    }

    @Override
    public String getMessageBundle() {
        return messageBundle;
    }

    @Override
    public void setMessageBundle(String messageBundle) {
        this.messageBundle = messageBundle;
    }

    @Override
    public Converter<?> createConverter(String converterId) {
        if (NumberConverter.CONVERTER_ID.equals(converterId)) {
            return new NumberConverter();
        }
        throw new UnsupportedOperationException("Unsupported test converter: " + converterId);
    }

    @Override
    public ProjectStage getProjectStage() {
        return ProjectStage.Production;
    }
}
