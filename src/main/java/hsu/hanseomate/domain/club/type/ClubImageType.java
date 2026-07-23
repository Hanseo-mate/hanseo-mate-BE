package hsu.hanseomate.domain.club.type;

public enum ClubImageType {
    PROFILE("clubs/profile"),
    BACKGROUND("clubs/background");

    private final String storageDirectory;

    ClubImageType(String storageDirectory) {
        this.storageDirectory = storageDirectory;
    }

    public String storageDirectory() {
        return storageDirectory;
    }
}
