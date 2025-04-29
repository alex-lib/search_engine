package searchengine.services.indexingservice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.startIndexing.IndexingResponse;
import searchengine.model.SiteModel;
import searchengine.model.Status;
import searchengine.repositories.PageModelRepository;
import searchengine.repositories.SiteModelRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService, InterruptionChecker {
    private final SitesList sites;
    private final PageModelRepository pageModelRepository;
    private final SiteModelRepository siteModelRepository;

    @Value("${visit-settings.userAgent}")
    private String userAgent;
    @Value("${visit-settings.referrer}")
    private String referrer;

    private boolean isInterrupted;

    @Override
    public IndexingResponse startIndexing() {
        isInterrupted = false;

        if (siteModelRepository.findAll().stream()
                .allMatch(siteModel -> siteModel.getStatus() == Status.INDEXING) &&
                !siteModelRepository.findAll().stream()
                        .toList()
                        .isEmpty()) {
            log.info("Sites are already indexing right now");
            return new IndexingResponse(false, "Indexing has already started");
        }
            deleteSites(siteModelRepository);
            initSites(sites);
            ForkJoinPool pool = new ForkJoinPool();
            List<ForkJoinTask<Void>> tasks = new ArrayList<>();

                siteModelRepository.findAll().forEach(siteModel -> {
                    if (isInterrupted) return;
                    ForkJoinTask<Void> task = pool.submit(() ->
                            new SavePagesPool
                                    (siteModel,
                                    siteModel.getUrl(),
                                    siteModelRepository,
                                    pageModelRepository,
                                    userAgent,
                                    referrer,
                                    this::isInterrupted).invoke()
                    );
                    tasks.add(task);
                });
                tasks.forEach(ForkJoinTask::join);
                pool.shutdown();

            if (!isInterrupted) {
                changeSiteStatusToIndexed(siteModelRepository);
                log.info("Sites have been indexed");
            }
            return new IndexingResponse(true, "");
        }

    private void deleteSites(SiteModelRepository siteModelRepository) {
        for (Site site : sites.getSites()) {
            siteModelRepository.deleteByName(site.getName());
        }
    }

    private void initSites(SitesList sites) {
        for (Site site : sites.getSites()) {
            SiteModel siteModel = SiteModel.builder()
                    .status(Status.INDEXING)
                    .statusTime(LocalDateTime.now())
                    .url(site.getUrl())
                    .name(site.getName()).build();
            siteModelRepository.saveAndFlush(siteModel);
            log.info("Site {} has been initialized successfully", site.getName());
        }
    }

    private void changeSiteStatusToIndexed(SiteModelRepository siteModelRepository) {
        siteModelRepository.findAll().stream()
                .filter(siteModel -> siteModel.getStatus() == Status.INDEXING)
                .forEach(siteModel -> {
                    siteModel.setStatus(Status.INDEXED);
                    siteModel.setStatusTime(LocalDateTime.now());
                    siteModelRepository.saveAndFlush(siteModel);
                    log.debug("Site {} has changed status to indexed", siteModel.getName());
        });
    }

    @Override
    public IndexingResponse stopIndexing() {
        if (siteModelRepository.findAll().stream()
                .allMatch(siteModel -> siteModel.getStatus() != Status.INDEXING)
                || siteModelRepository.findAll().stream().toList().isEmpty()) {
            log.info("Sites are not indexing right now");
            return new IndexingResponse(false, "Indexing not started");
        }
        isInterrupted = true;
        log.info("Indexing has been stopped by user");
        return new IndexingResponse(true, "");
    }

    @Override
    public boolean isInterrupted() {
        return isInterrupted;
    }
}