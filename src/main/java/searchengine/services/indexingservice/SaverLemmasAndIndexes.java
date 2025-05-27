package searchengine.services.indexingservice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import searchengine.lemmafinder.LemmaFinder;
import searchengine.model.IndexModel;
import searchengine.model.LemmaModel;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.repositories.IndexModelRepository;
import searchengine.repositories.LemmaModelRepository;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class SaverLemmasAndIndexes {
    private final LemmaModelRepository lemmaModelRepository;
    private final IndexModelRepository indexModelRepository;
    private final PageModel pageModel;

    public void saveLemmaAndIndex() {
        try {
            if (pageModel.getContent().length() > 10000) {
                processInBatches(pageModel.getContent(), 2000);
            } else {
                collectLemmasAndIndexes(pageModel.getContent());
            }
        } catch (Exception e) {
            log.error("Lemma processing failed for page {}", pageModel.getId(), e);
        }
    }

    private void processInBatches(String content, int chunkSize) {
        for (int i = 0; i < content.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, content.length());
            collectLemmasAndIndexes(content.substring(i, end));
            if (i % (chunkSize * 10) == 0) {
                lemmaModelRepository.flush();
                indexModelRepository.flush();
            }
        }
    }

    private void collectLemmasAndIndexes(String content) {
        LemmaFinder lemmaFinder = new LemmaFinder();
        HashMap<String, Integer> lemmas = lemmaFinder.collectLemmas(content);
        synchronized (lemmaModelRepository) {
            for (Map.Entry<String, Integer> lemma : lemmas.entrySet()) {
                LemmaModel lemmaModelFromDb = lemmaModelRepository.findBySiteAndLemma(pageModel.getSite(), lemma.getKey());
                if (lemmaModelFromDb != null) {
                    log.debug("Lemma {} already exists for site {}", lemma.getKey(), pageModel.getSite().getName());
                    IndexModel indexModelFromDb = indexModelRepository.findByLemmaAndPage(lemmaModelFromDb, pageModel);
                    if (indexModelFromDb != null) {
                        indexModelFromDb.setRankScore(indexModelFromDb.getRankScore() + lemma.getValue());
                        indexModelRepository.save(indexModelFromDb);
                    } else {
                        lemmaModelFromDb.setFrequency(lemmaModelFromDb.getFrequency() + 1);
                        lemmaModelRepository.save(lemmaModelFromDb);
                        buildAndSaveIndex(pageModel, lemma, lemmaModelFromDb);
                    }
                } else {
                    LemmaModel lemmaModel = buildAndSaveLemma(pageModel.getSite(), lemma);
                    buildAndSaveIndex(pageModel, lemma, lemmaModel);
                }
            }
            lemmas.clear();
            lemmaModelRepository.flush();
            indexModelRepository.flush();
        }
    }

    private LemmaModel buildAndSaveLemma(SiteModel siteModel, Map.Entry<String, Integer> lemma) {
        LemmaModel lemmaModel = LemmaModel.builder()
                .site(siteModel)
                .lemma(lemma.getKey())
                .frequency(1)
                .build();
        lemmaModelRepository.save(lemmaModel);
        log.debug("Lemma {} created for site {}", lemma.getKey(), pageModel.getSite().getName());
        return lemmaModel;
    }

    private void buildAndSaveIndex(PageModel pageModel, Map.Entry<String, Integer> lemma, LemmaModel lemmaModel) {
        IndexModel indexModel = IndexModel.builder()
                .page(pageModel)
                .rankScore(Float.valueOf(lemma.getValue()))
                .lemma(lemmaModel)
                .build();
        log.debug("Index created for page {} and lemma {}", pageModel.getId(), lemma.getKey());
        indexModelRepository.save(indexModel);
    }
}