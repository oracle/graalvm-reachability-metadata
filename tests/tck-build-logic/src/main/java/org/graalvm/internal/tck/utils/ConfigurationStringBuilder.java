package org.graalvm.internal.tck.utils;

public class ConfigurationStringBuilder {

    private final StringBuilder sb;
    private int indentationLevel;

    private boolean startedNewLine;

    public ConfigurationStringBuilder() {
        this.sb = new StringBuilder();
        this.indentationLevel = 0;
        this.startedNewLine = false;
    }


    public ConfigurationStringBuilder append(String content) {
        if (startedNewLine) {
            sb.append("\t".repeat(Math.max(0, indentationLevel)));
        }
        sb.append(content);
        startedNewLine = false;

        return this;
    }

    public ConfigurationStringBuilder newLine() {
        this.append("\n");
        startedNewLine = true;

        return this;
    }

    public ConfigurationStringBuilder indent() {
        indentationLevel++;
        return this;
    }

    public ConfigurationStringBuilder unindent() {
        if (indentationLevel > 0) {
            indentationLevel--;
        }

        return this;
    }

    public ConfigurationStringBuilder openObject() {
        return this.append("{");
    }

    public ConfigurationStringBuilder closeObject() {
        return this.append("}");
    }

    public ConfigurationStringBuilder openArray() {
        return this.append("[");
    }

    public ConfigurationStringBuilder closeArray() {
        return this.append("]");
    }

    public ConfigurationStringBuilder space() {
        return this.append(" ");
    }

    public ConfigurationStringBuilder concat() {
        return this.append(",");
    }

    public ConfigurationStringBuilder separateWithSemicolon() {
        return this.append(": ");
    }

    public ConfigurationStringBuilder separateWithEquals() {
        return this.append(" = ");
    }

    public ConfigurationStringBuilder quote(String text) {
        return this.append("\"").append(text).append("\"");
    }

    public ConfigurationStringBuilder putInBrackets(String text) {
        return this.append("(").append(text).append(")");
    }

    public ConfigurationStringBuilder quoteInBrackets(String text) {
        return this.append("(").quote(text).append(")");
    }

    public ConfigurationStringBuilder appendStringProperty(String key, String value) {
        return this.quote(key).separateWithSemicolon().quote(value);
    }

    public ConfigurationStringBuilder appendAssignedVariable(String variable, String value) {
        return this.append(variable).separateWithEquals().quote(value);
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
