package com.fanok.audiobooks.interface_pacatge.favorite;

import android.content.Context;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.pojo.BookPOJO;

public interface FavoritePresenter {

    void loadBooks();

    void onCreate(Context context);

    void onBookItemClick(int position);

    void onBookItemLongClick(int position);

    /** Hides the book and offers undo; the deletion runs when {@link #commitRemove()} follows. */
    void onRemove(int position);

    void onRemove(@NonNull BookPOJO book);

    void undoRemove();

    void commitRemove();

    /** Empties the whole list (favorites or history) after the user confirmed. */
    void clearAll();

    void onSearch(String qery);

    void onOptionsItemSelected(int id);

    /** A value chosen in the filter popup the view showed for {@code filterId}; null means "all". */
    void onFilterSelected(int filterId, String value);

    void onMenuGenre(@NonNull String url, String title);

    void onMenuAutor(@NonNull String url, String title);

    void onMenuArtist(@NonNull String url, String title);

    void onMenuSeries(@NonNull String url, String title);
}
