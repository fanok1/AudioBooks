package com.fanok.audiobooks.interface_pacatge;

import java.io.File;

public interface ServiceListener {

    void loggedIn();

    void fileDownloaded(File file);

    void cancelled();

    void handleError(Exception exception);

}
