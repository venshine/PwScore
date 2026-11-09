package com.wx.pwd.sample;

import com.wx.pwd.CheckStrength.LEVEL;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 示例的色板与字体。浅色 / 深色两套色板字段一一对应，组件绘制时读取 {@link #current()}，
 * 切换主题后只需重绘。色板沿用 WheelView 示例。
 */
final class Theme {

    static final Theme LIGHT = new Theme(false,
            0xF4F2EE, 0xFFFFFF, 0xE4DFD6, 0xEFECE6, 0xFBFAF7,
            0x16181D, 0x3A3F48, 0x5C6370, 0xC2410C, 0x1FFF7A3D,
            new int[]{0xCE2C31, 0xA35200, 0x2A7E3B, 0x067A6F, 0x3451B2});

    static final Theme DARK = new Theme(true,
            0x0F1115, 0x171A20, 0x262B33, 0x2A2F38, 0x0B0D11,
            0xF2F1EC, 0xC9CDD4, 0x9AA0AB, 0xFF7A3D, 0x26FF7A3D,
            new int[]{0xFF6369, 0xFFCA16, 0x65BA74, 0x0BD8B6, 0x9EB1FF});

    static final Color ACCENT = new Color(0xFF7A3D);
    static final Color ON_ACCENT = new Color(0x1A0E06);

    /** 各等级强度条的填充色，深浅色共用；顺序同 LEVEL 枚举 */
    private static final int[] LEVEL_FILL = {0xE5484D, 0xF5A524, 0x46A758, 0x12A594, 0x3E63DD};

    private static Theme current = LIGHT;

    final boolean dark;
    final Color bg;
    final Color surface;
    final Color border;
    final Color track;
    final Color codeBg;
    final Color text;
    final Color textSub;
    final Color muted;
    final Color accentInk;
    final Color accentSoft;
    /** 等级颜色用作文字时的版本，浅色下加深以保证对比度 */
    private final Color[] levelInk;

    private Theme(boolean dark, int bg, int surface, int border, int track, int codeBg,
                  int text, int textSub, int muted, int accentInk, int accentSoft, int[] levelInk) {
        this.dark = dark;
        this.bg = new Color(bg);
        this.surface = new Color(surface);
        this.border = new Color(border);
        this.track = new Color(track);
        this.codeBg = new Color(codeBg);
        this.text = new Color(text);
        this.textSub = new Color(textSub);
        this.muted = new Color(muted);
        this.accentInk = new Color(accentInk);
        this.accentSoft = new Color(accentSoft, true);
        this.levelInk = new Color[levelInk.length];
        for (int i = 0; i < levelInk.length; i++) {
            this.levelInk[i] = new Color(levelInk[i]);
        }
    }

    static Theme current() {
        return current;
    }

    static void toggle() {
        current = current.dark ? LIGHT : DARK;
    }

    Color levelInk(LEVEL level) {
        return levelInk[level.ordinal()];
    }

    static Color levelFill(LEVEL level) {
        return new Color(LEVEL_FILL[level.ordinal()]);
    }

    Color levelSoft(LEVEL level) {
        return new Color((dark ? 0x33000000 : 0x22000000) | LEVEL_FILL[level.ordinal()], true);
    }

    static String levelName(LEVEL level) {
        switch (level) {
            case EASY:
                return "EASY";
            case MEDIUM:
                return "MEDIUM";
            case STRONG:
                return "STRONG";
            case VERY_STRONG:
                return "VERY STRONG";
            default:
                return "EXTREMELY STRONG";
        }
    }

    // ---------- 字体：打包 SIL OFL 1.1 字体，加载失败时退回系统逻辑字体 ----------

    private static final Font UI = load("SpaceGrotesk-Medium.ttf", Font.SANS_SERIF, Font.PLAIN);
    private static final Font UI_BOLD = load("SpaceGrotesk-Bold.ttf", Font.SANS_SERIF, Font.BOLD);
    private static final Font MONO = load("JetBrainsMono-Regular.ttf", Font.MONOSPACED, Font.PLAIN);

    static Font ui(float size) {
        return UI.deriveFont(size);
    }

    static Font uiBold(float size) {
        return UI_BOLD.deriveFont(size);
    }

    static Font mono(float size) {
        return MONO.deriveFont(size);
    }

    /** 加大字距的等宽字体，用于小号大写标签 */
    static Font monoTracked(float size) {
        Map<TextAttribute, Object> attrs = new HashMap<>();
        attrs.put(TextAttribute.TRACKING, 0.08f);
        return mono(size).deriveFont(attrs);
    }

    private static Font load(String file, String fallback, int fallbackStyle) {
        try (InputStream in = Theme.class.getResourceAsStream("/fonts/" + file)) {
            if (in != null) {
                return Font.createFont(Font.TRUETYPE_FONT, in);
            }
        } catch (Exception e) {
            System.err.println("Failed to load font " + file + ": " + e);
        }
        return new Font(fallback, fallbackStyle, 12);
    }
}
