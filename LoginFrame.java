package com.mycompany.chat;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.net.Socket;
import org.mindrot.jbcrypt.BCrypt;

public class LoginFrame extends JFrame {

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextField portField;
    private JButton loginButton;
    private JButton registerButton;

    public LoginFrame() {
        // Thiết lập tiêu đề
        setTitle("Chào mừng bạn đến với ChatOnlineDB");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Căn giữa màn hình

        // Sử dụng JPanel chính với BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // Padding lớn hơn

        // Panel nội dung ở giữa với BoxLayout theo chiều dọc
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // Tiêu đề
        JLabel titleLabel = new JLabel("Chào mừng bạn đến với Chat Online DB", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(titleLabel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        // Trường "Tên đăng nhập"
        JLabel usernameLabel = new JLabel("Tên đăng nhập:");
        usernameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(usernameLabel);
        usernameField = new JTextField(20); // Độ rộng cố định
        usernameField.setMaximumSize(new Dimension(280, usernameField.getPreferredSize().height));
        usernameField.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(usernameField);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Trường "Mật khẩu"
        JLabel passwordLabel = new JLabel("Mật khẩu:");
        passwordLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(passwordLabel);
        passwordField = new JPasswordField(20);
        passwordField.setMaximumSize(new Dimension(280, passwordField.getPreferredSize().height));
        passwordField.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(passwordField);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Trường "Port"
        JLabel portLabel = new JLabel("Port:");
        portLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(portLabel);
        portField = new JTextField(20);
        portField.setMaximumSize(new Dimension(280, portField.getPreferredSize().height));
        portField.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(portField);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        // Nút "Đăng nhập"
        loginButton = new JButton("Đăng nhập");
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.setBackground(new Color(50, 205, 50)); // Màu xanh lá
        loginButton.setForeground(Color.WHITE); // Chữ trắng
        loginButton.setMaximumSize(new Dimension(280, loginButton.getPreferredSize().height));
        contentPanel.add(loginButton);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Thêm contentPanel vào giữa mainPanel
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        // Panel cho nút "Đăng ký" ở dưới cùng, căn giữa
        JPanel registerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        registerButton = new JButton("Đăng ký tài khoản mới");
        registerButton.setBorderPainted(false); // Không có viền
        registerButton.setContentAreaFilled(false); // Không có nền
        registerButton.setForeground(Color.BLUE); // Chữ màu xanh để nổi bật hơn
        registerPanel.add(registerButton);
        mainPanel.add(registerPanel, BorderLayout.SOUTH);

        // Thêm mainPanel vào JFrame
        add(mainPanel);

        // Tự động điều chỉnh kích thước và đặt kích thước tối thiểu
        pack();
        setMinimumSize(new Dimension(350, 300)); // Tăng kích thước tối thiểu cho thoải mái hơn

        // Giữ nguyên chức năng của các nút
        loginButton.addActionListener(e -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            int port;
            try {
                port = Integer.parseInt(portField.getText());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Port không hợp lệ!");
                return;
            }
            if (!isPortValid(port)) {
                JOptionPane.showMessageDialog(this, "Port không khả dụng!");
                return;
            }
            if (username.length() < 3 || password.length() < 6) {
                JOptionPane.showMessageDialog(this, "Tên đăng nhập hoặc mật khẩu không hợp lệ!");
                return;
            }
            if (authenticate(username, password)) {
                JOptionPane.showMessageDialog(this, "Đăng nhập thành công!");
                ChatFrame chatFrame = new ChatFrame(username, port);
                chatFrame.setVisible(true);
                dispose();
            }
        });

        registerButton.addActionListener(e -> {
            RegisterFrame registerFrame = new RegisterFrame();
            registerFrame.setVisible(true);
            dispose();
        });
    }

    private boolean authenticate(String username, String password) {
        try (Connection connection = DBConnection.getConnection()) {
            String query = "SELECT password, is_logged_in FROM users WHERE username = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, username);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        String hashedPassword = resultSet.getString("password");
                        boolean isLoggedIn = resultSet.getBoolean("is_logged_in");
                        if (isLoggedIn) {
                            JOptionPane.showMessageDialog(this, "Tài khoản này đã được đăng nhập ở client khác!");
                            return false;
                        }
                        if (BCrypt.checkpw(password, hashedPassword)) {
                            updateLoginStatus(username, true); // Cập nhật trạng thái đăng nhập
                            return true;
                        }
                        return false;
                    }
                    JOptionPane.showMessageDialog(this, "Tên đăng nhập hoặc mật khẩu không đúng!");
                    return false;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
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

    private boolean isPortValid(int port) {
        try (Socket socket = new Socket("localhost", port)) {
            return true;
        } catch (Exception e) {
            return false;
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
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }
}
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables

