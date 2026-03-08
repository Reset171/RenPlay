package org.renpy.android;

import androidx.core.content.FileProvider;
import ru.reset.renplay.R;

public class RenPyFileProvider extends FileProvider {
    public RenPyFileProvider() {
        super(R.xml.file_paths);
    }
}
