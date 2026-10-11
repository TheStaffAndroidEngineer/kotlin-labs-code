package staff.android.contracts;

import java.io.IOException;
import java.util.List;

// region java-source
public interface LegacySavedSource {
    List<LegacySavedProduct> read() throws IOException;
}
// endregion
