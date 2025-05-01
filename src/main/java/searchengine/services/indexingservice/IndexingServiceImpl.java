package searchengine.services.indexingservice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.startIndexing.IndexingResponse;
import searchengine.lemmafinder.LemmaFinder;
import searchengine.model.*;
import searchengine.repositories.IndexModelRepository;
import searchengine.repositories.LemmaModelRepository;
import searchengine.repositories.PageModelRepository;
import searchengine.repositories.SiteModelRepository;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService, InterruptionChecker {
    private final SitesList sites;
    private final PageModelRepository pageModelRepository;
    private final SiteModelRepository siteModelRepository;
    private final LemmaModelRepository lemmaModelRepository;
    private final IndexModelRepository indexModelRepository;
    @Value("${visit-settings.userAgent}")
    private String userAgent;
    @Value("${visit-settings.referrer}")
    private String referrer;
    private boolean isInterrupted;

    @Override
    public IndexingResponse startIndexing() {
        isInterrupted = false;

        if (siteModelRepository.findAll().stream()
                .allMatch(siteModel -> siteModel.getSiteStatus() == SiteStatus.INDEXING) &&
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
                    new SavePagePool
                            (siteModel,
                                    siteModel.getUrl(),
                                    siteModelRepository,
                                    pageModelRepository,
                                    userAgent,
                                    referrer,
                                    this::isInterrupted).invoke());
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
                    .siteStatus(SiteStatus.INDEXING)
                    .statusTime(LocalDateTime.now())
                    .url(site.getUrl())
                    .name(site.getName()).build();
            siteModelRepository.saveAndFlush(siteModel);
            log.info("Site {} has been initialized successfully", site.getName());
        }
    }

    private void changeSiteStatusToIndexed(SiteModelRepository siteModelRepository) {
        siteModelRepository.findAll().stream()
                .filter(siteModel -> siteModel.getSiteStatus() == SiteStatus.INDEXING)
                .forEach(siteModel -> {
                    siteModel.setSiteStatus(SiteStatus.INDEXED);
                    siteModel.setStatusTime(LocalDateTime.now());
                    siteModelRepository.saveAndFlush(siteModel);
                    log.debug("Site {} has changed status to indexed", siteModel.getName());
                });
    }

    @Override
    public IndexingResponse stopIndexing() {
        if (siteModelRepository.findAll().stream()
                .allMatch(siteModel -> siteModel.getSiteStatus() != SiteStatus.INDEXING)
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

    @Override
    public IndexingResponse indexPage(String htmlCode) {
        String decodedHtmlCode = urlDecoding(htmlCode);
        log.info("Indexing page: {}", decodedHtmlCode);

        for (SiteModel siteModel : siteModelRepository.findAll()) {
            String url = siteModel.getUrl();

            if (decodedHtmlCode.contains(url)) {
                log.debug("Found site: {}", siteModel.getName());
                String childUrl = decodedHtmlCode.substring(url.length() - 1);
                log.debug("Target url: {}", childUrl);
                deletePageIfExisted(siteModel, childUrl);

                SavePagePool task = new SavePagePool(siteModel,
                        decodedHtmlCode,
                        siteModelRepository,
                        pageModelRepository,
                        userAgent,
                        referrer,
                        this::isInterrupted);

                ForkJoinPool pool = new ForkJoinPool();
                pool.invoke(task);

                PageModel pageModel = pageModelRepository.findByPath(childUrl);
                collectLemmasAndIndexes(siteModel, pageModel);
                log.info("Page has been indexed successfully {}", decodedHtmlCode);
                return new IndexingResponse(true, "");
            }
        }
        log.info("Site {} is not found", decodedHtmlCode);
        return new IndexingResponse(false,
                "This page is outside the sites specified in the configuration file");
    }


    private String urlDecoding(String htmlCode) {
        return URLDecoder.decode(htmlCode, StandardCharsets.UTF_8).replaceAll("url=", "");
    }

    private void deletePageIfExisted(SiteModel siteModel, String childUrl) {
        if (pageModelRepository.existsBySiteAndPath(siteModel, childUrl))
            pageModelRepository.deleteBySiteAndPath(siteModel, childUrl);
    }

    private void collectLemmasAndIndexes(SiteModel siteModel, PageModel pageModel) {
        try {
            LemmaFinder lemmaFinder = new LemmaFinder();
            HashMap<String, Integer> lemmas = lemmaFinder.collectLemmas(pageModel.getContent());

            for (Map.Entry<String, Integer> lemma : lemmas.entrySet()) {
                if (lemmaModelRepository.existsBySiteAndLemma(siteModel, lemma.getKey())) {
                    LemmaModel lemmaModelFromDb = lemmaModelRepository.findBySiteAndLemma(siteModel, lemma.getKey());
                    lemmaModelFromDb.setFrequency(lemma.getValue() + lemmaModelFromDb.getFrequency());
                    lemmaModelRepository.saveAndFlush(lemmaModelFromDb);
                }
                LemmaModel lemmaModel = buildAndSaveLemma(siteModel, lemma);
                buildAndSaveIndex(pageModel, lemmaModel);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private LemmaModel buildAndSaveLemma(SiteModel siteModel, Map.Entry<String, Integer> lemma) {
        LemmaModel lemmaModel = LemmaModel.builder()
                .site(siteModel)
                .lemma(lemma.getKey())
                .frequency(lemma.getValue())
                .build();
        lemmaModelRepository.saveAndFlush(lemmaModel);
        return lemmaModel;
    }

    private void buildAndSaveIndex(PageModel pageModel, LemmaModel lemmaModel) {
        IndexModel indexModel = IndexModel.builder()
                .page(pageModel)
                .rankScore(1.0f)
                .lemma(lemmaModel)
                .build();
        indexModelRepository.saveAndFlush(indexModel);
    }
}