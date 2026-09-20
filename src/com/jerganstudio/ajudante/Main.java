package com.jerganstudio.ajudante;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AjudanteFrame().setVisible(true));
    }
}

class AjudanteFrame extends JFrame {
    private final Project project = new Project();
    private final Agent agent = new Agent(project);
    private final JTextArea chat = new JTextArea();
    private final JTextArea input = new JTextArea(4, 60);
    private final JTextArea editor = new JTextArea();
    private final JLabel projectLabel = new JLabel("No project opened");
    private final JTree tree = new JTree(new DefaultMutableTreeNode("Project"));
    private final JLabel status = new JLabel("Ready");

    AjudanteFrame() {
        super("Ajudante — AI Coding Assistant");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1250, 780);
        setLocationRelativeTo(null);
        build();
    }

    private void build() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(root);

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JButton open = new JButton("Open Project");
        JButton refresh = new JButton("Refresh");
        JButton save = new JButton("Save");
        JButton settings = new JButton("Settings");
        bar.add(open); bar.add(refresh); bar.add(save);
        bar.addSeparator(); bar.add(settings);
        bar.add(Box.createHorizontalStrut(12)); bar.add(projectLabel);
        root.add(bar, BorderLayout.NORTH);

        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        center.setDividerLocation(250);

        JPanel left = new JPanel(new BorderLayout());
        left.add(new JLabel("  PROJECT"), BorderLayout.NORTH);
        tree.setRootVisible(true);
        tree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openSelectedFile();
            }
        });
        left.add(new JScrollPane(tree), BorderLayout.CENTER);
        center.setLeftComponent(left);

        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        right.setDividerLocation(470);

        JSplitPane work = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        work.setDividerLocation(650);
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        editor.setTabSize(4);
        work.setLeftComponent(new JScrollPane(editor));

        JPanel chatPanel = new JPanel(new BorderLayout(5, 5));
        chatPanel.add(new JLabel("  AJUDANTE AI"), BorderLayout.NORTH);
        chat.setEditable(false);
        chat.setLineWrap(true);
        chat.setWrapStyleWord(true);
        chat.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        chatPanel.add(new JScrollPane(chat), BorderLayout.CENTER);
        JPanel prompt = new JPanel(new BorderLayout(5, 5));
        JButton send = new JButton("Ask AI");
        input.setLineWrap(true);
        prompt.add(new JScrollPane(input), BorderLayout.CENTER);
        prompt.add(send, BorderLayout.EAST);
        chatPanel.add(prompt, BorderLayout.SOUTH);
        work.setRightComponent(chatPanel);

        right.setTopComponent(work);
        JTextArea output = new JTextArea();
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        right.setBottomComponent(new JScrollPane(output));
        center.setRightComponent(right);
        root.add(center, BorderLayout.CENTER);

        root.add(status, BorderLayout.SOUTH);

        open.addActionListener(e -> chooseProject());
        refresh.addActionListener(e -> refreshTree());
        save.addActionListener(e -> saveFile());
        settings.addActionListener(e -> settings());
        send.addActionListener(e -> ask(output, send));
        input.getInputMap().put(KeyStroke.getKeyStroke("ctrl ENTER"), "send");
        input.getActionMap().put("send", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { ask(output, send); }
        });

        chat.append("Ajudante AI\n");
        chat.append("Open a project, then tell me what you want to build.\n\n");
    }

    private void chooseProject() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            project.setRoot(chooser.getSelectedFile().toPath());
            projectLabel.setText(project.root().toString());
            refreshTree();
            chat.append("Opened project: " + project.root() + "\n");
        }
    }

    private void refreshTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(
                project.root() == null ? "Project" : project.root().getFileName().toString());
        if (project.root() != null) addChildren(root, project.root());
        tree.setModel(new DefaultTreeModel(root));
    }

    private void addChildren(DefaultMutableTreeNode node, Path dir) {
        try {
            List<Path> paths = Files.list(dir)
                    .filter(p -> !p.getFileName().toString().equals(".git"))
                    .sorted(Comparator.comparing(p -> !Files.isDirectory(p)))
                    .toList();
            for (Path p : paths) {
                DefaultMutableTreeNode child = new DefaultMutableTreeNode(p.getFileName().toString());
                node.add(child);
                if (Files.isDirectory(p)) addChildren(child, p);
            }
        } catch (IOException ignored) {}
    }

    private Path selectedPath() {
        if (project.root() == null) return null;
        var selection = tree.getSelectionPath();
        if (selection == null) return null;
        Path p = project.root();
        for (int i = 1; i < selection.getPathCount(); i++) {
            p = p.resolve(selection.getPathComponent(i).toString());
        }
        return p;
    }

    private void openSelectedFile() {
        Path p = selectedPath();
        if (p == null || Files.isDirectory(p)) return;
        try {
            editor.setText(Files.readString(p));
            editor.setCaretPosition(0);
            status.setText("Editing " + p);
        } catch (IOException e) {
            showError(e);
        }
    }

    private void saveFile() {
        Path p = selectedPath();
        if (p == null || Files.isDirectory(p)) return;
        try {
            Files.writeString(p, editor.getText(), StandardCharsets.UTF_8);
            status.setText("Saved " + p);
        } catch (IOException e) { showError(e); }
    }

    private void ask(JTextArea output, JButton send) {
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        if (project.root() == null) {
            chat.append("Open a project first.\n");
            return;
        }
        input.setText("");
        chat.append("\nYou: " + text + "\n");
        send.setEnabled(false);
        status.setText("AI is working...");
        new SwingWorker<String, Void>() {
            protected String doInBackground() {
                try { return agent.run(text, msg -> SwingUtilities.invokeLater(() -> chat.append(msg))); }
                catch (Exception e) { return "Error: " + e.getMessage(); }
            }
            protected void done() {
                try {
                    String result = get();
                    chat.append("Ajudante: " + result + "\n");
                    output.append(result + "\n");
                    refreshTree();
                    status.setText("Ready");
                } catch (Exception e) { chat.append("Error: " + e.getMessage() + "\n"); }
                send.setEnabled(true);
            }
        }.execute();
    }

    private void settings() {
        JTextField base = new JTextField(Settings.get("base", "http://localhost:11434/v1"));
        JTextField model = new JTextField(Settings.get("model", "llama3.2"));
        JPasswordField key = new JPasswordField(Settings.get("key", ""));
        JPanel p = new JPanel(new GridLayout(0, 1, 4, 4));
        p.add(new JLabel("OpenAI-compatible Base URL:")); p.add(base);
        p.add(new JLabel("Model:")); p.add(model);
        p.add(new JLabel("API key (optional for local Ollama):")); p.add(key);
        p.add(new JLabel("No Ajudante account is required."));
        if (JOptionPane.showConfirmDialog(this, p, "AI Settings",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            Settings.put("base", base.getText().trim());
            Settings.put("model", model.getText().trim());
            Settings.put("key", new String(key.getPassword()));
        }
    }

    private void showError(Exception e) {
        JOptionPane.showMessageDialog(this, e.getMessage(), "Ajudante", JOptionPane.ERROR_MESSAGE);
    }
}

