package searchengine.services.indexingservice;
import searchengine.dto.indexing.IndexingResponse;

public interface IndexingService {
    IndexingResponse startIndexing();
    IndexingResponse stopIndexing();
    IndexingResponse indexPage(String htmlCode);
}