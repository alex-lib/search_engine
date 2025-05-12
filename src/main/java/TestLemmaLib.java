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
        Сельдевые акулы распространены в теплых и умеренно теплых водах Мирового океана.
        Все они ведут пелагический образ жизни.
        Эти акулы питаются рыбой, тюленями, морскими котиками, каланами, а также падалью м.
        """;

        System.out.println(lemmas.collectLemmas(text1));
        System.out.println(lemmas.collectLemmas(text2));
    }
}