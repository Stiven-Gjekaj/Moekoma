# The parsers of the plugin extend and override the classes of Tsuki. R8 must not change them, not even to make an
# open method final, so the rule has no "allowoptimization".
-keep class tsuki.** { *; }
# The parser plugin calls the libraries of the app by name.
-keep,allowoptimization class androidx.collection.** { public protected *; }
-keep,allowoptimization class org.json.** { public protected *; }
