# Pterodactyl egg

Import [`egg-vanilla-votifier.json`](egg-vanilla-votifier.json) under **Admin → Nests → Import Egg**.

The GitHub Actions workflow builds the shaded JAR on pushes and pull requests to `master`. Each push to `master` also refreshes the GitHub `latest` release with `VanillaVotifier.jar`. The egg installer downloads that release asset. VanillaVotifier creates its complete `config.yaml` on first start. The egg does not parse or modify the config, so all settings survive reinstalls.

The egg runs the app with Java 8. The bundled config listens on `0.0.0.0:8192`; assign TCP port `8192` to the server, or change `server.port` in `config.yaml` to match another allocation. Pterodactyl's `stop` command is sent to the application console for a graceful shutdown.

Add RCON or shell actions to `config.yaml` as needed. Keep RCON restricted to a trusted network because its password is sent in plaintext by the protocol.
