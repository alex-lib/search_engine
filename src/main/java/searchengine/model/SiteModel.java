package searchengine.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Set;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sites")
public class SiteModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "INT")
    private Integer id;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "ENUM('INDEXED', 'FAILED', 'INDEXING')")
    private SiteStatus siteStatus;
    @Column(name = "status_time", nullable = false, columnDefinition = "DATETIME")
    private LocalDateTime statusTime;
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    @Column(name = "url", columnDefinition = "VARCHAR(255)")
    private String url;
    @Column(name = "name", columnDefinition = "VARCHAR(255)")
    private String name;
    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<PageModel> pages;
    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<LemmaModel> lemmas;
}