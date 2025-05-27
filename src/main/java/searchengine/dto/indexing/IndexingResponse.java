package searchengine.dto.indexing;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class IndexingResponse {
    private boolean result;
    private String error;
}