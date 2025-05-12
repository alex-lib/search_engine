package searchengine.dto.searching;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchingResponse {
    private boolean result;
    private int count;
    private String error;
    private List<SearchingData> searchingData;
}
