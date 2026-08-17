package com.nextgen.claims.agent;

/**
 * Spring AI 1.0.0-M6's ChatClient renders the ENTIRE user prompt as a StringTemplate-v4
 * template (default delimiters "&lt;" / "&gt;") whenever any advisor param is present -
 * which .entity(Class) triggers implicitly via its "spring_ai_soc_format" param, even though
 * we never call .param(...) ourselves. Any literal "&lt;"/"&gt;" in free-form text (OCR'd
 * document content, customer answers, extracted policy clauses) then gets parsed as template
 * syntax and throws STException. Escape those characters in anything not authored by us
 * before it goes into a prompt bound for .entity(...).
 */
public final class PromptTextSanitizer {

    private PromptTextSanitizer() {
    }

    public static String sanitize(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("\\", "\\\\")
                .replace("<", "\\<")
                .replace(">", "\\>");
    }
}
