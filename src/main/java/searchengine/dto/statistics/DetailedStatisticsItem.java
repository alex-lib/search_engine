package searchengine.dto.statistics;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DetailedStatisticsItem {
    private String url;
    private String name;
    private String status;
    private LocalDateTime statusTime;
    private String error;
    private int pages;
    private int lemmas;
}
