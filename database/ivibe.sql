-- =========================================================
-- iVibe - script del database (MySQL / MariaDB)
-- Da importare con HeidiSQL: File > Esegui file SQL...
-- ATTENZIONE: cancella e ricrea tutte le tabelle di iVibe.
-- =========================================================

CREATE DATABASE IF NOT EXISTS ivibe CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ivibe;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS playlist_canzoni;
DROP TABLE IF EXISTS playlist;
DROP TABLE IF EXISTS preferiti;
DROP TABLE IF EXISTS canzoni;
DROP TABLE IF EXISTS utenti;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE utenti (
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    nickname           VARCHAR(50)  NOT NULL UNIQUE,
    email              VARCHAR(100) NOT NULL UNIQUE,
    password           VARCHAR(255) NOT NULL,
    data_nascita       DATE NULL,
    is_admin           BOOLEAN NOT NULL DEFAULT FALSE,
    data_registrazione TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE canzoni (
    id      INT AUTO_INCREMENT PRIMARY KEY,
    titolo  VARCHAR(150) NOT NULL,
    artista VARCHAR(150) NOT NULL,
    durata  DECIMAL(5,2) NOT NULL,
    url     VARCHAR(255) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE preferiti (
    utente_id   INT NOT NULL,
    canzone_id  INT NOT NULL,
    aggiunto_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (utente_id, canzone_id),
    FOREIGN KEY (utente_id)  REFERENCES utenti(id)  ON DELETE CASCADE,
    FOREIGN KEY (canzone_id) REFERENCES canzoni(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE playlist (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    utente_id INT NOT NULL,
    nome      VARCHAR(100) NOT NULL,
    UNIQUE (utente_id, nome),
    FOREIGN KEY (utente_id) REFERENCES utenti(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE playlist_canzoni (
    playlist_id INT NOT NULL,
    canzone_id  INT NOT NULL,
    posizione   INT NOT NULL DEFAULT 0,
    PRIMARY KEY (playlist_id, canzone_id),
    FOREIGN KEY (playlist_id) REFERENCES playlist(id) ON DELETE CASCADE,
    FOREIGN KEY (canzone_id)  REFERENCES canzoni(id)  ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------
-- Dati importati dai vecchi file CSV
-- ---------------------------------------------------------

-- Le password restano nel vecchio formato Base64: al primo login
-- l'app le converte automaticamente nel nuovo formato sicuro (PBKDF2).
INSERT INTO utenti (id, nickname, email, password, data_nascita, is_admin) VALUES
(1, 'ivibe_admin', '-', 'SVZJQkU2N2l2aWJlX2FkbWluSVZJQkU2Nw==', '2026-04-11', TRUE),
(2, 'iVibe_admin1', 'iVibe_admin1', 'SVZJQkU2N2lWaWJlX2FkbWluMUlWSUJFNjc=', '2026-04-11', TRUE),
(4, 'phippomiao', 'phippomiao@gmail.com', 'SVZJQkU2NyRpVmliZV9GMTNJVklCRTY3', '2008-05-12', FALSE),
(5, 'Zigga', 'zigga@gmail.com', 'SVZJQkU2N1ppZyZTaGFya282N0lWSUJFNjc=', '2007-08-14', FALSE);

-- Canzone 115 (Rolling Stone) non importata: doppione di 'Like a Rolling Stone' (stesso URL)
-- Canzone 175 (Bad Habit) non importata: URL sbagliato (era quello di 'Bad Habits' di Ed Sheeran)
INSERT INTO canzoni (id, titolo, artista, durata, url) VALUES
(1, 'Shape of You', 'Ed Sheeran', 3.53, 'https://www.youtube.com/watch?v=JGwWNGJdvx8'),
(2, 'Blinding Lights', 'The Weeknd', 3.2, 'https://www.youtube.com/watch?v=4NRXx6U8ABQ'),
(5, 'Billie Jean', 'Michael Jackson', 4.54, 'https://www.youtube.com/watch?v=Zi_XLOBDo_Y'),
(6, 'Smells Like Teen Spirit', 'Nirvana', 5.01, 'https://www.youtube.com/watch?v=hTWKbfoikeg'),
(7, 'Rolling in the Deep', 'Adele', 3.48, 'https://www.youtube.com/watch?v=rYEDA3JcQqw'),
(8, 'Someone Like You', 'Adele', 4.45, 'https://www.youtube.com/watch?v=hLQl3WQQoQ0'),
(9, 'Uptown Funk', 'Bruno Mars', 4.3, 'https://www.youtube.com/watch?v=OPf0YbXqDm0'),
(10, 'Thinking Out Loud', 'Ed Sheeran', 4.41, 'https://www.youtube.com/watch?v=lp-EO5I60KA'),
(11, 'Perfect', 'Ed Sheeran', 4.23, 'https://www.youtube.com/watch?v=2Vv-BfVoq4g'),
(12, 'Bad Guy', 'Billie Eilish', 3.14, 'https://www.youtube.com/watch?v=DyDfgMOUjCI'),
(13, 'Levitating', 'Dua Lipa', 3.23, 'https://www.youtube.com/watch?v=TUVcZfQe-Kw'),
(14, 'Dance Monkey', 'Tones and I', 3.29, 'https://www.youtube.com/watch?v=q0hyYWKXF0Q'),
(15, 'Havana', 'Camila Cabello', 3.37, 'https://www.youtube.com/watch?v=HCjNJDNzw8Y'),
(16, 'Senorita', 'Shawn Mendes', 3.11, 'https://www.youtube.com/watch?v=Pkh8UtuejGw'),
(17, 'Believer', 'Imagine Dragons', 3.24, 'https://www.youtube.com/watch?v=7wtfhZwyrcc'),
(18, 'Thunder', 'Imagine Dragons', 3.07, 'https://www.youtube.com/watch?v=fKopy74weus'),
(19, 'Counting Stars', 'OneRepublic', 4.17, 'https://www.youtube.com/watch?v=hT_nvWreIhg'),
(20, 'Apologize', 'OneRepublic', 3.25, 'https://www.youtube.com/watch?v=ZSM3w1v-A_Y'),
(21, 'Radioactive', 'Imagine Dragons', 3.06, 'https://www.youtube.com/watch?v=ktvTqknDobU'),
(22, 'Faded', 'Alan Walker', 3.32, 'https://www.youtube.com/watch?v=60ItHLz5WEA'),
(23, 'Alone', 'Alan Walker', 2.43, 'https://www.youtube.com/watch?v=1-xGerv5FOk'),
(24, 'Dark Horse', 'Katy Perry', 3.45, 'https://www.youtube.com/watch?v=0KSOMA3QBU0'),
(25, 'Roar', 'Katy Perry', 3.43, 'https://www.youtube.com/watch?v=CevxZvSJLk8'),
(26, 'Firework', 'Katy Perry', 3.48, 'https://www.youtube.com/watch?v=QGJuMBdaqIw'),
(27, 'Shake It Off', 'Taylor Swift', 3.39, 'https://www.youtube.com/watch?v=nfWlot6h_JM'),
(28, 'Blank Space', 'Taylor Swift', 3.51, 'https://www.youtube.com/watch?v=e-ORhEE9VVg'),
(29, 'Love Story', 'Taylor Swift', 3.55, 'https://www.youtube.com/watch?v=8xg3vE8Ie_E'),
(30, 'Bad Romance', 'Lady Gaga', 4.54, 'https://www.youtube.com/watch?v=qrO4YZeyl0I'),
(31, 'Poker Face', 'Lady Gaga', 3.57, 'https://www.youtube.com/watch?v=bESGLojNYSo'),
(32, 'Just Dance', 'Lady Gaga', 4.02, 'https://www.youtube.com/watch?v=2Abk1jAONjw'),
(33, 'Halo', 'Beyonce', 4.21, 'https://www.youtube.com/watch?v=bnVUHWCynig'),
(34, 'Single Ladies', 'Beyonce', 3.13, 'https://www.youtube.com/watch?v=4m1EFMoRFvY'),
(35, 'Happy', 'Pharrell Williams', 3.53, 'https://www.youtube.com/watch?v=ZbZSe6N_BXs'),
(36, 'Cant Stop the Feeling', 'Justin Timberlake', 3.56, 'https://www.youtube.com/watch?v=ru0K8uYEZWw'),
(37, 'Sorry', 'Justin Bieber', 3.2, 'https://www.youtube.com/watch?v=fRh_vgS2dFE'),
(38, 'Stay', 'The Kid LAROI', 2.21, 'https://www.youtube.com/watch?v=kTJczUoc26U'),
(39, 'Heat Waves', 'Glass Animals', 3.58, 'https://www.youtube.com/watch?v=mRD0-GxqHVo'),
(40, 'Stairway to Heaven', 'Led Zeppelin', 8.02, 'https://www.youtube.com/watch?v=QkF3oxziUI4'),
(41, 'Hotel California', 'Eagles', 6.3, 'https://www.youtube.com/watch?v=EqPtz5qN7HM'),
(42, 'Wonderwall', 'Oasis', 4.18, 'https://www.youtube.com/watch?v=bx1Bh8ZvH84'),
(43, 'Yellow', 'Coldplay', 4.26, 'https://www.youtube.com/watch?v=yKNxeF4KMsY'),
(44, 'Fix You', 'Coldplay', 4.55, 'https://www.youtube.com/watch?v=k4V3Mo61fJM'),
(45, 'Viva La Vida', 'Coldplay', 4.02, 'https://www.youtube.com/watch?v=dvgZkm1xWPE'),
(46, 'Paradise', 'Coldplay', 4.38, 'https://www.youtube.com/watch?v=1G4isv_Fylg'),
(47, 'The Scientist', 'Coldplay', 5.09, 'https://www.youtube.com/watch?v=RB-RcX5DS5A'),
(48, 'Lose Yourself', 'Eminem', 5.26, 'https://www.youtube.com/watch?v=_Yhyp-_hX2s'),
(49, 'Without Me', 'Eminem', 4.5, 'https://www.youtube.com/watch?v=YVkUvmDQ3HY'),
(50, 'Mockingbird', 'Eminem', 4.11, 'https://www.youtube.com/watch?v=S9bCLPwzSC0'),
(51, 'Numb', 'Linkin Park', 3.07, 'https://www.youtube.com/watch?v=kXYiU_JCYtU'),
(52, 'In the End', 'Linkin Park', 3.36, 'https://www.youtube.com/watch?v=eVTXPUF4Oz4'),
(53, 'Crawling', 'Linkin Park', 3.29, 'https://www.youtube.com/watch?v=Gd9OhYroLN0'),
(54, 'Somebody That I Used to Know', 'Gotye', 4.04, 'https://www.youtube.com/watch?v=8UVNT4wvIGY'),
(55, 'Take On Me', 'A-ha', 3.46, 'https://www.youtube.com/watch?v=djV11Xbc914'),
(56, 'Africa', 'Toto', 4.55, 'https://www.youtube.com/watch?v=FTQbiNvZqaY'),
(57, 'Sweet Child O'' Mine', 'Guns N'' Roses', 5.56, 'https://www.youtube.com/watch?v=1w7OgIMMRc4'),
(58, 'Livin'' on a Prayer', 'Bon Jovi', 4.09, 'https://www.youtube.com/watch?v=lDK9QqIzhwk'),
(59, 'Don''t Stop Believin''', 'Journey', 4.11, 'https://www.youtube.com/watch?v=1k8craCGpgs'),
(60, 'Every Breath You Take', 'The Police', 4.13, 'https://www.youtube.com/watch?v=OMOGaugKpzs'),
(61, 'With or Without You', 'U2', 4.55, 'https://www.youtube.com/watch?v=XmSdTa9kaiQ'),
(62, 'Beautiful Day', 'U2', 4.08, 'https://www.youtube.com/watch?v=co6WMzDOh1o'),
(63, 'I Will Always Love You', 'Whitney Houston', 4.31, 'https://www.youtube.com/watch?v=3JWTaaS7LdU'),
(64, 'My Heart Will Go On', 'Celine Dion', 4.4, 'https://www.youtube.com/watch?v=3gK_2XdjOdY'),
(65, 'Let Her Go', 'Passenger', 4.13, 'https://www.youtube.com/watch?v=RBumgq5yVrA'),
(66, 'Some Nights', 'Fun.', 4.37, 'https://www.youtube.com/watch?v=qQkBeOisNM0'),
(67, 'We Are Young', 'Fun.', 4.1, 'https://www.youtube.com/watch?v=Sv6dMFF_yts'),
(68, 'Riptide', 'Vance Joy', 3.24, 'https://www.youtube.com/watch?v=uJ_1HMAGb4k'),
(69, 'Shallow', 'Lady Gaga & Bradley Cooper', 3.35, 'https://www.youtube.com/watch?v=bo_efYhYU2A'),
(70, 'Drivers License', 'Olivia Rodrigo', 4.02, 'https://www.youtube.com/watch?v=ZmDBbnmKpqQ'),
(71, 'Good 4 U', 'Olivia Rodrigo', 2.58, 'https://www.youtube.com/watch?v=gNi_6U5Pm_o'),
(72, 'As It Was', 'Harry Styles', 2.47, 'https://www.youtube.com/watch?v=H5v3kku4y6Q'),
(73, 'Watermelon Sugar', 'Harry Styles', 2.54, 'https://www.youtube.com/watch?v=E07s5ZYygMg'),
(74, 'Circles', 'Post Malone', 3.35, 'https://www.youtube.com/watch?v=wXhTHyIgQ_U'),
(75, 'Rockstar', 'Post Malone', 3.38, 'https://www.youtube.com/watch?v=UceaB4D0jpo'),
(76, 'Sunflower', 'Post Malone & Swae Lee', 2.38, 'https://www.youtube.com/watch?v=ApXoWvfEYVU'),
(77, 'Old Town Road', 'Lil Nas X', 2.37, 'https://www.youtube.com/watch?v=r7qovpFAGrQ'),
(78, 'Bohemian Like You', 'The Dandy Warhols', 3.28, 'https://www.youtube.com/watch?v=IY1y3J8r5xA'),
(79, 'Seven Nation Army', 'The White Stripes', 3.52, 'https://www.youtube.com/watch?v=0J2QdDbelmY'),
(80, 'Mr. Brightside', 'The Killers', 3.43, 'https://www.youtube.com/watch?v=gGdGFtwCNBE'),
(81, 'Somebody Told Me', 'The Killers', 3.18, 'https://www.youtube.com/watch?v=Y5fBdpreJiU'),
(82, 'Sweet Dreams', 'Eurythmics', 3.36, 'https://www.youtube.com/watch?v=qeMFqkcPYcg'),
(83, 'Toxic', 'Britney Spears', 3.18, 'https://www.youtube.com/watch?v=LOZuxwVk7TU'),
(84, 'Baby One More Time', 'Britney Spears', 3.31, 'https://www.youtube.com/watch?v=C-u5WLJ9Yk4'),
(85, 'Cry Me a River', 'Justin Timberlake', 4.48, 'https://www.youtube.com/watch?v=R9WZt-pq9xY'),
(86, 'SexyBack', 'Justin Timberlake', 4.02, 'https://www.youtube.com/watch?v=3gOHvDP_vCs'),
(87, 'Crazy in Love', 'Beyonce', 3.56, 'https://www.youtube.com/watch?v=ViwtNLUqkMY'),
(88, 'Umbrella', 'Rihanna', 4.36, 'https://www.youtube.com/watch?v=CvBfHwUxHIk'),
(89, 'Diamonds', 'Rihanna', 3.45, 'https://www.youtube.com/watch?v=lWA2pjMjpBs'),
(90, 'We Found Love', 'Rihanna', 3.35, 'https://www.youtube.com/watch?v=tg00YEETFzg'),
(91, 'Love Yourself', 'Justin Bieber', 3.53, 'https://www.youtube.com/watch?v=oyEuk8j8imI'),
(92, 'What Do You Mean?', 'Justin Bieber', 3.26, 'https://www.youtube.com/watch?v=DK_0jXPuIr0'),
(93, 'Closer', 'The Chainsmokers', 4.05, 'https://www.youtube.com/watch?v=PT2_F-1esPk'),
(94, 'Something Just Like This', 'The Chainsmokers & Coldplay', 4.07, 'https://www.youtube.com/watch?v=FM7MFYoylVs'),
(95, 'Don''t Let Me Down', 'The Chainsmokers', 3.28, 'https://www.youtube.com/watch?v=Io0fBr1XBUA'),
(96, 'Bad Habits', 'Ed Sheeran', 3.51, 'https://www.youtube.com/watch?v=orJSJGHjBLI'),
(97, 'Shivers', 'Ed Sheeran', 3.27, 'https://www.youtube.com/watch?v=Il0S8BoucSA'),
(98, 'Stay With Me', 'Sam Smith', 2.52, 'https://www.youtube.com/watch?v=pB-5XG-DbAA'),
(99, 'Too Good at Goodbyes', 'Sam Smith', 3.21, 'https://www.youtube.com/watch?v=J_ub7Etch2U'),
(100, 'Shape of My Heart', 'Sting', 4.39, 'https://www.youtube.com/watch?v=QK-Z1K67uaA'),
(101, 'Heroes', 'David Bowie', 3.32, 'https://www.youtube.com/watch?v=bsYp9q3QNaQ'),
(102, 'Let''s Dance', 'David Bowie', 4.06, 'https://www.youtube.com/watch?v=N4d7Wp9kKjA'),
(103, 'Mr. Blue Sky', 'Electric Light Orchestra', 5.04, 'https://www.youtube.com/watch?v=wuJIqmha2Hk'),
(104, 'Dreams', 'Fleetwood Mac', 4.17, 'https://www.youtube.com/watch?v=mrZRURcb1cM'),
(105, 'Go Your Own Way', 'Fleetwood Mac', 3.41, 'https://www.youtube.com/watch?v=6ul-cZyuYq4'),
(106, 'Hotel Room Service', 'Pitbull', 3.57, 'https://www.youtube.com/watch?v=Sg9y9eY9K1s'),
(107, 'Give Me Everything', 'Pitbull', 4.12, 'https://www.youtube.com/watch?v=EPo5wWmKEaI'),
(108, 'On The Floor', 'Jennifer Lopez', 3.57, 'https://www.youtube.com/watch?v=t4H_Zoh7G5A'),
(109, 'Tik Tok', 'Kesha', 3.2, 'https://www.youtube.com/watch?v=iP6XpLQM2Cs'),
(110, 'Party In The U.S.A.', 'Miley Cyrus', 3.22, 'https://www.youtube.com/watch?v=M11SvDtPBhA'),
(111, 'Wrecking Ball', 'Miley Cyrus', 3.41, 'https://www.youtube.com/watch?v=-YICuUtkjlg'),
(112, 'Happy Together', 'The Turtles', 2.54, 'https://www.youtube.com/watch?v=9ZEURntrQOg'),
(113, 'Dream On', 'Aerosmith', 4.28, 'https://www.youtube.com/watch?v=89dGC8de0CA'),
(114, 'I Don''t Want to Miss a Thing', 'Aerosmith', 4.58, 'https://www.youtube.com/watch?v=JkK8g6FMEXE'),
(116, 'Like a Rolling Stone', 'Bob Dylan', 6.13, 'https://www.youtube.com/watch?v=IwOfCgkyEj0'),
(117, 'What a Wonderful World', 'Louis Armstrong', 2.21, 'https://www.youtube.com/watch?v=A3yCcXgbKrE'),
(118, 'Stand By Me', 'Ben E. King', 2.57, 'https://www.youtube.com/watch?v=hwZNL7QVJjE'),
(119, 'Let It Be', 'The Beatles', 4.03, 'https://www.youtube.com/watch?v=QDYfEBY9NM4'),
(120, 'Hey Jude', 'The Beatles', 7.11, 'https://www.youtube.com/watch?v=A_MjCqQoLLA'),
(121, 'Yesterday', 'The Beatles', 2.05, 'https://www.youtube.com/watch?v=Jz0iK7iZ1VQ'),
(122, 'Come Together', 'The Beatles', 4.19, 'https://www.youtube.com/watch?v=45cYwDMibGo'),
(123, 'Back in Black', 'AC/DC', 4.15, 'https://www.youtube.com/watch?v=pAgnJDJN4VA'),
(124, 'Highway to Hell', 'AC/DC', 3.28, 'https://www.youtube.com/watch?v=l482T0yNkeo'),
(125, 'Sweet Home Alabama', 'Lynyrd Skynyrd', 4.45, 'https://www.youtube.com/watch?v=ye5BuYf8q4o'),
(126, 'Take Me Home Country Roads', 'John Denver', 3.1, 'https://www.youtube.com/watch?v=1vrEljMfXYo'),
(127, 'Don''t Look Back in Anger', 'Oasis', 4.48, 'https://www.youtube.com/watch?v=r8OipmKFDeM'),
(128, 'The A Team', 'Ed Sheeran', 4.18, 'https://www.youtube.com/watch?v=UAWcs5H-qgQ'),
(129, 'Photograph', 'Ed Sheeran', 4.19, 'https://www.youtube.com/watch?v=nSDgHBxUbVQ'),
(130, 'Castle on the Hill', 'Ed Sheeran', 4.21, 'https://www.youtube.com/watch?v=7Qp5vcuMIlk'),
(131, 'Love Me Like You Do', 'Ellie Goulding', 4.12, 'https://www.youtube.com/watch?v=AJtDXIazrMo'),
(132, 'Lights', 'Ellie Goulding', 3.32, 'https://www.youtube.com/watch?v=0NKUpo_xKyQ'),
(133, 'Royals', 'Lorde', 3.1, 'https://www.youtube.com/watch?v=nlcIKh6sBtc'),
(134, 'Team', 'Lorde', 3.13, 'https://www.youtube.com/watch?v=3N3N0YvQ0pY'),
(135, 'Cheap Thrills', 'Sia', 3.31, 'https://www.youtube.com/watch?v=nYh-n7EOtMA'),
(136, 'Chandelier', 'Sia', 3.36, 'https://www.youtube.com/watch?v=2vjPBrBU-TM'),
(137, 'Elastic Heart', 'Sia', 4.17, 'https://www.youtube.com/watch?v=KWZGAExj-es'),
(138, 'Titanium', 'David Guetta ft. Sia', 4.05, 'https://www.youtube.com/watch?v=JRfuAukYTKg'),
(139, 'Memories', 'Maroon 5', 3.09, 'https://www.youtube.com/watch?v=SlPhMPnQ58k'),
(140, 'Sugar', 'Maroon 5', 3.55, 'https://www.youtube.com/watch?v=09R8_2nJtjg'),
(141, 'Animals', 'Maroon 5', 4.05, 'https://www.youtube.com/watch?v=qpgTC9MDx1o'),
(142, 'Moves Like Jagger', 'Maroon 5', 3.21, 'https://www.youtube.com/watch?v=iEPTlhBmwRg'),
(143, 'Locked Out of Heaven', 'Bruno Mars', 3.53, 'https://www.youtube.com/watch?v=sr7P2t5dW9Q'),
(144, 'Grenade', 'Bruno Mars', 3.42, 'https://www.youtube.com/watch?v=SR6iYWJxHqs'),
(145, 'Just the Way You Are', 'Bruno Mars', 3.4, 'https://www.youtube.com/watch?v=LjhCEhWiKXk'),
(146, '24K Magic', 'Bruno Mars', 3.46, 'https://www.youtube.com/watch?v=UqyT8IEBkvY'),
(147, 'Don''t Start Now', 'Dua Lipa', 3.03, 'https://www.youtube.com/watch?v=oygrmJFKYZY'),
(148, 'Physical', 'Dua Lipa', 3.13, 'https://www.youtube.com/watch?v=9HDEHj2yzew'),
(149, 'Break My Heart', 'Dua Lipa', 3.41, 'https://www.youtube.com/watch?v=Nj2U6rhnucI'),
(150, 'If I Can''t Have You', 'Shawn Mendes', 3.1, 'https://www.youtube.com/watch?v=6-x0dF6eZcE'),
(151, 'Understand', 'BoyWithUke', 3.04, 'https://www.youtube.com/watch?v=8Q0xQ9v1x3A'),
(152, 'IDGAF', 'BoyWithUke', 2.42, 'https://www.youtube.com/watch?v=5Q0xQ9v1x3A'),
(153, 'Glimpse of Us', 'Joji', 3.53, 'https://www.youtube.com/watch?v=K9P4B0c1x9A'),
(154, 'Slow Dancing in the Dark', 'Joji', 3.39, 'https://www.youtube.com/watch?v=K3Qzzggn--s'),
(155, 'Sanctuary', 'Joji', 3.03, 'https://www.youtube.com/watch?v=E3oG0b0t1mQ'),
(156, 'Sweater Weather', 'The Neighbourhood', 4.0, 'https://www.youtube.com/watch?v=GCdwKhTtNNw'),
(157, 'Daddy Issues', 'The Neighbourhood', 4.2, 'https://www.youtube.com/watch?v=2tJ5wQk9X9Y'),
(158, 'Softcore', 'The Neighbourhood', 3.26, 'https://www.youtube.com/watch?v=Jx9t2bQ9k3Q'),
(159, 'Stargazing', 'Travis Scott', 3.05, 'https://www.youtube.com/watch?v=Q4n0YQ9v1x3A'),
(160, 'SICKO MODE', 'Travis Scott', 5.12, 'https://www.youtube.com/watch?v=6ONRf7h3Mdk'),
(161, 'Goosebumps', 'Travis Scott', 4.04, 'https://www.youtube.com/watch?v=Dst9gZkq1a8'),
(162, 'FE!N', 'Travis Scott', 3.11, 'https://www.youtube.com/watch?v=B9synWjqBn8'),
(163, 'Starboy', 'The Weeknd', 3.5, 'https://www.youtube.com/watch?v=34Na4j8AVgA'),
(164, 'Save Your Tears', 'The Weeknd', 3.36, 'https://www.youtube.com/watch?v=XXYlFuWEuKI'),
(165, 'The Hills', 'The Weeknd', 4.02, 'https://www.youtube.com/watch?v=yzTuBuRdAyA'),
(166, 'HUMBLE.', 'Kendrick Lamar', 2.57, 'https://www.youtube.com/watch?v=tvTRZJ-4EyI'),
(167, 'DNA.', 'Kendrick Lamar', 3.06, 'https://www.youtube.com/watch?v=NLZRYQMLDW4'),
(168, 'Money Trees', 'Kendrick Lamar', 6.26, 'https://www.youtube.com/watch?v=H6k3p8g0V4A'),
(169, 'Lucid Dreams', 'Juice WRLD', 4.0, 'https://www.youtube.com/watch?v=mzB1VGEGcSU'),
(170, 'All Girls Are the Same', 'Juice WRLD', 2.45, 'https://www.youtube.com/watch?v=h3EJICKwITw'),
(171, 'Legends', 'Juice WRLD', 3.12, 'https://www.youtube.com/watch?v=mZ5H8sn1Z3Y'),
(172, 'Congratulations', 'Post Malone', 3.4, 'https://www.youtube.com/watch?v=SC4xMk98Pdc'),
(173, 'Industry Baby', 'Lil Nas X', 3.32, 'https://www.youtube.com/watch?v=UTHLKHL_whs'),
(174, 'Montero', 'Lil Nas X', 2.17, 'https://www.youtube.com/watch?v=6swmTBVI83k'),
(176, 'Dark Red', 'Steve Lacy', 3.54, 'https://www.youtube.com/watch?v=36tggrpRoTI'),
(177, 'Redbone', 'Childish Gambino', 5.27, 'https://www.youtube.com/watch?v=Kp7eSUU9oy8'),
(178, 'This Is America', 'Childish Gambino', 3.45, 'https://www.youtube.com/watch?v=VYOjWnS4cMY'),
(179, 'Pompeii', 'Bastille', 3.34, 'https://www.youtube.com/watch?v=F90Cw4l-8NY'),
(180, 'Ocean Eyes', 'Billie Eilish', 3.2, 'https://www.youtube.com/watch?v=viimfQi_pUw'),
(181, 'Happier', 'Marshmello & Bastille', 3.34, 'https://www.youtube.com/watch?v=m7Bc3pLyij0'),
(182, 'Wake Me Up', 'Avicii', 4.07, 'https://www.youtube.com/watch?v=IcrbM1l_BoI'),
(183, 'The Nights', 'Avicii', 3.11, 'https://www.youtube.com/watch?v=UtF6Jej8yb4'),
(184, 'Levels', 'Avicii', 3.19, 'https://www.youtube.com/watch?v=_ovdm2yX4MA'),
(185, 'One More Time', 'Daft Punk', 5.2, 'https://www.youtube.com/watch?v=FGBhQbmPwH8'),
(186, 'Get Lucky', 'Daft Punk', 4.08, 'https://www.youtube.com/watch?v=5NV6Rdv1a3I'),
(187, 'Harder Better Faster Stronger', 'Daft Punk', 3.44, 'https://www.youtube.com/watch?v=gAjR4_CbPpQ'),
(188, 'Clocks', 'Coldplay', 5.07, 'https://www.youtube.com/watch?v=d020hcWA_Wg'),
(189, 'A Sky Full of Stars', 'Coldplay', 4.28, 'https://www.youtube.com/watch?v=VPRjCeoBqrI'),
(190, 'In My Feelings', 'Drake', 3.37, 'https://www.youtube.com/watch?v=DRS_PpOrUZ4'),
(191, 'God''s Plan', 'Drake', 3.18, 'https://www.youtube.com/watch?v=xpVfcZ0ZcFM'),
(192, 'Hotline Bling', 'Drake', 4.27, 'https://www.youtube.com/watch?v=uxpDa-c-4Mc'),
(193, 'In Da Club', '50 Cent', 3.13, 'https://www.youtube.com/watch?v=5qm8PH4xAss'),
(194, 'Stan', 'Eminem', 6.44, 'https://www.youtube.com/watch?v=gOMhN-hfMtY'),
(195, 'The Real Slim Shady', 'Eminem', 4.44, 'https://www.youtube.com/watch?v=eJO5HU_7_1w'),
(196, 'Set Fire to the Rain', 'Adele', 4.03, 'https://www.youtube.com/watch?v=Ri7-vnrJD3k'),
(197, 'Hello', 'Adele', 4.55, 'https://www.youtube.com/watch?v=YQHsXMglC9A'),
(198, 'Can''t Hold Us', 'Macklemore & Ryan Lewis', 4.18, 'https://www.youtube.com/watch?v=2zNSgSzhBfM'),
(199, 'Thrift Shop', 'Macklemore & Ryan Lewis', 3.55, 'https://www.youtube.com/watch?v=QK8mJJJvaes'),
(200, 'Boulevard of Broken Dreams', 'Green Day', 4.2, 'https://www.youtube.com/watch?v=Soa3gO7tL-c'),
(201, 'Prairies', 'BoyWithUke', 3.2, 'https://www.youtube.com/watch?v=paqcdLiBvsw');

INSERT INTO preferiti (utente_id, canzone_id) VALUES
(2, 8),
(2, 9),
(2, 7),
(2, 6),
(2, 31),
(4, 5),
(4, 6),
(4, 7),
(4, 26);

INSERT INTO playlist (id, utente_id, nome) VALUES
(1, 4, 'Bho 1');

INSERT INTO playlist_canzoni (playlist_id, canzone_id, posizione) VALUES
(1, 1, 0),
(1, 7, 1),
(1, 14, 2),
(1, 17, 3),
(1, 26, 4),
(1, 5, 5);

