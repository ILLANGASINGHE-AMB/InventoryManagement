import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Ice Inventory Management System (Swing) - SINGLE FILE
 * - Left nav: Inventory, sales (customer details), reports (daily), settings (theme)
 * - Inventory table columns: Item ID, Type, Quantity, Unit Price (LKR), Last Updated
 * - Edit Unit Price directly in table (LKR formatting)
 * - Types: "Manufactured", "Resell", and "WASTE" (WASTE is not sellable; tracked & highlighted)
 * - Sales form (right) generates bill + records sale + updates stock
 * - Sales History table (customers who bought)
 * - Daily Reports (date field)
 * - Settings: Light/Dark theme
 * - In-memory demo (no DB)
 */
public class IceInventoryApp {

    private JFrame frame;
    private JTable inventoryTable;
    private DefaultTableModel inventoryModel;
    private JTextArea billPreview;
    private JLabel statusLabel;
    private int billCounter = 1000;

    // CardLayout panels
    private JPanel cardsPanel;
    private JPanel inventoryViewPanel;
    private JPanel salesHistoryPanel;
    private JPanel reportsPanel;
    private JPanel settingsPanel;

    // Sales history
    static class SaleRecord {
        int billNo;
        String customerName;
        String phone;
        String type; // Manufactured / Resell
        int qty;
        double unitPrice;
        double total;
        String dateTime; // formatted string

        SaleRecord(int billNo, String customerName, String phone, String type, int qty, double unitPrice, String dateTime) {
            this.billNo = billNo;
            this.customerName = customerName;
            this.phone = phone;
            this.type = type;
            this.qty = qty;
            this.unitPrice = unitPrice;
            this.total = qty * unitPrice;
            this.dateTime = dateTime;
        }
    }
    private final List<SaleRecord> salesList = new ArrayList<>();
    private DefaultTableModel salesModel;

    // Inventory item
    static class InventoryItem {
        String itemId;
        String type; // "Manufactured" or "Resell" or "WASTE"
        int quantity;
        double unitPrice;
        Date lastUpdated;

        InventoryItem(String itemId, String type, int quantity, double unitPrice) {
            this.itemId = itemId;
            this.type = type;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.lastUpdated = new Date();
        }
    }

