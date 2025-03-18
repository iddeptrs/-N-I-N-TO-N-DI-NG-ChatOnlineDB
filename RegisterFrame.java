package com.mycompany.chat;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.mindrot.jbcrypt.BCrypt;

public class RegisterFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextField emailField;
    private JButton registerButton;
    private JButton loginButton;

    public RegisterFrame() {
        // Thiết lập tiêu đề
        setTitle("Đăng ký tài khoản");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Căn giữa màn hình

        // Sử dụng JPanel chính với BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // Padding lớn hơn

        // Panel nội dung ở giữa với BoxLayout theo chiều dọc
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // Tiêu đề
        JLabel titleLabel = new JLabel("Đăng ký tài khoản mới", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(titleLabel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        // Trường "Tên tài khoản"
        JLabel usernameLabel = new JLabel("Tên tài khoản:");
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

        // Trường "Email"
        JLabel emailLabel = new JLabel("Email:");
        emailLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(emailLabel);
        emailField = new JTextField(20);
        emailField.setMaximumSize(new Dimension(280, emailField.getPreferredSize().height));
        emailField.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(emailField);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        // Nút "Đăng ký"
        registerButton = new JButton("Đăng ký");
        registerButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        registerButton.setBackground(new Color(50, 205, 50)); // Màu xanh lá
        registerButton.setForeground(Color.WHITE); // Chữ trắng
        registerButton.setMaximumSize(new Dimension(280, registerButton.getPreferredSize().height));
        contentPanel.add(registerButton);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Thêm contentPanel vào giữa mainPanel
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        // Panel cho nút "Đăng nhập" ở dưới cùng, căn giữa
        JPanel loginPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loginButton = new JButton("Đã có tài khoản? Đăng nhập");
        loginButton.setBorderPainted(false); // Không có viền
        loginButton.setContentAreaFilled(false); // Không có nền
        loginButton.setForeground(Color.BLUE); // Chữ màu xanh để nổi bật hơn
        loginPanel.add(loginButton);
        mainPanel.add(loginPanel, BorderLayout.SOUTH);

        // Thêm mainPanel vào JFrame
        add(mainPanel);

        // Tự động điều chỉnh kích thước và đặt kích thước tối thiểu
        pack();
        setMinimumSize(new Dimension(350, 300)); // Tăng kích thước tối thiểu cho thoải mái hơn

        // Giữ nguyên chức năng của các nút
        registerButton.addActionListener(e -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            String email = emailField.getText();
            if (username.length() < 3 || password.length() < 6 || !email.contains("@")) {
                JOptionPane.showMessageDialog(this, "Dữ liệu không hợp lệ!");
                return;
            }
            if (register(username, password, email)) {
                JOptionPane.showMessageDialog(this, "Đăng ký thành công!");
                LoginFrame loginFrame = new LoginFrame();
                loginFrame.setVisible(true);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Đăng ký thất bại!");
            }
        });

        loginButton.addActionListener(e -> {
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
            dispose();
        });
    }

    private boolean register(String username, String password, String email) {
        try (Connection connection = DBConnection.getConnection()) {
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO users (username, password, email) VALUES (?, ?, ?)")) {
                statement.setString(1, username);
                statement.setString(2, hashedPassword);
                statement.setString(3, email);
                int rowsInserted = statement.executeUpdate();
                return rowsInserted > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            RegisterFrame registerFrame = new RegisterFrame();
            registerFrame.setVisible(true);
        });
    }
}
    

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables

