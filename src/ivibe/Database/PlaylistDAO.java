package ivibe.Database;

import ivibe.Model.Canzone;
import ivibe.Model.Playlist;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Operazioni sulle tabelle "playlist" e "playlist_canzoni". */
public class PlaylistDAO {

    /** Tutte le playlist dell'utente, già riempite con le loro canzoni. */
    public static List<Playlist> playlistUtente(int utenteId) throws SQLException {

        Map<Integer, Playlist> mappa = new LinkedHashMap<>();

        String sqlPlaylist = "SELECT id, nome FROM playlist WHERE utente_id = ? ORDER BY id";

        String sqlCanzoni = "SELECT pc.playlist_id, c.id, c.titolo, c.artista, c.durata, c.url "
                + "FROM playlist_canzoni pc "
                + "JOIN playlist p ON p.id = pc.playlist_id "
                + "JOIN canzoni c ON c.id = pc.canzone_id "
                + "WHERE p.utente_id = ? "
                + "ORDER BY pc.playlist_id, pc.posizione";

        try (Connection c = Database.getConnection()) {

            try (PreparedStatement ps = c.prepareStatement(sqlPlaylist)) {
                ps.setInt(1, utenteId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        mappa.put(id, new Playlist(id, rs.getString("nome")));
                    }
                }
            }

            try (PreparedStatement ps = c.prepareStatement(sqlCanzoni)) {
                ps.setInt(1, utenteId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Playlist p = mappa.get(rs.getInt("playlist_id"));
                        if (p != null) {
                            p.addCanzone(CanzoneDAO.leggiRiga(rs));
                        }
                    }
                }
            }
        }

        return new ArrayList<>(mappa.values());
    }

    /** Crea una playlist vuota e restituisce l'oggetto con l'id del database. */
    public static Playlist crea(int utenteId, String nome) throws SQLException {

        String sql = "INSERT INTO playlist (utente_id, nome) VALUES (?, ?)";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, utenteId);
            ps.setString(2, nome);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return new Playlist(rs.getInt(1), nome);
                }
            }
        }
        return null;
    }

    public static void elimina(int playlistId) throws SQLException {

        String sql = "DELETE FROM playlist WHERE id = ?";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, playlistId);
            ps.executeUpdate();
        }
    }

    /** Aggiunge la canzone in fondo alla playlist. */
    public static void aggiungiCanzone(int playlistId, Canzone canzone) throws SQLException {

        String sql = "INSERT IGNORE INTO playlist_canzoni (playlist_id, canzone_id, posizione) "
                + "SELECT ?, ?, COALESCE(MAX(posizione) + 1, 0) FROM playlist_canzoni WHERE playlist_id = ?";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, playlistId);
            ps.setInt(2, canzone.getN());
            ps.setInt(3, playlistId);
            ps.executeUpdate();
        }
    }

    public static void rimuoviCanzone(int playlistId, Canzone canzone) throws SQLException {

        String sql = "DELETE FROM playlist_canzoni WHERE playlist_id = ? AND canzone_id = ?";

        try (Connection c = Database.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, playlistId);
            ps.setInt(2, canzone.getN());
            ps.executeUpdate();
        }
    }
}
