package searchengine.dto.searching;
import lombok.Data;
import java.util.Objects;

@Data
public class SearchingData {
private String site;
private String siteName;
private String url;
private String title;
private String snippet;
private float relevance;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchingData that = (SearchingData) o;
        return Float.compare(relevance, that.relevance) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(relevance);
    }
}