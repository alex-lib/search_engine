package searchengine.controllers;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.searching.SearchingResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.indexingservice.IndexingService;
import searchengine.services.searchingservice.SearchingService;
import searchengine.services.statisticsservice.StatisticsService;

import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ApiController {
    @Autowired
    private final StatisticsService statisticsService;
    @Autowired
    private final IndexingService indexingService;
    @Autowired
    private final SearchingService searchingService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing() {
        return ResponseEntity.ok(indexingService.startIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexPage(@RequestBody String htmlCode) {
        return ResponseEntity.ok(indexingService.indexPage(htmlCode));
    }

    @GetMapping("/search")
    public ResponseEntity<SearchingResponse> search(@RequestParam String query, @RequestParam(required = false) String site ) {
        return ResponseEntity.ok(searchingService.search(query, site));
    }
}