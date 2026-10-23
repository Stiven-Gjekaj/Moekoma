-keep,allowoptimization class tsuki.** { public protected *; }
# The parser plugin calls the libraries of the app by name.
-keep,allowoptimization class androidx.collection.** { public protected *; }
-keep,allowoptimization class org.json.** { public protected *; }
