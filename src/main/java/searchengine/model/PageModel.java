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
@Table(name = "pages")
public class PageModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "INT")
    private Integer id;
    @ManyToOne
    @JoinColumn(name = "site_id", referencedColumnName = "id", nullable = false, columnDefinition = "INT")
    private SiteModel site;
    @Column(name = "path", nullable = false, columnDefinition = "TEXT")
    private String path;
    @Column(name = "code", nullable = false, columnDefinition = "INT")
    private Integer code;
    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<IndexModel> indexes;
}
