package searchengine.dto.searching;
import lombok.Data;

@Data
public class SearchingData implements Comparable<SearchingData> {
private String site;
private String siteName;
private String url;
private String title;
private String snippet;
private float relevance;

    @Override
    public int compareTo(SearchingData o) {
        return Float.compare(this.relevance, o.relevance);
    }
}