class Project {
    private Path root;
    void setRoot(Path p) { root = p.toAbsolutePath().normalize(); }
    Path root() { return root; }

    Path safe(String relative) throws IOException {
        if (root == null) throw new IOException("No project is open.");
        Path p = root.resolve(relative).normalize();
        if (!p.startsWith(root)) throw new IOException("Path escapes the project directory.");
        return p;
    }

    String list(String relative) throws IOException {
        Path dir = safe(relative == null || relative.isBlank() ? "." : relative);
        if (!Files.isDirectory(dir)) throw new IOException("Not a directory: " + relative);
        StringBuilder b = new StringBuilder();
        try (var s = Files.list(dir)) {
            s.sorted().forEach(p -> b.append(Files.isDirectory(p) ? "[DIR] " : "[FILE] ")
                    .append(root.relativize(p)).append("\n"));
        }
        return b.toString();
    }

    String read(String relative) throws IOException {
        Path p = safe(relative);
        if (Files.size(p) > 1_000_000) throw new IOException("File is too large for the AI reader.");
        return Files.readString(p);
    }

    void mkdir(String relative) throws IOException {
        Files.createDirectories(safe(relative));
    }

    void write(String relative, String content) throws IOException {
        Path p = safe(relative);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    void delete(String relative) throws IOException {
        Path p = safe(relative);
        if (p.equals(root)) throw new IOException("Cannot delete the project root.");
        Files.deleteIfExists(p);
    }
}

class Agent {
    interface Reporter { void report(String text); }
    private final Project project;
    private final AIClient ai = new AIClient();

