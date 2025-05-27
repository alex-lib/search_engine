package searchengine.services.indexingservice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.model.SiteStatus;
import searchengine.repositories.IndexModelRepository;
import searchengine.repositories.LemmaModelRepository;
import searchengine.repositories.PageModelRepository;
import searchengine.repositories.SiteModelRepository;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveAction;

@RequiredArgsConstructor
@Slf4j
public class SaverPagesPool extends RecursiveAction {
    private final SiteModel siteModel;
    private final String url;
    private final SiteModelRepository siteModelRepository;
    private final PageModelRepository pageModelRepository;
    private final String userAgent;
    private final String referrer;
    private final InterruptionChecker interruptionChecker;
    private final LemmaModelRepository lemmaModelRepository;
    private final IndexModelRepository indexModelRepository;

    @Override
    protected void compute() {
        String relativePath = getRelativePath(url);
        synchronized (pageModelRepository) {
            if (pageModelRepository.existsBySiteAndPath(siteModel, relativePath)) {
                log.debug("Skipping existing page: {}", url);
                return;
            }
        }
        try {
            Document document = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .timeout(6000)
                    .get();
            PageModel pageModel = createAndSavePageModel(document, relativePath);
            siteModel.setStatusTime(LocalDateTime.now());
            siteModelRepository.saveAndFlush(siteModel);
            if (pageModel != null && pageModel.getSite().getSiteStatus() == SiteStatus.INDEXING) {
                new SaverLemmasAndIndexes(lemmaModelRepository, indexModelRepository, pageModel).saveLemmaAndIndex();
                processPaginatedContent(document, url);
                processPageLinks(document);
            }
        } catch (SocketTimeoutException socketTimeoutException) {
            log.warn("Timeout accessing {}: {}", url, socketTimeoutException.getMessage());
        } catch (HttpStatusException httpStatusException) {
            log.warn("Url fetching error for {}: {}", url, httpStatusException.getMessage());
        } catch (Exception exception) {
            log.warn("Page {} processing failed: {}", url, exception.getMessage());
            changeSiteStatusToFailedByError(siteModelRepository, exception.getMessage());
        } finally {
            siteModelRepository.flush();
            pageModelRepository.flush();
        }
    }

    private void processPageLinks(Document document) {
        List<SaverPagesPool> taskList = new ArrayList<>();
        Set<String> visitedUrls = new HashSet<>();
        for (Element element : document.select("a[href]")) {
            String childUrl = element.absUrl("href");

            if (interruptionChecker.isInterrupted()) {
                changeSiteStatusToFailedByStop(siteModelRepository);
                log.info("Interrupted processing site: {}", siteModel.getName());
                return;
            }

            if (isValidUrl(childUrl) && !visitedUrls.contains(childUrl)) {
                visitedUrls.add(childUrl);
                SaverPagesPool task = new SaverPagesPool(
                        siteModel,
                        childUrl,
                        siteModelRepository,
                        pageModelRepository,
                        userAgent,
                        referrer,
                        interruptionChecker,
                        lemmaModelRepository,
                        indexModelRepository);
                task.fork();
                taskList.add(task);
            }
        }
        taskList.forEach(SaverPagesPool::join);
    }

    private boolean isValidUrl(String url) {
        return url.startsWith(siteModel.getUrl()) &&
                !url.contains("#") &&
                !url.matches("(?iu).*\\.(pdf|jpg|png|gif|zip|webp|jpeg|eps|doc|xlsx)$");
    }

    private PageModel createAndSavePageModel(Document document, String relativePath) {
        synchronized (pageModelRepository) {
                log.debug("Created new page: {}", relativePath);
                PageModel pageModel = PageModel.builder()
                        .path(relativePath)
                        .code(document.connection().response().statusCode())
                        .content(document.outerHtml())
                        .site(siteModel)
                        .build();
                pageModelRepository.saveAndFlush(pageModel);
                return pageModel;
        }
    }

    private void changeSiteStatusToFailedByStop(SiteModelRepository siteModelRepository) {
        siteModelRepository.findAll().forEach(siteModel -> {
            siteModel.setSiteStatus(SiteStatus.FAILED);
            siteModel.setStatusTime(LocalDateTime.now());
            siteModel.setLastError("Indexing has been stopped by user");
            siteModelRepository.saveAndFlush(siteModel);
        });
    }

    private void changeSiteStatusToFailedByError(SiteModelRepository siteModelRepository, String exception) {
        siteModel.setSiteStatus(SiteStatus.FAILED);
        siteModel.setLastError(exception);
        siteModel.setStatusTime(LocalDateTime.now());
        siteModelRepository.saveAndFlush(siteModel);
    }

    private void processPaginatedContent(Document document, String baseUrl) {
        Set<String> paginatedUrls = PaginationHandler.discoverPaginatedUrls(document, baseUrl, siteModel.getUrl());
        if (!paginatedUrls.isEmpty()) {
            paginatedUrls.forEach(pageUrl -> {
                String relativePath = getRelativePath(pageUrl);
                if(!isValidUrl(pageUrl) || pageModelRepository.existsBySiteAndPath(siteModel, relativePath)) {
                    return;
                }
                log.info("Processing paginated page {})", pageUrl);
                try {
                        Document pageDocument = Jsoup.connect(pageUrl)
                                .userAgent(userAgent)
                                .referrer(referrer)
                                .timeout(6000)
                                .get();

                        PageModel pageModel = createAndSavePageModel(pageDocument, relativePath);
                        new SaverLemmasAndIndexes(lemmaModelRepository, indexModelRepository, pageModel).saveLemmaAndIndex();
                        processPageLinks(pageDocument);
                } catch (Exception e) {
                    log.error("Page {} processing failed: {}", pageUrl, e.getMessage());
                }
            });
        }
    }

    private String getRelativePath(String pageUrl) {
        if (siteModel.getUrl().length() <= pageUrl.length()) {
            return siteModel.getUrl().length() == pageUrl.length() ? "/" : pageUrl.substring(siteModel.getUrl().length() - 1);
        }
        return null;
    }
}