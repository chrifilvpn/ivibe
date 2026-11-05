package ivibe.Database;

import ivibe.Model.Canzone;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Operazioni sulla tabella "preferiti" (relazione utente - canzone). */
public class PreferitiDAO {

    /** Id delle canzoni preferite dell'utente, nell'ordine in cui sono state aggiunte. */
    public static List<Integer> idPreferiti(int utenteId) throws SQLException {

        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT canzone_id FROM preferiti WHERE utente_id = ? ORDER BY aggiunto_il, canzone_id";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, utenteId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    public static void aggiungi(int utenteId, Canzone c) throws SQLException {

        String sql = "INSERT IGNORE INTO preferiti (utente_id, canzone_id) VALUES (?, ?)";

        try (Connection con = Database.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, utenteId);
            ps.setInt(2, c.getN());
            ps.executeUpdate();
        }
    }

    public static void rimuovi(int utenteId, Canzone c) throws SQLException {

        String sql = "DELETE FROM preferiti WHERE utente_id = ? AND canzone_id = ?";

        try (Connection con = Database.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, utenteId);
            ps.setInt(2, c.getN());
            ps.executeUpdate();
        }
    }
}
