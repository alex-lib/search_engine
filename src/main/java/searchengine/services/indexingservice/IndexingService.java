package searchengine.services.indexingservice;
import searchengine.dto.startIndexing.IndexingResponse;

import javax.swing.text.html.HTML;
import java.net.URL;

public interface IndexingService {
    IndexingResponse startIndexing();
    IndexingResponse stopIndexing();
    IndexingResponse indexPage(String htmlCode);
}