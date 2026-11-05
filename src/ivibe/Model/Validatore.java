package ivibe.Model;

import java.util.regex.Pattern;

/**
 * Regole di validazione usate sia in registrazione sia nelle impostazioni,
 * così sono uguali ovunque. Ogni metodo restituisce null se il valore va
 * bene, altrimenti il messaggio d'errore da mostrare.
 */
public class Validatore {

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern NICKNAME = Pattern.compile("^[A-Za-z0-9._-]{3,30}$");

    public static String controllaPassword(String pass) {

        if (pass == null || pass.length() < 7) {
            return "⚠ Password troppo corta (minimo 7 caratteri)";
        }
        if (pass.length() > 64) {
            return "⚠ Password troppo lunga (massimo 64 caratteri)";
        }
        if (pass.contains(" ")) {
            return "⚠ La password non può contenere spazi";
        }

        boolean maiuscola = false;
        boolean numero = false;
        boolean speciale = false;

        for (char c : pass.toCharArray()) {
            if (Character.isUpperCase(c)) {
                maiuscola = true;
            } else if (Character.isDigit(c)) {
                numero = true;
            } else if (!Character.isLetterOrDigit(c)) {
                speciale = true;
            }
        }

        if (!maiuscola || !numero || !speciale) {
            return "⚠ La password deve avere una maiuscola, un numero e un simbolo";
        }
        return null;
    }

    public static String controllaEmail(String email) {
        if (email == null || !EMAIL.matcher(email).matches()) {
            return "⚠ Email non valida (es. nome@dominio.it)";
        }
        if (email.length() > 100) {
            return "⚠ Email troppo lunga";
        }
        return null;
    }

    public static String controllaNickname(String nickname) {
        if (nickname == null || !NICKNAME.matcher(nickname).matches()) {
            return "⚠ Username: 3-30 caratteri tra lettere, numeri, . _ -";
        }
        return null;
    }
}
