-- phpMyAdmin: eigene DB für Laby-Addon Presence-Tracking
-- Nutzt denselben MySQL-User wie die Website-Config (Rechte auf labymod_addon vergeben).

CREATE DATABASE IF NOT EXISTS `labymod_addon`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `labymod_addon`;

CREATE TABLE IF NOT EXISTS `addon_presence` (
  `uuid` CHAR(36) NOT NULL COMMENT 'LabyMod UniqueId',
  `station` VARCHAR(64) NOT NULL COMMENT 'radioInfo-Wert, z.B. Mashup',
  `addon_version` VARCHAR(32) DEFAULT NULL,
  `user_agent` VARCHAR(255) DEFAULT NULL,
  `last_seen` DATETIME NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`uuid`),
  KEY `idx_last_seen` (`last_seen`),
  KEY `idx_station_last_seen` (`station`, `last_seen`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Rechte für den bestehenden Website-User (Namen anpassen falls nötig):
-- GRANT SELECT, INSERT, UPDATE ON `labymod_addon`.* TO 'DEIN_WEBSITE_USER'@'%';
-- FLUSH PRIVILEGES;

-- Aktive User (15 Min):
-- SELECT COUNT(*) AS online_total FROM addon_presence
-- WHERE last_seen >= (NOW() - INTERVAL 15 MINUTE);
