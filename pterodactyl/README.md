# Pterodactyl egg

Import [`egg-vanilla-votifier.json`](egg-vanilla-votifier.json) under **Admin → Nests → Import Egg**.

The GitHub Actions workflow builds the shaded JAR on pushes and pull requests to `master`. Each push to `master` also refreshes the GitHub `latest` release with `VanillaVotifier.jar`. The egg installer downloads that release asset and creates a starter `config.yaml`.

The egg runs the app with Java 8. Assign one allocation for the Votifier listener. On startup, Pterodactyl updates `server.port` in `config.yaml` to that allocation. The listener binds to `0.0.0.0`, and Pterodactyl's `stop` command is sent to the application console for a graceful shutdown.

The starter config has no vote actions. Add your RCON or shell actions to `config.yaml` after installation. Keep RCON restricted to a trusted network because its password is sent in plaintext by the protocol.
