package searchengine.model;
import jakarta.persistence.*;
import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "indexes")
public class IndexModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "INT")
    private Integer id;
    @ManyToOne
    @JoinColumn(name = "page_id", referencedColumnName = "id", nullable = false, columnDefinition = "INT")
    private PageModel page;
    @ManyToOne
    @JoinColumn(name = "lemma_id", referencedColumnName = "id" , nullable = false, columnDefinition = "INT")
    private LemmaModel lemma;
    @Column(name = "rank_score", nullable = false, columnDefinition = "FLOAT")
    private Float rankScore;
}