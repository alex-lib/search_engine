package searchengine.repositories;
import org.hibernate.mapping.Table;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaModel;
import searchengine.model.SiteModel;

@Repository
public interface LemmaModelRepository extends JpaRepository<LemmaModel, Integer> {
    boolean existsBySiteAndLemma(SiteModel siteModel, String lemma);
    LemmaModel findBySiteAndLemma(SiteModel siteModel, String lemma);
    LemmaModel findByLemma(String lemma);
    LemmaModel findByLemmaAndSite(String lemma, String site);
}