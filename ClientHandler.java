package com.mycompany.chat;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ClientHandler extends Thread {
    private final Socket clientSocket;
    private final List<ClientHandler> clients;
    private DataOutputStream dataOut;
    private DataInputStream dataIn;
    private String username;

    private static class Group {
        String name;
        String admin;
        List<String> members;

        Group(String name, String admin) {
            this.name = name;
            this.admin = admin;
            this.members = new ArrayList<>();
            this.members.add(admin);
        }
    }
    private static final List<Group> groups = new ArrayList<>();

    public ClientHandler(Socket socket, List<ClientHandler> clients) {
        this.clientSocket = socket;
        this.clients = clients;
        try {
            this.dataIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            this.dataOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        } catch (IOException e) {
            e.printStackTrace();
            closeConnection();
        }
    }

    @Override
    public void run() {
        try {
            this.username = dataIn.readUTF();
            if (this.username == null || this.username.isEmpty()) {
                dataOut.writeUTF("ERROR:Tên người dùng không hợp lệ.");
                dataOut.flush();
                return;
            }
            System.out.println("User connected: " + username);
            broadcast("TEXT:" + username + " đã tham gia phòng chat.");
            updateOnlineUsers();
            updateGroupList();

            String message;
            while ((message = dataIn.readUTF()) != null) {
                System.out.println("Received from " + username + ": " + message);
                if (!ServerFrame.isServerRunning) {
                    dataOut.writeUTF("ERROR:Server đã đóng.");
                    dataOut.flush();
                    continue;
                }

                if (message.startsWith("ERROR:")) {
                    dataOut.writeUTF(message);
                    dataOut.flush();
                    continue;
                }

                if (message.startsWith("TEXT:")) {
                    message = message.substring(5);
                    if (message.startsWith("@")) {
                        String[] parts = message.split(" ", 2);
                        if (parts.length < 2) continue;
                        String target = parts[0].substring(1);
                        String content = parts[1];
                        sendPrivateMessage(target, content);
                    } else {
                        broadcast("TEXT:" + username + ": " + message);
                    }
                } else if (message.startsWith("FILE:")) {
                    String fileName = message.substring(5);
                    receiveFile(fileName);
                } else if (message.startsWith("GROUP_FILE:")) {
                    String[] parts = message.substring(11).split(":", 2);
                    if (parts.length < 2) continue;
                    String groupName = parts[0];
                    String fileName = parts[1];
                    receiveAndSendGroupFile(groupName, fileName);
                } else if (message.startsWith("CREATE_GROUP:")) {
                    String groupName = message.substring(13);
                    createGroup(groupName);
                } else if (message.startsWith("GROUP_TEXT:")) {
                    String[] parts = message.substring(11).split(" ", 2);
                    if (parts.length < 2) continue;
                    String groupName = parts[0];
                    String content = parts[1];
                    sendGroupMessage(groupName, content);
                } else if (message.startsWith("ADD_MEMBER:")) {
                    String[] parts = message.substring(11).split(" ", 2);
                    if (parts.length < 2) continue;
                    String groupName = parts[0];
                    String targetUser = parts[1];
                    inviteToGroup(groupName, targetUser);
                } else if (message.startsWith("ACCEPT_INVITE:")) {
                    String groupName = message.substring(14);
                    handleInviteResponse(groupName, true);
                } else if (message.startsWith("DECLINE_INVITE:")) {
                    String groupName = message.substring(15);
                    handleInviteResponse(groupName, false);
                }
            }
        } catch (IOException e) {
            System.out.println("Client " + username + " disconnected: " + e.getMessage());
        } finally {
            clients.remove(this);
            if (username != null && !username.isEmpty()) {
                broadcast("TEXT:" + username + " đã rời khỏi phòng chat.");
                updateOnlineUsers();
                updateLoginStatus(username, false);
            }
            closeConnection();
        }
    }

    private void broadcast(String message) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                try {
                    client.dataOut.writeUTF(message);
                    client.dataOut.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendPrivateMessage(String targetUser, String message) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.username.equals(targetUser) || client.username.equals(username)) {
                    try {
                        client.dataOut.writeUTF("TEXT:(Private) " + username + ": " + message);
                        client.dataOut.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void sendGroupMessage(String groupName, String message) {
        synchronized (groups) {
            Group group = groups.stream().filter(g -> g.name.equals(groupName)).findFirst().orElse(null);
            if (group != null) {
                if (!group.members.contains(username)) {
                    try {
                        dataOut.writeUTF("ERROR:Bạn không phải thành viên của nhóm " + groupName);
                        dataOut.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return;
                }
                synchronized (clients) {
                    for (ClientHandler client : clients) {
                        if (group.members.contains(client.username)) {
                            try {
                                client.dataOut.writeUTF("TEXT:(Group) " + groupName + " " + username + ": " + message);
                                client.dataOut.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } else {
                try {
                    dataOut.writeUTF("ERROR:Nhóm " + groupName + " không tồn tại");
                    dataOut.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendToClient(ClientHandler client, String message) {
        try {
            client.dataOut.writeUTF(message);
            client.dataOut.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateOnlineUsers() {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                updateOnlineUsersForClient(client);
            }
        }
    }

    private void updateOnlineUsersForClient(ClientHandler client) {
        StringBuilder onlineUsers = new StringBuilder("TEXT:ONLINE_USERS");
        synchronized (clients) {
            for (ClientHandler c : clients) {
                onlineUsers.append(" ").append(c.username);
            }
        }
        sendToClient(client, onlineUsers.toString().trim());
    }

    private void updateGroupList() {
        StringBuilder groupList = new StringBuilder("TEXT:GROUP_LIST");
        synchronized (groups) {
            for (Group group : groups) {
                groupList.append(" ").append(group.name);
            }
        }
        String message = groupList.toString().trim();
        synchronized (clients) {
            for (ClientHandler client : clients) {
                sendToClient(client, message);
            }
        }
    }

    private void createGroup(String groupName) {
        synchronized (groups) {
            if (groups.stream().noneMatch(g -> g.name.equals(groupName))) {
                Group newGroup = new Group(groupName, username);
                groups.add(newGroup);
                updateGroupList();
                sendGroupMessage(groupName, "đã tạo nhóm " + groupName);
            } else {
                try {
                    dataOut.writeUTF("ERROR:Tên nhóm đã tồn tại!");
                    dataOut.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void inviteToGroup(String groupName, String targetUser) {
        synchronized (groups) {
            Group group = groups.stream().filter(g -> g.name.equals(groupName)).findFirst().orElse(null);
            if (group != null && group.admin.equals(username)) {
                if (group.members.contains(targetUser)) {
                    try {
                        dataOut.writeUTF("ERROR:Người dùng " + targetUser + " đã ở trong nhóm!");
                        dataOut.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return;
                }
                synchronized (clients) {
                    for (ClientHandler client : clients) {
                        if (client.username.equals(targetUser)) {
                            try {
                                client.dataOut.writeUTF("INVITE:" + groupName + ":" + username);
                                client.dataOut.flush();
                                dataOut.writeUTF("SYSTEM:Đã gửi lời mời đến " + targetUser);
                                dataOut.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        }
                    }
                }
            } else {
                try {
                    dataOut.writeUTF("ERROR:Bạn không có quyền mời thành viên!");
                    dataOut.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleInviteResponse(String groupName, boolean accepted) {
        synchronized (groups) {
            Group group = groups.stream().filter(g -> g.name.equals(groupName)).findFirst().orElse(null);
            if (group != null && accepted && !group.members.contains(username)) {
                group.members.add(username);
                sendGroupMessage(groupName, username + " đã tham gia nhóm.");
            }
        }
    }

    private void receiveFile(String fileName) {
        try {
            File file = new File("received_files/" + fileName);
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
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    if (!client.username.equals(this.username)) { // Không gửi lại cho người gửi
                        client.sendFile(file, this.username);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void receiveAndSendGroupFile(String groupName, String fileName) {
        synchronized (groups) {
            Group group = groups.stream().filter(g -> g.name.equals(groupName)).findFirst().orElse(null);
            if (group != null) {
                if (!group.members.contains(username)) {
                    try {
                        dataOut.writeUTF("ERROR:Bạn không phải thành viên của nhóm " + groupName);
                        dataOut.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return;
                }
                try {
                    File file = new File("received_files/" + fileName);
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
                    synchronized (clients) {
                        for (ClientHandler client : clients) {
                            if (group.members.contains(client.username) && !client.username.equals(this.username)) { // Không gửi lại cho người gửi
                                client.sendGroupFile(file, this.username, groupName);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    dataOut.writeUTF("ERROR:Nhóm " + groupName + " không tồn tại");
                    dataOut.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendFile(File file, String senderUsername) {
        try {
            dataOut.writeUTF("FILE:" + file.getName() + ":" + senderUsername);
            try (FileInputStream fis = new FileInputStream(file)) {
                dataOut.writeLong(file.length());
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dataOut.write(buffer, 0, bytesRead);
                }
                dataOut.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendGroupFile(File file, String senderUsername, String groupName) {
        try {
            dataOut.writeUTF("GROUP_FILE:" + groupName + ":" + file.getName() + ":" + senderUsername);
            try (FileInputStream fis = new FileInputStream(file)) {
                dataOut.writeLong(file.length());
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dataOut.write(buffer, 0, bytesRead);
                }
                dataOut.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
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

    public void closeConnection() {
        try {
            if (dataIn != null) dataIn.close();
            if (dataOut != null) dataOut.close();
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}