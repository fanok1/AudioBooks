package com.fanok.audiobooks.presenter;

import static org.junit.Assert.assertEquals;

import com.fanok.audiobooks.CookesExeption;
import com.fanok.audiobooks.R;
import java.io.IOException;
import java.net.SocketTimeoutException;
import org.jsoup.HttpStatusException;
import org.junit.Test;

public class BookDescriptionPresenterTest {

    @Test
    public void loadErrorsAreToldApart() {
        assertEquals(R.string.site_challenge,
                BookDescriptionPresenter.errorMessage(new CookesExeption("https://izib.uk/art1")));
        assertEquals(R.string.cookes_baza_knig_exeption,
                BookDescriptionPresenter.errorMessage(new CookesExeption("https://baza-knig.top/a/1.html")));
        assertEquals(R.string.error_load_data,
                BookDescriptionPresenter.errorMessage(new HttpStatusException("HTTP error", 503, "https://izib.uk")));
        assertEquals(R.string.error_load_data,
                BookDescriptionPresenter.errorMessage(new SocketTimeoutException()));
        assertEquals(R.string.error_load_data,
                BookDescriptionPresenter.errorMessage(new IOException("no book on page")));
        assertEquals(R.string.worong_data_sorce,
                BookDescriptionPresenter.errorMessage(new IllegalArgumentException("unsupported host: x")));
        assertEquals(R.string.error_display_data,
                BookDescriptionPresenter.errorMessage(new NullPointerException()));
    }
}
