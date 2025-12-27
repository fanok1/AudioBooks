package com.fanok.audiobooks.interface_pacatge.login;

import android.content.Intent;

import com.yandex.authsdk.YandexAuthResult;

public interface LoginPresenter {
    void onLoginClicked(String email, String password);
    void onRegisterClicked(String email, String password);
    void onForgotPasswordClicked();
    void onGoogleClicked();
    void onYandexClicked();
    void onTelegramClicked();
    void onDataReceived(String methodLogin, String username, String name, String photo);
    void onDestroy();
    void login(Intent intent);
    void onYandexAuthResult(YandexAuthResult result);
    void onGoogleAuthSuccess(String idToken);
    void onGoogleAuthFailed(String errorMessage);
}
