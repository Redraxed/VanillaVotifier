package mamo.vanillaVotifier;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class YamlConfigValidationTest {
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void reportsOutOfRangeServerPortClearly() throws Exception {
		YamlConfig config = writeConfig(baseConfig("port: 70000\n"));
		assertLoadFailsWith(config, "server.port");
	}

	@Test
	public void rejectsUnknownActionClearly() throws Exception {
		String action = "  - action: webhook\n    commands: []\n";
		YamlConfig config = writeConfig(baseConfig("port: 8192\n") + "on-vote:\n" + action);
		assertLoadFailsWith(config, "Unsupported action");
	}

	private YamlConfig writeConfig(String contents) throws IOException {
		File file = new File(temporaryFolder.getRoot(), "config.yaml");
		Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
		return new YamlConfig(file);
	}

	private static String baseConfig(String portLine) {
		return "config-version: 4\n" +
				"log-directory: logs\n" +
				"server:\n" +
				"  ip: 127.0.0.1\n" +
				"  " + portLine +
				"key-pair-files:\n" +
				"  public: public.pem\n" +
				"  private: private.pem\n";
	}

	private static void assertLoadFailsWith(YamlConfig config, String expectedMessage) throws Exception {
		try {
			config.load();
			fail("Expected config loading to fail");
		} catch (IOException e) {
			assertTrue("Unexpected error: " + e.getMessage(), e.getMessage().contains(expectedMessage));
		}
	}
}