    private final Map<String, InventoryItem> inventoryMap = new LinkedHashMap<>();
    private boolean darkTheme = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            IceInventoryApp app = new IceInventoryApp();
            app.initialize();
        });
    }

    private void initialize() {
        frame = new JFrame("Sagacious PVT Holdings - Inventory Management");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 760);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        // LEFT: Vertical navigation
        frame.add(buildLeftNav(), BorderLayout.WEST);

        // TOP: Header
        frame.add(buildHeader(), BorderLayout.NORTH);

        // CENTER: CardLayout panels
        cardsPanel = new JPanel(new CardLayout());
        inventoryViewPanel = buildInventoryPanel();
        salesHistoryPanel = buildSalesHistoryPanel();
        reportsPanel = buildReportsPanel();
        settingsPanel = buildSettingsPanel();

        cardsPanel.add(inventoryViewPanel, "inventory");
        cardsPanel.add(salesHistoryPanel, "salesHistory");
        cardsPanel.add(reportsPanel, "reports");
        cardsPanel.add(settingsPanel, "settings");

        // Right side: sales form + bill
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setLeftComponent(cardsPanel);
        mainSplit.setRightComponent(buildSalesPanel());
        mainSplit.setDividerLocation(760);
        frame.add(mainSplit, BorderLayout.CENTER);

        // STATUS bar bottom
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(new EmptyBorder(6, 10, 6, 10));
        frame.add(statusLabel, BorderLayout.SOUTH);

        // Seed example data
        seedExampleData();

        // Show inventory by default
        showCard("inventory");

        frame.setVisible(true);
    }

    private JPanel buildLeftNav() {
        JPanel nav = new JPanel();
        nav.setPreferredSize(new Dimension(180, 0));
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(new EmptyBorder(12, 8, 12, 8));
        applyNavColors(nav);

        JLabel title = new JLabel("<html><b style='color:black'>Sagacious</b><br/><small style='color:#0B6FA8'>IMS</small></html>");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.add(title);
        nav.add(Box.createRigidArea(new Dimension(0, 12)));

        String[][] items = {
                {"Inventory", "inventory"},
                {"sales (customer details)", "salesHistory"},
                {"reports (daily)", "reports"},
                {"settings (theme)", "settings"}
        };

        for (String[] it : items) {
            String label = it[0];
            String card = it[1];
            JButton btn = new JButton(label);
            btn.setAlignmentX(Component.LEFT_ALIGNMENT);
            btn.setMaximumSize(new Dimension(160, 40));
            btn.setFocusable(false);
            btn.addActionListener(e -> showCard(card));
            nav.add(btn);
            nav.add(Box.createRigidArea(new Dimension(0, 8)));
        }

        return nav;
    }

    private void applyNavColors(JPanel nav) {
        if (darkTheme) nav.setBackground(new Color(35, 35, 35));
        else nav.setBackground(new Color(230, 245, 255));
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(8, 12, 8, 12));
        applyHeaderColors(header);

        JLabel appTitle = new JLabel("Sagacious Ice Factory");
        appTitle.setFont(appTitle.getFont().deriveFont(Font.BOLD, 16f));
        header.add(appTitle, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        JTextField search = new JTextField(20);
        search.setMaximumSize(new Dimension(200, 30));
        search.setToolTipText("Search inventory...");
        right.add(search);

        JLabel dt = new JLabel();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dt.setText(df.format(new Date()));
        new javax.swing.Timer(1000, e -> dt.setText(df.format(new Date()))).start();
        right.add(dt);

        JLabel avatar = new JLabel("Admin");
        right.add(avatar);

        header.add(right, BorderLayout.EAST);
        return header;
    }

    private void applyHeaderColors(JPanel header) {
        if (darkTheme) header.setBackground(new Color(45, 45, 45));
        else header.setBackground(new Color(230, 245, 255));
    }

    private JPanel buildInventoryPanel() {
        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(new EmptyBorder(12, 12, 12, 12));
        applyMainPanelColors(center);

        // Top overview cards
        JPanel cards = new JPanel(new GridLayout(1, 4, 12, 0));
        cards.setPreferredSize(new Dimension(0, 100));
        cards.add(makeStatCard("Total Cubes (Manufactured)", () -> getTotalByType("Manufactured")));
        cards.add(makeStatCard("Total Cubes (Resell)", () -> getTotalByType("Resell")));
        cards.add(makeStatCard("Useless Cubes (Waste)", this::getTotalWaste));
        cards.add(makeStatCard("Available Stock", this::getTotalStock));
        center.add(cards, BorderLayout.NORTH);

        // Inventory table
        String[] cols = {"Item ID", "Type", "Quantity", "Unit Price (LKR)", "Last Updated"};
        inventoryModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3; // only Unit Price editable
            }

            @Override
            public void setValueAt(Object aValue, int row, int column) {
                super.setValueAt(aValue, row, column);
                if (column == 3) {
                    String id = (String) getValueAt(row, 0);
                    InventoryItem it = inventoryMap.get(id);
                    if (it != null) {
                        try {
                            String raw = String.valueOf(aValue).replaceAll("LKR", "").replaceAll(",", "").trim();
                            double val = Double.parseDouble(raw);
                            it.unitPrice = val;
                            it.lastUpdated = new Date();
                            refreshInventoryTable();
                            statusLabel.setText("Unit price updated for " + id);
                        } catch (NumberFormatException ex) {
                            JOptionPane.showMessageDialog(frame, "Invalid unit price. Enter number like 123.45");
                            refreshInventoryTable();
                        }
                    }
                }
            }
        };
        inventoryTable = new JTable(inventoryModel);
        inventoryTable.setRowHeight(28);

        // Unit price renderer (show LKR)
        inventoryTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public void setValue(Object value) {
                try {
                    double val = 0.0;
                    if (value instanceof Number) val = ((Number) value).doubleValue();
                    else {
                        String s = String.valueOf(value).replaceAll("LKR", "").replaceAll(",", "").trim();
                        val = Double.parseDouble(s);
                    }
                    setText(String.format("LKR %.2f", val));
                } catch (Exception e) {
                    setText(String.valueOf(value));
                }
            }
        });

        // Renderer: WASTE rows red, low-stock orange, else theme color
        inventoryTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String type = String.valueOf(table.getModel().getValueAt(row, 1));
                int qty;
                try { qty = Integer.parseInt(String.valueOf(table.getModel().getValueAt(row, 2))); }
                catch (Exception e) { qty = 0; }

                if ("WASTE".equalsIgnoreCase(type)) {
                    c.setForeground(Color.RED.darker());
                } else if (qty <= 5) {
                    c.setForeground(new Color(180, 85, 0)); // orange
                } else {
                    c.setForeground(darkTheme ? Color.WHITE : Color.BLACK);
                }
                return c;
            }
        });

        JScrollPane sp = new JScrollPane(inventoryTable);
        center.add(sp, BorderLayout.CENTER);

        // Action buttons
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        applyMainPanelColors(actions);
        JButton addBtn = new JButton("Add Cubes");
        addBtn.addActionListener(e -> openAddItemDialog());
        JButton removeBtn = new JButton("Remove Cubes");
        removeBtn.addActionListener(e -> openRemoveDialog());
        JButton exportBtn = new JButton("Export Inventory CSV");
        exportBtn.addActionListener(e -> exportInventoryCsv());
        actions.add(addBtn);
        actions.add(removeBtn);
        actions.add(exportBtn);
        center.add(actions, BorderLayout.SOUTH);

        return center;
    }

    private JPanel makeStatCard(String title, Supplier<Integer> valueSupplier) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        card.setPreferredSize(new Dimension(0, 80));
        applyMainPanelColors(card);

        JLabel lblTitle = new JLabel(title, SwingConstants.CENTER);
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 14f));
        card.add(lblTitle, BorderLayout.NORTH);

        JLabel lblValue = new JLabel(String.valueOf(valueSupplier.get()), SwingConstants.CENTER);
        lblValue.setFont(lblValue.getFont().deriveFont(Font.PLAIN, 18f));
        card.add(lblValue, BorderLayout.CENTER);

        // Auto-refresh every 2s
        new javax.swing.Timer(2000, e -> lblValue.setText(String.valueOf(valueSupplier.get()))).start();
        return card;
    }

    private JPanel buildSalesHistoryPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        applyMainPanelColors(p);

        String[] cols = {"Bill No", "Customer", "Phone", "Type", "Qty", "Unit Price (LKR)", "Total (LKR)", "Date & Time"};
        salesModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable salesTable = new JTable(salesModel);
        salesTable.setRowHeight(26);
        JScrollPane sp = new JScrollPane(salesTable);
        p.add(sp, BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        applyMainPanelColors(top);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshSalesTable());
        top.add(refresh);
        p.add(top, BorderLayout.NORTH);

        return p;
    }

    private JPanel buildReportsPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        applyMainPanelColors(p);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        applyMainPanelColors(controls);
        controls.add(new JLabel("Select date (yyyy-MM-dd):"));
        JTextField dateField = new JTextField(new SimpleDateFormat("yyyy-MM-dd").format(new Date()), 10);
        controls.add(dateField);
        JButton run = new JButton("Show Report");
        controls.add(run);
        p.add(controls);

        JTextArea reportArea = new JTextArea(10, 40);
        reportArea.setEditable(false);
        reportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane rsp = new JScrollPane(reportArea);
        p.add(rsp);

        run.addActionListener(e -> {
            String dateStr = dateField.getText().trim();
            double total = 0.0;
            int transactions = 0;
            for (SaleRecord s : salesList) {
                if (s.dateTime.startsWith(dateStr)) {
                    total += s.total;
                    transactions++;
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Daily Sales Report for ").append(dateStr).append("\n");
            sb.append("--------------------------------\n");
            sb.append(String.format("Transactions: %d\n", transactions));
            sb.append(String.format("Total Sales (LKR): %.2f\n", total));
            reportArea.setText(sb.toString());
        });

        run.doClick();
        return p;
    }

    private JPanel buildSettingsPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        applyMainPanelColors(p);

        JPanel themePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        applyMainPanelColors(themePanel);
        themePanel.add(new JLabel("Theme:"));
        JToggleButton themeToggle = new JToggleButton("Dark");
        themeToggle.setSelected(darkTheme);
        themeToggle.addActionListener(e -> {
            darkTheme = themeToggle.isSelected();
            themeToggle.setText(darkTheme ? "Dark" : "Light");
            applyTheme();
        });
        themePanel.add(themeToggle);
        p.add(themePanel);

        p.add(Box.createRigidArea(new Dimension(0, 10)));
        p.add(new JLabel("Settings apply immediately."));

        return p;
    }

    private void applyTheme() {
        // Re-apply colors to major panels and refresh UI
        applyNavColors((JPanel) frame.getContentPane().getComponent(0)); // left nav
        applyHeaderColors((JPanel) frame.getContentPane().getComponent(1)); // header
        applyMainPanelColors(inventoryViewPanel);
        applyMainPanelColors(salesHistoryPanel);
        applyMainPanelColors(reportsPanel);
        applyMainPanelColors(settingsPanel);
        SwingUtilities.updateComponentTreeUI(frame);
    }

    private void applyMainPanelColors(Component comp) {
        if (comp == null) return;
        if (darkTheme) {
            comp.setBackground(new Color(55, 55, 55));
            comp.setForeground(Color.WHITE);
        } else {
            comp.setBackground(Color.WHITE);
            comp.setForeground(Color.BLACK);
        }
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                applyMainPanelColors(child);
            }
        }
    }

    private JPanel buildSalesPanel() {
        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(new EmptyBorder(12, 12, 12, 12));
        applyMainPanelColors(right);

        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(BorderFactory.createTitledBorder("Sell Ice Cubes"));
        applyMainPanelColors(formPanel);

        // Customer Name
        JTextField customerName = new JTextField();
        formPanel.add(makeLabeledField("Customer Name:", customerName));

        // Customer Phone
        JTextField customerPhone = new JTextField();
        formPanel.add(makeLabeledField("Customer Phone No:", customerPhone));

        // Type of Cube (Manufactured / Resell) -- WASTE is intentionally not sellable
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"Manufactured", "Resell"});
        formPanel.add(makeLabeledField("Type of Cube:", typeCombo));

        // Number of cubes (spinner)
        JSpinner qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10000, 1));
        formPanel.add(makeLabeledField("Number of Cubes:", qtySpinner));

        // Unit Price (LKR)
        JFormattedTextField unitPriceField = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        unitPriceField.setColumns(10);
        unitPriceField.setValue(0.0);
        formPanel.add(makeLabeledField("Unit Price (LKR):", unitPriceField));

        // Payment method (radio)
        JPanel paymentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JRadioButton cash = new JRadioButton("Cash", true);
        JRadioButton credit = new JRadioButton("Credit");
        ButtonGroup pg = new ButtonGroup();
        pg.add(cash); pg.add(credit);
        paymentPanel.add(cash); paymentPanel.add(credit);
        formPanel.add(makeLabeledField("Payment Method:", paymentPanel));

        // Date & Time (text field default to now)
        JTextField dateTimeField = new JTextField();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dateTimeField.setText(df.format(new Date()));
        formPanel.add(makeLabeledField("Date & Time:", dateTimeField));
        JButton nowBtn = new JButton("Now");
        nowBtn.addActionListener(e -> dateTimeField.setText(df.format(new Date())));
        formPanel.add(nowBtn);

        // Generate Bill
        JButton generateBtn = new JButton("Generate Bill");
        generateBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        generateBtn.addActionListener(e -> {
            String name = customerName.getText().trim();
            String phone = customerPhone.getText().trim();
            String type = (String) typeCombo.getSelectedItem();
            int qty = (Integer) qtySpinner.getValue();
            double unitPrice = ((Number) unitPriceField.getValue()).doubleValue();
            String paymentMethod = cash.isSelected() ? "Cash" : "Credit";
            String dtime = dateTimeField.getText().trim();

            if (name.isEmpty() || phone.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please enter customer name and phone.");
                return;
            }
            if (qty <= 0 || unitPrice <= 0) {
                JOptionPane.showMessageDialog(frame, "Quantity and unit price must be > 0.");
                return;
            }

            if (!attemptSaleAndUpdateInventory(type, qty)) {
                JOptionPane.showMessageDialog(frame, "Insufficient stock for type: " + type);
                return;
            }

            int billNo = ++billCounter;
            String billText = generateBillText(billNo, name, phone, type, qty, unitPrice, paymentMethod, dtime);
            billPreview.setText(billText);

            // record sale
            salesList.add(new SaleRecord(billNo, name, phone, type, qty, unitPrice, dtime));
            refreshSalesTable();

            statusLabel.setText("Sale recorded — Bill #" + billNo + " generated");
            JOptionPane.showMessageDialog(frame, "Sale recorded — Bill #" + billNo + " generated");
            refreshInventoryTable();
        });

        formPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        formPanel.add(generateBtn);

        // Bill preview + Print/Save
        JPanel previewAndButtons = new JPanel(new BorderLayout());
        previewAndButtons.setBorder(BorderFactory.createTitledBorder("Bill Preview"));
        applyMainPanelColors(previewAndButtons);

        billPreview = new JTextArea(12, 30);
        billPreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        billPreview.setEditable(false);
        JScrollPane billScroll = new JScrollPane(billPreview);
        previewAndButtons.add(billScroll, BorderLayout.CENTER);

        JPanel pb = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton printBtn = new JButton("Print");
        printBtn.addActionListener(e -> {
            try { billPreview.print(); } catch (Exception ex) { JOptionPane.showMessageDialog(frame, "Print failed: " + ex.getMessage()); }
        });
        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> saveBillToFile());
        pb.add(printBtn);
        pb.add(saveBtn);
        previewAndButtons.add(pb, BorderLayout.SOUTH);

        right.add(formPanel, BorderLayout.NORTH);
        right.add(previewAndButtons, BorderLayout.CENTER);

        return right;
    }

    private void saveBillToFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Text File", "txt"));
        int rc = chooser.showSaveDialog(frame);
        if (rc == JFileChooser.APPROVE_OPTION) {
            try {
                String path = chooser.getSelectedFile().getAbsolutePath();
                if (!path.endsWith(".txt")) path += ".txt";
                Files.write(Paths.get(path), billPreview.getText().getBytes());
                JOptionPane.showMessageDialog(frame, "Saved to " + path);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Save failed: " + ex.getMessage());
            }
        }
    }

    private JPanel makeLabeledField(String label, Component field) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(140, 24));
        panel.add(lbl, BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        panel.setBorder(new EmptyBorder(6, 0, 6, 0));
        applyMainPanelColors(panel);
        return panel;
    }

    // ---------- Inventory / Stats helpers ----------

    private int getTotalByType(String type) {
        return inventoryMap.values().stream().filter(i -> i.type.equals(type)).mapToInt(i -> i.quantity).sum();
    }

    private int getTotalWaste() {
        // Count rows where type = WASTE
        return inventoryMap.values().stream()
                .filter(i -> "WASTE".equalsIgnoreCase(i.type))
                .mapToInt(i -> i.quantity)
                .sum();
    }

    private int getTotalStock() {
        return inventoryMap.values().stream().mapToInt(i -> i.quantity).sum();
    }

    private void seedExampleData() {
        addInventoryItem(new InventoryItem("I-100", "Manufactured", 500, 0.50));
        addInventoryItem(new InventoryItem("I-101", "Manufactured", 50, 1.10));
        addInventoryItem(new InventoryItem("R-200", "Resell", 200, 0.80));
        addInventoryItem(new InventoryItem("R-201", "Resell", 30, 0.45));
        addInventoryItem(new InventoryItem("W-001", "WASTE", 12, 0.00)); // example waste row
        refreshInventoryTable();
        refreshSalesTable();
    }

    private void addInventoryItem(InventoryItem it) {
        inventoryMap.put(it.itemId, it);
    }

    private void refreshInventoryTable() {
        if (inventoryModel == null) return;
        inventoryModel.setRowCount(0);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (InventoryItem it : inventoryMap.values()) {
            inventoryModel.addRow(new Object[]{
                    it.itemId,
                    it.type,
                    it.quantity,
                    it.unitPrice, // renderer adds LKR
                    df.format(it.lastUpdated)
            });
        }
    }

    private void refreshSalesTable() {
        if (salesModel == null) return;
        salesModel.setRowCount(0);
        for (SaleRecord s : salesList) {
            salesModel.addRow(new Object[]{
                    s.billNo, s.customerName, s.phone, s.type, s.qty,
                    String.format("%.2f", s.unitPrice),
                    String.format("%.2f", s.total),
                    s.dateTime
            });
        }
    }

    private void openAddItemDialog() {
        JDialog dlg = new JDialog(frame, "Add Inventory Item", true);
        dlg.setSize(420, 320);
        dlg.setLocationRelativeTo(frame);
        dlg.setLayout(new BorderLayout());

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JTextField idField = new JTextField("AUTO-" + (inventoryMap.size() + 1));
        // >>> Include WASTE here <<<
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"Manufactured", "Resell", "WASTE"});
        JSpinner qty = new JSpinner(new SpinnerNumberModel(1, 0, 100000, 1));
        JFormattedTextField price = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        price.setValue(0.0);

        p.add(makeLabeledField("Item ID:", idField));
        p.add(makeLabeledField("Type:", typeCombo));
        p.add(makeLabeledField("Quantity:", qty));
        p.add(makeLabeledField("Unit Price (LKR):", price));

        JButton add = new JButton("Add Item");
        add.addActionListener(e -> {
            String id = idField.getText().trim();
            if (id.isEmpty()) { JOptionPane.showMessageDialog(dlg, "Item ID required."); return; }
            String type = (String) typeCombo.getSelectedItem();
            int q = (Integer) qty.getValue();
            double pval = ((Number) price.getValue()).doubleValue();
            InventoryItem it = new InventoryItem(id, type, q, pval);
            addInventoryItem(it);
            refreshInventoryTable();
            dlg.dispose();
            statusLabel.setText("Added inventory item: " + id);
        });

        dlg.add(p, BorderLayout.CENTER);
        dlg.add(add, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void openRemoveDialog() {
        String id = JOptionPane.showInputDialog(frame, "Enter Item ID to remove from (e.g., I-100):");
        if (id == null || id.trim().isEmpty()) return;
        InventoryItem it = inventoryMap.get(id.trim());
        if (it == null) {
            JOptionPane.showMessageDialog(frame, "Item not found: " + id);
            return;
        }
        String qStr = JOptionPane.showInputDialog(frame, "Current qty: " + it.quantity + ". Enter quantity to remove:");
        if (qStr == null) return;
        try {
            int q = Integer.parseInt(qStr.trim());
            if (q <= 0) throw new NumberFormatException();
            if (q > it.quantity) {
                JOptionPane.showMessageDialog(frame, "Cannot remove more than available.");
                return;
            }
            it.quantity -= q;
            it.lastUpdated = new Date();
            if (it.quantity == 0) {
                int rc = JOptionPane.showConfirmDialog(frame, "Quantity is now zero. Remove item fully from inventory?", "Remove Item", JOptionPane.YES_NO_OPTION);
                if (rc == JOptionPane.YES_OPTION) inventoryMap.remove(it.itemId);
            }
            refreshInventoryTable();
            statusLabel.setText("Removed " + q + " from " + id);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(frame, "Invalid number.");
        }
    }

    private boolean attemptSaleAndUpdateInventory(String type, int qtyNeeded) {
        // Gather candidates of the requested type (largest stock first)
        List<InventoryItem> candidates = new ArrayList<>();
        for (InventoryItem it : inventoryMap.values())
            if (it.type.equals(type)) candidates.add(it);
        candidates.sort((a, b) -> Integer.compare(b.quantity, a.quantity));

        int totalAvailable = candidates.stream().mapToInt(i -> i.quantity).sum();
        if (totalAvailable < qtyNeeded) return false;

        int remain = qtyNeeded;
        for (InventoryItem it : candidates) {
            if (remain <= 0) break;
            int take = Math.min(it.quantity, remain);
            it.quantity -= take;
            it.lastUpdated = new Date();
            remain -= take;
        }

        // Remove empty items
        List<String> toRemove = new ArrayList<>();
        for (InventoryItem it : inventoryMap.values()) if (it.quantity <= 0) toRemove.add(it.itemId);
        for (String id : toRemove) inventoryMap.remove(id);

        return true;
    }

    private String generateBillText(int billNo, String customerName, String phone, String type, int qty, double unitPrice, String paymentMethod, String dateTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("            Sagacious PVT Holdings - Bill\n");
        sb.append("            -------------------\n");
        sb.append(String.format("Bill No: %d\n", billNo));
        sb.append(String.format("Date & Time: %s\n", dateTime));
        sb.append("\n");
        sb.append("Customer:\n");
        sb.append(String.format("  %s\n", customerName));
        sb.append(String.format("  Phone: %s\n", phone));
        sb.append("\n");
        sb.append("Items:\n");
        double subtotal = qty * unitPrice;
        sb.append(String.format("  %-15s %5d x LKR %8.2f = LKR %8.2f\n", type, qty, unitPrice, subtotal));
        double tax = 0.0;
        double total = subtotal + tax;
        sb.append("\n");
        sb.append(String.format("Subtotal: LKR %.2f\n", subtotal));
        sb.append(String.format("Tax: LKR %.2f\n", tax));
        sb.append(String.format("Total: LKR %.2f\n", total));
        sb.append("\n");
        sb.append("Payment Method: " + paymentMethod + "\n");
        sb.append("\n");
        sb.append("Thank you for your purchase!\n");
        return sb.toString();
    }

    private void exportInventoryCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CSV", "csv"));
        int rc = chooser.showSaveDialog(frame);
        if (rc == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(chooser.getSelectedFile()))) {
                pw.println("ItemID,Type,Quantity,UnitPrice(LKR),LastUpdated");
                DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                for (InventoryItem it : inventoryMap.values()) {
                    pw.printf("%s,%s,%d,%.2f,%s%n",
                            it.itemId, it.type, it.quantity, it.unitPrice, df.format(it.lastUpdated));
                }
                JOptionPane.showMessageDialog(frame, "Exported inventory CSV.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Export failed: " + ex.getMessage());
            }
        }
    }

    private void showCard(String cardName) {
        CardLayout cl = (CardLayout) (cardsPanel.getLayout());
        cl.show(cardsPanel, cardName);
        if ("salesHistory".equals(cardName)) refreshSalesTable();
        if ("inventory".equals(cardName)) refreshInventoryTable();
    }

    // Lightweight Supplier for stat cards
    @FunctionalInterface
    private interface Supplier<T> { T get(); }
}
