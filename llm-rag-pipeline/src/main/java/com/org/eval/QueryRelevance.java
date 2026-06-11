package com.org.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One labelled entry in the gold evaluation set ({@code eval/qrels.json}).
 *
 * @param query           the user query to evaluate
 * @param relevantSources source labels considered relevant — matched (case-insensitive,
 *                        substring) against a chunk's {@code fileName} / {@code identity} /
 *                        {@code source} metadata. e.g. {@code "HR_Leave_Policy.pdf"}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QueryRelevance(String query, List<String> relevantSources) {

    public List<String> relevantSources() {
        return relevantSources == null ? List.of() : relevantSources;
    }
}
