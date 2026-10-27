# CruxCalculator - R8 / ProGuard rules.
# Room, Hilt, DataStore and Compose ship their own consumer rules, so very little is needed here.

# Keep line numbers for readable crash reports from Google Play (stack traces still need the mapping file).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room entities are accessed by generated code.
-keep class com.tapecalc.core.data.db.** { *; }

# The math engine reads function / unit tables reflectively through enums only; nothing else to keep.
-dontwarn javax.annotation.**
