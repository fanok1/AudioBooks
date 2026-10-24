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
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.palette.graphics.Palette;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.databinding.ActivityImageFullScrinBinding;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import org.jetbrains.annotations.NotNull;

public class ImageFullScreenActivity extends AppCompatActivity {

    private boolean isHide = true;

    private ActivityImageFullScrinBinding binding;

    /** Picasso keeps targets weakly: held here so the load cannot be collected before delivery (UI-19). */
    private Target coverTarget;

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
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setStatusBarHidden(true);

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

        coverTarget = new Target() {
            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                binding.image.setImageDrawable(errorDrawable);
                Toast.makeText(ImageFullScreenActivity.this,
                        getString(R.string.error_load_photo), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                binding.image.setImageBitmap(bitmap);
                // the palette is computed off the main thread
                Palette.from(bitmap).generate(p -> {
                    if (p == null || isFinishing() || isDestroyed()) {
                        return;
                    }
                    Palette.Swatch vibrantSwatch = p.getVibrantSwatch();
                    Palette.Swatch darkVibrantSwatch = p.getMutedSwatch();

                    if (vibrantSwatch != null && darkVibrantSwatch != null) {
                        GradientDrawable gd = new GradientDrawable(
                                GradientDrawable.Orientation.TOP_BOTTOM,
                                new int[]{vibrantSwatch.getRgb(), darkVibrantSwatch.getRgb()});
                        gd.setCornerRadius(45f);
                        binding.image.setBackground(gd);
                    }
                });
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
                binding.image.setImageDrawable(placeHolderDrawable);
            }
        };
        Picasso.get()
                .load(imageUrl)
                .error(android.R.drawable.ic_menu_gallery)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(coverTarget);

        binding.image.setOnClickListener(view -> {
            if (actionBar != null) {
                if (actionBar.isShowing()) {
                    binding.appBar.animate().alpha(0f).setDuration(340).start();
                    setStatusBarHidden(true);
                } else {
                    actionBar.show();
                    binding.appBar.animate().alpha(1f).setDuration(300).start();
                    setStatusBarHidden(false);
                }
            }
        });


    }

    @Override
    protected void onResume() {
        if (getSupportActionBar() != null && !getSupportActionBar().isShowing()) {
            setStatusBarHidden(true);
        }
        super.onResume();
    }

    private void setStatusBarHidden(boolean hidden) {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(),
                getWindow().getDecorView());
        if (hidden) {
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.statusBars());
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars());
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (coverTarget != null) {
            Picasso.get().cancelRequest(coverTarget);
        }
        super.onDestroy();
    }


}
