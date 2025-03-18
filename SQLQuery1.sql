USE ChatonlineDB;
GO
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS chat_participants;
DROP TABLE IF EXISTS chats;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    user_id INT IDENTITY(1,1) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) NOT NULL,
    avatar VARCHAR(255) NULL,
    status VARCHAR(20) DEFAULT 'active',
    created_at DATETIME DEFAULT GETDATE(),
    updated_at DATETIME DEFAULT GETDATE()
);

CREATE TABLE chats (
    chat_id INT IDENTITY(1,1) PRIMARY KEY,
    chat_type VARCHAR(20) NOT NULL,
    chat_name VARCHAR(100) NULL,
    created_at DATETIME DEFAULT GETDATE()
);

CREATE TABLE chat_participants (
    chat_id INT NOT NULL,
    user_id INT NOT NULL,
    joined_at DATETIME DEFAULT GETDATE(),
    CONSTRAINT PK_chat_participants PRIMARY KEY (chat_id, user_id),
    CONSTRAINT FK_chat FOREIGN KEY (chat_id) REFERENCES chats(chat_id) ON DELETE CASCADE,
    CONSTRAINT FK_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE messages (
    message_id INT IDENTITY(1,1) PRIMARY KEY,
    chat_id INT NOT NULL,
    sender_id INT NOT NULL,
    message NVARCHAR(MAX) NOT NULL,
    created_at DATETIME DEFAULT GETDATE(),
    CONSTRAINT FK_message_chat FOREIGN KEY (chat_id) REFERENCES chats(chat_id) ON DELETE CASCADE,
    CONSTRAINT FK_message_user FOREIGN KEY (sender_id) REFERENCES users(user_id) ON DELETE CASCADE
);
USE ChatonlineDB;
GO
ALTER TABLE users
ADD is_logged_in BIT DEFAULT 0;


