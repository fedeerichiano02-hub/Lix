from pathlib import Path

p = Path("build-app/app/src/main/java/com/example/llama/MainActivity.kt")
s = p.read_text()
s = s.replace('2 -> pickImageForVision()', '2 -> LixVisualCreator.open(this@MainActivity)')
s = s.replace('setOnClickListener { openMenu() }\\n        })',
              'setOnClickListener { LixFeatureHub.show(this@MainActivity) }\\n        })', 1)
p.write_text(s)
