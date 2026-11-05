package ivibe.Database;

import ivibe.Model.Utente;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Tutte le operazioni sulla tabella "utenti". */
public class UtenteDAO {

    private static final String CAMPI = "id, nickname, email, password, data_nascita, is_admin";

    private static Utente leggiRiga(ResultSet rs) throws SQLException {

        Date d = rs.getDate("data_nascita");
        LocalDate data = (d != null) ? d.toLocalDate() : null;

        return new Utente(
                rs.getInt("id"),
                rs.getString("nickname"),
                rs.getString("email"),
                rs.getString("password"),
                data,
                rs.getBoolean("is_admin")
        );
    }

    /** Cerca un utente per nickname oppure email. Restituisce null se non esiste. */
    public static Utente trovaPerNicknameOEmail(String input) throws SQLException {

        String sql = "SELECT " + CAMPI + " FROM utenti WHERE nickname = ? OR email = ?";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, input);
            ps.setString(2, input);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return leggiRiga(rs);
                }
            }
        }
        return null;
    }

    public static List<Utente> tutti() throws SQLException {

        List<Utente> lista = new ArrayList<>();
        String sql = "SELECT " + CAMPI + " FROM utenti ORDER BY id";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                lista.add(leggiRiga(rs));
            }
        }
        return lista;
    }

    /** true se esiste già un utente con quel nickname o quella email. */
    public static boolean esiste(String nickname, String email) throws SQLException {

        String sql = "SELECT 1 FROM utenti WHERE nickname = ? OR email = ?";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, nickname);
            ps.setString(2, email);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Inserisce un nuovo utente e restituisce l'id generato dal database. */
    public static int inserisci(String nickname, String email, String passwordHash,
            LocalDate dataNascita, boolean isAdmin) throws SQLException {

        String sql = "INSERT INTO utenti (nickname, email, password, data_nascita, is_admin) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, nickname);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.setDate(4, dataNascita != null ? Date.valueOf(dataNascita) : null);
            ps.setBoolean(5, isAdmin);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public static void aggiornaPassword(int id, String passwordHash) throws SQLException {

        String sql = "UPDATE utenti SET password = ? WHERE id = ?";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, passwordHash);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public static void impostaAdmin(int id, boolean isAdmin) throws SQLException {

        String sql = "UPDATE utenti SET is_admin = ? WHERE id = ?";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setBoolean(1, isAdmin);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /** Elimina l'utente: preferiti e playlist vengono cancellati in automatico (ON DELETE CASCADE). */
    public static void elimina(int id) throws SQLException {

        String sql = "DELETE FROM utenti WHERE id = ?";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public static int contaAdmin() throws SQLException {

        String sql = "SELECT COUNT(*) FROM utenti WHERE is_admin = TRUE";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            rs.next();
            return rs.getInt(1);
        }
    }
}
