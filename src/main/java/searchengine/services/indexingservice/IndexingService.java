package searchengine.services.indexingservice;
import searchengine.dto.startIndexing.IndexingResponse;

public interface IndexingService {
    IndexingResponse startIndexing();
    IndexingResponse stopIndexing();
}
