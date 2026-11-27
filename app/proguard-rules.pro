# Keep the optional system engine reached by class name from RadioEngineProvider.
-keep class tech.iox.freeradio.system.AndroidBroadcastRadioEngine { *; }
-keep class tech.iox.freeradio.system.AndroidBroadcastRadioEngine$* { *; }

# Retain useful source/line information for retracing obfuscated crash reports.
-keepattributes SourceFile,LineNumberTable

# Android framework stubs contain a legacy Cleaner reference absent from modern JDKs.
-dontwarn sun.misc.Cleaner
