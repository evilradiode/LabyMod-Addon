<?php

require_once ("Config.php");

class WebsiteDatabase {

    function getTeamspeakBots($nickname) : int {
        return $this->numRows("SELECT * FROM teamspeak_bots WHERE `nickname` LIKE '%$nickname%' AND status='Online'");
    }

    /**
     * Laby-Addon Presence in DB `labymod_addon` (siehe labymod_addon.sql).
     */
    function upsertAddonPresence(string $uuid, string $station, ?string $addonVersion, ?string $userAgent) : void {
        $station = mb_substr(trim($station), 0, 64);
        $addonVersion = $addonVersion !== null ? mb_substr(trim($addonVersion), 0, 32) : null;
        $userAgent = $userAgent !== null ? mb_substr(trim($userAgent), 0, 255) : null;

        $connection = $this->getLabyAddonConnection();
        if($connection === null) {
            return;
        }

        $sql = "INSERT INTO `addon_presence` (`uuid`, `station`, `addon_version`, `user_agent`, `last_seen`)
                VALUES (?, ?, ?, ?, NOW())
                ON DUPLICATE KEY UPDATE
                  `station` = VALUES(`station`),
                  `addon_version` = VALUES(`addon_version`),
                  `user_agent` = VALUES(`user_agent`),
                  `last_seen` = NOW()";

        $stmt = mysqli_prepare($connection, $sql);
        if(!$stmt) {
            return;
        }

        mysqli_stmt_bind_param($stmt, "ssss", $uuid, $station, $addonVersion, $userAgent);
        mysqli_stmt_execute($stmt);
        mysqli_stmt_close($stmt);
    }

    /**
     * Aktive Addon-User in `labymod_addon` (Default: letzte 15 Minuten).
     */
    function countOnlineAddonUsers(int $minutes = 15) : int {
        $minutes = max(1, min(1440, $minutes));
        $connection = $this->getLabyAddonConnection();
        if($connection === null) {
            return 0;
        }

        $sql = "SELECT COUNT(*) AS c FROM `addon_presence`
                WHERE `last_seen` >= (NOW() - INTERVAL ? MINUTE)";
        $stmt = mysqli_prepare($connection, $sql);
        if(!$stmt) {
            return 0;
        }
        mysqli_stmt_bind_param($stmt, "i", $minutes);
        mysqli_stmt_execute($stmt);
        $result = mysqli_stmt_get_result($stmt);
        $row = $result ? mysqli_fetch_assoc($result) : null;
        mysqli_stmt_close($stmt);
        return $row ? (int)$row["c"] : 0;
    }

    // Feedback

    function insertAppFeedBack($name, $alter, $application, $feedback, $stars, $userType, $publishing, $ip) : void {
        $date = date("d.m.Y | H:i:s");
        $this->getQuery("INSERT INTO `app_feedback`(`IP`, `Name`, `Alter`, `Datum`, `Application`, `Feedback`, `Stars`, `UserType`, `Publishing`) VALUES ('$ip', '$name','$alter', '$date', '$application','$feedback', '$stars', '$userType', '$publishing')");
        $this->getQuery("INSERT INTO `app_feedback_cooldown` (`Name`, `Alter`, `Feedback`, `IP`) VALUES ('$name','$alter','$feedback','$ip')");
    }


    function checkAppForCooldown($ip) : bool {
        $query = mysqli_query($this->getConnection(), "SELECT * FROM app_feedback_cooldown WHERE IP='$ip'");
        $select = mysqli_fetch_assoc($query);
        return $select != null;
    }

    function clearAppCooldown() : void {
        $this->getQuery("TRUNCATE `app_feedback_cooldown`");
    }

    // AppData

    function getAppData() : array {
        $query = $this->getQuery("SELECT * FROM app_data WHERE ID='1'");
        $row = mysqli_fetch_assoc($query);
        return array(
            "InfoText" => $row["InfoText"],
            "ConfettiEnabled" => $row["ConfettiEnabled"] == 1,
            "PopUpEnabled" => $row["PopUpEnabled"] == 1,
            "PopUpText" => $row["PopUpText"],
            "EventEnabled" => $row["EventEnabled"] == 1,
            "EventMainPage" => $row["EventMainPage"] == 1,
            "EventNavDrawer" => $row["EventNavDrawer"] == 1,
            "EventDate" => $row["Date"],
            "EventTime" => $row["Time"],
            "EventCountdown" => $row["Countdown"] == 1,
            "EventUrl" => $row["EventUrl"],
            "EventText" => $row["EventText"],
            "EventMainPageColor" => $row["EventMainPageColor"],
        );
    }

    // News

    function getNews() : string {
        $dataArray = array();
        //$query = $this->getQuery("SELECT * FROM website_news WHERE Enabled='1' ORDER BY ID DESC");
        $query = $this->getQuery("SELECT news.*, u.username as user_name, u.avatar as user_avatar FROM website_news news JOIN users u ON news.user_id = u.id WHERE Enabled='1' AND (DisableDate IS NULL OR DisableDate > NOW()) ORDER BY news.ID + 0 DESC");
        $num = mysqli_num_rows($query);
        if ($num >= 1) {
            for ($i = 0; $i < $num; $i++) {
                $row = mysqli_fetch_assoc($query);
                $dataArray = array_merge($dataArray,
                    array(
                        $row["ID"] => array(
                            "ID" => $row["ID"],
                            "Image" => $row['Image'],
                            "Category" => $row['Category'],
                            "Title" => $row['Title'],
                            "Text" => $row['Text'],
                            "Link" => $row['Link'],
                            "Author" => $row['user_name'],
                            "Date" => $row['Date'],
                            "AuthorImage" => "https://panel.evil-radio.de/" . $row['user_avatar']
                        )
                    )
                );
            }
        }
        return json_encode(array(
            'News' => $dataArray,
            'Categories' => array(
                'Info',
                'Event',
                'News',
                'Blog'
            )
        ));
    }

    function getConnection() {
        $connection = mysqli_connect(Config::$WEBSITE_HOST, Config::$WEBSITE_USERNAME, Config::$WEBSITE_PASSWORD, Config::$WEBSITE_DATABASE, Config::$WEBSITE_PORT)
        or die(mysqli_connect_error());
        return $connection;
    }

    /**
     * Verbindung zur DB `labymod_addon` (gleicher User wie Website-Config).
     */
    function getLabyAddonConnection() : ?mysqli {
        $connection = @mysqli_connect(
            Config::$WEBSITE_HOST,
            Config::$WEBSITE_USERNAME,
            Config::$WEBSITE_PASSWORD,
            "labymod_addon",
            Config::$WEBSITE_PORT
        );

        return $connection ?: null;
    }

    function getQuery($sql) : mysqli_result|bool {
        return mysqli_query($this->getConnection(), $sql);
    }

    function numRows($sql) : int|string {
        return mysqli_num_rows($this->getQuery($sql));
    }

    function fetchAssoc($sql, $value) {
        $fetch = mysqli_fetch_assoc($this->getQuery($sql));
        return $fetch[$value];
    }

}