    Agent(Project project) { this.project = project; }

    String run(String request, Reporter reporter) throws Exception {
        String system = """
        You are Ajudante, a coding agent inside a local project.
        You help the user build software. You have tools that operate ONLY inside the selected project.
        Do not claim a file was changed unless the tool actually changed it.
        Prefer inspecting existing files before replacing them.
        Work in small, verifiable steps.
        When the user asks you to create an application, create the needed folders and source files.
        Explain what you changed after finishing.
        """;
        List<Map<String,String>> messages = new ArrayList<>();
        messages.add(msg("system", system));
        messages.add(msg("user", request));

        for (int turn = 0; turn < 12; turn++) {
            AIClient.Response r = ai.chat(messages, toolDefinitions());
            if (r.toolCalls().isEmpty()) return r.text();

            messages.add(msg("assistant", "I am using the requested project tools."));
            for (AIClient.ToolCall tc : r.toolCalls()) {
                reporter.report("[" + tc.name() + "]\n");
                String result = execute(tc.name(), tc.arguments());
                messages.add(msg("user", "Tool result from " + tc.name() + ":\n" + result));
            }
        }
        return "I stopped after 12 agent steps. The project may need another instruction to continue.";
    }

    private Map<String,String> msg(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    private String execute(String name, String args) {
        try {
            Map<String,String> a = Json.flat(args);
            return switch (name) {
                case "list_files" -> project.list(a.getOrDefault("path", "."));
                case "read_file" -> project.read(a.get("path"));
                case "create_folder" -> { project.mkdir(a.get("path")); yield "Created folder: " + a.get("path"); }
                case "write_file" -> { project.write(a.get("path"), a.getOrDefault("content", "")); yield "Wrote: " + a.get("path"); }
                case "delete_path" -> { project.delete(a.get("path")); yield "Deleted: " + a.get("path"); }
                default -> "Unknown tool: " + name;
            };
        } catch (Exception e) { return "Tool error: " + e.getMessage(); }
    }

    private List<Map<String,Object>> toolDefinitions() {
        List<Map<String,Object>> t = new ArrayList<>();
        t.add(tool("list_files", "List project files", schema("path", "string")));
        t.add(tool("read_file", "Read a UTF-8 text file", schema("path", "string")));
        t.add(tool("create_folder", "Create a folder", schema("path", "string")));
        t.add(tool("write_file", "Create or replace a UTF-8 text file", schema("path", "string", "content", "string")));
        t.add(tool("delete_path", "Delete a file or empty folder", schema("path", "string")));
        return t;
    }

    private Map<String,Object> tool(String name, String desc, Map<String,Object> schema) {
        return Map.of("type","function","function",Map.of(
                "name",name,"description",desc,
                "parameters",Map.of("type","object","properties",schema,"required",schema.keySet().stream().toList())));
    }

    private Map<String,Object> schema(String... x) {
        Map<String,Object> m = new LinkedHashMap<>();
        for (int i=0;i<x.length;i+=2) m.put(x[i], Map.of("type", x[i+1]));
        return m;
    }
}

class AIClient {
    record ToolCall(String name, String arguments) {}
    record Response(String text, List<ToolCall> toolCalls, String rawAssistant) {}

