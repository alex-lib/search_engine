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
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;

@RequiredArgsConstructor
@Slf4j
public class SavePagePool extends RecursiveTask<Void> {
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
    protected Void compute() {
        try {

            if (!url.endsWith("/")) {
                return null;
            }

            Document document = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .timeout(6000)
                    .get();

            PageModel pageModel = createAndSavePageModel(document, url);

            siteModel.setStatusTime(LocalDateTime.now());
            siteModelRepository.saveAndFlush(siteModel);

            if (pageModel != null) {
                SaveLemmaAndIndex saveLemmaAndIndex = new SaveLemmaAndIndex(lemmaModelRepository, indexModelRepository, pageModel);
                saveLemmaAndIndex.saveLemmaAndIndex();
            }

            List<SavePagePool> taskList = new ArrayList<>();

            for (Element element : document.select("a[href]")) {
                String childUrl = element.absUrl("href");

                if (interruptionChecker.isInterrupted()) {
                    changeSiteStatusToFailedByStop(siteModelRepository);
                    log.info("Interrupted processing site: {}", siteModel.getName());
                    return null;
                }

                if (isValidUrl(childUrl) && !pageModelRepository.existsBySiteAndPath(siteModel, getRelativePath(childUrl))) {
                    SavePagePool task = new SavePagePool(
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
            taskList.forEach(SavePagePool::join);
        } catch (SocketTimeoutException socketTimeoutException) {
            log.warn("Timeout accessing {}: {}", url, socketTimeoutException.getMessage());
        } catch (HttpStatusException httpStatusException) {
            log.warn("Url fetching error for {}: {}", url, httpStatusException.getMessage());
        } catch (Exception exception) {
            changeSiteStatusToFailedByError(siteModelRepository, exception.getMessage());
            log.error("Error processing {}: {}", url, exception.getMessage());
        } finally {
            siteModelRepository.flush();
            pageModelRepository.flush();
        }
        return null;
    }

    private String getRelativePath(String url) {
        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getPath();
        } catch (MalformedURLException e) {
            log.debug("Malformed URL: {}", url);
            return url;
        }
    }

    private boolean isValidUrl(String url) {
        return url.startsWith(siteModel.getUrl()) &&
                !url.contains("#") &&
                !url.matches(".*\\.(pdf|jpg|png|gif|zip|JPG|webp|PDF|jpeg|eps|doc|xlsx)$");
    }

    private PageModel createAndSavePageModel(Document document, String childUrl) {
        synchronized (pageModelRepository) {
            if (!pageModelRepository.existsBySiteAndPath(siteModel, getRelativePath(childUrl))) {
                log.debug("Created new page: {}", childUrl);
                PageModel pageModel = PageModel.builder()
                        .path(getRelativePath(childUrl))
                        .code(document.connection().response().statusCode())
                        .content(document.outerHtml())
                        .site(siteModel)
                        .build();
                pageModelRepository.saveAndFlush(pageModel);
                return pageModel;
            } else {
                log.debug("Skipping existing page: {}", url);
                return null;
            }
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
}