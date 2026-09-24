from pathlib import Path

p = Path("build-app/app/src/main/java/com/example/llama/MainActivity.kt")
s = p.read_text()

# The visual action must generate images, not analyze a selected image.
s = s.replace('2 -> pickImageForVision()', '2 -> LixVisualCreator.open(this@MainActivity)')

# The More Options entry opens the full capability hub.
old = 'setOnClickListener { openMenu() }\\n        })'
new = 'setOnClickListener { LixFeatureHub.show(this@MainActivity) }\\n        })'
s = s.replace(old, new, 1)

# Keep the image-analysis action available from the Files/vision flows.
p.write_text(s)
