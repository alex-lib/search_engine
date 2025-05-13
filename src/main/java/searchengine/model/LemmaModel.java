package searchengine.model;
import jakarta.persistence.*;
import lombok.*;
import java.util.Set;


@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "lemmas")
public class LemmaModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "INT")
    private Integer id;
    @ManyToOne
    @JoinColumn(name = "site_id", referencedColumnName = "id", nullable = false, columnDefinition = "INT")
    private SiteModel site;
    @Column(name = "lemma", nullable = false, columnDefinition = "VARCHAR(255)")
    private String lemma;
    @Column(name = "frequency", nullable = false, columnDefinition = "INT")
    private Integer frequency;

    @OneToMany(mappedBy = "lemma", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<IndexModel> indexes;
}