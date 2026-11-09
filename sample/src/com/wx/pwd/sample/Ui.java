package com.wx.pwd.sample;

import com.wx.pwd.CheckStrength.LEVEL;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 示例用到的自绘组件。所有颜色在绘制时从 {@link Theme#current()} 读取，切换主题后重绘即可。
 */
final class Ui {

    /** 分值上限：库的所有加分项全部命中时为 14 */
    static final int MAX_SCORE = 14;

    private Ui() {
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    static JPanel column() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    static Component gap(int height) {
        Box.Filler f = new Box.Filler(new Dimension(0, height), new Dimension(0, height), new Dimension(0, height));
        f.setAlignmentX(Component.LEFT_ALIGNMENT);
        return f;
    }

    /** 左右两端各放一段文字的一行 */
    static JPanel spread(Component left, Component right) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(left, BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    /** 画一个胶囊标签，右端对齐到 right，返回宽度 */
    static int pill(Graphics2D g, String text, Font font, Color fg, Color bg, int right, int centerY, int height) {
        FontMetrics fm = g.getFontMetrics(font);
        int w = fm.stringWidth(text) + height;
        int x = right - w;
        g.setColor(bg);
        g.fill(new RoundRectangle2D.Float(x, centerY - height / 2f, w, height, height, height));
        g.setColor(fg);
        g.setFont(font);
        g.drawString(text, x + height / 2f, centerY + (fm.getAscent() - fm.getDescent()) / 2f);
        return w;
    }

    /** 颜色随主题变化的文字 */
    interface Role {
        Color of(Theme t);
    }

    static final class Label extends JLabel {
        private final Role role;

        Label(String text, Font font, Role role) {
            super(text);
            this.role = role;
            setFont(font);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        public Color getForeground() {
            // 构造期间 role 尚未赋值
            return role == null ? super.getForeground() : role.of(Theme.current());
        }
    }

    /** 宽度可拉伸、高度固定的自绘块 */
    abstract static class Block extends JComponent {
        Block(int height) {
            setPreferredSize(new Dimension(400, height));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            // 只用于展示，不占 Tab 焦点；内容通过 describe 暴露给读屏软件
            setFocusable(false);
        }

        /** 更新读屏软件读到的文字 */
        void describe(String text) {
            getAccessibleContext().setAccessibleName(text);
        }

        @Override
        public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleJComponent() {
                    @Override
                    public AccessibleRole getAccessibleRole() {
                        return AccessibleRole.LABEL;
                    }
                };
            }
            return accessibleContext;
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** 圆角卡片容器 */
    static final class Card extends JPanel {
        Card(int padding) {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(padding, padding, padding, padding));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Theme t = Theme.current();
            Graphics2D g2 = smooth(g);
            RoundRectangle2D r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 22, 22);
            g2.setColor(t.surface);
            g2.fill(r);
            g2.setColor(t.border);
            g2.draw(r);
            g2.dispose();
        }
    }

    static final class Divider extends Block {
        Divider() {
            super(1);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(Theme.current().border);
            g.fillRect(0, 0, getWidth(), 1);
        }
    }

    /**
     * 可点击的自绘组件：悬停高亮；鼠标松开、回车、空格都会触发；
     * 以按钮角色和可执行动作暴露给辅助功能；获得键盘焦点时由子类调用 paintFocusRing 画焦点环
     */
    abstract static class Clickable extends JComponent {
        private final Runnable onClick;
        boolean hover;

        Clickable(String name, Runnable onClick) {
            this.onClick = onClick;
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName(name);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    // 用松开而不是 mouseClicked：按下和松开之间指针稍有移动时 mouseClicked 不会触发
                    if (SwingUtilities.isLeftMouseButton(e) && contains(e.getPoint())) {
                        Clickable.this.onClick.run();
                    }
                }
            });
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "press");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "press");
            getActionMap().put("press", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    Clickable.this.onClick.run();
                }
            });
        }

        void paintFocusRing(Graphics2D g2, float arc) {
            if (isFocusOwner()) {
                g2.setColor(Theme.ACCENT);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, arc, arc));
            }
        }

        @Override
        public AccessibleContext getAccessibleContext() {
            // JComponent 默认不创建 AccessibleContext，自绘组件需要自己提供
            if (accessibleContext == null) {
                accessibleContext = new AccessibleClickable();
            }
            return accessibleContext;
        }

        private final class AccessibleClickable extends AccessibleJComponent implements AccessibleAction {
            @Override
            public AccessibleRole getAccessibleRole() {
                return AccessibleRole.PUSH_BUTTON;
            }

            @Override
            public AccessibleAction getAccessibleAction() {
                return this;
            }

            @Override
            public int getAccessibleActionCount() {
                return 1;
            }

            @Override
            public String getAccessibleActionDescription(int i) {
                return i == 0 ? AccessibleAction.CLICK : null;
            }

            @Override
            public boolean doAccessibleAction(int i) {
                if (i != 0) {
                    return false;
                }
                onClick.run();
                return true;
            }
        }
    }

    // ---------- 图标按钮 ----------

    enum Glyph {MOON, SUN, EYE, EYE_OFF}

    static final class IconButton extends Clickable {
        private final boolean boxed;
        private Glyph glyph;

        IconButton(Glyph glyph, int size, boolean boxed, String name, Runnable onClick) {
            super(name, onClick);
            this.glyph = glyph;
            this.boxed = boxed;
            Dimension d = new Dimension(size, size);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            setToolTipText(name);
        }

        void setGlyph(Glyph glyph) {
            this.glyph = glyph;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Theme t = Theme.current();
            Graphics2D g2 = smooth(g);
            int w = getWidth();
            int h = getHeight();
            RoundRectangle2D box = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 12, 12);
            if (boxed) {
                g2.setColor(hover ? t.track : t.surface);
                g2.fill(box);
                g2.setColor(t.border);
                g2.draw(box);
            } else if (hover) {
                g2.setColor(t.track);
                g2.fill(box);
            }
            g2.setColor(boxed ? t.text : t.muted);
            drawGlyph(g2, glyph, w / 2f, h / 2f);
            paintFocusRing(g2, 12);
            g2.dispose();
        }

        private static void drawGlyph(Graphics2D g, Glyph glyph, float cx, float cy) {
            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (glyph) {
                case MOON: {
                    Area moon = new Area(new Ellipse2D.Float(cx - 7.5f, cy - 7.5f, 15, 15));
                    moon.subtract(new Area(new Ellipse2D.Float(cx - 3f, cy - 11f, 15, 15)));
                    g.fill(moon);
                    break;
                }
                case SUN: {
                    g.fill(new Ellipse2D.Float(cx - 4f, cy - 4f, 8, 8));
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI / 4 * i;
                        g.draw(new Line2D.Double(cx + Math.cos(a) * 6.5, cy + Math.sin(a) * 6.5,
                                cx + Math.cos(a) * 9, cy + Math.sin(a) * 9));
                    }
                    break;
                }
                default: {
                    Path2D eye = new Path2D.Float();
                    eye.moveTo(cx - 9, cy);
                    eye.quadTo(cx, cy - 9, cx + 9, cy);
                    eye.quadTo(cx, cy + 9, cx - 9, cy);
                    eye.closePath();
                    g.draw(eye);
                    g.draw(new Ellipse2D.Float(cx - 2.6f, cy - 2.6f, 5.2f, 5.2f));
                    if (glyph == Glyph.EYE_OFF) {
                        g.draw(new Line2D.Float(cx - 7.5f, cy - 7.5f, cx + 7.5f, cy + 7.5f));
                    }
                }
            }
        }
    }

    /** 标题栏左侧的应用图标：橙色圆角方块 + 锁 */
    static final class Logo extends JComponent {
        Logo() {
            setFocusable(false);
            Dimension d = new Dimension(36, 36);
            setPreferredSize(d);
            setMaximumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(Theme.ACCENT);
            g2.fill(new RoundRectangle2D.Float(0, 0, 36, 36, 12, 12));
            g2.setColor(Theme.ON_ACCENT);
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D shackle = new Path2D.Float();
            shackle.moveTo(13.5, 17);
            shackle.lineTo(13.5, 14.5);
            shackle.append(new Arc2D.Float(13.5f, 10f, 9f, 9f, 180, -180, Arc2D.OPEN), true);
            shackle.lineTo(22.5, 17);
            g2.draw(shackle);
            g2.fill(new RoundRectangle2D.Float(10.5f, 16.5f, 15, 11, 4, 4));
            g2.setColor(Theme.ACCENT);
            g2.fill(new Ellipse2D.Float(16.5f, 20, 3, 3));
            g2.dispose();
        }
    }

    /** 版本号小标签 */
    static final class Chip extends JComponent {
        private final String text;
        private final Font font = Theme.mono(11.5f);

        Chip(String text) {
            this.text = text;
            setFocusable(false);
            int w = getFontMetrics(font).stringWidth(text) + 18;
            Dimension d = new Dimension(w, 24);
            setPreferredSize(d);
            setMaximumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Theme t = Theme.current();
            Graphics2D g2 = smooth(g);
            RoundRectangle2D r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 24, 24);
            g2.setColor(t.track);
            g2.fill(r);
            g2.setColor(t.border);
            g2.draw(r);
            g2.setColor(t.textSub);
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, 9, (getHeight() + fm.getAscent() - fm.getDescent()) / 2f);
            g2.dispose();
        }
    }

    // ---------- 检测结果展示 ----------

    /** 大号分值 + 等级胶囊 */
    static final class ScoreHeader extends Block {
        private Integer score;
        private LEVEL level;

        ScoreHeader() {
            super(64);
        }

        void set(Integer score, LEVEL level) {
            this.score = score;
            this.level = level;
            describe(level == null ? "No input" : "Score " + score + " of " + MAX_SCORE + ", " + Theme.levelName(level));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Theme t = Theme.current();
            Graphics2D g2 = smooth(g);
            int baseline = 54;
            int x;
            if (score == null) {
                // 没有分值时画数字大小的占位块，而不是假装成 0 分
                g2.setColor(t.track);
                g2.fill(new RoundRectangle2D.Float(0, baseline - 41, 52, 41, 12, 12));
                x = 60;
            } else {
                String num = String.valueOf(score);
                g2.setFont(Theme.uiBold(58));
                g2.setColor(t.levelInk(level));
                g2.drawString(num, 0, baseline);
                x = g2.getFontMetrics().stringWidth(num) + 8;
            }
            g2.setFont(Theme.ui(18));
            g2.setColor(t.muted);
            g2.drawString("/ " + MAX_SCORE, x, baseline);

            int cy = baseline - 20;
            if (level == null) {
                pill(g2, "NO INPUT", Theme.monoTracked(11.5f), t.muted, t.track, getWidth(), cy, 30);
            } else {
                pill(g2, Theme.levelName(level), Theme.monoTracked(11.5f), t.levelInk(level), t.levelSoft(level),
                        getWidth(), cy, 30);
            }
            g2.dispose();
        }
    }

    /** 14 格强度条，下方标出五个等级的分值区间 */
    static final class Meter extends Block {
        private static final int[][] BANDS = {{1, 3}, {4, 6}, {7, 9}, {10, 12}, {13, 14}};
        private static final String[] NAMES = {"EASY", "MEDIUM", "STRONG", "VERY STRONG", "EXTREME"};
        private static final String[] RANGES = {"0-3", "4-6", "7-9", "10-12", "13+"};
        private static final float GAP = 4;

        private int score;
        private LEVEL level;

        Meter() {
            super(54);
        }

        void set(Integer score, LEVEL level) {
            this.score = score == null ? 0 : score;
            this.level = level;
            describe("Strength meter, " + this.score + " of " + MAX_SCORE + " segments filled");
            repaint();
        }

        private float segWidth() {
            return (getWidth() - GAP * (MAX_SCORE - 1)) / MAX_SCORE;
        }

        private float segX(int index) {
            return index * (segWidth() + GAP);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Theme t = Theme.current();
            Graphics2D g2 = smooth(g);
            float w = segWidth();
            for (int i = 0; i < MAX_SCORE; i++) {
                g2.setColor(level != null && i < score ? Theme.levelFill(level) : t.track);
                g2.fill(new RoundRectangle2D.Float(segX(i), 0, w, 10, 6, 6));
            }
            Font name = Theme.monoTracked(10);
            Font range = Theme.mono(10);
            for (int b = 0; b < BANDS.length; b++) {
                float x = segX(BANDS[b][0] - 1);
                boolean on = level != null && level.ordinal() == b;
                g2.setFont(name);
                g2.setColor(on ? t.levelInk(level) : t.muted);
                g2.drawString(NAMES[b], x, 31);
                g2.setFont(range);
                g2.setColor(on ? t.levelInk(level) : t.muted);
                g2.drawString(RANGES[b], x, 47);
            }
            g2.dispose();
        }
    }

    /** 密码构成：长度与四类字符的数量 */
    static final class Stats extends Block {
        private static final String[] LABELS = {"LENGTH", "DIGITS", "LOWER", "UPPER", "SYMBOLS"};
        private final int[] values = new int[LABELS.length];

        Stats() {
            super(48);
        }

        void set(String pwd) {
            java.util.Arrays.fill(values, 0);
            values[0] = pwd.length();
            for (char c : pwd.toCharArray()) {
                // 与库的字符分类一致：ASCII 数字、小写、大写，其余都算符号
                if (c >= '0' && c <= '9') {
                    values[1]++;
                } else if (c >= 'a' && c <= 'z') {
                    values[2]++;
                } else if (c >= 'A' && c <= 'Z') {
                    values[3]++;
                } else {
                    values[4]++;
                }
            }
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < LABELS.length; i++) {
                text.append(i == 0 ? "" : ", ").append(LABELS[i].toLowerCase(java.util.Locale.ROOT))
                        .append(' ').append(values[i]);
            }
            describe(text.toString());
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Theme t = Theme.current();
            Graphics2D g2 = smooth(g);
            float col = getWidth() / (float) LABELS.length;
            for (int i = 0; i < LABELS.length; i++) {
                float x = i * col;
                g2.setFont(Theme.uiBold(24));
                g2.setColor(values[i] > 0 ? t.text : t.muted);
                g2.drawString(String.valueOf(values[i]), x, 22);
                g2.setFont(Theme.monoTracked(10));
                g2.setColor(t.muted);
                g2.drawString(LABELS[i], x, 42);
            }
            g2.dispose();
        }
    }

    /** 两个公开 API 的原始返回值；空密码时显示库抛出的异常 */
    static final class ApiResult extends Block {
        private static final String CALL_SCORE = "checkPasswordStrength(pwd)";
        private static final String CALL_LEVEL = "getPasswordLevel(pwd)";

        private Integer score;
        private LEVEL level;
        private String error;

        ApiResult() {
            super(78);
        }

        void set(Integer score, LEVEL level, String error) {
            this.score = score;
            this.level = level;
            this.error = error;
            describe(error != null ? "Throws IllegalArgumentException: " + error
                    : CALL_SCORE + " returns " + score + ", " + CALL_LEVEL + " returns " + level.name());
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Theme t = Theme.current();
            Graphics2D g2 = smooth(g);
            RoundRectangle2D r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.setColor(t.codeBg);
            g2.fill(r);
            g2.setColor(t.border);
            g2.draw(r);

            Font font = Theme.mono(12);
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            int pad = 16;
            int y1 = 32;
            int y2 = 56;
            if (error != null) {
                g2.setColor(t.levelInk(LEVEL.EASY));
                g2.drawString("throws IllegalArgumentException", pad, y1);
                g2.setColor(t.muted);
                g2.drawString("// \"" + error + "\"", pad, y2);
            } else {
                int arrowX = pad + fm.stringWidth(CALL_SCORE) + 14;
                int valueX = arrowX + fm.stringWidth("\u2192") + 10;
                g2.setColor(t.textSub);
                g2.drawString(CALL_SCORE, pad, y1);
                g2.drawString(CALL_LEVEL, pad, y2);
                g2.setColor(t.muted);
                g2.drawString("\u2192", arrowX, y1);
                g2.drawString("\u2192", arrowX, y2);
                g2.setColor(t.accentInk);
                g2.drawString(String.valueOf(score), valueX, y1);
                g2.drawString(level.name(), valueX, y2);
            }
            g2.dispose();
        }
    }

    // ---------- 输入框 ----------

    /** 密码输入框：透明背景，颜色随主题，空时显示占位提示 */
    static final class PasswordInput extends JPasswordField {
        static final char ECHO = '\u2022';
        private final Font bundled = Theme.mono(18);
        /** 打包字体只有拉丁字形，显示中文、emoji 等明文时改用系统等宽字体（带系统级字形回退） */
        private final Font fallback = new Font(Font.MONOSPACED, Font.PLAIN, 18);

        PasswordInput() {
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder());
            setFont(bundled);
            setEchoChar(ECHO);
            putClientProperty("caretWidth", 2);
            getAccessibleContext().setAccessibleName("Password");
            getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    updateFont();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    updateFont();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    updateFont();
                }
            });
        }

        @Override
        public void setEchoChar(char c) {
            super.setEchoChar(c);
            updateFont();
        }

        private void updateFont() {
            // 父类构造期间会先调用 setEchoChar，此时字段尚未赋值
            if (bundled == null) {
                return;
            }
            boolean plain = getEchoChar() == 0;
            Font want = plain && bundled.canDisplayUpTo(new String(getPassword())) != -1 ? fallback : bundled;
            if (!want.equals(getFont())) {
                setFont(want);
            }
        }

        @Override
        public Color getForeground() {
            return Theme.current().text;
        }

        @Override
        public Color getCaretColor() {
            return Theme.ACCENT;
        }

        @Override
        public Color getSelectionColor() {
            return Theme.current().accentSoft;
        }

        @Override
        public Color getSelectedTextColor() {
            return Theme.current().text;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getDocument().getLength() == 0) {
                Graphics2D g2 = smooth(g);
                Insets in = getInsets();
                g2.setFont(Theme.ui(16));
                g2.setColor(Theme.current().muted);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("Type a password", in.left + 2, (getHeight() + fm.getAscent() - fm.getDescent()) / 2f);
                g2.dispose();
            }
        }
    }

    /** 输入框外框：聚焦时描橙色边 */
    static final class FieldBox extends JPanel {
        private final JComponent input;

        FieldBox(JComponent input, JComponent trailing) {
            super(new BorderLayout(8, 0));
            this.input = input;
            setOpaque(false);
            setBorder(new EmptyBorder(0, 18, 0, 10));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setPreferredSize(new Dimension(400, 58));
            JPanel east = new JPanel(new java.awt.GridBagLayout());
            east.setOpaque(false);
            east.add(trailing);
            add(input, BorderLayout.CENTER);
            add(east, BorderLayout.EAST);
            input.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Theme t = Theme.current();
            Graphics2D g2 = smooth(g);
            boolean focused = input.isFocusOwner();
            RoundRectangle2D r = new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 16, 16);
            g2.setColor(t.codeBg);
            g2.fill(r);
            g2.setColor(focused ? Theme.ACCENT : t.border);
            g2.setStroke(new BasicStroke(focused ? 1.6f : 1f));
            g2.draw(r);
            g2.dispose();
        }
    }

    // ---------- 样例列表 ----------

    static final class SampleRow extends Clickable {
        private final String password;
        private final String hint;
        private final int score;
        private final LEVEL level;
        private boolean selected;

        SampleRow(String password, String hint, int score, LEVEL level, Runnable onClick) {
            super("Try sample " + password, onClick);
            this.password = password;
            this.hint = hint;
            this.score = score;
            this.level = level;
            setPreferredSize(new Dimension(400, 58));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        String password() {
            return password;
        }

        void setSelected(boolean selected) {
            if (this.selected != selected) {
                this.selected = selected;
                repaint();
            }
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Theme t = Theme.current();
            Graphics2D g2 = smooth(g);
            int w = getWidth();
            int h = getHeight();
            if (selected || hover) {
                g2.setColor(selected ? t.accentSoft : t.track);
                g2.fill(new RoundRectangle2D.Float(0, 1, w, h - 2, 14, 14));
            }
            if (selected) {
                g2.setColor(Theme.ACCENT);
                g2.fill(new RoundRectangle2D.Float(0, 14, 3, h - 28, 3, 3));
            }
            paintFocusRing(g2, 14);
            int pad = 14;
            g2.setFont(Theme.mono(14));
            g2.setColor(t.text);
            g2.drawString(password, pad, 25);
            g2.setFont(Theme.ui(12));
            g2.setColor(t.muted);
            g2.drawString(hint, pad, 44);

            int cy = h / 2;
            int pw = pill(g2, Theme.levelName(level), Theme.monoTracked(10), t.levelInk(level), t.levelSoft(level),
                    w - pad, cy, 24);
            g2.setFont(Theme.uiBold(20));
            g2.setColor(t.levelInk(level));
            String s = String.valueOf(score);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(s, w - pad - pw - 12 - fm.stringWidth(s), cy + (fm.getAscent() - fm.getDescent()) / 2f);
            g2.dispose();
        }
    }
}
