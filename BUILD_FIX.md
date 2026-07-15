# Build fix: missing Android color resources

GitHub Actions failed during `:app:processDebugResources` because these resources were referenced by drawable XML files but were missing from the repository version of `colors.xml`:

- `@color/surface_border`
- `@color/primary_pressed`

The corrected `app/src/main/res/values/colors.xml` contains:

```xml
<color name="surface_border">#30323A</color>
<color name="primary_pressed">#6258EA</color>
```

Run after committing the fix:

```bash
./gradlew clean assembleDebug
```
