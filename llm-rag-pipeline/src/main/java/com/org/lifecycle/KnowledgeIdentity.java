package com.org.lifecycle;

import com.org.lifecycle.model.KnowledgeRequest;
import com.org.lifecycle.model.SourceType;

public class KnowledgeIdentity {

    /**
     * Builds the stable identity string ({@code PDF#name}, {@code WIKI#name}, or {@code DB#name})
     * for a knowledge request, or an empty string for an unrecognized source type.
     */
    public static String from(KnowledgeRequest request) {

        if (request.sourceType() == SourceType.PDF) {
            return "PDF#" + request.name();
        }

        if (request.sourceType() == SourceType.WIKI) {
            return "WIKI#" + request.name();
        }

        if (request.sourceType() == SourceType.DATABASE) {
            return "DB#" + request.name();
        }

        return "";
    }
}
