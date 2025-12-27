package com.fanok.audiobooks.interface_pacatge.login;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.SkipStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;
import com.yandex.authsdk.YandexAuthSdkContract;

public interface LoginView extends MvpView {
    @StateStrategyType(SkipStrategy.class)
    void showToast(int messageId);

    @StateStrategyType(SkipStrategy.class)
    void showMessage(String message);

    @StateStrategyType(SkipStrategy.class)
    void close();

    @StateStrategyType(SkipStrategy.class)
    void launchYandexAuth();

    @StateStrategyType(SkipStrategy.class)
    void launchGoogleAuth();

    @StateStrategyType(SkipStrategy.class)
    void showForgotPassword();
}
