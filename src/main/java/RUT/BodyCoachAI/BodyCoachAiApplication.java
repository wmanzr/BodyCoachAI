package RUT.BodyCoachAI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.Properties;

@SpringBootApplication
public class BodyCoachAiApplication {
	
	private static final Logger log = LoggerFactory.getLogger(BodyCoachAiApplication.class);

	public static void main(String[] args) {
		loadEnvFile();
		SpringApplication app = new SpringApplication(BodyCoachAiApplication.class);
		Environment env = app.run(args).getEnvironment();
	}

	private static void loadEnvFile() {
		try {
			File envFile = new File(".env");
			if (!envFile.exists()) {
				envFile = Paths.get(System.getProperty("user.dir"), ".env").toFile();
			}
			if (envFile.exists()) {
				Properties props = new Properties();
				try (FileInputStream fis = new FileInputStream(envFile)) {
					props.load(fis);

					props.forEach((key, value) -> {
						String keyStr = key.toString();
						String valueStr = value.toString().trim();

						if (System.getenv(keyStr) == null && System.getProperty(keyStr) == null) {
							System.setProperty(keyStr, valueStr);
						}
					});

				}
			}
		} catch (Exception e) {
			log.error("Ошибка при загрузке .env файла", e);
		}
	}

}
