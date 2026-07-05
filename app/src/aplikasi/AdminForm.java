/*
 * Aplikasi Pendataan Mahasiswa
 * Universitas Bina Sarana Informatika
 *
 * Catatan: Form ini dibuat langsung lewat kode (bukan lewat GUI Builder / .form file)
 * karena strukturnya cukup kompleks (tab + beberapa tabel dinamis). Tetap bisa
 * dijalankan dan diedit normal lewat tab "Source" di NetBeans, hanya saja tab
 * "Design" tidak tersedia untuk file ini.
 */
package aplikasi;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminForm extends JFrame {

    private final String loggedInUsername;
    private final String loggedInNamaLengkap;

    // ==== Tab Data Mahasiswa ====
    private DefaultTableModel mhsTableModel;
    private JTable mhsTable;
    private JTextField mhsNimField, mhsNamaField, mhsProdiField;
    private int mhsSelectedRow = -1;
    private String mhsOriginalNim = null;

    // ==== Tab Kelola User ====
    private DefaultTableModel userTableModel;
    private JTable userTable;
    private JTextField userUsernameField, userNamaField;
    private JPasswordField userPasswordField;
    private JComboBox<String> userRoleCombo;
    private int userSelectedRow = -1;
    private String userOriginalUsername = null;

    // ==== Tab Log Aktivitas ====
    private DefaultTableModel logTableModel;
    private JTable logTable;

    // ==== Tab Statistik ====
    private JLabel totalMahasiswaLabel;
    private JTextArea statistikProdiArea;

    public AdminForm(String username, String namaLengkap) {
        this.loggedInUsername = username;
        this.loggedInNamaLengkap = (namaLengkap == null || namaLengkap.isEmpty()) ? username : namaLengkap;

        setTitle("Panel Admin - Sistem Pendataan Mahasiswa");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 650);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());
        add(buildHeaderPanel(), BorderLayout.NORTH);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Data Mahasiswa", buildMahasiswaTab());
        tabbedPane.addTab("Kelola User", buildUserTab());
        tabbedPane.addTab("Log Aktivitas", buildLogTab());
        tabbedPane.addTab("Statistik", buildStatistikTab());
        add(tabbedPane, BorderLayout.CENTER);

        loadMahasiswaData();
        loadUserData();
        loadLogData();
        refreshStatistik();
        startAutoRefresh();

        RealtimeClient rt = RealtimeClient.getInstance();
        rt.subscribe("mahasiswa", () -> {
            if (mhsSelectedRow == -1) {
                loadMahasiswaData();
            }
            refreshStatistik();
        });
        rt.subscribe("users", () -> {
            if (userSelectedRow == -1) {
                loadUserData();
            }
        });
        rt.subscribe("activity_log", this::loadLogData);
    }

    /**
     * Timer cadangan: realtime lewat WebSocket (lihat RealtimeClient) sudah
     * membuat perubahan data langsung muncul tanpa menunggu timer ini.
     * Timer ini tetap dijalankan dengan interval yang lebih longgar sebagai
     * jaring pengaman, kalau-kalau koneksi realtime sedang putus (misal
     * internet sempat bermasalah).
     */
    private void startAutoRefresh() {
        javax.swing.Timer refreshTimer = new javax.swing.Timer(20000, e -> {
            if (mhsSelectedRow == -1) {
                loadMahasiswaData();
            }
            if (userSelectedRow == -1) {
                loadUserData();
            }
            loadLogData();
            refreshStatistik();
        });
        refreshTimer.start();
    }

    // ==================================================================
    // HEADER
    // ==================================================================
    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(60, 90, 150));
        header.setPreferredSize(new Dimension(0, 60));

        JLabel titleLabel = new JLabel("  Panel Admin - Sistem Pendataan Mahasiswa");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        header.add(titleLabel, BorderLayout.WEST);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightPanel.setOpaque(false);

        JLabel welcomeLabel = new JLabel("Login sebagai: " + loggedInNamaLengkap + " (Admin)  ");
        welcomeLabel.setForeground(Color.WHITE);
        rightPanel.add(welcomeLabel);

        JButton logoutButton = new JButton("Logout");
        logoutButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Apakah Anda yakin ingin logout?",
                    "Konfirmasi Logout",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                DatabaseHelper.logActivity(loggedInUsername, "Logout dari sistem");
                RealtimeClient.getInstance().shutdown();
                this.dispose();
                new LoginForm().setVisible(true);
            }
        });
        rightPanel.add(logoutButton);

        header.add(rightPanel, BorderLayout.EAST);
        return header;
    }

    // ==================================================================
    // TAB 1: DATA MAHASISWA
    // ==================================================================
    private JPanel buildMahasiswaTab() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        mhsNimField = new JTextField();
        mhsNamaField = new JTextField();
        mhsProdiField = new JTextField();

        formPanel.add(labeledRow("NIM:", mhsNimField));
        formPanel.add(labeledRow("Nama Lengkap:", mhsNamaField));
        formPanel.add(labeledRow("Program Studi:", mhsProdiField));
        panel.add(formPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton tambahBtn = new JButton("Tambah");
        tambahBtn.addActionListener(e -> tambahMahasiswa());
        JButton ubahBtn = new JButton("Ubah");
        ubahBtn.addActionListener(e -> ubahMahasiswa());
        JButton hapusBtn = new JButton("Hapus");
        hapusBtn.addActionListener(e -> hapusMahasiswa());
        JButton bersihkanBtn = new JButton("Bersihkan");
        bersihkanBtn.addActionListener(e -> {
            mhsNimField.setText("");
            mhsNamaField.setText("");
            mhsProdiField.setText("");
            mhsSelectedRow = -1;
            mhsOriginalNim = null;
        });
        JButton exportBtn = new JButton("Export ke CSV");
        exportBtn.addActionListener(e -> exportMahasiswaToCsv());

        buttonPanel.add(tambahBtn);
        buttonPanel.add(ubahBtn);
        buttonPanel.add(hapusBtn);
        buttonPanel.add(bersihkanBtn);
        buttonPanel.add(exportBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        mhsTableModel = new DefaultTableModel(new Object[]{"NIM", "Nama Lengkap", "Program Studi"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        mhsTable = new JTable(mhsTableModel);
        mhsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                mhsSelectedRow = mhsTable.getSelectedRow();
                if (mhsSelectedRow != -1) {
                    mhsOriginalNim = mhsTableModel.getValueAt(mhsSelectedRow, 0).toString();
                    mhsNimField.setText(mhsTableModel.getValueAt(mhsSelectedRow, 0).toString());
                    mhsNamaField.setText(mhsTableModel.getValueAt(mhsSelectedRow, 1).toString());
                    mhsProdiField.setText(mhsTableModel.getValueAt(mhsSelectedRow, 2).toString());
                }
            }
        });
        panel.add(new JScrollPane(mhsTable), BorderLayout.CENTER);

        return panel;
    }

    private void loadMahasiswaData() {
        mhsTableModel.setRowCount(0);
        try {
            List<Map<String, Object>> rows = DatabaseHelper.getAllMahasiswa();
            for (Map<String, Object> row : rows) {
                mhsTableModel.addRow(new Object[]{row.get("nim"), row.get("nama"), row.get("prodi")});
            }
        } catch (DbException e) {
            JOptionPane.showMessageDialog(this, "Gagal memuat data mahasiswa:\n" + e.getMessage());
        }
    }

    private void tambahMahasiswa() {
        String nim = mhsNimField.getText().trim();
        String nama = mhsNamaField.getText().trim();
        String prodi = mhsProdiField.getText().trim();
        if (nim.isEmpty() || nama.isEmpty() || prodi.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Semua data harus diisi!", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            DatabaseHelper.insertMahasiswa(nim, nama, prodi);
            DatabaseHelper.logActivity(loggedInUsername, "[Admin] Menambah data mahasiswa NIM " + nim);
            loadMahasiswaData();
            refreshStatistik();
            mhsNimField.setText("");
            mhsNamaField.setText("");
            mhsProdiField.setText("");
        } catch (DbException e) {
            String msg = e.isDuplicate() ? "NIM sudah terdaftar!" : "Gagal menambah data:\n" + e.getMessage();
            JOptionPane.showMessageDialog(this, msg, "Peringatan", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void ubahMahasiswa() {
        if (mhsSelectedRow == -1 || mhsOriginalNim == null) {
            JOptionPane.showMessageDialog(this, "Pilih data di tabel terlebih dahulu!", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String nim = mhsNimField.getText().trim();
        String nama = mhsNamaField.getText().trim();
        String prodi = mhsProdiField.getText().trim();
        if (nim.isEmpty() || nama.isEmpty() || prodi.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Semua data harus diisi!", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            DatabaseHelper.updateMahasiswa(mhsOriginalNim, nim, nama, prodi);
            DatabaseHelper.logActivity(loggedInUsername, "[Admin] Mengubah data mahasiswa NIM " + mhsOriginalNim);
            loadMahasiswaData();
            refreshStatistik();
            mhsNimField.setText("");
            mhsNamaField.setText("");
            mhsProdiField.setText("");
            mhsSelectedRow = -1;
            mhsOriginalNim = null;
        } catch (DbException e) {
            JOptionPane.showMessageDialog(this, "Gagal mengubah data:\n" + e.getMessage());
        }
    }

    private void hapusMahasiswa() {
        if (mhsSelectedRow == -1 || mhsOriginalNim == null) {
            JOptionPane.showMessageDialog(this, "Pilih data di tabel terlebih dahulu!", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Yakin ingin menghapus data ini?", "Konfirmasi", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            DatabaseHelper.deleteMahasiswa(mhsOriginalNim);
            DatabaseHelper.logActivity(loggedInUsername, "[Admin] Menghapus data mahasiswa NIM " + mhsOriginalNim);
            loadMahasiswaData();
            refreshStatistik();
            mhsNimField.setText("");
            mhsNamaField.setText("");
            mhsProdiField.setText("");
            mhsSelectedRow = -1;
            mhsOriginalNim = null;
        } catch (DbException e) {
            JOptionPane.showMessageDialog(this, "Gagal menghapus data:\n" + e.getMessage());
        }
    }

    private void exportMahasiswaToCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("data_mahasiswa.csv"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("NIM,Nama Lengkap,Program Studi\n");
            for (int i = 0; i < mhsTableModel.getRowCount(); i++) {
                writer.write(csvEscape(mhsTableModel.getValueAt(i, 0)) + ","
                        + csvEscape(mhsTableModel.getValueAt(i, 1)) + ","
                        + csvEscape(mhsTableModel.getValueAt(i, 2)) + "\n");
            }
            DatabaseHelper.logActivity(loggedInUsername, "[Admin] Export data mahasiswa ke CSV");
            JOptionPane.showMessageDialog(this,
                    "Data berhasil di-export ke:\n" + file.getAbsolutePath()
                    + "\n\nFile CSV ini bisa langsung dibuka di Excel.",
                    "Sukses", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Gagal export data:\n" + e.getMessage());
        }
    }

    private String csvEscape(Object value) {
        String s = value == null ? "" : value.toString();
        if (s.contains(",") || s.contains("\"")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ==================================================================
    // TAB 2: KELOLA USER
    // ==================================================================
    private JPanel buildUserTab() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        userUsernameField = new JTextField();
        userPasswordField = new JPasswordField();
        userNamaField = new JTextField();
        userRoleCombo = new JComboBox<>(new String[]{"user", "admin"});

        formPanel.add(labeledRow("Username:", userUsernameField));
        formPanel.add(labeledRow("Password:", userPasswordField));
        formPanel.add(labeledRow("Nama Lengkap:", userNamaField));
        formPanel.add(labeledRow("Role:", userRoleCombo));
        panel.add(formPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton tambahBtn = new JButton("Tambah User");
        tambahBtn.addActionListener(e -> tambahUser());
        JButton resetPassBtn = new JButton("Ubah / Reset Password");
        resetPassBtn.addActionListener(e -> ubahUser());
        JButton hapusBtn = new JButton("Hapus User");
        hapusBtn.addActionListener(e -> hapusUser());
        JButton bersihkanBtn = new JButton("Bersihkan");
        bersihkanBtn.addActionListener(e -> {
            userUsernameField.setText("");
            userPasswordField.setText("");
            userNamaField.setText("");
            userSelectedRow = -1;
            userOriginalUsername = null;
        });
        buttonPanel.add(tambahBtn);
        buttonPanel.add(resetPassBtn);
        buttonPanel.add(hapusBtn);
        buttonPanel.add(bersihkanBtn);

        userTableModel = new DefaultTableModel(new Object[]{"Username", "Nama Lengkap", "Role"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        userTable = new JTable(userTableModel);
        userTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                userSelectedRow = userTable.getSelectedRow();
                if (userSelectedRow != -1) {
                    userOriginalUsername = userTableModel.getValueAt(userSelectedRow, 0).toString();
                    userUsernameField.setText(userOriginalUsername);
                    userNamaField.setText(userTableModel.getValueAt(userSelectedRow, 1).toString());
                    userRoleCombo.setSelectedItem(userTableModel.getValueAt(userSelectedRow, 2).toString());
                    userPasswordField.setText("");
                }
            }
        });
        panel.add(new JScrollPane(userTable), BorderLayout.CENTER);

        JLabel hint = new JLabel(" Catatan: kosongkan password saat 'Ubah' jika tidak ingin menggantinya.");
        hint.setFont(new Font("Arial", Font.ITALIC, 11));

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(buttonPanel, BorderLayout.NORTH);
        southPanel.add(hint, BorderLayout.SOUTH);
        panel.add(southPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void loadUserData() {
        userTableModel.setRowCount(0);
        try {
            List<Map<String, Object>> rows = DatabaseHelper.getAllUsers();
            for (Map<String, Object> row : rows) {
                userTableModel.addRow(new Object[]{row.get("username"), row.get("nama_lengkap"), row.get("role")});
            }
        } catch (DbException e) {
            JOptionPane.showMessageDialog(this, "Gagal memuat data user:\n" + e.getMessage());
        }
    }

    private void tambahUser() {
        String username = userUsernameField.getText().trim();
        String password = new String(userPasswordField.getPassword());
        String nama = userNamaField.getText().trim();
        String role = (String) userRoleCombo.getSelectedItem();

        if (username.isEmpty() || password.isEmpty() || nama.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Semua data harus diisi!", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            DatabaseHelper.insertUser(username, password, role, nama);
            DatabaseHelper.logActivity(loggedInUsername, "[Admin] Menambah akun user: " + username + " (" + role + ")");
            loadUserData();
            userUsernameField.setText("");
            userPasswordField.setText("");
            userNamaField.setText("");
        } catch (DbException e) {
            String msg = e.isDuplicate() ? "Username sudah digunakan!" : "Gagal menambah user:\n" + e.getMessage();
            JOptionPane.showMessageDialog(this, msg, "Peringatan", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void ubahUser() {
        if (userSelectedRow == -1 || userOriginalUsername == null) {
            JOptionPane.showMessageDialog(this, "Pilih user di tabel terlebih dahulu!", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String username = userUsernameField.getText().trim();
        String password = new String(userPasswordField.getPassword());
        String nama = userNamaField.getText().trim();
        String role = (String) userRoleCombo.getSelectedItem();

        if (username.isEmpty() || nama.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username dan Nama Lengkap harus diisi!", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            DatabaseHelper.updateUser(userOriginalUsername, username, nama, role, password);
            DatabaseHelper.logActivity(loggedInUsername, "[Admin] Mengubah akun user: " + userOriginalUsername);
            loadUserData();
            userUsernameField.setText("");
            userPasswordField.setText("");
            userNamaField.setText("");
            userSelectedRow = -1;
            userOriginalUsername = null;
        } catch (DbException e) {
            JOptionPane.showMessageDialog(this, "Gagal mengubah user:\n" + e.getMessage());
        }
    }

    private void hapusUser() {
        if (userSelectedRow == -1 || userOriginalUsername == null) {
            JOptionPane.showMessageDialog(this, "Pilih user di tabel terlebih dahulu!", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (userOriginalUsername.equals(loggedInUsername)) {
            JOptionPane.showMessageDialog(this, "Tidak bisa menghapus akun yang sedang login!", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Yakin ingin menghapus user ini?", "Konfirmasi", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            DatabaseHelper.deleteUser(userOriginalUsername);
            DatabaseHelper.logActivity(loggedInUsername, "[Admin] Menghapus akun user: " + userOriginalUsername);
            loadUserData();
            userUsernameField.setText("");
            userPasswordField.setText("");
            userNamaField.setText("");
            userSelectedRow = -1;
            userOriginalUsername = null;
        } catch (DbException e) {
            JOptionPane.showMessageDialog(this, "Gagal menghapus user:\n" + e.getMessage());
        }
    }

    // ==================================================================
    // TAB 3: LOG AKTIVITAS
    // ==================================================================
    private JPanel buildLogTab() {
        JPanel panel = new JPanel(new BorderLayout());

        logTableModel = new DefaultTableModel(new Object[]{"Waktu", "Username", "Aktivitas"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        logTable = new JTable(logTableModel);
        panel.add(new JScrollPane(logTable), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> loadLogData());
        bottomPanel.add(refreshBtn);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void loadLogData() {
        logTableModel.setRowCount(0);
        try {
            List<Map<String, Object>> rows = DatabaseHelper.getRecentLogs(500);
            for (Map<String, Object> row : rows) {
                logTableModel.addRow(new Object[]{row.get("waktu"), row.get("username"), row.get("aktivitas")});
            }
        } catch (DbException e) {
            JOptionPane.showMessageDialog(this, "Gagal memuat log aktivitas:\n" + e.getMessage());
        }
    }

    // ==================================================================
    // TAB 4: STATISTIK
    // ==================================================================
    private JPanel buildStatistikTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        totalMahasiswaLabel = new JLabel("Total Mahasiswa: 0");
        totalMahasiswaLabel.setFont(new Font("Arial", Font.BOLD, 20));
        panel.add(totalMahasiswaLabel, BorderLayout.NORTH);

        statistikProdiArea = new JTextArea();
        statistikProdiArea.setEditable(false);
        statistikProdiArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        panel.add(new JScrollPane(statistikProdiArea), BorderLayout.CENTER);

        JButton refreshBtn = new JButton("Refresh Statistik");
        refreshBtn.addActionListener(e -> refreshStatistik());
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.add(refreshBtn);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void refreshStatistik() {
        Map<String, Integer> perProdi;
        try {
            perProdi = DatabaseHelper.getStatistikPerProdi();
        } catch (DbException e) {
            JOptionPane.showMessageDialog(this, "Gagal memuat statistik:\n" + e.getMessage());
            return;
        }

        int total = 0;
        for (int jumlah : perProdi.values()) {
            total += jumlah;
        }
        totalMahasiswaLabel.setText("Total Mahasiswa: " + total);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-40s %s%n", "Program Studi", "Jumlah"));
        sb.append("--------------------------------------------------\n");
        for (Map.Entry<String, Integer> entry : perProdi.entrySet()) {
            sb.append(String.format("%-40s %d%n", entry.getKey(), entry.getValue()));
        }
        statistikProdiArea.setText(sb.toString());
    }

    // ==================================================================
    // HELPER
    // ==================================================================
    private JPanel labeledRow(String labelText, JComponent field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel label = new JLabel(labelText);
        label.setPreferredSize(new Dimension(120, 20));
        row.add(label);
        field.setPreferredSize(new Dimension(220, 28));
        row.add(field);
        return row;
    }
}
