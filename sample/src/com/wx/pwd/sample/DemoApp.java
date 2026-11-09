package com.wx.pwd.sample;

import com.wx.pwd.CheckStrength;
import com.wx.pwd.CheckStrength.LEVEL;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * CheckPasswordStrength 的 Swing 示例：输入密码实时显示库返回的分值和等级，
 * 右侧样例覆盖五个等级和主要扣分规则，点击即可填入。
 * <p>
 * 运行：{@code ./gradlew :sample:run}
 */
public final class DemoApp {

    /**
     * 样例密码与说明：说明只写影响分值的规则或构成，不评价强弱；
     * 分值和等级都在运行时由库计算，这里不写死
     */
    private static final String[][] SAMPLES = {
            {"123456", "Digits only, numeric run 1234"},
            {"password", "Dictionary word"},
            {"Ab1!Ab1!Ab1!", "Repeats Ab1!, scored as Ab1!"},
            {"iloveyou520", "Contains dictionary word iloveyou"},
            {"Tom19881010!", "Contains date 19881010"},
            {"Summer2026", "3 character classes, 10 characters"},
            {"Qwerty123!", "Keyboard run qwer, 4 classes"},
            {"correct-horse-Battery-9", "4 classes, 23 characters"},
            {"2hAj5#mne-ix.86H", "4 classes, 16 characters, 3 symbols"},
    };

    private final JFrame frame = new JFrame("CheckPasswordStrength Demo");
    private final Ui.PasswordInput input = new Ui.PasswordInput();
    private final Ui.IconButton eyeButton = new Ui.IconButton(Ui.Glyph.EYE, 36, false, "Show password", this::toggleEcho);
    private final Ui.IconButton themeButton = new Ui.IconButton(Ui.Glyph.MOON, 40, true, "Toggle dark mode", this::toggleTheme);
    private final List<Ui.SampleRow> rows = new ArrayList<>();

    private final Ui.ScoreHeader scoreHeader = new Ui.ScoreHeader();
    private final Ui.Meter meter = new Ui.Meter();
    private final Ui.Stats stats = new Ui.Stats();
    private final Ui.ApiResult api = new Ui.ApiResult();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(DemoApp::launch);
    }

    private static void launch() {
        try {
            // 跨平台外观，保证各系统上观感一致；组件外观基本都是自绘
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // 退回默认外观也能正常使用
        }
        DemoApp app = new DemoApp();
        app.frame.setVisible(true);
        app.input.requestFocusInWindow();
    }

    private DemoApp() {
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.current().bg);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.setBorder(new EmptyBorder(24, 28, 28, 28));
        root.add(header(), BorderLayout.NORTH);

        JPanel body = new JPanel(new GridLayout(1, 2, 24, 0));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(24, 0, 0, 0));
        body.add(leftColumn());
        body.add(rightColumn());
        root.add(body, BorderLayout.CENTER);

        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        });
        refresh();

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(root);
        frame.pack();
        frame.setMinimumSize(frame.getSize());
        frame.setLocationRelativeTo(null);
    }

    private JPanel header() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.add(new Ui.Logo());
        p.add(Box.createHorizontalStrut(12));
        p.add(new Ui.Label("CheckPasswordStrength", Theme.uiBold(19), t -> t.text));
        String version = System.getProperty("checkstrength.version");
        if (version != null) {
            p.add(Box.createHorizontalStrut(10));
            p.add(new Ui.Chip("v" + version));
        }
        p.add(Box.createHorizontalGlue());
        p.add(themeButton);
        return p;
    }

    private JPanel leftColumn() {
        JPanel col = Ui.column();
        col.add(new Ui.Label("JAVA \u00B7 PASSWORD STRENGTH", Theme.monoTracked(12), t -> t.accentInk));
        col.add(Ui.gap(10));
        col.add(new Ui.Label("How strong is", Theme.uiBold(36), t -> t.text));
        col.add(new Ui.Label("your password?", Theme.uiBold(36), t -> t.text));
        col.add(Ui.gap(10));
        col.add(new Ui.Label("Pure Java, zero dependencies. Every number on", Theme.ui(14.5f), t -> t.muted));
        col.add(new Ui.Label("this screen comes straight from CheckStrength.", Theme.ui(14.5f), t -> t.muted));
        col.add(Ui.gap(22));

        Ui.Card card = new Ui.Card(22);
        card.add(Ui.spread(new Ui.Label("LIVE CHECK", Theme.monoTracked(11), t -> t.muted),
                new Ui.Label("type, or pick a sample", Theme.ui(12.5f), t -> t.muted)));
        card.add(Ui.gap(12));
        card.add(new Ui.FieldBox(input, eyeButton));
        card.add(Ui.gap(18));
        card.add(scoreHeader);
        card.add(Ui.gap(14));
        card.add(meter);
        card.add(Ui.gap(14));
        card.add(new Ui.Divider());
        card.add(Ui.gap(18));
        card.add(stats);
        card.add(Ui.gap(16));
        card.add(api);
        col.add(card);
        return col;
    }

    private JPanel rightColumn() {
        Ui.Card card = new Ui.Card(12);
        JPanel head = Ui.spread(new Ui.Label("SAMPLES", Theme.monoTracked(11), t -> t.muted),
                new Ui.Label("click a row to try it", Theme.ui(12.5f), t -> t.muted));
        head.setBorder(new EmptyBorder(10, 14, 8, 14));
        card.add(head);
        for (String[] s : SAMPLES) {
            String pwd = s[0];
            Ui.SampleRow row = new Ui.SampleRow(pwd, s[1], CheckStrength.checkPasswordStrength(pwd),
                    CheckStrength.getPasswordLevel(pwd), () -> pick(pwd));
            rows.add(row);
            card.add(row);
        }
        card.add(Box.createVerticalGlue());
        Ui.Label foot = new Ui.Label("Scores 0-14 \u00B7 higher is stronger", Theme.ui(12.5f), t -> t.muted);
        foot.setBorder(new EmptyBorder(10, 14, 6, 14));
        card.add(foot);

        JPanel col = Ui.column();
        col.add(card);
        return col;
    }

    /** 读取当前输入，调用库的两个公开 API，刷新所有结果展示 */
    private void refresh() {
        String pwd = new String(input.getPassword());
        Integer score = null;
        LEVEL level = null;
        String error = null;
        try {
            score = CheckStrength.checkPasswordStrength(pwd);
            level = CheckStrength.getPasswordLevel(pwd);
        } catch (IllegalArgumentException e) {
            error = e.getMessage();
        }
        scoreHeader.set(score, level);
        meter.set(score, level);
        stats.set(pwd);
        api.set(score, level, error);
        for (Ui.SampleRow row : rows) {
            row.setSelected(row.password().equals(pwd));
        }
    }

    private void pick(String pwd) {
        input.setText(pwd);
        input.requestFocusInWindow();
    }

    private void toggleEcho() {
        boolean shown = input.getEchoChar() == 0;
        input.setEchoChar(shown ? Ui.PasswordInput.ECHO : (char) 0);
        eyeButton.setGlyph(shown ? Ui.Glyph.EYE : Ui.Glyph.EYE_OFF);
        String name = shown ? "Show password" : "Hide password";
        eyeButton.setToolTipText(name);
        eyeButton.getAccessibleContext().setAccessibleName(name);
        input.requestFocusInWindow();
    }

    private void toggleTheme() {
        Theme.toggle();
        themeButton.setGlyph(Theme.current().dark ? Ui.Glyph.SUN : Ui.Glyph.MOON);
        frame.repaint();
    }
}
