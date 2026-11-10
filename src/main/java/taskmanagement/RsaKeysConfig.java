package taskmanagement;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Stellt das RSA-Schlüsselpaar für die JWT-Signatur bereit.
 * <p>
 * Das Schlüsselpaar wird beim ersten Start erzeugt und im Verzeichnis
 * {@code app.jwt.key-dir} gespeichert. Bei späteren Starts wird es wieder
 * geladen, damit bereits ausgestellte Tokens nach einem Neustart gültig bleiben.
 */
@Configuration
public class RsaKeysConfig {

    private static final String PRIVATE_KEY_FILE = "jwt-private.key";
    private static final String PUBLIC_KEY_FILE = "jwt-public.key";

    @Bean
    public KeyPair rsaKeyPair(@Value("${app.jwt.key-dir:./data/keys}") String keyDir) {
        Path dir = Path.of(keyDir);
        Path privatePath = dir.resolve(PRIVATE_KEY_FILE);
        Path publicPath = dir.resolve(PUBLIC_KEY_FILE);

        try {
            if (Files.exists(privatePath) && Files.exists(publicPath)) {
                return load(privatePath, publicPath);
            }
            KeyPair keyPair = generate();
            Files.createDirectories(dir);
            Files.write(privatePath, keyPair.getPrivate().getEncoded());
            Files.write(publicPath, keyPair.getPublic().getEncoded());
            return keyPair;
        } catch (IOException | GeneralSecurityException ex) {
            throw new IllegalStateException(
                    "RSA-Schlüsselpaar konnte nicht geladen oder erzeugt werden (" + dir.toAbsolutePath() + ")",
                    ex
            );
        }
    }

    private KeyPair generate() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private KeyPair load(Path privatePath, Path publicPath) throws IOException, GeneralSecurityException {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(privatePath)));
        PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(publicPath)));
        return new KeyPair(publicKey, privateKey);
    }
}
