package net.thewellodev.server.gui;

import net.thewellodev.server.TWServer;
import net.thewellodev.server.entity.TWPlayer;
import net.thewellodev.server.log.LogSetup;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

/** The server control window. */
public class ServerGui {

    // Colours
    static final Color BG = new Color(0x1E1F22);
    static final Color PANEL = new Color(0x2B2D31);
    static final Color PANEL2 = new Color(0x313338);
    static final Color TEXT = new Color(0xDBDEE1);
    static final Color MUTED = new Color(0x949BA4);
    static final Color GREEN = new Color(0x3BA55D);
    static final Color RED = new Color(0xED4245);
    static final Color YELLOW = new Color(0xFAA61A);
    static final Color BLUE = new Color(0x5865F2);

    private final TWServer server;
    private final JFrame frame;
    private final JTextPane console = new JTextPane();
    private final StyledDocument doc = console.getStyledDocument();
    private final JTextField input = new JTextField();
    private final JLabel status = new JLabel("● Starting...");
    private final JLabel ramLabel = new JLabel();
    private final JProgressBar ramBar = new JProgressBar(0, 100);
    private final JLabel tpsValue = value();
    private final JLabel msptValue = value();
    private final JLabel playersValue = value();
    private final JLabel chunksValue = value();
    private final JLabel entitiesValue = value();
    private final JLabel uptimeValue = value();
    private final JLabel threadsValue = value();
    private final Graph graph = new Graph();
    private final DefaultListModel<String> playerModel = new DefaultListModel<String>();
    private final JList<String> playerList = new JList<String>(playerModel);
    private final JLabel playerTitle = new JLabel("Players (0)");
    private final JTextField wanField = new JTextField();
    private final JTextField lanField = new JTextField();
    private final JTextField vpnField = new JTextField();
    private final java.util.Map<JTextField, String> realAddress = new java.util.HashMap<JTextField, String>();
    private boolean wanRevealed;
    private JPanel vpnRow;
    private final JLabel netStatus = new JLabel(" ");
    private String lastNetStatus = "";
    private final ConcurrentLinkedQueue<Object[]> pendingLogs = new ConcurrentLinkedQueue<Object[]>();
    private final List<String> history = new ArrayList<String>();
    private int historyIndex = -1;
    private volatile boolean started;
    private volatile boolean failed;
    private JButton stopButton;
    private JLabel subLabel;

