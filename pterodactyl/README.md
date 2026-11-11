# Pterodactyl egg

Import [`egg-vanilla-votifier.json`](egg-vanilla-votifier.json) under **Admin → Nests → Import Egg**.

The GitHub Actions workflow builds the shaded JAR on pushes and pull requests to `master`. Each push to `master` also refreshes the GitHub `latest` release with `VanillaVotifier.jar`. The egg installer downloads that release asset. VanillaVotifier creates its complete `config.yaml` on first start; the egg only sets the allocated listener address and port, preserving the rest of the config on future starts and reinstalls.

The egg runs the app with Java 8. Assign one allocation for the Votifier listener. On startup, Pterodactyl updates `server.port` in `config.yaml` to that allocation. The listener binds to `0.0.0.0`, and Pterodactyl's `stop` command is sent to the application console for a graceful shutdown.

The application copies its complete bundled config on first start. The egg installer does not create or replace `config.yaml`, so your settings survive reinstalls. Add RCON or shell actions to `config.yaml` as needed. Keep RCON restricted to a trusted network because its password is sent in plaintext by the protocol.
