package com.fanok.audiobooks.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;import android.graphics.Typeface;

public class AvatarGenerator {

    // Генерация Bitmap с кругом и буквой
    public static Bitmap generateAvatar(String name, int size) {
        int[] colors = {
                0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7,
                0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4,
                0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39,
                0xFFFFC107, 0xFFFF9800, 0xFFFF5722, 0xFF795548
        };

        // 2. Выбираем цвет на основе имени (чтобы у одного юзера всегда был один цвет)
        int colorIndex = Math.abs(name.hashCode()) % colors.length;
        int backgroundColor = colors[colorIndex];

        // 3. Создаем пустой Bitmap
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 4. Рисуем круг
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(backgroundColor);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

        // 5. Получаем инициалы (первая буква)
        String initials = name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase();

        // 6. Рисуем текст
        paint.setColor(Color.WHITE);
        paint.setTextSize(size * 0.4f); // Размер текста - 40% от размера картинки
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);

        // Вычисляем центр для текста по вертикали
        Rect textBounds = new Rect();
        paint.getTextBounds(initials, 0, initials.length(), textBounds);
        float y = (size / 2f) + (textBounds.height() / 2f) - textBounds.bottom;

        canvas.drawText(initials, size / 2f, y, paint);

        return bitmap;
    }
}
