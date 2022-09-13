# Auth

## Supported Minecraft Versions

1.18.2 is the only supported version for now.

## Install

1. A PaperMC server
2. Download the file from the releases tab and move it inside your `Plugins` folder

## How to use

The plugin uses [Spark](https://sparkjava.com/) to create a REST API.

After the first run, the plugin will create a `config.json` file in `Plugins/OAuth/` with a secret code in it.

You are free to modify the secret code to your liking.

The server uses this secret code to make sure the endpoints are used only from authorized sources.

Spark will start the server on `0.0.0.0:4567`. You can change the port inside the `config.json` file.

The player will be kicked out the server on log-in and a countdown is displayed.

The countdown expires after 60 seconds (default). This value can be changed in the `config.json` file.

The config auto reloads.
