package searchengine.services.indexingservice;
import org.jsoup.nodes.Document;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaginationHandler {
    public static Set<String> discoverPaginatedUrls(Document doc, String baseUrl, String parentUrl) {
        Set<String> pageUrls = new HashSet<>();
        doc.select("a[onclick]").forEach(link -> {
            String onclick = link.attr("onclick");
            Matcher matcher = Pattern.compile("gotoPage\\((\\d+)\\);").matcher(onclick);
            if (matcher.find()) {
                String pageNum = matcher.group(1);
                String jsUrl = baseUrl.endsWith("/") ? baseUrl + "page=" + pageNum : baseUrl + "/page=" + pageNum + "/";
                pageUrls.add(jsUrl);
            }
        });

        String newParentUrl = parentUrl.substring(0, parentUrl.lastIndexOf("/"));
        doc.select("div.pagination").forEach(link -> {
            String href = newParentUrl + link.select("a").attr("href") + "/";
            pageUrls.add(href);
                });
        return pageUrls;
    }
}