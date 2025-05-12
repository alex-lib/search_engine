package searchengine.repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;

@Repository
public interface PageModelRepository extends JpaRepository<PageModel, Integer> {
    Boolean existsBySiteAndPath(SiteModel siteModel, String path);
    @Transactional
    void deleteBySiteAndPath(SiteModel siteModel, String path);
}