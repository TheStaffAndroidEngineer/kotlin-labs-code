package staff.android.contracts;

// region java-product
public final class LegacySavedProduct {
    private final String id;
    private final String title;

    public LegacySavedProduct(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
}
// endregion
