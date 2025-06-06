package searchengine.repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexModel;
import searchengine.model.LemmaModel;
import searchengine.model.PageModel;

@Repository
public interface IndexModelRepository extends JpaRepository<IndexModel, Integer> {
    IndexModel findByLemmaAndPage(LemmaModel lemmaModelFromDb, PageModel pageModel);
}