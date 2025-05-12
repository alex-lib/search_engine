package searchengine.services.searchingservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.dto.searching.SearchingResponse;
import searchengine.lemmafinder.LemmaFinder;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchingServiceImpl implements SearchingService {

    @Override
    public SearchingResponse search(String query, String site) {
        LemmaFinder lemmaFinder = new LemmaFinder();

        Set<String> lemmas = lemmaFinder.collectLemmas(query).keySet();

        return null;
    }
}