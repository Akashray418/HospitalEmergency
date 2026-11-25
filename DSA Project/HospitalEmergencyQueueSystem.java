import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Hospital Emergency Queue System
 * Run with: javac HospitalEmergencyQueueSystem.java && java
 * HospitalEmergencyQueueSystem
 * Severity 5 is most urgent. Equal severities are served in arrival order.
 */
public class HospitalEmergencyQueueSystem {
    private static final Path DATA_FILE = Paths.get("data", "emergency_queue.txt");
    private final HospitalQueueService service = new HospitalQueueService(DATA_FILE);
    private JFrame frame;
    private JTextField idField, nameField, searchField;
    private JComboBox<Integer> severityBox;
    private JTable waitingTable;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new HospitalEmergencyQueueSystem().start());
    }

    private void start() {
        try {
            service.load();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Could not load saved data: " + ex.getMessage(),
                    "Load error", JOptionPane.ERROR_MESSAGE);
        }
        buildWindow();
        refreshTable();
        frame.setVisible(true);
    }

    private void buildWindow() {
        frame = new JFrame("Hospital Emergency Queue System");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setSize(850, 570);
        frame.setMinimumSize(new Dimension(700, 460));
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(12, 12));

        JLabel heading = new JLabel("Hospital Emergency Queue", SwingConstants.CENTER);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        heading.setBorder(BorderFactory.createEmptyBorder(16, 10, 4, 10));
        frame.add(heading, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        frame.add(content, BorderLayout.CENTER);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Register patient"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 8, 6, 8);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        idField = new JTextField(12);
        nameField = new JTextField(14);
        severityBox = new JComboBox<>(new Integer[] { 1, 2, 3, 4, 5 });
        addFormRow(form, c, 0, "Patient ID", idField);
        addFormRow(form, c, 1, "Patient name", nameField);
        addFormRow(form, c, 2, "Severity (5 = highest)", severityBox);
        JButton registerButton = new JButton("Register Patient");
        c.gridx = 1;
        c.gridy = 3;
        form.add(registerButton, c);
        registerButton.addActionListener(e -> registerPatient());

        JPanel actions = new JPanel(new GridLayout(0, 1, 8, 8));
        actions.setBorder(BorderFactory.createTitledBorder("Queue actions"));
        JButton callButton = new JButton("Call Next Patient");
        JButton saveButton = new JButton("Save Data");
        JButton searchButton = new JButton("Search Patient by ID");
        searchField = new JTextField();
        JPanel searchRow = new JPanel(new BorderLayout(5, 0));
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(searchButton, BorderLayout.EAST);
        actions.add(callButton);
        actions.add(searchRow);
        actions.add(saveButton);
        callButton.addActionListener(e -> callNextPatient());
        searchButton.addActionListener(e -> searchPatient());
        searchField.addActionListener(e -> searchPatient());
        saveButton.addActionListener(e -> saveData(true));

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.add(form, BorderLayout.CENTER);
        top.add(actions, BorderLayout.EAST);
        content.add(top, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new Object[] { "#", "Patient ID", "Name", "Severity", "Priority" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        waitingTable = new JTable(tableModel);
        waitingTable.setRowHeight(26);
        waitingTable.setFillsViewportHeight(true);
        waitingTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Waiting list (highest priority first)"));
        tablePanel.add(new JScrollPane(waitingTable), BorderLayout.CENTER);
        content.add(tablePanel, BorderLayout.CENTER);

        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 16, 12, 16));
        frame.add(statusLabel, BorderLayout.SOUTH);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (saveData(false)) {
                    frame.dispose();
                } else {
                    JOptionPane.showMessageDialog(frame,
                            "Data could not be saved. The application will remain open so you can retry.",
                            "Save failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private void addFormRow(JPanel panel, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        panel.add(new JLabel(label + ":"), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);
    }

    private void registerPatient() {
        try {
            Patient p = service.register(idField.getText(), nameField.getText(),
                    (Integer) severityBox.getSelectedItem());
            idField.setText("");
            nameField.setText("");
            refreshTable();
            setStatus("Patient added: " + p);
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void callNextPatient() {
        Patient p = service.callNext();
        if (p == null) {
            setStatus("The waiting queue is empty.");
            return;
        }
        refreshTable();
        setStatus("Calling " + p + ". Patient record remains registered.");
    }

    private void searchPatient() {
        String id = searchField.getText().trim();
        if (id.isEmpty()) {
            showError("Enter a patient ID to search.");
            return;
        }
        Patient p = service.search(id);
        if (p == null) {
            setStatus("Patient " + id + " not found.");
            JOptionPane.showMessageDialog(frame, "Patient " + id + " not found.", "Search result",
                    JOptionPane.INFORMATION_MESSAGE);
        } else {
            String message = p + " | Arrival order: " + p.arrivalOrder;
            setStatus("Found: " + message);
            JOptionPane.showMessageDialog(frame, message, "Patient found", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void refreshTable() {
        if (tableModel == null)
            return;
        tableModel.setRowCount(0);
        int i = 1;
        for (Patient p : service.getWaitingPatients()) {
            tableModel.addRow(new Object[] { i++, p.id, p.name, p.severity, priorityLabel(p.severity) });
        }
    }

    private String priorityLabel(int severity) {
        if (severity == 5)
            return "CRITICAL";
        if (severity == 4)
            return "HIGH";
        if (severity == 3)
            return "MODERATE";
        if (severity == 2)
            return "LOW";
        return "MINOR";
    }

    private boolean saveData(boolean showMessage) {
        try {
            service.save();
            setStatus("Data saved to " + DATA_FILE);
            if (showMessage) {
                JOptionPane.showMessageDialog(frame, "Patient data saved.", "Saved",
                        JOptionPane.INFORMATION_MESSAGE);
            }
            return true;
        } catch (IOException ex) {
            showError("Could not save data: " + ex.getMessage());
            return false;
        }
    }

    private void setStatus(String text) {
        if (statusLabel != null)
            statusLabel.setText(text);
    }

    private void showError(String text) {
        JOptionPane.showMessageDialog(frame, text, "Input error", JOptionPane.ERROR_MESSAGE);
        setStatus(text);
    }

    private static final class Patient {
        final String id;
        final String name;
        final int severity;
        final long arrivalOrder;
        boolean waiting;

        Patient(String id, String name, int severity, long arrivalOrder, boolean waiting) {
            this.id = id;
            this.name = name;
            this.severity = severity;
            this.arrivalOrder = arrivalOrder;
            this.waiting = waiting;
        }

        @Override
        public String toString() {
            return id + " | " + name + " | Severity " + severity;
        }
    }

    /**
     * Custom singly linked list retaining every registered patient for ID search.
     */
    private static final class PatientLinkedList {
        private static final class Node {
            Patient value;
            Node next;

            Node(Patient p) {
                value = p;
            }
        }

        private Node head, tail;
        private int size;

        void add(Patient p) {
            Node n = new Node(p);
            if (head == null)
                head = n;
            else
                tail.next = n;
            tail = n;
            size++;
        }

        Patient findById(String id) {
            for (Node n = head; n != null; n = n.next)
                if (n.value.id.equalsIgnoreCase(id))
                    return n.value;
            return null;
        }

        java.util.List<Patient> toList() {
            java.util.List<Patient> result = new ArrayList<>(size);
            for (Node n = head; n != null; n = n.next)
                result.add(n.value);
            return result;
        }

        int size() {
            return size;
        }
    }

    /** Binary max-heap: higher severity wins; earlier arrival wins ties. */
    private static final class PatientMaxHeap {
        private final ArrayList<Patient> heap = new ArrayList<>();

        private boolean higherPriority(Patient a, Patient b) {
            return a.severity != b.severity ? a.severity > b.severity : a.arrivalOrder < b.arrivalOrder;
        }

        void add(Patient p) {
            heap.add(p);
            int i = heap.size() - 1;
            while (i > 0) {
                int parent = (i - 1) / 2;
                if (!higherPriority(heap.get(i), heap.get(parent)))
                    break;
                swap(i, parent);
                i = parent;
            }
        }

        Patient peek() {
            return heap.isEmpty() ? null : heap.get(0);
        }

        Patient removeTop() {
            if (heap.isEmpty())
                return null;
            Patient top = heap.get(0), last = heap.remove(heap.size() - 1);
            if (!heap.isEmpty()) {
                heap.set(0, last);
                int i = 0;
                while (true) {
                    int left = 2 * i + 1, right = left + 1, best = i;
                    if (left < heap.size() && higherPriority(heap.get(left), heap.get(best)))
                        best = left;
                    if (right < heap.size() && higherPriority(heap.get(right), heap.get(best)))
                        best = right;
                    if (best == i)
                        break;
                    swap(i, best);
                    i = best;
                }
            }
            return top;
        }

        java.util.List<Patient> sortedSnapshot() {
            PatientMaxHeap copy = new PatientMaxHeap();
            copy.heap.addAll(heap);
            java.util.List<Patient> result = new ArrayList<>(heap.size());
            Patient p;
            while ((p = copy.removeTop()) != null)
                result.add(p);
            return result;
        }

        private void swap(int a, int b) {
            Patient p = heap.get(a);
            heap.set(a, heap.get(b));
            heap.set(b, p);
        }
    }

    /** Service layer joins the data structures and text-file persistence. */
    private static final class HospitalQueueService {
        private final PatientLinkedList records = new PatientLinkedList();
        private final PatientMaxHeap waiting = new PatientMaxHeap();
        private final Path file;
        private long nextArrivalOrder = 1;

        HospitalQueueService(Path file) {
            this.file = file;
        }

        Patient register(String rawId, String rawName, int severity) {
            String id = rawId == null ? "" : rawId.trim();
            String name = rawName == null ? "" : rawName.trim();
            if (id.isEmpty())
                throw new IllegalArgumentException("Patient ID is required.");
            if (id.contains("|") || id.contains("\n") || id.contains("\r"))
                throw new IllegalArgumentException("Patient ID cannot contain | or line breaks.");
            if (name.isEmpty())
                throw new IllegalArgumentException("Patient name is required.");
            if (name.contains("|") || name.contains("\n") || name.contains("\r"))
                throw new IllegalArgumentException("Patient name cannot contain | or line breaks.");
            if (severity < 1 || severity > 5)
                throw new IllegalArgumentException("Severity must be from 1 to 5.");
            if (records.findById(id) != null)
                throw new IllegalArgumentException("Patient ID already exists: " + id);
            Patient p = new Patient(id, name, severity, nextArrivalOrder++, true);
            records.add(p);
            waiting.add(p);
            return p;
        }

        Patient callNext() {
            Patient p = waiting.removeTop();
            if (p != null) {
                p.waiting = false;
            }
            return p;
        }

        Patient search(String id) {
            return records.findById(id == null ? "" : id.trim());
        }

        java.util.List<Patient> getWaitingPatients() {
            return waiting.sortedSnapshot();
        }

        void save() throws IOException {
            Path parent = file.getParent();
            if (parent != null)
                Files.createDirectories(parent);
            try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                for (Patient p : records.toList()) {
                    out.write(p.id + "|" + p.name + "|" + p.severity + "|" + p.arrivalOrder
                            + "|" + (p.waiting ? "WAITING" : "CALLED"));
                    out.newLine();
                }
            }
        }

        void load() throws IOException {
            if (!Files.exists(file)) {
                return;
            }

            // Parse and validate the complete file before changing in-memory data.
            java.util.List<Patient> loaded = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            long maxOrder = 0;
            int lineNo = 0;

            try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = in.readLine()) != null) {
                    lineNo++;
                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    String[] parts = line.split("\\|", -1);
                    if (parts.length != 4 && parts.length != 5) {
                        throw new IOException("Invalid record at line " + lineNo
                                + ": expected 4 or 5 fields.");
                    }

                    try {
                        String id = parts[0].trim();
                        String name = parts[1].trim();
                        int severity = Integer.parseInt(parts[2].trim());
                        long order = Long.parseLong(parts[3].trim());
                        // Backward compatibility: old 4-field files restore records as WAITING.
                        String state = parts.length == 4 ? "WAITING" : parts[4].trim();
                        boolean isWaiting;

                        if ("WAITING".equalsIgnoreCase(state)) {
                            isWaiting = true;
                        } else if ("CALLED".equalsIgnoreCase(state)) {
                            isWaiting = false;
                        } else {
                            throw new IllegalArgumentException("Unknown patient status: " + state);
                        }

                        if (id.isEmpty() || name.isEmpty() || id.contains("|") || name.contains("|")
                                || severity < 1 || severity > 5 || order < 1
                                || !ids.add(id.toLowerCase(Locale.ROOT))) {
                            throw new IllegalArgumentException("Invalid or duplicate patient data.");
                        }

                        loaded.add(new Patient(id, name, severity, order, isWaiting));
                        maxOrder = Math.max(maxOrder, order);
                    } catch (IllegalArgumentException ex) {
                        throw new IOException("Invalid patient record at line " + lineNo, ex);
                    }
                }
            }

            for (Patient p : loaded) {
                records.add(p);
                if (p.waiting) {
                    waiting.add(p);
                }
            }
            nextArrivalOrder = maxOrder + 1;
        }
    }
}