package com.fanok.audiobooks.activity;

import android.animation.Animator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.palette.graphics.Palette;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.databinding.ActivityImageFullScrinBinding;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import org.jetbrains.annotations.NotNull;

public class ImageFullScreenActivity extends AppCompatActivity {

    private boolean isHide = true;

    private ActivityImageFullScrinBinding binding;

    public static void start(@NotNull Activity activity, @NotNull String image, String title,
            View anim) {
        Intent intent = new Intent(activity, ImageFullScreenActivity.class);
        intent.putExtra("image", image);
        intent.putExtra("title", title);
        if (anim != null) {
            ActivityOptions options = ActivityOptions
                    .makeSceneTransitionAnimation(activity, anim, "robot");
            activity.startActivity(intent, options.toBundle());
        } else {
            activity.startActivity(intent);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String imageUrl = intent.getStringExtra("image");
        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new IllegalArgumentException(
                    "imageUrl must be not empty");
        }
        binding = ActivityImageFullScrinBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setOutlineProvider(null);
        setSupportActionBar(binding.toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setElevation(0f);
            actionBar.hide();
        }

        binding.appBar.setAlpha(0);
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_VISIBLE | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

        String title = intent.getStringExtra("title");
        if (title != null) setTitle(title);

        binding.appBar.animate().setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animator) {

            }

            @Override
            public void onAnimationEnd(@NonNull Animator animator) {
                if (!isHide) {
                    if (actionBar != null) {
                        actionBar.hide();
                    }
                    isHide = true;
                } else {
                    isHide = false;
                }
            }

            @Override
            public void onAnimationCancel(@NonNull Animator animator) {

            }

            @Override
            public void onAnimationRepeat(@NonNull Animator animator) {

            }
        });

        Picasso.get()
                .load(imageUrl)
                .error(android.R.drawable.ic_menu_gallery)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(new Target() {
                    @Override
                    public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                        binding.image.setImageDrawable(errorDrawable);
                        Toast.makeText(ImageFullScreenActivity.this,
                                getString(R.string.error_load_photo), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                        binding.image.setImageBitmap(bitmap);
                        Palette p = createPaletteSync(bitmap);
                        Palette.Swatch vibrantSwatch = p.getVibrantSwatch();
                        Palette.Swatch darkVibrantSwatch = p.getMutedSwatch();

                        if (vibrantSwatch != null && darkVibrantSwatch != null) {
                            int gradientColor1 = vibrantSwatch.getRgb();
                            int gradientColor2 = darkVibrantSwatch.getRgb();

                            GradientDrawable gd = new GradientDrawable(
                                    GradientDrawable.Orientation.TOP_BOTTOM,
                                    new int[]{gradientColor1, gradientColor2});
                            gd.setCornerRadius(45f);
                            binding.image.setBackgroundDrawable(gd);
                        }

                    }

                    @Override
                    public void onPrepareLoad(Drawable placeHolderDrawable) {
                        binding.image.setImageDrawable(placeHolderDrawable);
                    }
                });

        binding.image.setOnClickListener(view -> {
            if (actionBar != null) {
                if (actionBar.isShowing()) {
                    binding.appBar.animate().alpha(0f).setDuration(340).start();
                    View decorView1 = getWindow().getDecorView();
                    int uiOptions1 =
                            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                    decorView1.setSystemUiVisibility(uiOptions1);
                } else {
                    actionBar.show();
                    binding.appBar.animate().alpha(1f).setDuration(300).start();
                    View decorView1 = getWindow().getDecorView();
                    int uiOptions1 =
                            View.SYSTEM_UI_FLAG_VISIBLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                    decorView1.setSystemUiVisibility(uiOptions1);
                }
            }
        });


    }

    @Override
    protected void onResume() {
        if (getSupportActionBar() != null && !getSupportActionBar().isShowing()) {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
        super.onResume();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private Palette createPaletteSync(Bitmap bitmap) {
        return Palette.from(bitmap).generate();
    }

    @Override
    public void finish() {
        super.finish();
    }


}
