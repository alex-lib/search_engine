package searchengine.repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteModel;

@Repository
public interface SiteModelRepository extends JpaRepository<SiteModel, Integer> {
    @Transactional
    void deleteByName(String name);
    SiteModel findByUrl(String url);
}