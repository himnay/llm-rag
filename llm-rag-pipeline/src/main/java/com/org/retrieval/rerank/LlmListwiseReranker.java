package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Listwise LLM reranking (RankGPT-style): the chat LLM sees all candidates at once and returns the
 * permutation of indices ordered by relevance. One LLM call regardless of candidate count, and the
 * model can compare documents against each other (which pointwise grading cannot). Candidates are
 * truncated to bound prompt size; indices missing from the reply keep their original relative order
 * at the tail, so a sloppy reply never loses documents.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmListwiseReranker implements Reranker {

    private static final Pattern INDEX = Pattern.compile("\\d+");

    private static final String PROMPT = """
            You rank search results. Order the documents below from most to least relevant \
            to the query. Respond with ONLY the document numbers, comma-separated \
            (e.g. "2, 0, 1"). Include every number exactly once.
            
            Query: %s
            
            %s
            """;

    private final ChatClient chatClient;

    @Override
    public RerankStrategy strategy() {
        return RerankStrategy.LLM_LISTWISE;
    }

    @Override
    public List<Chunk> rerank(String query, List<Chunk> chunks) {
        StringBuilder documents = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            documents.append("Document ").append(i).append(":\n")
                    .append(LlmPointwiseReranker.truncate(chunks.get(i).content())).append("\n\n");
        }
        String reply = chatClient.prompt()
                .user(PROMPT.formatted(query, documents))
                .call()
                .content();

        List<Chunk> reranked = new ArrayList<>(chunks.size());
        Set<Integer> seen = new LinkedHashSet<>();
        Matcher m = INDEX.matcher(reply == null ? "" : reply);
        while (m.find()) {
            int index = Integer.parseInt(m.group());
            if (index >= 0 && index < chunks.size() && seen.add(index)) {
                reranked.add(chunks.get(index));
            }
        }
        for (int i = 0; i < chunks.size(); i++) {     // anything the LLM dropped keeps its order
            if (!seen.contains(i)) {
                reranked.add(chunks.get(i));
            }
        }
        // Rank-derived score so ScoreAwareRanker preserves the permutation downstream.
        for (int rank = 0; rank < reranked.size(); rank++) {
            Reranker.score(reranked.get(rank), (double) (reranked.size() - rank) / reranked.size());
        }
        return reranked;
    }
}
