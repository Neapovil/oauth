# Auth

## Supported Minecraft Versions

1.18.2 is the only supported version at current time.

## Install

1. A PaperMC server
2. Download the file from the releases tab and move it inside your `Plugins` folder

## How to use

The plugin uses Spark to create a REST API.

After the first run, the plugin will create a `config.json` file in `Plugins/OAuth/` with a secret code in it. If you want, you can modify it with a code of your choice.

The server uses this code to make sure the endpoints are used only from authorized sources.

Spark will start the server on `0.0.0.0:4567`. You can change the port inside the `config.json` file.

The player will be kicked out the server on log-in and a code is displayed.

The code expires after 60 seconds (default). This value can be changed in the `config.json` file.

If you made changes to the config, make sure to restart the server to load the new values.