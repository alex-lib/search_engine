package searchengine.lemmafinder;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class LemmaFinder {
    private static final LuceneMorphology LUCENE_MORPHOLOGY;
    private static final String WORD_TYPE_REGEX = "\\W\\w&&[^а-яА-Я]";
    private static final Set<String> PARTICLES_NAMES =
            Set.of(" МЕЖД", " ПРЕДЛ", " СОЮЗ", " МС-П", " ЧАСТ", " МС");

    static {
        try {
            LUCENE_MORPHOLOGY = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize morphology analyzer", e);
        }
    }

    public HashMap<String, Integer> collectLemmas(String text)  {
        HashMap<String, Integer> lemmas = new HashMap<>();
        String regex = "[^0-9,;.!'?\"\\s]+";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(cleanHtmlFromTags(text).toLowerCase().replaceAll("([^а-я])", " ").trim());

        while (matcher.find()) {
            String word = matcher.group();
            if (isCorrectWordForm(word)) {
                List<String> wordBaseForms = LUCENE_MORPHOLOGY.getMorphInfo(matcher.group());
                String rightForm = getRightForm(wordBaseForms, word);
                if (!hasParticleProperty(rightForm)) {
                    String lemma = LUCENE_MORPHOLOGY.getNormalForms(matcher.group()).get(0);
                    lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
                }
            }
        }
        return lemmas;
    }

    private String cleanHtmlFromTags(String html) {
        return html.replaceAll("<[^>]*>", "");
    }

    private boolean isCorrectWordForm(String word) {
        return !word.matches(WORD_TYPE_REGEX) && word.length() > 2;
    }

    private String getRightForm(List<String> wordBaseForms, String word) {
        HashMap<String, Integer> forms = new HashMap<>();
        for (String wordForm : wordBaseForms) {
            forms.put(wordForm.substring(0, wordForm.indexOf('|')),
                    Math.abs(word.length() - wordForm.substring(0, wordForm.indexOf('|')).length()));
        }
        String closeForm = forms.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        return wordBaseForms.stream()
                .filter(w -> w.substring(0, w.indexOf('|')).equals(closeForm))
                .findFirst()
                .orElse(null);
    }

    private boolean hasParticleProperty(String rightForm) {
        String upperForm = rightForm.toUpperCase();
        return PARTICLES_NAMES.stream().anyMatch(upperForm::contains);
    }
}