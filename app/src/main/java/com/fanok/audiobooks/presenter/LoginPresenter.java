package com.fanok.audiobooks.presenter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.interface_pacatge.login.LoginView;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.yandex.authsdk.YandexAuthResult;
import com.yandex.authsdk.YandexAuthToken;

import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@InjectViewState
public class LoginPresenter extends MvpPresenter<LoginView> implements com.fanok.audiobooks.interface_pacatge.login.LoginPresenter {

    private final Context mContext;
    private static final String TAG = "LoginPresenter";


    public LoginPresenter(@NonNull Context context) {
        mContext = context;
    }

    @Override
    public void onLoginClicked(String email, String password) {
        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            getViewState().showToast(R.string.empty_text);
            return;
        }
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        String method = "email";
                        String username = user.getEmail();
                        String name = user.getDisplayName();
                        if (name == null || name.isEmpty()) {
                            name = username;
                            if (name != null && name.contains("@")) {
                                name = name.substring(0, name.indexOf("@"));
                            }
                        }
                        String photo = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null;
                        onDataReceived(method, username, name, photo);
                        getViewState().close();
                    } else {
                        getViewState().showToast(R.string.error_auth);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Login failed", e);
                    getViewState().showMessage("Login failed: " + e.getMessage());
                });
    }

    @Override
    public void onRegisterClicked(String email, String password) {
        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            getViewState().showToast(R.string.empty_text);
            return;
        }
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        String method = "email";
                        String username = user.getEmail();
                        String name = username;
                        if (name != null && name.contains("@")) {
                            name = name.substring(0, name.indexOf("@"));
                        }

                        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                .setDisplayName(name)
                                .build();

                        String finalName = name;
                        user.updateProfile(profileUpdates)
                                .addOnCompleteListener(task -> {
                                    if (!task.isSuccessful()) {
                                        Log.e(TAG, "User profile update failed.");
                                    }
                                    onDataReceived(method, username, finalName, null);
                                    getViewState().close();
                                });
                    } else {
                        getViewState().showToast(R.string.error_auth);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Registration failed", e);
                    getViewState().showMessage("Registration failed: " + e.getMessage());
                });
    }

    @Override
    public void onForgotPasswordClicked() {
        getViewState().showForgotPassword();
    }

    @Override
    public void onGoogleClicked() {
        getViewState().launchGoogleAuth();
    }

    @Override
    public void onYandexClicked() {
        getViewState().launchYandexAuth();
    }

    @Override
    public void onYandexAuthResult(YandexAuthResult result) {
        if (result instanceof YandexAuthResult.Success) {
            sendYandexTokenToBackend(((YandexAuthResult.Success) result).getToken());
        } else if (result instanceof YandexAuthResult.Failure) {
            getViewState().showToast(R.string.failure_auth);
            Log.e(TAG, "YandexAuthResult: ", (((YandexAuthResult.Failure) result).getException()));
        } else {
            getViewState().showToast(R.string.failure_auth);
        }
    }

    @Override
    public void onGoogleAuthSuccess(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        user.getIdToken(true).addOnSuccessListener(
                                tokenResult -> {
                                    String method = "google";
                                    String username = user.getEmail();
                                    String name = user.getDisplayName();
                                    String photo = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null;

                                    onDataReceived(method, username, name, photo);
                                    getViewState().close();
                                }
                        );
                    } else {
                        Log.e(TAG, "userNotFound");
                        getViewState().showToast(R.string.error_auth);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Google signInWithCredential failed", e);
                    getViewState().showMessage("Google SignIn failed: " + e.getMessage());
                });
    }

    @Override
    public void onGoogleAuthFailed(String errorMessage) {
        Log.e(TAG, "Google Auth Failed: " + errorMessage);
        getViewState().showMessage("Auth Failed: " + errorMessage);
    }

    private void sendYandexTokenToBackend(YandexAuthToken yandexAuthToken) {

        OkHttpClient client = new OkHttpClient();
        JsonObject json = new JsonObject();
        json.addProperty("yandexJwt", yandexAuthToken.getValue());

        RequestBody body = RequestBody.create(
                json.toString(),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url("https://server-sage-rho.vercel.app/api/yandex")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Backend request failed", e);
                new Handler(Looper.getMainLooper()).post(() ->
                        getViewState().showToast(R.string.failure_auth)
                );
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    try {
                        String responseData = response.body().string();
                        Gson gson = new Gson();
                        JsonObject jsonResponse = gson.fromJson(responseData, JsonObject.class);

                        if (jsonResponse.has("firebaseToken")) {
                            String firebaseToken = jsonResponse.get("firebaseToken").getAsString();
                            // FirebaseAuth callbacks run on main thread
                            firebaseAuthWithToken(firebaseToken);
                        } else {
                            Log.e(TAG, "No firebaseToken in response");
                            new Handler(Looper.getMainLooper()).post(() ->
                                    getViewState().showToast(R.string.failure_auth)
                            );
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "JSON parsing error", e);
                        new Handler(Looper.getMainLooper()).post(() ->
                                getViewState().showToast(R.string.failure_auth)
                        );
                    }
                } else {
                    Log.e(TAG, "Backend error code: " + response.code());
                    new Handler(Looper.getMainLooper()).post(() ->
                            getViewState().showToast(R.string.failure_auth)
                    );
                }
            }
        });
    }

    @Override
    public void onTelegramClicked() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/audiobooks_login_bot?start=auth"));
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    @Override
    public void login(Intent intent) {
        Uri data = intent.getData();
        if (data != null) {
            String token = data.getQueryParameter("token");
            if (token != null && !token.isEmpty()) {
                firebaseAuthWithToken(token);
            }
        }
    }

    private void firebaseAuthWithToken(String token) {
        FirebaseAuth.getInstance()
                .signInWithCustomToken(token)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        user.getIdToken(true).addOnSuccessListener(
                                tokenResult -> {
                                    Map<String, Object> claims = tokenResult.getClaims();
                                    String method = (String) claims.get("method");
                                    String username = (String) claims.get("username");
                                    String name = (String) claims.get("fullname");
                                    String photo = (String) claims.get("photo_url");

                                    if (method != null) {
                                        onDataReceived(method, username, name, photo);
                                        getViewState().close();
                                    }
                                }
                        );
                    } else {
                        Log.e(TAG, "userNotFound: ");
                        getViewState().showToast(R.string.error_auth);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "onFailure: ", e);
                    getViewState().showToast(R.string.failure_auth);
                });
    }

    @Override
    public void onDataReceived(@NonNull String methodLogin, String username, String name, String photo) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = preferences.edit();
        if (username != null) editor.putString("username", username);
        if (name != null) editor.putString("name", name);
        if (photo != null) editor.putString("photo", photo);
        editor.putString("methodLogin", methodLogin);
        editor.apply();
    }

    @Override
    public void onDestroy() {
        // Clean up resources if needed
    }
}
