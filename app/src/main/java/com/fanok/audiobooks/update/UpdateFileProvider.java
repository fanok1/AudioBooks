package com.fanok.audiobooks.update;

import androidx.core.content.FileProvider;

/** Gives the system installer the downloaded update ({@code res/xml/update_paths.xml}); its own class, so no library's
 *  FileProvider entry clashes with it in the merged manifest. */
public class UpdateFileProvider extends FileProvider {
}
