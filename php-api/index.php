<?php
    require("assets/DiscordAPI.php");
    require("assets/station_info.php");
    require("assets/WebsiteDatabase.php");

    $station = new station_info();
    $discordApi = new DiscordAPI();

    $database = new WebsiteDatabase();

    $maintenance = false;

    if($_SERVER['REQUEST_METHOD'] == 'GET') {
        if($maintenance & !isset($_GET['auth'])) {
            header("HTTP/1.0 503 WARTUNGEN");
            exit();
        }

        if(isset($_GET['radioInfo'])) {
            $stream = $_GET['radioInfo'];
            $uuid = isset($_GET['uuid']) ? trim((string)$_GET['uuid']) : null;
            $handled = true;
            $payload = null;

            if($stream == 'Mashup') {
                $payload = $station->getRadioInfo("mashup", "evil-radio", $database->getTeamspeakBots("mashup"), $discordApi->getBotListeners("Discord_Mashup"));
            } else if($stream == "POP") {
                $payload = $station->getRadioInfo("pop_und_rap", "evil-radio-popundrap", $database->getTeamspeakBots("POP & RAP"), $discordApi->getBotListeners("Discord_POP"));
            } else if($stream == "Schlager") {
                $payload = $station->getRadioInfo("schlager", "er-schlager", $database->getTeamspeakBots("Schlager"), $discordApi->getBotListeners("Discord_Schlager"));
            } else if($stream == "Oldie") {
                $payload = $station->getRadioInfo("oldi", "er-oldie", $database->getTeamspeakBots("Oldie"), $discordApi->getBotListeners("Discord_Oldie"));
            } else if($stream == "Xmas") {
                $payload = $station->getRadioInfo("x-mas", "evil-radiox-mas", $database->getTeamspeakBots("X-MAS"), $discordApi->getBotListeners("Discord_Xmas"));
            } else if($stream == "Anime") {
                $payload = $station->getRadioInfo("animefm", "evil-animefm", $database->getTeamspeakBots("Anime"), $discordApi->getBotListeners("Discord_Anime"));
            } else if($stream == "Summer") {
                $payload = $station->getRadioInfo("sommer", "summer-time", $database->getTeamspeakBots("Sommer"), $discordApi->getBotListeners("Discord_Sommer"));
            } else if($stream == "TechTime") {
                $payload = $station->getRadioInfo("techno", "terramusic", $database->getTeamspeakBots("TechTime"), /*$discordApi->getBotListeners("Discord_TechTime")*/ 0);
            } else {
                $handled = false;
            }

            if(!$handled) {
                header("HTTP/1.0 400 Bad Request");
                exit();
            }

            // Presence → DB labymod_addon (Addon: radioInfo + uuid + X-Addon-Version / User-Agent)
            if($uuid !== null && $uuid !== '' && preg_match('/^[0-9a-fA-F-]{36}$/', $uuid)) {
                $addonVersion = $_SERVER['HTTP_X_ADDON_VERSION'] ?? null;
                $userAgent = $_SERVER['HTTP_USER_AGENT'] ?? null;
                $database->upsertAddonPresence($uuid, $stream, $addonVersion, $userAgent);
            }

            header("HTTP/1.0 200 OK");
            header("Content-Type: application/json; charset=utf-8");
            echo $payload;
        } else

        if(isset($_GET['system'])) {
            $data = $_GET['system'];
            if($data == "Data") {
                header("HTTP/1.0 200 OK");
                echo $station->getSystemInfo();
            } else if($data == "news") {
                header("HTTP/1.0 200 OK");
                echo $database->getNews();
            } else {
                header("HTTP/1.0 400 Bad Request");
            }

        } else {
            echo json_encode(array("status" => "OK"));
        }

    }
