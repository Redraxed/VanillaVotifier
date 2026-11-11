/*
 * Copyright (C) 2016  Matteo Morena
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package mamo.vanillaVotifier;

import mamo.vanillaVotifier.utils.TimestampUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.InetSocketAddress;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YamlConfig extends AbstractConfig {
	@SuppressWarnings("deprecation") @Nullable protected JsonConfig jsonConfig;

	public YamlConfig(@NotNull File configFile) {
		this(configFile, null);
	}

	public YamlConfig(@NotNull File configFile, @SuppressWarnings("deprecation") @Nullable JsonConfig jsonConfig) {
		super(configFile);
		this.jsonConfig = jsonConfig;
	}

	@Override
	public void load() throws IOException, InvalidKeySpecException {
		loadFromConfigFile();
		if (jsonConfig != null) {
			loadFromOldJsonConfig();
		}
	}

	protected void loadFromOldJsonConfig() throws IOException, InvalidKeySpecException {
		jsonConfig.load();
		inetSocketAddress = jsonConfig.getInetSocketAddress();
		keyPair = jsonConfig.getKeyPair();
		voteActions.clear();
		voteActions.addAll(jsonConfig.getVoteActions());
		save();
	}

	protected void loadFromConfigFile() throws IOException, InvalidKeySpecException {
		if (!configFile.exists()) {
			InputStream in = YamlConfig.class.getResourceAsStream("config.yaml");
			if (in == null) {
				throw new FileNotFoundException("Bundled default config.yaml is missing");
			}
			try (InputStream defaultConfig = in) {
				copyDefaultConfig(defaultConfig);
			}
		}

		Map config;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), java.nio.charset.StandardCharsets.UTF_8))) {
			Object loaded = new Yaml().load(reader);
			if (!(loaded instanceof Map)) {
				throw new IOException("Configuration root must be a YAML mapping");
			}
			config = (Map) loaded;
		}

		boolean save = false;
		if (!config.containsKey("config-version")) {
			config.put("config-version", 4);
			save = true;
		}
		if (!config.containsKey("log-directory")) {
			config.put("log-directory", "logs");
			save = true;
		}
		if (!config.containsKey("server")) {
			config.put("server", new HashMap());
			save = true;
		}
		Map serverSubconfig = requireMap(config.get("server"), "server");
		if (!serverSubconfig.containsKey("ip")) {
			serverSubconfig.put("ip", "0.0.0.0");
			save = true;
		}
		if (!serverSubconfig.containsKey("port")) {
			serverSubconfig.put("port", 8192);
			save = true;
		}
		if (!config.containsKey("key-pair-files")) {
			config.put("key-pair-files", new HashMap());
			save = true;
		}
		Map keyPairFilesSubconfig = requireMap(config.get("key-pair-files"), "key-pair-files");
		if (!keyPairFilesSubconfig.containsKey("public")) {
			keyPairFilesSubconfig.put("public", "public.pem");
			save = true;
		}
		if (!keyPairFilesSubconfig.containsKey("private")) {
			keyPairFilesSubconfig.put("private", "private.pem");
			save = true;
		}
		if (!config.containsKey("on-vote")) {
			config.put("on-vote", new ArrayList<Map>());
			save = true;
		}
		Object onVoteValue = config.get("on-vote");
		if (!(onVoteValue instanceof List)) {
			throw new IOException("Configuration key 'on-vote' must be a list");
		}
		List onVoteSubconfig = (List) onVoteValue;
		for (int i = 0; i < onVoteSubconfig.size(); i++) {
			Map voteActionSubconfig = requireMap(onVoteSubconfig.get(i), "on-vote[" + i + "]");
			String voteActionType = requireString(voteActionSubconfig.get("action"), "on-vote[" + i + "].action");
			if (!voteActionType.equalsIgnoreCase("rcon") && !voteActionType.equalsIgnoreCase("shell")) {
				throw new IOException("Unsupported action at on-vote[" + i + "]: " + voteActionType);
			}
			if (voteActionSubconfig.containsKey("action")) {
				if (voteActionType.equalsIgnoreCase("rcon")) {
					if (!voteActionSubconfig.containsKey("server")) {
						voteActionSubconfig.put("server", new HashMap());
						save = true;
					}
					Map voteActionServerSubconfig = requireMap(voteActionSubconfig.get("server"), "on-vote[" + i + "].server");
					if (!voteActionServerSubconfig.containsKey("ip")) {
						voteActionServerSubconfig.put("ip", "0.0.0.0");
						save = true;
					}
					if (!voteActionServerSubconfig.containsKey("port")) {
						voteActionServerSubconfig.put("port", 25575);
						save = true;
					}
					if (!voteActionServerSubconfig.containsKey("password")) {
						voteActionServerSubconfig.put("password", "password");
						save = true;
					}
					if (!voteActionSubconfig.containsKey("commands")) {
						voteActionSubconfig.put("commands", new ArrayList());
						save = true;
					}
					if (!voteActionSubconfig.containsKey("regex-replace")) {
						voteActionSubconfig.put("regex-replace", new HashMap());
						save = true;
					}
				}
				if (voteActionType.equalsIgnoreCase("shell")) {
					if (!voteActionSubconfig.containsKey("commands")) {
						voteActionSubconfig.put("commands", new ArrayList());
						save = true;
					}
					if (!voteActionSubconfig.containsKey("regex-replace")) {
						voteActionSubconfig.put("regex-replace", new HashMap());
						save = true;
					}
				}
			}
		}

		configVersion = requirePortLikeInteger(config.get("config-version"), "config-version", Integer.MIN_VALUE, Integer.MAX_VALUE);
		logDirectory = new File(requireString(config.get("log-directory"), "log-directory"));
		if (!logDirectory.exists()) {
			logDirectory.mkdirs();
		}
		logFile = new File(logDirectory, TimestampUtils.getTimestamp() + ".log");
		inetSocketAddress = new InetSocketAddress(requireString(serverSubconfig.get("ip"), "server.ip"), requirePortLikeInteger(serverSubconfig.get("port"), "server.port", 1, 65535));
		publicKeyFile = new File(requireString(keyPairFilesSubconfig.get("public"), "key-pair-files.public"));
		privateKeyFile = new File(requireString(keyPairFilesSubconfig.get("private"), "key-pair-files.private"));
		loadKeyPair();
		voteActions.clear();
		for (int i = 0; i < onVoteSubconfig.size(); i++) {
			Map voteActionSubconfig = requireMap(onVoteSubconfig.get(i), "on-vote[" + i + "]");
			if (((String) voteActionSubconfig.get("action")).equalsIgnoreCase("rcon")) {
				Map voteActionServerSubconfig = requireMap(voteActionSubconfig.get("server"), "on-vote[" + i + "].server");
				voteActions.add(new VoteAction(new RconCommandSender(new RconConnection(new InetSocketAddress(requireString(voteActionServerSubconfig.get("ip"), "on-vote[" + i + "].server.ip"), requirePortLikeInteger(voteActionServerSubconfig.get("port"), "on-vote[" + i + "].server.port", 1, 65535)), requireString(voteActionServerSubconfig.get("password"), "on-vote[" + i + "].server.password"))), requireStringList(voteActionSubconfig.get("commands"), "on-vote[" + i + "].commands"), requireStringMap(voteActionSubconfig.get("regex-replace"), "on-vote[" + i + "].regex-replace")));
			} else if (((String) voteActionSubconfig.get("action")).equalsIgnoreCase("shell")) {
				voteActions.add(new VoteAction(new ShellCommandSender(), requireStringList(voteActionSubconfig.get("commands"), "on-vote[" + i + "].commands"), requireStringMap(voteActionSubconfig.get("regex-replace"), "on-vote[" + i + "].regex-replace")));
			}
		}

		if (save) {
			save();
		}
	}

	@Override
	public void save() throws IOException {
		HashMap yamlConfig = new HashMap();
		yamlConfig.put("config-version", getConfigVersion());
		yamlConfig.put("log-directory", getLogDirectory().getPath());
		yamlConfig.put("server", new HashMap() {{
			put("ip", getInetSocketAddress().getAddress().getHostName());
			put("port", getInetSocketAddress().getPort());
		}});
		yamlConfig.put("key-pair-files", new HashMap() {{
			put("public", getPublicKeyFile().getPath());
			put("private", getPrivateKeyFile().getPath());
		}});
		yamlConfig.put("on-vote", new ArrayList<Map>() {{
			for (final VoteAction voteAction : getVoteActions()) {
				add(new HashMap() {{
					if (voteAction.getCommandSender() instanceof RconCommandSender) {
						final RconCommandSender commandSender = (RconCommandSender) voteAction.getCommandSender();
						put("action", "rcon");
						put("server", new HashMap() {{
							put("ip", commandSender.getRconConnection().getInetSocketAddress().getAddress().getHostName());
							put("port", commandSender.getRconConnection().getInetSocketAddress().getPort());
							put("password", commandSender.getRconConnection().getPassword());
						}});
					}
					if (voteAction.getCommandSender() instanceof ShellCommandSender) {
						put("action", "shell");
					}
					put("commands", voteAction.getCommands());
					put("regex-replace", voteAction.getRegexReplacements());
				}});
			}
		}});

		try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getConfigFile()), java.nio.charset.StandardCharsets.UTF_8))) {
			new Yaml().dump(yamlConfig, out);
		}
		saveKeyPair();
	}

	private static Map requireMap(Object value, String key) throws IOException {
		if (!(value instanceof Map)) {
			throw new IOException("Configuration key '" + key + "' must be a mapping");
		}
		return (Map) value;
	}

	private static String requireString(Object value, String key) throws IOException {
		if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
			throw new IOException("Configuration key '" + key + "' must be a non-empty string");
		}
		return (String) value;
	}

	private static int requirePortLikeInteger(Object value, String key, int minimum, int maximum) throws IOException {
		if (!(value instanceof Number)) {
			throw new IOException("Configuration key '" + key + "' must be an integer");
		}
		long number = ((Number) value).longValue();
		if (number < minimum || number > maximum || ((Number) value).doubleValue() != number) {
			throw new IOException("Configuration key '" + key + "' must be between " + minimum + " and " + maximum);
		}
		return (int) number;
	}

	private static List<String> requireStringList(Object value, String key) throws IOException {
		if (!(value instanceof List)) {
			throw new IOException("Configuration key '" + key + "' must be a list of strings");
		}
		List<String> result = new ArrayList<String>();
		for (Object item : (List) value) {
			if (!(item instanceof String)) {
				throw new IOException("Configuration key '" + key + "' must contain only strings");
			}
			result.add((String) item);
		}
		return result;
	}

	private static HashMap<String, String> requireStringMap(Object value, String key) throws IOException {
		Map map = requireMap(value, key);
		HashMap<String, String> result = new HashMap<String, String>();
		for (Object entryObject : map.entrySet()) {
			Map.Entry entry = (Map.Entry) entryObject;
			if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
				throw new IOException("Configuration key '" + key + "' must contain string keys and values");
			}
			result.put((String) entry.getKey(), (String) entry.getValue());
		}
		return result;
	}
}
