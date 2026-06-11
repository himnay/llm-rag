package com.org.chunking;

import com.org.chunking.model.Chunk;
import com.org.ingestion.model.IngestedDocument;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseChunker {

    public List<Chunk> chunk(IngestedDocument document) {
        return List.of(
                new Chunk(
                        document.source(),
                        document.content(),
                        document.metadata(),
                        0
                ));
    }
}
