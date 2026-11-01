package com.fanok.audiobooks.search;

import static org.junit.Assert.assertEquals;

import com.fanok.audiobooks.pojo.GenrePOJO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class GenreSortTest {

    private static GenrePOJO genre(String name, int count) {
        GenrePOJO g = new GenrePOJO(name, "https://x/" + name);
        g.setReting(count);
        return g;
    }

    private static List<String> names(List<GenrePOJO> genres) {
        List<String> names = new ArrayList<>();
        for (GenrePOJO g : genres) {
            names.add(g.getName());
        }
        return names;
    }

    private final List<GenrePOJO> site = Arrays.asList(
            genre("фэнтези", 0), genre("Детектив", 120), genre("аудиоспектакль", 7), genre("Ёлки", 120),
            genre("Боевик", 0));

    @Test
    public void noneKeepsTheSiteOrderInACopy() {
        List<GenrePOJO> sorted = GenreSort.NONE.sorted(site, Collections.emptyMap());
        assertEquals(names(site), names(sorted));
        sorted.clear();
        assertEquals(5, site.size());
    }

    @Test
    public void nameSortsAlphabeticallyIgnoringCase() {
        assertEquals(Arrays.asList("аудиоспектакль", "Боевик", "Детектив", "Ёлки", "фэнтези"),
                names(GenreSort.NAME.sorted(site, Collections.emptyMap())));
    }

    @Test
    public void nameDescSortsFromYaToA() {
        assertEquals(Arrays.asList("фэнтези", "Ёлки", "Детектив", "Боевик", "аудиоспектакль"),
                names(GenreSort.NAME_DESC.sorted(site, Collections.emptyMap())));
    }

    @Test
    public void countPutsMostBooksFirstAndGenresWithoutACountLastInTheSiteOrder() {
        assertEquals(Arrays.asList("Детектив", "Ёлки", "аудиоспектакль", "фэнтези", "Боевик"),
                names(GenreSort.COUNT.sorted(site, Collections.emptyMap())));
    }

    @Test
    public void aDescriptionThatIsOnlyACountCountsWhenThereIsNoRating() {
        GenrePOJO books = genre("a", 0);
        books.setDescription("4 738 аудиокниг");
        GenrePOJO bracketed = genre("b", 0);
        bracketed.setDescription("[953]");
        GenrePOJO text = genre("c", 0);
        text.setDescription("Книги о войне 1812 года и не только");
        GenrePOJO rated = genre("d", 5);
        rated.setDescription("[100]");
        assertEquals(4738, GenreSort.count(books));
        assertEquals(953, GenreSort.count(bracketed));
        assertEquals(0, GenreSort.count(text));
        assertEquals(5, GenreSort.count(rated));
    }

    @Test
    public void booksNamedInTheDescriptionCountBeforeTheRating() {
        GenrePOJO author = genre("Кинг Стивен", 1600010); // akniga: the rating column, then "listens • books"
        author.setDescription("99K • 593 аудиокниги");
        GenrePOJO few = genre("b", 5);
        few.setDescription("100 книг");
        assertEquals(593, GenreSort.count(author));
        assertEquals(100, GenreSort.count(few));
    }

    @Test
    public void favoritePutsTheMostPickedGenresOfAnySiteFirst() {
        Map<String, Object> picks = new HashMap<>();
        picks.put(Genre.HORROR.name(), 3);
        picks.put(Genre.KIDS.name(), 1);
        picks.put(GenrePicks.key("Искусственный голос"), 2);
        List<GenrePOJO> genres = Arrays.asList(genre("Фэнтези", 0), genre("Ужасы, мистика", 0), genre("Для детей", 0),
                genre("Искусственный  голос", 0), genre("Мистика и ужасы", 0));
        assertEquals(Arrays.asList("Ужасы, мистика", "Мистика и ужасы", "Искусственный  голос", "Для детей", "Фэнтези"),
                names(GenreSort.FAVORITE.sorted(genres, picks)));
    }

    @Test
    public void aPickCountsForTheGenreOnEverySiteAndAnUnknownNameForItself() {
        assertEquals(Genre.HORROR.name(), GenrePicks.key("Ужасы, мистика"));
        assertEquals(Genre.HORROR.name(), GenrePicks.key("Мистика и ужасы"));
        assertEquals("?искусственный голос", GenrePicks.key(" Искусственный  голос "));
        assertEquals(null, GenrePicks.key(" "));
        assertEquals(null, GenrePicks.key(null));
    }

    @Test
    public void anUnknownKeptKeyIsNone() {
        assertEquals(GenreSort.NONE, GenreSort.of(null));
        assertEquals(GenreSort.NONE, GenreSort.of("bogus"));
        assertEquals(GenreSort.COUNT, GenreSort.of(GenreSort.COUNT.name()));
    }
}
