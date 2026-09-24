from pathlib import Path

p = Path("build-app/app/src/main/java/com/example/llama/MainActivity.kt")
s = p.read_text()
start = s.index("    private fun buildUi() {")
end = s.index("    private suspend fun prepareModel()", start)

new = r'''    private fun buildUi() {
        val bgTop = Color.rgb(5, 8, 19)
        val bgBottom = Color.rgb(12, 7, 28)
        val glass = Color.argb(135, 20, 27, 49)
        val glassStrong = Color.argb(185, 17, 24, 45)
        val glassSoft = Color.argb(95, 38, 46, 75)
        val line = Color.argb(110, 119, 164, 218)
        val cyan = Color.rgb(53, 224, 255)
        val blue = Color.rgb(76, 128, 255)
        val violet = Color.rgb(177, 92, 255)
        val white = Color.rgb(244, 248, 255)
        val muted = Color.rgb(158, 170, 198)

        fun card(color: Int, radius: Int = 20, stroke: Int = line) =
            GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(radius).toFloat()
                setStroke(dp(1), stroke)
            }
        fun t(value: String, size: Float, color: Int = white) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            includeFontPadding = false
        }
        fun hs() = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(bgTop, Color.rgb(8, 12, 28), bgBottom)
            )
            setPadding(dp(14), dp(9), dp(14), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(t("☰", 23f).apply {
            gravity = Gravity.CENTER
            setOnClickListener { openMenu() }
        }, LinearLayout.LayoutParams(dp(48), dp(54)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        brand.addView(t("Lix", 24f).apply {
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        brand.addView(t("ASISTENTE INTELIGENTE", 8f, cyan).apply {
            gravity = Gravity.CENTER
            letterSpacing = .16f
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(t("⋯", 28f).apply {
            gravity = Gravity.CENTER
            setOnClickListener { openSettings() }
        }, LinearLayout.LayoutParams(dp(48), dp(54)))
        root.addView(header)

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(12))
            background = card(glassStrong, 24)
        }
        val infoTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        infoTop.addView(t("Lix", 16f).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        status = t("Preparando…", 11f, muted)
        infoTop.addView(status)
        info.addView(infoTop)
        info.addView(t("Tu espacio privado para pensar, crear y resolver.", 12f, muted).apply {
            setPadding(0, dp(5), 0, 0)
        })
        root.addView(info, LinearLayout.LayoutParams(-1, dp(80)).apply { topMargin = dp(4) })

        val toolsScroll = hs().apply { setPadding(0, dp(9), 0, dp(4)) }
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        arrayOf("Chat", "Internet", "Proyecto", "Archivos", "Código", "Voz", "Memoria", "Ajustes")
            .forEachIndexed { index, name ->
                val chip = t((if (index == 0) "●  " else "○  ") + name, 11f, if (index == 0) white else muted).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(13), 0, dp(13), 0)
                    background = card(
                        if (index == 0) Color.argb(170, 37, 93, 143) else glassSoft,
                        18,
                        if (index == 0) Color.argb(190, 70, 221, 255) else line
                    )
                    setOnClickListener { toolAction(index) }
                }
                tools.addView(chip, LinearLayout.LayoutParams(dp(118), dp(43)).apply {
                    marginEnd = dp(8)
                })
            }
        toolsScroll.addView(tools)
        root.addView(toolsScroll, LinearLayout.LayoutParams(-1, dp(56)))

        scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(1), dp(7), dp(1), dp(12))
        }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(23), dp(27), dp(23), dp(27))
            background = card(
                Color.argb(105, 28, 35, 61),
                28,
                Color.argb(100, 120, 157, 219)
            )
        }
        welcome.addView(t("Lix", 34f).apply {
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        welcome.addView(t("¿En qué trabajamos hoy?", 17f).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        })
        welcome.addView(t(
            "Preguntá, escribí, investigá o hablá. Lix se adapta a vos.",
            12f, muted
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, 0)
        })
        chat.addView(welcome, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })

        val quickScroll = hs().apply { setPadding(0, dp(1), 0, dp(5)) }
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        arrayOf(
            "Buscar algo",
            "Analizar archivo",
            "Ayudarme a programar",
            "Recordar esto",
            "Hablar con Lix"
        ).forEach { action ->
            val chip = t(action, 10.5f).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
                background = card(glassSoft, 16)
                setOnClickListener { quickAction(action) }
            }
            quick.addView(chip, LinearLayout.LayoutParams(dp(160), dp(42)).apply {
                marginEnd = dp(8)
            })
        }
        quickScroll.addView(quick)
        root.addView(quickScroll, LinearLayout.LayoutParams(-1, dp(51)))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(7))
        }
        composer.addView(t("＋", 25f).apply {
            gravity = Gravity.CENTER
            background = card(glassSoft, 18)
            setOnClickListener { pickFile() }
        }, LinearLayout.LayoutParams(dp(48), dp(56)).apply {
            marginEnd = dp(7)
        })

        input = EditText(this).apply {
            hint = "Escribile a Lix…"
            textSize = 14f
            setTextColor(white)
            setHintTextColor(Color.rgb(117, 130, 157))
            maxLines = 3
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(10), 0)
            background = card(Color.argb(145, 20, 27, 48), 19)
            isEnabled = false
        }
        composer.addView(input, LinearLayout.LayoutParams(0, dp(56), 1f))

        composer.addView(t("◉", 19f, cyan).apply {
            gravity = Gravity.CENTER
            background = card(glassSoft, 18)
            setOnClickListener { startVoice() }
        }, LinearLayout.LayoutParams(dp(48), dp(56)).apply {
            marginStart = dp(7)
        })

        send = t("↑", 22f).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(blue, violet)
            ).apply { cornerRadius = dp(18).toFloat() }
            isEnabled = false
            setOnClickListener { sendMessage() }
        }
        composer.addView(send, LinearLayout.LayoutParams(dp(55), dp(56)).apply {
            marginStart = dp(7)
        })
        root.addView(composer)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = card(
                Color.argb(125, 13, 18, 34),
                21,
                Color.argb(90, 104, 139, 193)
            )
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }
        arrayOf("Inicio", "Herramientas", "Lix", "Historial", "Perfil")
            .forEachIndexed { index, name ->
                nav.addView(t(
                    name,
                    if (index == 2) 10.5f else 9.5f,
                    if (index == 2) cyan else muted
                ).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(7), 0, dp(7))
                    setOnClickListener { bottomAction(index) }
                }, LinearLayout.LayoutParams(0, dp(44), 1f))
            }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(t(
            "Lix  •  privado, local y pensado para vos",
            8f, Color.rgb(116, 126, 151)
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        }, LinearLayout.LayoutParams(-1, dp(18)))

        setContentView(root)
    }

'''

p.write_text(s[:start] + new + s[end:])
