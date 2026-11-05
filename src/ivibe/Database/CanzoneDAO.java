package ivibe.Database;

import ivibe.Model.Canzone;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Tutte le operazioni sulla tabella "canzoni". */
public class CanzoneDAO {

    static Canzone leggiRiga(ResultSet rs) throws SQLException {
        return new Canzone(
                rs.getString("titolo"),
                rs.getString("artista"),
                rs.getDouble("durata"),
                rs.getString("url"),
                rs.getInt("id")
        );
    }

    public static List<Canzone> tutte() throws SQLException {

        List<Canzone> lista = new ArrayList<>();
        String sql = "SELECT id, titolo, artista, durata, url FROM canzoni ORDER BY id";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                lista.add(leggiRiga(rs));
            }
        }
        return lista;
    }

    public static boolean esisteUrl(String url) throws SQLException {

        String sql = "SELECT 1 FROM canzoni WHERE url = ?";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, url);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Inserisce la canzone e restituisce l'id generato dal database. */
    public static int inserisci(String titolo, String artista, double durata, String url) throws SQLException {

        String sql = "INSERT INTO canzoni (titolo, artista, durata, url) VALUES (?, ?, ?, ?)";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, titolo);
            ps.setString(2, artista);
            ps.setDouble(3, durata);
            ps.setString(4, url);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    /** Elimina la canzone: viene tolta anche da preferiti e playlist (ON DELETE CASCADE). */
    public static void elimina(int id) throws SQLException {

        String sql = "DELETE FROM canzoni WHERE id = ?";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
