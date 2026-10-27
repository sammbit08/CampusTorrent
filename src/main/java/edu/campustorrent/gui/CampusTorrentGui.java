
package edu.campustorrent.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;

public class CampusTorrentGui extends JFrame {

    private static final Color BG = new Color(242, 247, 253);
    private static final Color WHITE = Color.WHITE;
    private static final Color NAVY = new Color(30, 52, 78);
    private static final Color BLUE = new Color(55, 126, 218);
    private static final Color BLUE_LIGHT = new Color(226, 239, 255);
    private static final Color MUTED = new Color(125, 143, 164);
    private static final Color GREEN = new Color(30, 157, 112);
    private static final Color BORDER = new Color(224, 233, 244);
    private static final Color TERMINAL = new Color(24, 37, 55);

    private JLabel statusValue;
    private JLabel peersValue;
    private JLabel completedValue;
    private JLabel timeValue;
    private JLabel progressText;
    private JLabel footerStatus;
    private JProgressBar progressBar;
    private JTextArea console;
    private JButton runButton;

    private final File projectRoot =
            new File(System.getProperty("user.dir"));

    private long startTime;

    public CampusTorrentGui() {
        setTitle("CampusTorrent | Peer-to-Peer Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 700));
        setSize(1120, 820);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 22));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(26, 30, 26, 30));
        setContentPane(root);

        root.add(createHeader(), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(createHero());
        content.add(Box.createVerticalStrut(22));
        content.add(createSectionTitle("Transfer overview",
                "Monitor your local download simulation"));
        content.add(Box.createVerticalStrut(13));
        content.add(createStatistics());
        content.add(Box.createVerticalStrut(22));
        content.add(createProgressCard());
        content.add(Box.createVerticalStrut(22));
        content.add(createConsoleCard());
        content.add(Box.createVerticalStrut(15));

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        root.add(scroll, BorderLayout.CENTER);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        brand.setOpaque(false);

        RoundedPanel logo = new RoundedPanel(16, BLUE);
        logo.setPreferredSize(new Dimension(48, 48));
        logo.setLayout(new GridBagLayout());

        JLabel logoText = new JLabel("CT");
        logoText.setForeground(WHITE);
        logoText.setFont(new Font("SansSerif", Font.BOLD, 20));
        logo.add(logoText);

        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("CampusTorrent");
        title.setForeground(NAVY);
        title.setFont(new Font("SansSerif", Font.BOLD, 23));

        JLabel subtitle = new JLabel("PEER-TO-PEER FILE SHARING");
        subtitle.setForeground(MUTED);
        subtitle.setFont(new Font("SansSerif", Font.BOLD, 10));

        names.add(title);
        names.add(Box.createVerticalStrut(4));
        names.add(subtitle);

        brand.add(logo);
        brand.add(names);

        RoundedPanel badge = new RoundedPanel(18, BLUE_LIGHT);
        badge.setLayout(new GridBagLayout());
        badge.setBorder(new EmptyBorder(9, 13, 9, 13));

        JLabel badgeText = new JLabel("\u25CF  LOCAL ENVIRONMENT");
        badgeText.setForeground(BLUE);
        badgeText.setFont(new Font("SansSerif", Font.BOLD, 11));
        badge.add(badgeText);

        header.add(brand, BorderLayout.WEST);
        header.add(badge, BorderLayout.EAST);

        return header;
    }

    private JPanel createHero() {
        RoundedPanel hero = new RoundedPanel(24, WHITE);
        hero.setLayout(new BorderLayout(20, 12));
        hero.setBorder(new EmptyBorder(28, 30, 28, 30));
        hero.setAlignmentX(Component.LEFT_ALIGNMENT);
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 175));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel eyebrow = new JLabel("YOUR P2P WORKSPACE");
        eyebrow.setForeground(BLUE);
        eyebrow.setFont(new Font("SansSerif", Font.BOLD, 11));

        JLabel heading = new JLabel("Share files. Track every transfer.");
        heading.setForeground(NAVY);
        heading.setFont(new Font("SansSerif", Font.BOLD, 24));

        JLabel description = new JLabel(
                "<html>Run the local demo and watch the transfer output "
                        + "right here.<br>Verify file integrity and inspect "
                        + "peer activity in real time.</html>"
        );
        description.setForeground(MUTED);
        description.setFont(new Font("SansSerif", Font.PLAIN, 13));

        text.add(eyebrow);
        text.add(Box.createVerticalStrut(10));
        text.add(heading);
        text.add(Box.createVerticalStrut(9));
        text.add(description);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));

        runButton = new JButton("\u25B6   Run Local Demo");
        stylePrimaryButton(runButton);
        runButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
        runButton.addActionListener(e -> runDemo());

        JButton resetButton = new JButton("Reset dashboard");
        styleTextButton(resetButton);
        resetButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
        resetButton.addActionListener(e -> resetDashboard());

        actions.add(runButton);
        actions.add(Box.createVerticalStrut(8));
        actions.add(resetButton);

        hero.add(text, BorderLayout.CENTER);
        hero.add(actions, BorderLayout.EAST);

        return hero;
    }

    private JPanel createSectionTitle(String titleText, String subtitleText) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));

        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(titleText);
        title.setFont(new Font("SansSerif", Font.BOLD, 19));
        title.setForeground(NAVY);

        JLabel subtitle = new JLabel(subtitleText);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitle.setForeground(MUTED);

        labels.add(title);
        labels.add(Box.createVerticalStrut(5));
        labels.add(subtitle);

        panel.add(labels, BorderLayout.WEST);
        return panel;
    }

    private JPanel createStatistics() {
        JPanel grid = new JPanel(new GridLayout(1, 4, 15, 0));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 145));

        statusValue = new JLabel("Ready");
        peersValue = new JLabel("--");
        completedValue = new JLabel("0");
        timeValue = new JLabel("--");

        grid.add(createStatCard("DOWNLOAD STATUS", statusValue,
                "Waiting to start", "S"));
        grid.add(createStatCard("ACTIVE PEERS", peersValue,
                "Local demo peers", "P"));
        grid.add(createStatCard("COMPLETED", completedValue,
                "Verified downloads", "D"));
        grid.add(createStatCard("ELAPSED TIME", timeValue,
                "Current demo run", "T"));

        return grid;
    }

    private JPanel createStatCard(String heading, JLabel value,
                                  String caption, String symbol) {
        RoundedPanel card = new RoundedPanel(19, WHITE);
        card.setLayout(new BorderLayout(10, 15));
        card.setBorder(new EmptyBorder(18, 17, 17, 17));

        JPanel top = new JPanel(new BorderLayout(5, 0));
        top.setOpaque(false);

        JLabel label = new JLabel(heading);
        label.setForeground(MUTED);
        label.setFont(new Font("SansSerif", Font.BOLD, 10));

        RoundedPanel icon = new RoundedPanel(10, BLUE_LIGHT);
        icon.setPreferredSize(new Dimension(34, 34));
        icon.setLayout(new GridBagLayout());

        JLabel iconText = new JLabel(symbol);
        iconText.setForeground(BLUE);
        iconText.setFont(new Font("SansSerif", Font.BOLD, 14));
        icon.add(iconText);

        top.add(label, BorderLayout.CENTER);
        top.add(icon, BorderLayout.EAST);

        value.setForeground(BLUE);
        value.setFont(new Font("SansSerif", Font.BOLD, 23));

        JLabel note = new JLabel(caption);
        note.setForeground(MUTED);
        note.setFont(new Font("SansSerif", Font.PLAIN, 11));

        JPanel lower = new JPanel();
        lower.setOpaque(false);
        lower.setLayout(new BoxLayout(lower, BoxLayout.Y_AXIS));
        lower.add(value);
        lower.add(Box.createVerticalStrut(6));
        lower.add(note);

        card.add(top, BorderLayout.NORTH);
        card.add(lower, BorderLayout.CENTER);

        return card;
    }

    private JPanel createProgressCard() {
        RoundedPanel card = new RoundedPanel(21, WHITE);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(23, 24, 23, 24));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);

        JLabel title = new JLabel("Download progress");
        title.setFont(new Font("SansSerif", Font.BOLD, 17));
        title.setForeground(NAVY);

        progressText = new JLabel("Ready when you are");
        progressText.setFont(new Font("SansSerif", Font.PLAIN, 12));
        progressText.setForeground(MUTED);

        heading.add(title, BorderLayout.WEST);
        heading.add(progressText, BorderLayout.EAST);

        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setString("0%");
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("SansSerif", Font.BOLD, 11));
        progressBar.setForeground(BLUE);
        progressBar.setBackground(BLUE_LIGHT);
        progressBar.setBorderPainted(false);
        progressBar.setPreferredSize(new Dimension(100, 19));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 19));

        JLabel footnote = new JLabel(
                "Progress reaches 100% when the demo finishes successfully."
        );
        footnote.setFont(new Font("SansSerif", Font.PLAIN, 11));
        footnote.setForeground(MUTED);

        card.add(heading);
        card.add(Box.createVerticalStrut(20));
        card.add(progressBar);
        card.add(Box.createVerticalStrut(12));
        card.add(footnote);

        return card;
    }

    private JPanel createConsoleCard() {
        RoundedPanel card = new RoundedPanel(21, WHITE);
        card.setLayout(new BorderLayout(0, 15));
        card.setBorder(new EmptyBorder(22, 24, 20, 24));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 380));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Activity console");
        title.setForeground(NAVY);
        title.setFont(new Font("SansSerif", Font.BOLD, 17));

        JLabel subtitle = new JLabel("Live output from the demo script");
        subtitle.setForeground(MUTED);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));

        titles.add(title);
        titles.add(Box.createVerticalStrut(5));
        titles.add(subtitle);

        JButton clearButton = new JButton("Clear console");
        styleTextButton(clearButton);
        clearButton.addActionListener(e -> console.setText(""));

        heading.add(titles, BorderLayout.WEST);
        heading.add(clearButton, BorderLayout.EAST);

        RoundedPanel terminal = new RoundedPanel(14, TERMINAL);
        terminal.setLayout(new BorderLayout());
        terminal.setBorder(new EmptyBorder(3, 3, 3, 3));

        console = new JTextArea(10, 60);
        console.setEditable(false);
        console.setLineWrap(true);
        console.setWrapStyleWord(true);
        console.setBackground(TERMINAL);
        console.setForeground(new Color(213, 229, 246));
        console.setCaretColor(WHITE);
        console.setFont(new Font("Monospaced", Font.PLAIN, 12));
        console.setMargin(new Insets(14, 15, 14, 15));
        console.setText(
                "CampusTorrent terminal initialized.\n"
                + "Java runtime: "
                + System.getProperty("java.version") + "\n"
                + "Click Run Local Demo to begin.\n"
        );

        JScrollPane scroll = new JScrollPane(console);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(15);

        terminal.add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);

        footerStatus = new JLabel("\u25CF  System ready");
        footerStatus.setForeground(GREEN);
        footerStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JButton folderButton = new JButton("Open output folder");
        styleTextButton(folderButton);
        folderButton.addActionListener(e -> openOutputFolder());

        footer.add(footerStatus, BorderLayout.WEST);
        footer.add(folderButton, BorderLayout.EAST);

        card.add(heading, BorderLayout.NORTH);
        card.add(terminal, BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);

        return card;
    }

    private void stylePrimaryButton(JButton button) {
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setForeground(WHITE);
        button.setBackground(BLUE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(new EmptyBorder(13, 17, 13, 17));
    }

    private void styleTextButton(JButton button) {
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setForeground(BLUE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void runDemo() {
        File script = new File(projectRoot, "scripts/run-demo.sh");

        if (!script.isFile()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not find scripts/run-demo.sh.\n\n"
                            + "Project folder:\n"
                            + projectRoot.getAbsolutePath()
                            + "\n\nOpen the GUI from your CampusTorrent project.",
                    "Demo script not found",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        runButton.setEnabled(false);
        runButton.setText("Running demo...");
        statusValue.setText("Running");
        statusValue.setForeground(BLUE);
        peersValue.setText("--");
        completedValue.setText("0");
        timeValue.setText("--");
        progressBar.setValue(0);
        progressBar.setString("Running...");
        progressText.setText("Executing local simulation");
        footerStatus.setText("\u25CF  Demo running");
        footerStatus.setForeground(BLUE);
        console.setText("");

        console.append("Starting CampusTorrent demo...\n");
        console.append("Project: " + projectRoot.getAbsolutePath() + "\n");
        console.append("Java runtime: " + System.getProperty("java.version") + "\n\n");

        startTime = System.currentTimeMillis();

        SwingWorker<Integer, String> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                ProcessBuilder builder = new ProcessBuilder(
                        "bash", "-c",
                        "export JAVA_HOME='/c/Program Files/Java/jdk-22' && "
                                + "export PATH=\"$JAVA_HOME/bin:$PATH\" && "
                                + "java -version && "
                                + "./scripts/run-demo.sh"
                );

                builder.directory(projectRoot);
                builder.redirectErrorStream(true);

                Process process = builder.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        publish(line);
                    }
                }

                return process.waitFor();
            }

            @Override
            protected void process(List<String> lines) {
                for (String line : lines) {
                    console.append(line + "\n");
                    console.setCaretPosition(
                            console.getDocument().getLength()
                    );

                    String lower = line.toLowerCase();

                    if (lower.contains("leech")) {
                        peersValue.setText("4");
                    }

                    if (lower.contains("demo success")) {
                        progressBar.setValue(100);
                        progressBar.setString("100%");
                        progressText.setText("Demo completed");
                    }

                    if (lower.contains("match")) {
                        completedValue.setText("Updating...");
                    }
                }
            }

            @Override
            protected void done() {
                double seconds =
                        (System.currentTimeMillis() - startTime) / 1000.0;
                timeValue.setText(String.format("%.1f s", seconds));

                try {
                    int exitCode = get();

                    if (exitCode == 0) {
                        statusValue.setText("Success");
                        statusValue.setForeground(GREEN);
                        peersValue.setText("4");
                        completedValue.setText("4");
                        progressBar.setValue(100);
                        progressBar.setString("100%");
                        progressText.setText("All demo downloads finished");
                        footerStatus.setText("\u25CF  Demo successful");
                        footerStatus.setForeground(GREEN);
                        console.append("\nDemo process finished successfully.\n");
                    } else {
                        statusValue.setText("Failed");
                        statusValue.setForeground(Color.RED);
                        progressText.setText("Review the console output");
                        footerStatus.setText("\u25CF  Exit code: " + exitCode);
                        footerStatus.setForeground(Color.RED);
                        console.append("\nDemo failed with exit code "
                                + exitCode + ".\n");
                    }
                } catch (Exception ex) {
                    statusValue.setText("Error");
                    statusValue.setForeground(Color.RED);
                    footerStatus.setText("\u25CF  Error running demo");
                    footerStatus.setForeground(Color.RED);
                    console.append("\nError: " + ex.getMessage() + "\n");
                }

                runButton.setEnabled(true);
                runButton.setText("\u25B6   Run Local Demo");
            }
        };

        worker.execute();
    }

    private void resetDashboard() {
        statusValue.setText("Ready");
        statusValue.setForeground(BLUE);
        peersValue.setText("--");
        completedValue.setText("0");
        timeValue.setText("--");
        progressBar.setValue(0);
        progressBar.setString("0%");
        progressText.setText("Ready when you are");
        footerStatus.setText("\u25CF  System ready");
        footerStatus.setForeground(GREEN);

        console.setText(
                "Dashboard reset.\n"
                        + "Click Run Local Demo to start another run.\n"
        );
    }

    private void openOutputFolder() {
        File output = new File(projectRoot, "demo-work");

        if (!output.isDirectory()) {
            JOptionPane.showMessageDialog(
                    this,
                    "No demo output folder was found yet.\n"
                            + "Run the local demo first.",
                    "Output folder unavailable",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(output);
            } else {
                throw new UnsupportedOperationException(
                        "Desktop folder opening is not supported."
                );
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not open the folder:\n" + ex.getMessage(),
                    "Unable to open folder",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color fill;

        RoundedPanel(int radius, Color fill) {
            this.radius = radius;
            this.fill = fill;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g2.setColor(new Color(35, 72, 115, 12));
            g2.fill(new RoundRectangle2D.Float(
                    1, 3, getWidth() - 2, getHeight() - 4,
                    radius, radius
            ));

            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Float(
                    0, 0, getWidth() - 2, getHeight() - 4,
                    radius, radius
            ));

            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName()
                );
            } catch (Exception ignored) {
                // Keep the default Swing look and feel.
            }

            new CampusTorrentGui().setVisible(true);
        });
    }
}
