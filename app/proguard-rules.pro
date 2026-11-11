# StandBy Clock R8 rules.
# Framework entry points (Activities, DreamService, TileService) are referenced from the
# manifest and kept automatically by AAPT-generated rules. No reflection is used by the app.

# Keep enum names stable: settings persist enum *names* in DataStore.
-keepclassmembers enum com.nitestand.standbyclock.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
