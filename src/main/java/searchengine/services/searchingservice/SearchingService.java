package searchengine.services.searchingservice;
import searchengine.dto.searching.SearchingResponse;

public interface SearchingService {
    SearchingResponse search(String query, String site);
}