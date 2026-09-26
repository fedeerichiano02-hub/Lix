from pathlib import Path
import os

run_number = os.environ.get("GITHUB_RUN_NUMBER", "1")

app = Path("build-app/app/build.gradle.kts")
s = app.read_text()

deps = [
    'implementation("com.google.mlkit:text-recognition:16.0.1")',
    'implementation("com.google.mlkit:image-labeling:17.0.9")',
]
missing = [d for d in deps if d not in s]
if missing:
    s = s.replace(
        "dependencies {",
        "dependencies {\n" + "\n".join("    " + d for d in missing),
        1,
    )

s = s.replace('applicationId = "com.example.llama.aichat"', 'applicationId = "com.lix.ai"')
s = s.replace('abiFilters += listOf("arm64-v8a")', '')
if 'abiFilters += listOf("arm64-v8a")' not in s:
    s = s.replace(
        "vectorDrawables {",
        'ndk { abiFilters += listOf("arm64-v8a") }\n        vectorDrawables {',
        1,
    )
s = s.replace("versionCode = 1", f"versionCode = {run_number}")
s = s.replace('versionName = "1.0"', f'versionName = "2.0.{run_number}"')
app.write_text(s)

lib = Path("build-app/lib/build.gradle.kts")
s = lib.read_text()
s = s.replace('abiFilters += listOf("arm64-v8a", "x86_64")', 'abiFilters += listOf("arm64-v8a")')
s = s.replace('abiFilters += listOf("x86_64")', 'abiFilters += listOf("arm64-v8a")')
s = s.replace("-DGGML_CPU_ALL_VARIANTS=ON", "-DGGML_CPU_ALL_VARIANTS=OFF")
lib.write_text(s)

print("prepare_build.py: Gradle/ABI configuration prepared.")
