package searchengine.services.statisticsservice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.repositories.LemmaModelRepository;
import searchengine.repositories.PageModelRepository;
import searchengine.repositories.SiteModelRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    private final SitesList sites;
    private final PageModelRepository pageModelRepository;
    private final LemmaModelRepository lemmaModelRepository;
    private final SiteModelRepository siteModelRepository;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setPages(pageModelRepository.findAll().size());
        total.setLemmas(lemmaModelRepository.findAll().size());
        total.setIndexing(true);
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();
        for (Site site : sitesList) {
            DetailedStatisticsItem item = setStatisticToSite(site);
            detailed.add(item);
        }
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    private DetailedStatisticsItem setStatisticToSite(Site site) {
        DetailedStatisticsItem item = new DetailedStatisticsItem();
        item.setName(site.getName());
        item.setUrl(site.getUrl());
        if (!siteModelRepository.findAll().isEmpty()) {
            item.setPages((int) pageModelRepository.findAll().stream()
                    .filter(p -> p.getSite().getUrl().equals(site.getUrl()))
                    .count());
            item.setLemmas((int) lemmaModelRepository.findAll().stream()
                    .filter(l -> l.getSite().getUrl().equals(site.getUrl()))
                    .count());
            item.setStatus(String.valueOf(siteModelRepository.findByUrl(site.getUrl() + "/").getSiteStatus()));
            item.setError(siteModelRepository.findByUrl(site.getUrl() + "/").getLastError() != null ?
                    siteModelRepository.findByUrl(site.getUrl() + "/").getLastError()
                    : "");
            item.setStatusTime(siteModelRepository.findByUrl(site.getUrl()+ "/").getStatusTime());
        } else {
            item.setPages(0);
            item.setLemmas(0);
            item.setStatus("");
            item.setError("");
            item.setStatusTime(LocalDateTime.now());
        }
        return item;
    }
}