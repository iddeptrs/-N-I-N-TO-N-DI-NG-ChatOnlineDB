package com.mycompany.chat;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ServerFrame extends JFrame {
    private JTextArea serverLogs;
    private JTextField portField;
    private JButton startButton, stopButton;
    private ServerSocket serverSocket;
    private List<ClientHandler> clients;
    public static int serverPort;
    public static boolean isServerRunning = false;

    public ServerFrame() {
        setTitle("Server Console");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        serverLogs = new JTextArea();
        serverLogs.setEditable(false);
        add(new JScrollPane(serverLogs), BorderLayout.CENTER);

        JPanel panel = new JPanel(new FlowLayout());
        panel.add(new JLabel("Mã Port:"));
        portField = new JTextField(10);
        panel.add(portField);

        startButton = new JButton("KHỞI ĐỘNG SERVER");
        panel.add(startButton);
        stopButton = new JButton("ĐÓNG SERVER");
        stopButton.setEnabled(false);
        panel.add(stopButton);
        add(panel, BorderLayout.NORTH);

        startButton.addActionListener(e -> startServer(Integer.parseInt(portField.getText())));
        stopButton.addActionListener(e -> stopServer());

        clients = Collections.synchronizedList(new ArrayList<>());
    }

    private void startServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            serverPort = port;
            isServerRunning = true;
            serverLogs.append("Server started on port: " + port + "\n");
            startButton.setEnabled(false);
            stopButton.setEnabled(true);

            new Thread(() -> {
                while (isServerRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        ClientHandler clientHandler = new ClientHandler(clientSocket, clients);
                        clients.add(clientHandler);
                        clientHandler.start();
                        serverLogs.append("New client connected: " + clientSocket.getInetAddress() + "\n");
                    } catch (IOException e) {
                        if (isServerRunning) e.printStackTrace();
                    }
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopServer() {
        try {
            isServerRunning = false;
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    client.closeConnection();
                }
                clients.clear();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            serverLogs.append("Server stopped.\n");
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        } catch (IOException e) {
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
        SwingUtilities.invokeLater(() -> {
            ServerFrame serverFrame = new ServerFrame();
            serverFrame.setVisible(true);
        });
    }
}
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables

