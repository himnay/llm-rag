package com.org.lifecycle;

import com.org.lifecycle.model.KnowledgeRequest;
import com.org.lifecycle.model.SourceType;

public class KnowledgeIdentity {

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
