package com.mycompany.chat;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.Desktop;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ChatFrame extends JFrame {

    private enum ChatMode {
        PUBLIC, PRIVATE, GROUP
    }
    private ChatMode chatMode = ChatMode.PUBLIC;
    private JTextPane chatPane;
    private JTextField messageField;
    private JButton sendButton, emojiButton, fileButton, backToPublicButton, exitButton, createGroupButton, addMemberButton;
    private JList<String> onlineUsersList, groupList;
    private DefaultListModel<String> onlineUsersModel, groupModel;
    private DataOutputStream dataOut;
    private DataInputStream dataIn;
    private String selectedUser, selectedGroup, username;
    private ConcurrentHashMap<String, StringBuilder> privateMessages;
    private ConcurrentHashMap<String, StringBuilder> groupMessages;
    private StringBuilder publicMessages;
    private Map<Integer, File> fileMessagePositions;
    private volatile boolean isRunning = true;
    private Socket socket;
    private int lastDisplayedPublicMessageLength = 0;

    private StyledDocument publicDoc;
    private ConcurrentHashMap<String, StyledDocument> privateDocs;
    private ConcurrentHashMap<String, StyledDocument> groupDocs;

    public ChatFrame(String username, int port) {
        this.username = username;
        this.privateMessages = new ConcurrentHashMap<>();
        this.groupMessages = new ConcurrentHashMap<>();
        this.publicMessages = new StringBuilder();
        this.fileMessagePositions = new HashMap<>();
        this.privateDocs = new ConcurrentHashMap<>();
        this.groupDocs = new ConcurrentHashMap<>();
        setTitle("Chat - " + username);
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        publicDoc = chatPane.getStyledDocument();
        add(new JScrollPane(chatPane), BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        panel.add(messageField, BorderLayout.CENTER);
        sendButton = new JButton("Gửi");
        panel.add(sendButton, BorderLayout.EAST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        emojiButton = new JButton("😊");
        buttonPanel.add(emojiButton);
        fileButton = new JButton("📁");
        buttonPanel.add(fileButton);
        backToPublicButton = new JButton("Quay lại Chat Public");
        backToPublicButton.setVisible(false);
        buttonPanel.add(backToPublicButton);
        createGroupButton = new JButton("Tạo nhóm");
        buttonPanel.add(createGroupButton);
        addMemberButton = new JButton("Thêm thành viên");
        addMemberButton.setVisible(false);
        buttonPanel.add(addMemberButton);
        panel.add(buttonPanel, BorderLayout.WEST);
        add(panel, BorderLayout.SOUTH);

        JPanel onlineUsersPanel = new JPanel(new BorderLayout());
        onlineUsersPanel.setPreferredSize(new Dimension(200, 0));
        JLabel usernameLabel = new JLabel("Tên tài khoản: " + username);
        JPanel userInfoPanel = new JPanel();
        userInfoPanel.setLayout(new BoxLayout(userInfoPanel, BoxLayout.Y_AXIS));
        userInfoPanel.add(usernameLabel);
        userInfoPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        JLabel onlineUsersLabel = new JLabel("Danh sách online");
        userInfoPanel.add(onlineUsersLabel);
        onlineUsersModel = new DefaultListModel<>();
        onlineUsersList = new JList<>(onlineUsersModel);
        onlineUsersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userInfoPanel.add(new JScrollPane(onlineUsersList));

        JLabel groupLabel = new JLabel("Danh sách nhóm");
        userInfoPanel.add(groupLabel);
        groupModel = new DefaultListModel<>();
        groupList = new JList<>(groupModel);
        groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userInfoPanel.add(new JScrollPane(groupList));

        onlineUsersPanel.add(userInfoPanel, BorderLayout.CENTER);

        exitButton = new JButton("Thoát");
        JPanel exitPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        exitPanel.add(exitButton);
        onlineUsersPanel.add(exitPanel, BorderLayout.SOUTH);

        add(onlineUsersPanel, BorderLayout.EAST);

        backToPublicButton.addActionListener(e -> {
            selectedUser = null;
            selectedGroup = null;
            updateChatMode();
            onlineUsersList.clearSelection();
            groupList.clearSelection();
        });

        onlineUsersList.addListSelectionListener(e -> {
    if (!e.getValueIsAdjusting()) {
        String selected = onlineUsersList.getSelectedValue();
        if (selected != null && !selected.equals(username)) {
            selectedUser = selected;
            selectedGroup = null;
            groupList.clearSelection(); 
            updateChatMode();
        } else {
            selectedUser = null;
            updateChatMode();
            onlineUsersList.clearSelection();
        }
    }
});

       groupList.addListSelectionListener(e -> {
    if (!e.getValueIsAdjusting()) {
        String selected = groupList.getSelectedValue();
        if (selected != null) {
            selectedGroup = selected.replace("Nhóm ", "");
            selectedUser = null;
            onlineUsersList.clearSelection(); 
            updateChatMode();
        } else {
            selectedGroup = null;
            updateChatMode();
            groupList.clearSelection();
        }
    }
});

        Action sendMessageAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String message = messageField.getText();
                if (!message.isEmpty()) {
                    try {
                        if (selectedGroup != null) {
                            dataOut.writeUTF("GROUP_TEXT:" + selectedGroup + " " + message);
                            dataOut.flush();
                        } else if (selectedUser != null) {
                            dataOut.writeUTF("TEXT:@" + selectedUser + " " + message);
                            dataOut.flush();
                        } else {
                            dataOut.writeUTF("TEXT:" + message);
                            dataOut.flush();
                        }
                        messageField.setText("");
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(ChatFrame.this, "Lỗi gửi tin nhắn: " + ex.getMessage());
                    }
                }
            }
        };

        sendButton.addActionListener(sendMessageAction);
        messageField.addActionListener(sendMessageAction);

        emojiButton.addActionListener(e -> {
    JDialog emojiDialog = new JDialog(ChatFrame.this, "Chọn Emoji", true);
    emojiDialog.setLayout(new GridLayout(0, 5, 0, 0));
    emojiDialog.setSize(400, 300);

    // Danh sách emoji mẫu (có thể mở rộng)
    String[] emojis = {"😊", "😂", "😍", "😢", "😎", "😡", "👍", "👎", "🙏", "👏",
                       "😀", "😃", "😄", "😅", "😆", "😇", "😈", "😉", "😊", "😋",
                       "😌", "😍", "😎", "😏", "😐", "😑", "😒", "😓", "😔", "😕"};

    for (String emoji : emojis) {
        JButton emojiBtn = new JButton(emoji);
        emojiBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        emojiBtn.addActionListener(evt -> {
            messageField.setText(messageField.getText() + emoji);
            emojiDialog.dispose();
        });
        emojiDialog.add(emojiBtn);
    }

    emojiDialog.setLocationRelativeTo(ChatFrame.this);
    emojiDialog.setVisible(true);
});

        fileButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                sendFile(fileChooser.getSelectedFile());
            }
        });

        createGroupButton.addActionListener(e -> {
            String groupName = JOptionPane.showInputDialog(this, "Nhập tên nhóm:", "Tạo nhóm", JOptionPane.PLAIN_MESSAGE);
            if (groupName != null && !groupName.isEmpty()) {
                try {
                    selectedGroup = groupName;
                    dataOut.writeUTF("CREATE_GROUP:" + groupName);
                    dataOut.flush();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Lỗi tạo nhóm: " + ex.getMessage());
                }
            }
        });

        addMemberButton.addActionListener(e -> {
            showAddMemberDialog();
        });

        exitButton.addActionListener(e -> {
            isRunning = false;
            updateLoginStatus(username, false);
            closeConnection();
            dispose();
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });

        chatPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                try {
                    int pos = chatPane.viewToModel2D(evt.getPoint());
                    for (Map.Entry<Integer, File> entry : fileMessagePositions.entrySet()) {
                        int startPos = entry.getKey();
                        StyledDocument doc = chatPane.getStyledDocument();
                        int messageEnd = doc.getText(startPos, doc.getLength() - startPos).indexOf("\n") + startPos + 1;
                        if (pos >= startPos && pos < messageEnd) {
                            Desktop.getDesktop().open(entry.getValue());
                            break;
                        }
                    }
                } catch (IOException | BadLocationException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(ChatFrame.this, "Không thể mở file: " + e.getMessage());
                }
            }
        });

        try {
            socket = new Socket("localhost", port);
            dataOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            dataIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            dataOut.writeUTF(username);
            dataOut.flush();

            SwingUtilities.invokeLater(() -> chatPane.setText(""));

            new Thread(() -> {
        try {
            String message;
            while (isRunning && (message = dataIn.readUTF()) != null) {
                System.out.println("Client " + username + " received: " + message);
                if (message.startsWith("ERROR:")) {
                    String errorMessage = message.substring(6);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(ChatFrame.this, errorMessage, "Lỗi", JOptionPane.ERROR_MESSAGE);
                    });
                    continue;
                }
                if (message.startsWith("SYSTEM:")) {
                    String systemMessage = message.substring(7);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(ChatFrame.this, systemMessage, "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                    });
                    continue;
                }
                if (message.startsWith("TEXT:")) {
                    message = message.substring(5);
                    if (message.startsWith("ONLINE_USERS")) {
                        String userList = message.replaceFirst("ONLINE_USERS", "").trim();
                        if (!userList.isEmpty()) {
                            updateOnlineUsers(userList);
                        }
                    } else if (message.startsWith("GROUP_LIST")) {
                        String groupListStr = message.replaceFirst("GROUP_LIST", "").trim();
                        updateGroupList(groupListStr);
                    } else if (message.startsWith("(Private)")) {
                        String[] parts = message.split(" ", 3);
                        String sender = parts[1].substring(0, parts[1].length() - 1);
                        String privateMessage = parts[2];
                        String key = sender.equals(username) ? selectedUser : sender;
                        if (key != null) {
                            appendPrivateMessage(sender, key, privateMessage);
                        }
                    } else if (message.startsWith("(Group)")) {
                        String[] parts = message.split(" ", 4);
                        String groupName = parts[1];
                        String sender = parts[2].substring(0, parts[2].length() - 1);
                        String groupMessage = parts[3];
                        appendGroupMessage(groupName, sender, groupMessage);
                    } else {
                        publicMessages.append(message).append("\n");
                        if (chatMode == ChatMode.PUBLIC) {
                            boolean isOwnMessage = message.startsWith(username + ":");
                            appendMessage(publicDoc, message, isOwnMessage);
                            lastDisplayedPublicMessageLength = publicMessages.length();
                        }
                    }
                } else if (message.startsWith("FILE:")) {
                    String[] parts = message.split(":", 3);
                    String fileName = parts[1];
                    String sender = parts.length > 2 ? parts[2] : "Unknown";
                    receiveFile(fileName, sender);
                } else if (message.startsWith("GROUP_FILE:")) {
                    String[] parts = message.split(":", 4);
                    String groupName = parts[1];
                    String fileName = parts[2];
                    String sender = parts.length > 3 ? parts[3] : "Unknown";
                    receiveGroupFile(groupName, fileName, sender);
                } else if (message.startsWith("INVITE:")) {
                    String[] parts = message.split(":", 3);
                    String groupName = parts[1];
                    String inviter = parts[2];
                    SwingUtilities.invokeLater(() -> {
                        int response = JOptionPane.showConfirmDialog(
                                ChatFrame.this,
                                inviter + " mời bạn vào nhóm " + groupName + ". Bạn có đồng ý không?",
                                "Lời mời vào nhóm",
                                JOptionPane.YES_NO_OPTION
                        );
                        try {
                            if (response == JOptionPane.YES_OPTION) {
                                dataOut.writeUTF("ACCEPT_INVITE:" + groupName);
                                dataOut.flush();
                            } else {
                                dataOut.writeUTF("DECLINE_INVITE:" + groupName);
                                dataOut.flush();
                            }
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    });
                }
            }
        } catch (IOException e) {
            if (isRunning) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(ChatFrame.this, "Mất kết nối với server: " + e.getMessage());
                });
            }
        }
    }).start();
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Không thể kết nối đến server: " + e.getMessage());
        }

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                isRunning = false;
                updateLoginStatus(username, false);
                closeConnection();
            }
        });
    }

    private void closeConnection() {
        try {
            if (dataOut != null) {
                dataOut.close();
            }
            if (dataIn != null) {
                dataIn.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateOnlineUsers(String users) {
        SwingUtilities.invokeLater(() -> {
            onlineUsersModel.clear();
            String[] userList = users.split("\\s+");
            for (String user : userList) {
                if (!user.isEmpty() && !user.equals(username)) {
                    onlineUsersModel.addElement(user);
                }
            }
        });
    }

    private void updateGroupList(String groups) {
        SwingUtilities.invokeLater(() -> {
            groupModel.clear();
            if (!groups.isEmpty()) {
                String[] groupArray = groups.split("\\s+");
                for (String group : groupArray) {
                    if (!group.isEmpty()) {
                        String displayName = "Nhóm " + group;
                        groupModel.addElement(displayName);
                        System.out.println("Added group to list: " + displayName);
                        if (group.equals(selectedGroup)) {
                            groupList.setSelectedValue(displayName, true);
                        }
                    }
                }
            }
            groupList.revalidate();
            groupList.repaint();
        });
    }

    private void displayPublicMessages() {
        SwingUtilities.invokeLater(() -> {
            try {
                chatPane.setStyledDocument(publicDoc);
                if (publicMessages.length() > lastDisplayedPublicMessageLength) {
                    String newMessages = publicMessages.substring(lastDisplayedPublicMessageLength);
                    String[] messageLines = newMessages.split("\n");
                    for (String line : messageLines) {
                        if (!line.trim().isEmpty()) {
                            boolean isOwnMessage = line.startsWith(username + ":");
                            appendMessage(publicDoc, line, isOwnMessage);
                        }
                    }
                    lastDisplayedPublicMessageLength = publicMessages.length();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void displayPrivateMessages(String user) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument privateDoc = privateDocs.computeIfAbsent(user, k -> new DefaultStyledDocument());
            chatPane.setStyledDocument(privateDoc);
            StringBuilder messages = privateMessages.getOrDefault(user, new StringBuilder());
            if (privateDoc.getLength() == 0) {
                String[] messageLines = messages.toString().split("\n");
                for (String line : messageLines) {
                    if (!line.trim().isEmpty()) {
                        boolean isOwnMessage = line.startsWith(username + ":");
                        appendMessage(privateDoc, line, isOwnMessage);
                    }
                }
            }
        });
    }

    private void displayGroupMessages(String group) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument groupDoc = groupDocs.computeIfAbsent(group, k -> new DefaultStyledDocument());
            chatPane.setStyledDocument(groupDoc);
            StringBuilder messages = groupMessages.getOrDefault(group, new StringBuilder());
            if (groupDoc.getLength() == 0) {
                String[] messageLines = messages.toString().split("\n");
                for (String line : messageLines) {
                    if (!line.trim().isEmpty()) {
                        boolean isOwnMessage = line.startsWith(username + ":");
                        appendMessage(groupDoc, line, isOwnMessage);
                    }
                }
            }
        });
    }

    private void updateChatMode() {
        if (selectedGroup != null) {
            chatMode = ChatMode.GROUP;
            backToPublicButton.setVisible(true);
            addMemberButton.setVisible(true);
            createGroupButton.setVisible(false);
            displayGroupMessages(selectedGroup);
        } else if (selectedUser != null) {
            chatMode = ChatMode.PRIVATE;
            backToPublicButton.setVisible(true);
            addMemberButton.setVisible(false);
            createGroupButton.setVisible(true);
            displayPrivateMessages(selectedUser);
        } else {
            chatMode = ChatMode.PUBLIC;
            backToPublicButton.setVisible(false);
            addMemberButton.setVisible(false);
            createGroupButton.setVisible(true);
            displayPublicMessages();
        }
    }

    private void appendPrivateMessage(String sender, String key, String message) {
        StringBuilder messages = privateMessages.computeIfAbsent(key, k -> new StringBuilder());
        messages.append(sender).append(": ").append(message).append("\n");
        if (chatMode == ChatMode.PRIVATE && selectedUser != null && selectedUser.equals(key)) {
            StyledDocument privateDoc = privateDocs.get(key);
            boolean isOwnMessage = sender.equals(username);
            appendMessage(privateDoc, sender + ": " + message, isOwnMessage);
        }
    }

    private void appendGroupMessage(String group, String sender, String message) {
        StringBuilder messages = groupMessages.computeIfAbsent(group, k -> new StringBuilder());
        String newMessage = sender + ": " + message + "\n";
        if (!messages.toString().contains(newMessage)) {
            messages.append(newMessage);
        }
        if (chatMode == ChatMode.GROUP && selectedGroup != null && selectedGroup.equals(group)) {
            StyledDocument groupDoc = groupDocs.computeIfAbsent(group, k -> new DefaultStyledDocument());
            boolean isOwnMessage = sender.equals(username);
            appendMessage(groupDoc, sender + ": " + message, isOwnMessage);
        }
    }

    private synchronized void appendMessage(StyledDocument doc, String message, boolean isOwnMessage) {
    if (doc == null || message == null || message.isEmpty()) {
        return;
    }
    SwingUtilities.invokeLater(() -> {
        SimpleAttributeSet align = new SimpleAttributeSet();
        StyleConstants.setAlignment(align, isOwnMessage ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);
        try {
            int length = doc.getLength();
            doc.insertString(length, message + "\n", null);
            doc.setParagraphAttributes(length, message.length() + 1, align, false);
            if (doc.getDefaultRootElement().getElementCount() > 100) {
                int endOffset = doc.getDefaultRootElement().getElement(0).getEndOffset();
                if (endOffset > 0) {
                    doc.remove(0, endOffset);
                }
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    });
}

    private void appendFileMessage(String message, boolean isOwnMessage, File file) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = chatPane.getStyledDocument();
            SimpleAttributeSet align = new SimpleAttributeSet();
            StyleConstants.setAlignment(align, isOwnMessage ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);
            SimpleAttributeSet style = new SimpleAttributeSet();
            StyleConstants.setForeground(style, Color.BLUE);
            try {
                int length = doc.getLength();
                doc.insertString(length, message + "\n", style);
                doc.setParagraphAttributes(length, message.length() + 1, align, false);
                fileMessagePositions.put(length, file);
                if (doc.getDefaultRootElement().getElementCount() > 100) {
                    doc.remove(0, doc.getDefaultRootElement().getElement(0).getEndOffset());
                }
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private void sendFile(File file) {
        try {
            if (selectedGroup != null) {
                // Gửi file trong nhóm
                dataOut.writeUTF("GROUP_FILE:" + selectedGroup + ":" + file.getName());
            } else {
                // Gửi file công khai hoặc riêng tư
                dataOut.writeUTF("FILE:" + file.getName());
            }
            try (FileInputStream fis = new FileInputStream(file)) {
                dataOut.writeLong(file.length());
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dataOut.write(buffer, 0, bytesRead);
                }
                dataOut.flush();
            }
            appendFileMessage("Đã gửi tệp: " + file.getName() + " (Nhấp để mở)", true, file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void receiveFile(String fileName, String sender) {
        try {
            File file = new File("downloaded_files/" + fileName);
            file.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                long fileSize = dataIn.readLong();
                byte[] buffer = new byte[4096];
                long totalBytesRead = 0;
                int bytesRead;
                while (totalBytesRead < fileSize && (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
            }
            boolean isOwnMessage = sender.equals(username);
            String message = isOwnMessage ? "Đã gửi tệp: " + fileName : sender + " đã gửi tệp: " + fileName;
            appendFileMessage(message + " (Nhấp để mở)", isOwnMessage, file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void receiveGroupFile(String groupName, String fileName, String sender) {
        try {
            File file = new File("downloaded_files/" + fileName);
            file.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                long fileSize = dataIn.readLong();
                byte[] buffer = new byte[4096];
                long totalBytesRead = 0;
                int bytesRead;
                while (totalBytesRead < fileSize && (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
            }
            boolean isOwnMessage = sender.equals(username);
            String message = isOwnMessage ? "Đã gửi tệp: " + fileName : sender + " đã gửi tệp: " + fileName;
            if (chatMode == ChatMode.GROUP && selectedGroup != null && selectedGroup.equals(groupName)) {
                appendFileMessage(message + " (Nhấp để mở)", isOwnMessage, file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAddMemberDialog() {
        int size = onlineUsersModel.getSize();
        String[] onlineUsers = new String[size];
        for (int i = 0; i < size; i++) {
            onlineUsers[i] = onlineUsersModel.getElementAt(i);
        }
        if (onlineUsers.length == 0) {
            JOptionPane.showMessageDialog(this, "Không có người dùng online để mời!");
            return;
        }
        String selectedUser = (String) JOptionPane.showInputDialog(
                this,
                "Chọn người dùng để mời:",
                "Thêm thành viên",
                JOptionPane.PLAIN_MESSAGE,
                null,
                onlineUsers,
                onlineUsers[0]
        );
        if (selectedUser != null) {
            try {
                dataOut.writeUTF("ADD_MEMBER:" + selectedGroup + " " + selectedUser);
                dataOut.flush();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void updateLoginStatus(String username, boolean status) {
        try (Connection connection = DBConnection.getConnection()) {
            String query = "UPDATE users SET is_logged_in = ? WHERE username = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setBoolean(1, status);
                statement.setString(2, username);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
    public static void main(String args[]) {
        System.setProperty("file.encoding", "UTF-8");
        SwingUtilities.invokeLater(() -> {
            ChatFrame chatFrame = new ChatFrame("User", 12345);
            chatFrame.setVisible(true);
        });
    }
}
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables

