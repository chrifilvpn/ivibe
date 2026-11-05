package ivibe.Database;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Gestisce la connessione al database MySQL di iVibe.
 *
 * I parametri vengono letti dal file "db.properties" nella cartella del
 * progetto (accanto a build.xml). Se il file non esiste si usano i valori
 * standard di XAMPP / MySQL locale: localhost:3306, database "ivibe",
 * utente "root" senza password.
 */
public class Database {

    private static final String FILE_CONFIG = "db.properties";

    private static String url;
    private static String utente;
    private static String password;

    private static void caricaConfigurazione() {

        if (url != null) {
            return;
        }

        Properties p = new Properties();
        File file = new File(FILE_CONFIG);

        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                p.load(in);
            } catch (Exception e) {
                System.out.println("Impossibile leggere " + FILE_CONFIG + ", uso i valori predefiniti");
            }
        }

        String host = p.getProperty("db.host", "localhost");
        String porta = p.getProperty("db.port", "3306");
        String nome = p.getProperty("db.name", "ivibe");

        utente = p.getProperty("db.user", "root");
        password = p.getProperty("db.password", "");

        url = "jdbc:mysql://" + host + ":" + porta + "/" + nome
                + "?useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=Europe/Rome&characterEncoding=UTF-8";
    }

    /**
     * Apre una nuova connessione. Va sempre usata dentro un
     * try-with-resources, così viene chiusa automaticamente.
     */
    public static Connection getConnection() throws SQLException {
        caricaConfigurazione();
        DriverManager.setLoginTimeout(5);
        return DriverManager.getConnection(url, utente, password);
    }

    /** Controlla se il database è raggiungibile. */
    public static boolean testConnessione() {
        try (Connection c = getConnection()) {
            return c.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }

    /** Messaggio d'errore leggibile da mostrare all'utente. */
    public static String messaggioErrore(SQLException e) {

        String msg = e.getMessage() == null ? "" : e.getMessage();

        if (msg.contains("No suitable driver")) {
            return "Driver MySQL mancante: aggiungi mysql-connector-j nella cartella lib";
        }
        if (msg.contains("Unknown database")) {
            return "Database 'ivibe' non trovato: importa database/ivibe.sql con HeidiSQL";
        }
        if (msg.contains("Access denied")) {
            return "Accesso al database negato: controlla utente e password in db.properties";
        }
        if (msg.contains("Communications link failure") || msg.contains("Connection refused")) {
            return "Server MySQL non raggiungibile: è avviato?";
        }
        return "Errore database: " + msg;
    }
}
