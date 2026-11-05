package ivibe.Model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Cifratura delle password con PBKDF2 (hash lento + salt casuale per ogni utente).
 *
 * Formato salvato nel database:  pbkdf2$iterazioni$salt$hash
 *
 * A differenza del vecchio Base64, da questo formato NON si può risalire
 * alla password originale. Le vecchie password in Base64 vengono ancora
 * riconosciute (vedi isFormatoVecchio) e convertite al primo login.
 */
public class PasswordEncoder {

    private static final String PREFISSO = "pbkdf2";
    private static final int ITERAZIONI = 65536;
    private static final int LUNGHEZZA_BIT = 256;
    private static final String VECCHIO_SALT = "IVIBE67";

    private static final SecureRandom random = new SecureRandom();

    public static String encode(String password) {

        byte[] salt = new byte[16];
        random.nextBytes(salt);

        byte[] hash = pbkdf2(password, salt, ITERAZIONI);

        return PREFISSO + "$" + ITERAZIONI + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verify(String inputPassword, String salvata) {

        if (inputPassword == null || salvata == null) {
            return false;
        }

        if (isFormatoVecchio(salvata)) {
            return verificaVecchia(inputPassword, salvata);
        }

        try {
            String[] parti = salvata.split("\\$");
            if (parti.length != 4) {
                return false;
            }

            int iterazioni = Integer.parseInt(parti[1]);
            byte[] salt = Base64.getDecoder().decode(parti[2]);
            byte[] atteso = Base64.getDecoder().decode(parti[3]);

            byte[] calcolato = pbkdf2(inputPassword, salt, iterazioni);

            // confronto a tempo costante
            return MessageDigest.isEqual(atteso, calcolato);

        } catch (Exception e) {
            return false;
        }
    }

    /** true se la password è ancora salvata col vecchio sistema Base64 e va aggiornata. */
    public static boolean isFormatoVecchio(String salvata) {
        return salvata != null && !salvata.startsWith(PREFISSO + "$");
    }

    private static boolean verificaVecchia(String inputPassword, String salvata) {
        String combinata = VECCHIO_SALT + inputPassword + VECCHIO_SALT;
        String vecchia = Base64.getEncoder().encodeToString(combinata.getBytes(StandardCharsets.UTF_8));
        return MessageDigest.isEqual(
                vecchia.getBytes(StandardCharsets.UTF_8),
                salvata.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterazioni) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterazioni, LUNGHEZZA_BIT);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return f.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 non disponibile", e);
        }
    }
}