    public static ServerGui open(final TWServer server) throws Exception {
        final ServerGui[] out = new ServerGui[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                out[0] = new ServerGui(server);
            }
        });
        return out[0];
    }

    private ServerGui(TWServer server) {
        this.server = server;
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        frame = new JFrame("TheWello's Infdev Server - " + server.getGameVersion());
        frame.setIconImage(makeIcon());
        // The window opens before the server has read server.properties: refresh the build name until it is known
        final javax.swing.Timer versionTimer = new javax.swing.Timer(500, null);
        versionTimer.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                frame.setTitle("TheWello's Infdev Server - " + ServerGui.this.server.getGameVersion());
                if (subLabel != null) {
                    subLabel.setText("Minecraft " + ServerGui.this.server.getGameVersion() + "  •  version "
                            + TWServer.VERSION + "  •  Infdev server with plugin support (plugin API)");
                }
                if (started || failed) {
                    versionTimer.stop();
                }
            }
        });
        versionTimer.start();
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                requestClose();
            }
        });
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildLeft(), BorderLayout.WEST);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildRight(), BorderLayout.EAST);
        frame.setContentPane(root);
        frame.setSize(1280, 720);
        frame.setMinimumSize(new Dimension(900, 520));
        frame.setLocationRelativeTo(null);
        // test aid: -Dthewellodev.guipos=x,y puts the window there (negative values: a monitor left of the main one) and
        // does not take the keyboard focus
        String testPos = System.getProperty("thewellodev.guipos");
        if (testPos != null) {
            String[] xy = testPos.split(",");
            frame.setLocation(Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim()));
            frame.setAutoRequestFocus(false);
        }
        frame.setVisible(true);

        LogSetup.addListener(new LogSetup.LogListener() {
            public void onLog(Level level, String line) {
                pendingLogs.add(new Object[]{level, line});
            }
        });
        server.addStopListener(new Runnable() {
            public void run() {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        frame.dispose();
                        System.exit(0);
                    }
                });
            }
        });
        new Timer(100, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                drainLogs();
            }
        }).start();
        new Timer(1000, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                refreshStats();
            }
        }).start();
        refreshStats();
        input.requestFocusInWindow();
    }

    // ---------------------------------------------------------------- layout

    private JComponent buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PANEL);
        p.setBorder(new EmptyBorder(10, 14, 10, 14));
        JLabel title = new JLabel("TheWello's Infdev Server");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        JLabel sub = new JLabel("Minecraft " + server.getGameVersion() + "  •  version " + TWServer.VERSION
                + "  •  Infdev server with plugin support (plugin API)");
        subLabel = sub;
        sub.setForeground(MUTED);
        sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(title);
        left.add(sub);
        p.add(left, BorderLayout.WEST);
        status.setFont(new Font("SansSerif", Font.BOLD, 14));
        status.setForeground(YELLOW);
        p.add(status, BorderLayout.EAST);

        // Connection addresses
        JPanel addr = new JPanel(new GridLayout(4, 1, 0, 2));
        addr.setOpaque(false);
        addr.setBorder(new EmptyBorder(0, 30, 0, 20));
        addr.add(addressRow("Internet:", wanField, true));
        addr.add(addressRow("LAN / home network:", lanField, false));
        vpnRow = addressRow("VPN (Radmin, Hamachi, ...):", vpnField, false);
        vpnRow.setVisible(false);
        addr.add(vpnRow);
        netStatus.setForeground(MUTED);
        netStatus.setFont(new Font("SansSerif", Font.PLAIN, 11));
        addr.add(netStatus);
        p.add(addr, BorderLayout.CENTER);
        return p;
    }

    /** Shows the real address in the field; the internet address stays masked until "Show" is pressed. */
    private void setAddress(JTextField field, String real) {
        realAddress.put(field, real);
        String shown = real;
        if (field == wanField && !wanRevealed && real.indexOf(':') > 0) {
            shown = "***.***.***.***" + real.substring(real.lastIndexOf(':'));
        }
        if (!shown.equals(field.getText())) {
            field.setText(shown);
        }
    }

    private JPanel addressRow(String label, final JTextField field, boolean sensitive) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setForeground(MUTED);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        l.setPreferredSize(new Dimension(140, 20));
        field.setEditable(false);
        field.setBackground(PANEL2);
        field.setForeground(Color.WHITE);
        field.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        field.setBorder(new EmptyBorder(2, 6, 2, 6));
        field.setText("loading...");
        JButton copy = button("Copy", BLUE);
        copy.setBorder(new EmptyBorder(3, 10, 3, 10));
        copy.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String t = realAddress.get(field);
                if (t != null && t.indexOf(':') > 0) {
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new java.awt.datatransfer.StringSelection(t), null);
                    // Keep the internet IP off the screen (streaming/screenshots)
                    String shown = field == wanField && !wanRevealed ? "internet address" : t;
                    netStatus.setText("Address copied to clipboard: " + shown);
                }
            }
        });
        row.add(l, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        if (sensitive) {
            final JButton show = button("Show", PANEL2);
            show.setBorder(new EmptyBorder(3, 10, 3, 10));
            show.setToolTipText("Your public IP stays hidden in the window and in screenshots. Copy also works while hidden.");
            show.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    wanRevealed = !wanRevealed;
                    show.setText(wanRevealed ? "Hide" : "Show");
                    String real = realAddress.get(wanField);
                    if (real != null) {
                        setAddress(wanField, real);
                    }
                }
            });
            JPanel btns = new JPanel(new BorderLayout(4, 0));
            btns.setOpaque(false);
            btns.add(show, BorderLayout.WEST);
            btns.add(copy, BorderLayout.EAST);
            row.add(btns, BorderLayout.EAST);
        } else {
            row.add(copy, BorderLayout.EAST);
        }
        return row;
    }

    private JComponent buildLeft() {
        JPanel p = card();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setPreferredSize(new Dimension(290, 100));

        p.add(sectionTitle("Memory (RAM)"));
        ramLabel.setForeground(TEXT);
        ramLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        ramLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(ramLabel);
        p.add(Box.createVerticalStrut(4));
        ramBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        ramBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        ramBar.setForeground(GREEN);
        ramBar.setBackground(PANEL2);
        ramBar.setBorderPainted(false);
        p.add(ramBar);
        p.add(Box.createVerticalStrut(8));
        graph.setAlignmentX(Component.LEFT_ALIGNMENT);
        graph.setPreferredSize(new Dimension(260, 110));
        graph.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        p.add(graph);
        p.add(Box.createVerticalStrut(10));

        p.add(sectionTitle("Status"));
        JPanel grid = new JPanel(new GridLayout(0, 2, 6, 6));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        addStat(grid, "TPS", tpsValue);
        addStat(grid, "Tick time", msptValue);
        addStat(grid, "Players", playersValue);
        addStat(grid, "Chunks sent", chunksValue);
        addStat(grid, "Entity", entitiesValue);
        addStat(grid, "Thread", threadsValue);
        addStat(grid, "Uptime", uptimeValue);
        grid.add(new JLabel());
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        p.add(grid);
        p.add(Box.createVerticalGlue());

        p.add(sectionTitle("Controls"));
        stopButton = button("■  Stop server", RED);
        stopButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                requestClose();
            }
        });
        JButton save = button("Save world", BLUE);
        save.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                server.submitConsoleInput("/save-all");
            }
        });
        JButton gc = button("Free RAM (GC)", PANEL2);
        gc.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                long before = used();
                System.gc();
                long after = used();
                LogSetup.getLogger().info("GC: " + before + " MB -> " + after + " MB");
                refreshStats();
            }
        });
        JButton reload = button("Reload Plugins", PANEL2);
        reload.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                server.submitConsoleInput("/reload");
            }
        });
        for (JButton b : new JButton[]{stopButton, save, gc, reload}) {
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            p.add(b);
            p.add(Box.createVerticalStrut(6));
        }
        return p;
    }

    private JComponent buildCenter() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        console.setEditable(false);
        console.setBackground(new Color(0x111214));
        console.setForeground(TEXT);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        console.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane sp = new JScrollPane(console);
        sp.setBorder(BorderFactory.createLineBorder(PANEL, 1));
        sp.getVerticalScrollBar().setUnitIncrement(16);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(sectionTitle("Console"), BorderLayout.WEST);
        JLabel clear = new JLabel("clear");
        clear.setForeground(MUTED);
        clear.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clear.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                console.setText("");
            }
        });
        top.add(clear, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);
        p.add(sp, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(6, 4));
        bottom.setOpaque(false);
        input.setBackground(PANEL2);
        input.setForeground(Color.WHITE);
        input.setCaretColor(Color.WHITE);
        input.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        input.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(PANEL, 1),
                new EmptyBorder(7, 8, 7, 8)));
        input.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                submit();
            }
        });
        input.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_DOWN) {
                    navigateHistory(e.getKeyCode() == KeyEvent.VK_UP ? -1 : 1);
                    e.consume();
                }
            }
        });
        JButton send = button("Send", GREEN);
        send.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                submit();
            }
        });
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);
        JLabel hint = new JLabel("Lines starting with \"/\" run a command (e.g. /help, /list, /kick name). "
                + "Other lines are sent to players as [Server] messages. ↑↓ history.");
        hint.setForeground(MUTED);
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        bottom.add(hint, BorderLayout.SOUTH);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private JComponent buildRight() {
        JPanel p = card();
        p.setLayout(new BorderLayout(0, 8));
        p.setPreferredSize(new Dimension(220, 100));
        playerTitle.setForeground(Color.WHITE);
        playerTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        p.add(playerTitle, BorderLayout.NORTH);
        playerList.setBackground(PANEL2);
        playerList.setForeground(TEXT);
        playerList.setSelectionBackground(BLUE);
        playerList.setSelectionForeground(Color.WHITE);
        playerList.setFont(new Font("SansSerif", Font.PLAIN, 13));
        playerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sp = new JScrollPane(playerList);
        sp.setBorder(BorderFactory.createEmptyBorder());
        p.add(sp, BorderLayout.CENTER);
        JPanel btns = new JPanel(new GridLayout(0, 1, 4, 4));
        btns.setOpaque(false);
        btns.add(playerAction("Message (tell)", "tell", true));
        btns.add(playerAction("Teleport to spawn", "tp", false));
        btns.add(playerAction("Inventory", "inv", false));
        btns.add(playerAction("Op yap / al", "op", false));
        btns.add(playerAction("At (kick)", "kick", false));
        btns.add(playerAction("Ban", "ban", false));
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    private JButton playerAction(String label, final String cmd, final boolean askText) {
        JButton b = button(label, PANEL2);
        b.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String sel = playerList.getSelectedValue();
                if (sel == null) {
                    JOptionPane.showMessageDialog(frame, "Select a player from the list first.");
                    return;
                }
                String name = sel.split(" ")[0];
                if (cmd.equals("inv")) {
                    InventoryView.open(frame, server, name);
                } else if (cmd.equals("tell")) {
                    String msg = JOptionPane.showInputDialog(frame, "Message for " + name + ":");
                    if (msg != null && msg.trim().length() > 0) {
                        server.submitConsoleInput("/tell " + name + " " + msg.trim());
                    }
                } else if (cmd.equals("tp")) {
                    server.submitConsoleInput("/spawn " + name);
                } else if (cmd.equals("op")) {
                    boolean op = server.isOp(name);
                    server.submitConsoleInput((op ? "/deop " : "/op ") + name);
                } else if (cmd.equals("kick") || cmd.equals("ban")) {
                    String reason = JOptionPane.showInputDialog(frame, "Reason (can be left empty):");
                    if (reason != null) {
                        server.submitConsoleInput("/" + cmd + " " + name + (reason.trim().length() > 0 ? " " + reason.trim() : ""));
                    }
                }
            }
        });
        return b;
    }

    // ---------------------------------------------------------------- helper components

    private static JPanel card() {
        JPanel p = new JPanel();
        p.setBackground(PANEL);
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        return p;
    }

    private static JLabel sectionTitle(String s) {
        JLabel l = new JLabel(s.toUpperCase(java.util.Locale.ENGLISH));
        l.setForeground(MUTED);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setBorder(new EmptyBorder(0, 0, 6, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel value() {
        JLabel l = new JLabel("-");
        l.setForeground(Color.WHITE);
        l.setFont(new Font("SansSerif", Font.BOLD, 13));
        return l;
    }

    private static void addStat(JPanel grid, String name, JLabel v) {
        JPanel cell = new JPanel(new BorderLayout());
        cell.setBackground(PANEL2);
        cell.setBorder(new EmptyBorder(5, 7, 5, 7));
        JLabel n = new JLabel(name);
        n.setForeground(MUTED);
        n.setFont(new Font("SansSerif", Font.PLAIN, 11));
        cell.add(n, BorderLayout.NORTH);
        cell.add(v, BorderLayout.CENTER);
        grid.add(cell);
    }

    private static JButton button(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setBorder(new EmptyBorder(8, 12, 8, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setHorizontalAlignment(SwingConstants.CENTER);
        return b;
    }

    private static BufferedImage makeIcon() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x6B8E23));
        g.fillRect(0, 0, 32, 10);
        g.setColor(new Color(0x8B5A2B));
        g.fillRect(0, 10, 32, 22);
        g.setColor(new Color(0x5C3A1A));
        for (int i = 0; i < 12; i++) {
            g.fillRect((i * 7) % 30, 12 + (i * 5) % 18, 3, 3);
        }
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString("TW", 5, 26);
        g.dispose();
        return img;
    }

    private static long used() {
        Runtime r = Runtime.getRuntime();
        return (r.totalMemory() - r.freeMemory()) / 1048576L;
    }

    // ---------------------------------------------------------------- behaviour

    private void submit() {
        String text = input.getText().trim();
        if (text.length() == 0) {
            return;
        }
        if (history.isEmpty() || !history.get(history.size() - 1).equals(text)) {
            history.add(text);
        }
        historyIndex = -1;
        input.setText("");
        if (!started) {
            appendLine(Level.WARNING, "The server is not ready yet.\n");
            return;
        }
        server.submitConsoleInput(text);
    }

    private void navigateHistory(int dir) {
        if (history.isEmpty()) {
            return;
        }
        if (historyIndex < 0) {
            historyIndex = history.size();
        }
        historyIndex = Math.max(0, Math.min(history.size(), historyIndex + dir));
        input.setText(historyIndex >= history.size() ? "" : history.get(historyIndex));
    }

    private void requestClose() {
        if (failed || !server.isRunning()) {
            if (!started || failed) {
                frame.dispose();
                System.exit(0);
            }
            return;
        }
        int r = JOptionPane.showConfirmDialog(frame,
                "Stop the server?\nThe world will be saved and all players will be disconnected.",
                "Stop the server", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.YES_OPTION) {
            stopButton.setEnabled(false);
            status.setText("● Stopping...");
            status.setForeground(YELLOW);
            server.shutdown();
        }
    }

    public void onServerStarted() {
        started = true;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                status.setText("● Running  •  port " + server.getPort());
                status.setForeground(GREEN);
            }
        });
    }

    public void onServerFailed(final Throwable t) {
        failed = true;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                status.setText("● Could not start");
                status.setForeground(RED);
                stopButton.setText("Close");
                JOptionPane.showMessageDialog(frame, "The server could not start:\n" + t.getMessage(),
                        "TheWello's Infdev Server", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void refreshStats() {
        Runtime r = Runtime.getRuntime();
        long used = used();
        long max = r.maxMemory() / 1048576L;
        long alloc = r.totalMemory() / 1048576L;
        int pct = (int) (used * 100 / Math.max(1, max));
        ramLabel.setText(used + " MB / " + max + " MB  (%" + pct + ")");
        ramBar.setValue(pct);
        ramBar.setForeground(pct > 85 ? RED : pct > 65 ? YELLOW : GREEN);
        ramBar.setToolTipText("Allocated: " + alloc + " MB");
        double tps = server.getTps();
        tpsValue.setText(String.format("%.1f", tps));
        tpsValue.setForeground(tps >= 18 ? GREEN : tps >= 12 ? YELLOW : RED);
        msptValue.setText(String.format("%.1f ms", server.getMspt()));
        List<TWPlayer> ps = server.getPlayerList();
        playersValue.setText(ps.size() + " / " + server.getMaxPlayers());
        chunksValue.setText(String.valueOf(server.getLoadedChunkEstimate()));
        entitiesValue.setText(String.valueOf(server.getEntityCount()));
        threadsValue.setText(String.valueOf(Thread.activeCount()));
        uptimeValue.setText(server.getUptimeString());
        graph.add(pct, started ? tps : 0);

        String sel = playerList.getSelectedValue();
        String selName = sel == null ? null : sel.split(" ")[0];
        List<String> rows = new ArrayList<String>();
        for (TWPlayer p : ps) {
            rows.add(p.getName() + (server.isOp(p.getName()) ? " [OP]" : "") + "  " + (p.getPing() >= 0 ? p.getPing() + "ms" : ""));
        }
        boolean same = rows.size() == playerModel.size();
        for (int i = 0; same && i < rows.size(); i++) {
            same = rows.get(i).equals(playerModel.get(i));
        }
        if (!same) {
            playerModel.clear();
            for (String row : rows) {
                playerModel.addElement(row);
                if (selName != null && row.startsWith(selName + " ")) {
                    playerList.setSelectedIndex(playerModel.size() - 1);
                }
            }
        }
        playerTitle.setText("Players (" + ps.size() + ")");

        net.thewellodev.server.net.NetworkHelper nh = server.getNetwork();
        if (nh != null) {
            String lan = nh.lanIp + ":" + server.getPort();
            String wan = nh.publicIp != null ? nh.publicIp + ":" + server.getPort() : "not found (no internet?)";
            setAddress(lanField, lan);
            setAddress(wanField, wan);
            List<String> vpns = nh.vpnAddresses;
            if (!vpns.isEmpty()) {
                String v = vpns.get(0).substring(vpns.get(0).indexOf(": ") + 2) + ":" + server.getPort();
                setAddress(vpnField, v);
                if (!vpnRow.isVisible()) {
                    vpnField.setToolTipText(vpns.toString());
                    vpnRow.setVisible(true);
                }
            }
            String st = nh.status + (server.isOnlineMode() ? "" : "   |   online-mode OFF");
            if (!st.equals(lastNetStatus)) {
                lastNetStatus = st;
                netStatus.setText(st);
                netStatus.setToolTipText(st);
                netStatus.setForeground(nh.portOpened ? GREEN : YELLOW);
            }
        }
    }

    private void drainLogs() {
        Object[] e;
        int n = 0;
        while (n++ < 500 && (e = pendingLogs.poll()) != null) {
            appendLine((Level) e[0], (String) e[1]);
        }
    }

    private void appendLine(Level level, String line) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        Color c = TEXT;
        if (level == Level.SEVERE) {
            c = RED;
        } else if (level == Level.WARNING) {
            c = YELLOW;
        } else if (line.contains("[CHAT]")) {
            c = new Color(0x8EC8FF);
        } else if (line.contains("]: > /")) {
            c = new Color(0xB5E48C);
        }
        StyleConstants.setForeground(a, c);
        try {
            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, console);
            boolean atBottom = sp == null || sp.getVerticalScrollBar().getValue()
                    + sp.getVerticalScrollBar().getVisibleAmount() >= sp.getVerticalScrollBar().getMaximum() - 30;
            doc.insertString(doc.getLength(), line, a);
            Element root = doc.getDefaultRootElement();
            if (root.getElementCount() > 3000) {
                Element cut = root.getElement(root.getElementCount() - 2500);
                doc.remove(0, cut.getStartOffset());
            }
            if (atBottom) {
                console.setCaretPosition(doc.getLength());
            }
        } catch (BadLocationException ignored) {
        }
    }

    /** RAM and TPS history graph. */
    private static final class Graph extends JComponent {
        private final int[] ram = new int[120];
        private final double[] tps = new double[120];
        private int count;

        void add(int ramPct, double t) {
            System.arraycopy(ram, 1, ram, 0, ram.length - 1);
            System.arraycopy(tps, 1, tps, 0, tps.length - 1);
            ram[ram.length - 1] = ramPct;
            tps[tps.length - 1] = t;
            count = Math.min(ram.length, count + 1);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setColor(PANEL2);
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(0x3F4147));
            for (int i = 1; i < 4; i++) {
                g.drawLine(0, h * i / 4, w, h * i / 4);
            }
            if (count > 1) {
                int n = ram.length;
                // RAM area
                int[] xs = new int[count + 2];
                int[] ys = new int[count + 2];
                for (int i = 0; i < count; i++) {
                    int idx = n - count + i;
                    xs[i] = (int) ((double) (idx) / (n - 1) * (w - 1));
                    ys[i] = h - 2 - ram[idx] * (h - 4) / 100;
                }
                xs[count] = xs[count - 1];
                ys[count] = h;
                xs[count + 1] = xs[0];
                ys[count + 1] = h;
                g.setColor(new Color(59, 165, 93, 70));
                g.fillPolygon(xs, ys, count + 2);
                g.setColor(GREEN);
                g.setStroke(new BasicStroke(1.6f));
                for (int i = 1; i < count; i++) {
                    g.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i]);
                }
                // TPS line
                g.setColor(YELLOW);
                for (int i = 1; i < count; i++) {
                    int a = n - count + i - 1, b = a + 1;
                    int y1 = h - 2 - (int) (tps[a] / 20.0 * (h - 4));
                    int y2 = h - 2 - (int) (tps[b] / 20.0 * (h - 4));
                    g.drawLine(xs[i - 1], y1, xs[i], y2);
                }
            }
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.setColor(GREEN);
            g.drawString("RAM %", 6, 12);
            g.setColor(YELLOW);
            g.drawString("TPS", 46, 12);
            g.setColor(MUTED);
            g.drawString("last 2 min", w - 46, 12);
        }
    }

    @SuppressWarnings("unused")
    private static JPanel flow(Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        for (Component c : cs) {
            p.add(c);
        }
        return p;
    }
}
