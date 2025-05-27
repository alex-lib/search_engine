package searchengine.services.searchingservice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.dto.searching.SearchingData;
import searchengine.dto.searching.SearchingResponse;
import searchengine.lemmafinder.LemmaFinder;
import searchengine.model.*;
import searchengine.repositories.LemmaModelRepository;
import searchengine.repositories.SiteModelRepository;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchingServiceImpl implements SearchingService {
    private final LemmaModelRepository lemmaModelRepository;
    private final SiteModelRepository siteModelRepository;
    private final LemmaFinder lemmaFinder = new LemmaFinder();
    private static final int SNIPPET_LENGTH = 150;
    private static final int CONTEXT_PADDING = 50;

    @Override
    public SearchingResponse search(String query, String site, int limit, int offset) {
        SearchingResponse search = new SearchingResponse();
        if (query == null) {
            search.setError("An empty search query was specified");
            search.setResult(false);
        }
        if (siteModelRepository.findAll().stream()
                .anyMatch(siteModel -> siteModel.getSiteStatus() == SiteStatus.INDEXING)) {
            search.setError("Site haven't been indexed yet");
            search.setResult(false);
        }
        List<String> sortedLemmasList = findAndSortLemmasInDb(query);
        if(sortedLemmasList != null) {
            List<PageModel> matchedPages = getListOfMatchedPages(sortedLemmasList, site);
            Set<SearchingData> searchingDataSet = getSearchingData(matchedPages, query, sortedLemmasList, limit);
            search.setData(searchingDataSet.stream().limit(limit).toList());
            search.setCount(searchingDataSet.size());
            search.setResult(true);
        } else {
            search.setError("No matches found");
            search.setResult(false);
        }
        return search;
    }

    private List<PageModel> getListOfMatchedPages(List<String> sortedLemmasList, String site) {
        List<PageModel> matchedPages = new ArrayList<>();
        if (site == null) {
            for (SiteModel siteFromDb : siteModelRepository.findAll()) {
                List<PageModel> extraList = getPagesForLemma(sortedLemmasList.get(0), siteFromDb);
                for (int i = 1; i < sortedLemmasList.size(); i++) {
                    String lemma = sortedLemmasList.get(i);
                    List<PageModel> pagesForLemma = getPagesForLemma(lemma, siteFromDb);
                    extraList = extraList.stream()
                            .filter(pagesForLemma::contains)
                            .toList();
                }
                matchedPages.addAll(extraList);
            }
        } else {
            SiteModel siteFromDb = siteModelRepository.findByUrl(site + "/");
            matchedPages = getPagesForLemma(sortedLemmasList.get(0), siteFromDb);
            for (int i = 1; i < sortedLemmasList.size() && !matchedPages.isEmpty(); i++) {
                String lemma = sortedLemmasList.get(i);
                List<PageModel> pagesForLemma = getPagesForLemma(lemma, siteFromDb);
                matchedPages = matchedPages.stream()
                        .filter(pagesForLemma::contains)
                        .toList();
            }
        }
        return matchedPages;
    }

    private List<PageModel> getPagesForLemma(String lemma, SiteModel siteModel) {
        return lemmaModelRepository.findBySiteAndLemma(siteModel, lemma).getIndexes().stream()
                .map(IndexModel::getPage)
                .collect(Collectors.toList());
    }

    private List<String>findAndSortLemmasInDb(String query) {
        Set<String> lemmasFromQuery = lemmaFinder.collectLemmas(query).keySet();
        Map<String, Integer> lemmasMatchesWithLemmasInDb = lemmasFromQuery.stream()
                .map(lemmaModelRepository::findByLemma)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(LemmaModel::getLemma, LemmaModel::getFrequency));
        return lemmasMatchesWithLemmasInDb.isEmpty() ? null :
                lemmasMatchesWithLemmasInDb.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
    }

    private Float calculateAbsoluteRelevance(PageModel pageModel, List<String> lemmas) {
        return pageModel.getIndexes().stream()
                .filter(index -> lemmas.contains(index.getLemma().getLemma()))
                .map(IndexModel::getRankScore).reduce(0f, Float::sum);
    }

    private TreeSet<SearchingData> getSearchingData(List<PageModel> matchedPages, String query, List<String> sortedLemmasList, int limit) {
        TreeSet<SearchingData> dataSet = new TreeSet<>();
        for (PageModel pageModel : matchedPages) {
            SearchingData searchingData = new SearchingData();
            searchingData.setSite(pageModel.getSite().getUrl().substring(0, pageModel.getSite().getUrl().length() - 1));
            searchingData.setSiteName(pageModel.getSite().getName());
            searchingData.setUrl(pageModel.getPath());
            int startTitle = pageModel.getContent().indexOf("<title>") + 7;
            int endTitle = pageModel.getContent().indexOf("</title>", startTitle);
            String title = pageModel.getContent().substring(startTitle, endTitle);
            searchingData.setTitle(title);
            String snippet = generateSnippet(pageModel.getContent(), query);
            searchingData.setSnippet(snippet);
            float relevance = calculateAbsoluteRelevance(pageModel, sortedLemmasList);
            searchingData.setRelevance(relevance);
            dataSet.add(searchingData);
        }
        float maxRelevance = dataSet.stream().map(SearchingData::getRelevance).max(Float::compareTo).orElse(0f);
        dataSet.forEach(d -> d.setRelevance(maxRelevance / d.getRelevance()));
        return dataSet;
    }

    private String generateSnippet(String content, String query) {
        String text = Jsoup.parse(content).text();
        List<Integer> matchPositions = findMatchPositions(text, query);
        String snippet = matchPositions.isEmpty()
                ? getFallbackSnippet(text)
                : buildSnippetAroundMatches(text, matchPositions);

        return highlightMatches(snippet, query);
    }

    private List<Integer> findMatchPositions(String text, String query) {
        String regex = getRegexToMatchWords(query);
        Matcher matcher = Pattern.compile(regex).matcher(text);
        List<Integer> positions = new ArrayList<>();
        while (matcher.find()) {
            positions.add(matcher.start());
        }
        return positions;
    }

    private String buildSnippetAroundMatches(String text, List<Integer> positions) {
        int start = findOptimalSnippetStart(positions);
        int end = Math.min(start + SNIPPET_LENGTH, text.length());
        String snippet = text.substring(start, end);
        if (start > 0) {
            snippet = "..." + snippet;
        }
        return end < text.length() ? snippet + "..." : snippet;
    }

    private int findOptimalSnippetStart(List<Integer> positions) {
        Collections.sort(positions);
        int bestStart = 0;
        int maxDensity = 0;
        for (int i = 0; i < positions.size(); i++) {
            int windowEnd = positions.get(i) + SNIPPET_LENGTH;
            int matches = 1;
            for (int j = i + 1; j < positions.size() && positions.get(j) <= windowEnd; j++) {
                matches++;
            }
            if (matches > maxDensity) {
                maxDensity = matches;
                bestStart = Math.max(0, positions.get(i) - CONTEXT_PADDING);
            }
        }
        return bestStart;
    }

    private String highlightMatches(String snippet, String query) {
        String regex = getRegexToMatchWords(query);
        Matcher matcher = Pattern.compile(regex).matcher(snippet);
        StringBuilder stringBuilder = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(stringBuilder, "<b>" + matcher.group() + "</b>");
        }
        matcher.appendTail(stringBuilder);
        return stringBuilder.toString();
    }

    private String getFallbackSnippet(String text) {
        return text.substring(0, Math.min(SNIPPET_LENGTH, text.length())) +
                (text.length() > SNIPPET_LENGTH ? "..." : "");
    }

    private String getRegexToMatchWords(String query) {
        Set<String> lemmas = lemmaFinder.collectLemmas(query).keySet();
        Set<String> queryWords = new HashSet<>(Stream.of(query.split("\\s+")).filter(word -> word.length() > 2).toList());
        Set<String> wordsAndLemmas = new HashSet<>();
        wordsAndLemmas.addAll(queryWords);
        wordsAndLemmas.addAll(lemmas);
        return wordsAndLemmas.stream()
                .map(Pattern::quote)
                .map(word -> "(?iu)\\b" + word + "\\w*")
                .collect(Collectors.joining("|"));
    }
}