    Response chat(List<Map<String,String>> messages, List<Map<String,Object>> tools) throws Exception {
        String base = Settings.get("base", "http://localhost:11434/v1").replaceAll("/+$", "");
        String model = Settings.get("model", "llama3.2");
        String key = Settings.get("key", "");

        StringBuilder body = new StringBuilder();
        body.append("{\"model\":").append(Json.quote(model)).append(",\"messages\":[");
        for (int i=0;i<messages.size();i++) {
            if(i>0) body.append(",");
            body.append("{\"role\":").append(Json.quote(messages.get(i).get("role")))
                .append(",\"content\":").append(Json.quote(messages.get(i).getOrDefault("content",""))).append("}");
        }
        body.append("],\"temperature\":0.2,\"tools\":[");
        for(int i=0;i<tools.size();i++) { if(i>0) body.append(","); body.append(Json.stringify(tools.get(i))); }
        body.append("]}");

        var req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(base + "/chat/completions"))
                .header("Content-Type","application/json");
        if(!key.isBlank()) req.header("Authorization","Bearer " + key);
        var response = java.net.http.HttpClient.newHttpClient().send(
                req.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        if(response.statusCode() >= 300) throw new IOException("AI API HTTP " + response.statusCode() + ": " + response.body());
        String raw = response.body();
        String assistant = Json.stringAfter(raw, "\"message\":{");
        String content = Json.stringValue(raw, "content");
        List<ToolCall> calls = Json.toolCalls(raw);
        return new Response(content == null ? "" : content, calls, assistant == null ? raw : assistant);
    }
}

class Settings {
    private static final Preferences P = Preferences.userNodeForPackage(Settings.class);
    static String get(String k, String d) { return P.get(k, d); }
    static void put(String k, String v) { P.put(k, v); }
}

class Json {
    static String quote(String s) {
        if(s == null) return "null";
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"")
                .replace("\n","\\n").replace("\r","\\r").replace("\t","\\t") + "\"";
    }

    static String stringify(Object o) {
        if(o == null) return "null";
        if(o instanceof String s) return quote(s);
        if(o instanceof Map<?,?> m) {
            StringBuilder b=new StringBuilder("{"); boolean first=true;
            for(var e:m.entrySet()){if(!first)b.append(",");first=false;b.append(quote(e.getKey().toString())).append(":").append(stringify(e.getValue()));}
            return b.append("}").toString();
        }
        if(o instanceof List<?> l){StringBuilder b=new StringBuilder("[");boolean f=true;for(var x:l){if(!f)b.append(",");f=false;b.append(stringify(x));}return b.append("]").toString();}
        return o.toString();
    }

    static String stringValue(String json, String key) {
        String needle = quote(key) + ":";
        int i=json.indexOf(needle); if(i<0)return null; i+=needle.length();
        while(i<json.length() && Character.isWhitespace(json.charAt(i)))i++;
        if(i>=json.length()||json.charAt(i)!='\"')return null;
        StringBuilder b=new StringBuilder(); boolean esc=false;
        for(i++;i<json.length();i++){char c=json.charAt(i);if(esc){b.append(switch(c){case 'n'->'\n';case 'r'->'\r';case 't'->'\t';default->c;});esc=false;}else if(c=='\\')esc=true;else if(c=='\"')break;else b.append(c);}
        return b.toString();
    }

    static String stringAfter(String json, String marker) {
        int i=json.indexOf(marker); return i<0?null:json.substring(i);
    }

    static Map<String,String> flat(String json) {
        Map<String,String> out=new LinkedHashMap<>();
        if(json==null)return out;
        for(String k:List.of("path","content")){
            String v=stringValue(json,k); if(v!=null)out.put(k,v);
        }
        return out;
    }

    static List<AIClient.ToolCall> toolCalls(String json) {
        List<AIClient.ToolCall> out=new ArrayList<>();
        int pos=0;
        while((pos=json.indexOf("\"function\":",pos))>=0){
            String part=json.substring(pos);
            String name=stringValue(part,"name");
            String args=stringValue(part,"arguments");
            if(name!=null)out.add(new AIClient.ToolCall(name,args==null?"{}":args));
            pos+=11;
        }
        return out;
    }
}
