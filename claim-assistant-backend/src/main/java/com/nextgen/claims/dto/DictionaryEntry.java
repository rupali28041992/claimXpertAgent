package com.nextgen.claims.dto;

/** One option in a GoRules dictionary — label shown in UI, value sent to the API. */
public class DictionaryEntry {
    private String label;
    private String value;

    public DictionaryEntry() {}
    public DictionaryEntry(String label, String value) {
        this.label = label;
        this.value = value;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
