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
public class SaveLemmaAndIndex {
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

    private synchronized void collectLemmasAndIndexes(String content) {
        LemmaFinder lemmaFinder = new LemmaFinder();
        HashMap<String, Integer> lemmas = lemmaFinder.collectLemmas(content);

        synchronized (lemmaModelRepository) {
        for (Map.Entry<String, Integer> lemma : lemmas.entrySet()) {
                if (lemmaModelRepository.existsBySiteAndLemma(pageModel.getSite(), lemma.getKey())) {
                    log.debug("Lemma {} already exists for site {}", lemma.getKey(), pageModel.getSite().getName());
                    LemmaModel lemmaModelFromDb = lemmaModelRepository.findBySiteAndLemma(pageModel.getSite(), lemma.getKey());
                    lemmaModelFromDb.setFrequency(lemmaModelFromDb.getFrequency() + 1);
                    lemmaModelRepository.saveAndFlush(lemmaModelFromDb);

                    if (indexModelRepository.existsByLemmaAndPage(lemmaModelFromDb, pageModel)) {
                        IndexModel indexModelFromDb = indexModelRepository.findByLemmaAndPage(lemmaModelFromDb, pageModel);
                        indexModelFromDb.setRankScore(indexModelFromDb.getRankScore() + lemma.getValue());
                        indexModelRepository.saveAndFlush(indexModelFromDb);
                    } else {
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
                .frequency(lemma.getValue())
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