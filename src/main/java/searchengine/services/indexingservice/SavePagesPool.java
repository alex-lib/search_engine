package searchengine.services.indexingservice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.model.Status;
import searchengine.repositories.PageModelRepository;
import searchengine.repositories.SiteModelRepository;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;

@RequiredArgsConstructor
@Slf4j
public class SavePagesPool extends RecursiveTask<Void> {
    private final SiteModel siteModel;
    private final String url;
    private final SiteModelRepository siteModelRepository;
    private final PageModelRepository pageModelRepository;
    private final String userAgent;
    private final String referrer;
    private final InterruptionChecker interruptionChecker;

    @Override
    protected Void compute() {
        synchronized (pageModelRepository) {
            String relativePath = getRelativePath(url);
            if (pageModelRepository.existsBySiteAndPath(siteModel, relativePath)) {
                log.debug("Skipping existing page: {}", url);
                return null;
            }
        }

        try {
            Document document = Jsoup.connect(url.toString())
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .timeout(5000)
                    .ignoreHttpErrors(true)
                    .get();

            List<SavePagesPool> taskList = new ArrayList<>();

            for (Element element : document.select("a[href]")) {
                String childUrl = element.absUrl("href");
                String relativePath = getRelativePath(childUrl);

                if (interruptionChecker.isInterrupted()) {
                    changeSiteStatusToFailedStop(siteModelRepository);
                    log.info("Interrupted processing site: {}", siteModel.getName());
                    return null;
                }

                if (isValidUrl(childUrl) && !pageModelRepository.existsBySiteAndPath(siteModel, relativePath)) {
                    SavePagesPool task = new SavePagesPool(
                            siteModel,
                            childUrl,
                            siteModelRepository,
                            pageModelRepository,
                            userAgent,
                            referrer,
                            interruptionChecker);

                    task.fork();
                    taskList.add(task);
                    PageModel pageModel = createPageModel(document, relativePath);
                    savePageModel(pageModel);
                    siteModel.setStatusTime(LocalDateTime.now());
                    siteModelRepository.save(siteModel);
                }
            }
            taskList.forEach(SavePagesPool::join);
        } catch (SocketTimeoutException socketTimeoutException) {
            changeSiteStatusToFiledError(siteModelRepository, socketTimeoutException.getMessage());
            log.error("Timeout accessing {}: {}", url, socketTimeoutException.getMessage());
        } catch (IOException ioException) {
            changeSiteStatusToFiledError(siteModelRepository, ioException.getMessage());
            log.error("Error processing {}: {}", url, ioException.getMessage());
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
                !url.matches(".*\\.(pdf|jpg|png|gif|zip|JPG)$");
    }

    private PageModel createPageModel(Document document, String childUrl) {
        log.debug("Created new page: {}", childUrl);
        return PageModel.builder()
                .path(childUrl)
                .code(document.connection().response().statusCode())
                .content(document.outerHtml())
                .site(siteModel)
                .build();
    }

    private void savePageModel(PageModel pageModel) {
        synchronized (pageModelRepository) {
            if (!pageModelRepository.existsBySiteAndPath(siteModel, pageModel.getPath())) {
                pageModelRepository.saveAndFlush(pageModel);
                log.debug("Saved new page: {} for {}", pageModel.getPath(), pageModel.getSite());
            } else {
                log.debug("Skipping existing page in DB: {} for {}", pageModel.getPath(), pageModel.getSite());
            }
        }
    }

    private void changeSiteStatusToFailedStop(SiteModelRepository siteModelRepository) {
        siteModelRepository.findAll().forEach(siteModel -> {
            siteModel.setStatus(Status.FAILED);
            siteModel.setStatusTime(LocalDateTime.now());
            siteModel.setLastError("Indexing has been stopped by user");
            siteModelRepository.saveAndFlush(siteModel);
        });
    }

    private void changeSiteStatusToFiledError(SiteModelRepository siteModelRepository, String exception) {
        siteModel.setStatus(Status.FAILED);
        siteModel.setLastError(exception);
        siteModel.setStatusTime(LocalDateTime.now());
        siteModelRepository.saveAndFlush(siteModel);
    }
}