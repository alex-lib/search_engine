import searchengine.lemmafinder.LemmaFinder;
import java.io.IOException;

public class TestLemmaLib {
    public static void main(String[] args) throws IOException {
        LemmaFinder lemmas = new LemmaFinder();
        String text1 = """
        Объектно-ориентированное программирование (ООП) — это подход,
        при котором программа рассматривается как набор объектов,
        взаимодействующих не друг с другом. У каждого есть свойства и поведение.
        """;

        String text2 = """
        Повторное появление леопарда в Осетии позволяет предположить,
        что леопард постоянно обитает в некоторых районах Северного Кавказа.
        """;

        System.out.println(lemmas.collectLemmas(text1));
        System.out.println(lemmas.collectLemmas(text2));
    }
}
