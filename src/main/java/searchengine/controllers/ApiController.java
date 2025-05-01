package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.startIndexing.IndexingResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.indexingservice.IndexingService;
import searchengine.services.statisticsservice.StatisticsService;

import javax.swing.text.html.HTML;
import java.net.URL;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ApiController {
    @Autowired
    private final StatisticsService statisticsService;
    @Autowired
    private final IndexingService IndexingService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing() {
        return ResponseEntity.ok(IndexingService.startIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        return ResponseEntity.ok(IndexingService.stopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexPage(@RequestBody String htmlCode) {
        return ResponseEntity.ok(IndexingService.indexPage(htmlCode));
    }
}