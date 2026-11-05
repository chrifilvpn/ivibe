package ivibe.Control;

import ivibe.Database.CanzoneDAO;
import ivibe.Database.Database;
import ivibe.Database.PlaylistDAO;
import ivibe.Database.PreferitiDAO;
import ivibe.Model.Canzone;
import ivibe.Model.Playlist;
import ivibe.Model.Sessione;
import ivibe.Model.Utente;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.*;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.SQLException;
import java.util.*;

public class UtenteController implements Initializable {

    @FXML
    private Label playlistErrorLabel;
    @FXML
    private HBox dettaglioHBox;
    @FXML
    private TextField searchAggiungiField;
    @FXML
    private VBox listaAggiungiBox;
    @FXML
    private VBox listaPlaylistBox;
    @FXML
    private Label titoloPlaylistLabel;
    @FXML
    private Label sezioneLabel;
    @FXML
    private TableView<Canzone> tableSongs;
    @FXML
    private TableColumn<Canzone, String> colTitolo;
    @FXML
    private TableColumn<Canzone, String> colArtista;
    @FXML
    private TableColumn<Canzone, Void> colLike;
    @FXML
    private TableColumn<Canzone, Void> colPlay;

    @FXML
    private TextField searchField;
    @FXML
    private Label nowPlayingLabel;
    @FXML
    private VBox playlistSection;
    @FXML
    private TextField playlistNameField;
    @FXML
    private FlowPane playlistContainer;

    private Utente u;

    /** Tutte le canzoni del catalogo. */
    private List<Canzone> lista = new ArrayList<>();
    /** Canzoni preferite dell'utente. */
    private final List<Canzone> preferiti = new ArrayList<>();
    /** Lista mostrata in tabella prima del filtro di ricerca (catalogo o preferiti). */
    private List<Canzone> vistaCorrente = new ArrayList<>();

    private final List<Playlist> playlistList = new ArrayList<>();
    private Playlist playlistSelezionata = null;

    // C'è una sola finestra di riproduzione alla volta (prima se ne potevano aprire tante
    // e le canzoni suonavano insieme, scrivendo tutte sullo stesso file)
    private static Stage playerStage;
    private static RiproduzioneController playerController;

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        u = Sessione.getUtenteCorrente();
        if (u == null) {
            return;
        }

        setupColonne();
        setupGrafica();

        try {
            lista = CanzoneDAO.tutte();
            caricaPreferiti();
            caricaPlaylist();
        } catch (SQLException e) {
            e.printStackTrace();
            nowPlayingLabel.setText("❌ " + Database.messaggioErrore(e));
        }

        aggiornaVistaPlaylist();

        vistaCorrente = lista;
        applicaFiltro();

        searchField.textProperty().addListener((obs, oldVal, newVal) -> applicaFiltro());

        // il listener va registrato UNA sola volta (prima veniva aggiunto a ogni click su una playlist)
        searchAggiungiField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (playlistSelezionata != null) {
                aggiornaListaSinistra(playlistSelezionata);
            }
        });
    }

    // ===================== TABELLA CANZONI =====================

    private void applicaFiltro() {

        String testo = searchField.getText();

        if (testo == null || testo.trim().isEmpty()) {
            tableSongs.getItems().setAll(vistaCorrente);
            return;
        }

        String f = testo.toLowerCase().trim();
        List<Canzone> filtrate = new ArrayList<>();

        for (Canzone c : vistaCorrente) {
            if (c != null && corrisponde(c, f)) {
                filtrate.add(c);
            }
        }

        tableSongs.getItems().setAll(filtrate);
    }

    private static boolean corrisponde(Canzone c, String filtro) {
        String titolo = c.getTitolo() == null ? "" : c.getTitolo().toLowerCase();
        String artista = c.getArtista() == null ? "" : c.getArtista().toLowerCase();
        return titolo.contains(filtro) || artista.contains(filtro);
    }

    private void setupGrafica() {

        tableSongs.setPlaceholder(new Label("Nessuna canzone"));

        tableSongs.setRowFactory(tv -> {
            TableRow<Canzone> row = new TableRow<Canzone>() {
                @Override
                protected void updateItem(Canzone item, boolean empty) {
                    super.updateItem(item, empty);
                    stileRiga(this, false);
                }
            };

            row.selectedProperty().addListener((obs, o, n) -> stileRiga(row, false));
            row.setOnMouseEntered(e -> stileRiga(row, true));
            row.setOnMouseExited(e -> stileRiga(row, false));

            // doppio click sulla riga = riproduci
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    riproduciDaTabella(row.getItem());
                }
            });
            return row;
        });
    }

    private void stileRiga(TableRow<Canzone> row, boolean hover) {
        if (row.isEmpty() || row.getItem() == null) {
            row.setStyle("");
        } else if (row.isSelected()) {
            row.setStyle("-fx-background-color: #7c3aed;");
        } else if (hover) {
            row.setStyle("-fx-background-color: rgba(51,65,85,0.8);");
        } else {
            row.setStyle("-fx-background-color: rgba(30,41,59,0.7);");
        }
    }

    private void setupColonne() {

        colTitolo.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue() == null || d.getValue().getTitolo() == null ? "" : d.getValue().getTitolo()));
        colTitolo.setPrefWidth(750);
        colTitolo.setMinWidth(300);

        colArtista.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue() == null || d.getValue().getArtista() == null ? "" : d.getValue().getArtista()));
        colArtista.setPrefWidth(750);
        colArtista.setMinWidth(250);

        colLike.setPrefWidth(60);
        colLike.setCellFactory(col -> new TableCell<Canzone, Void>() {

            private final Button btn = new Button();

            {
                btn.setStyle("-fx-background-color: transparent;"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 16;");
                setStyle("-fx-alignment: CENTER;");

                btn.setOnAction(e -> {
                    Canzone c = canzoneDellaRiga(this);
                    if (c != null) {
                        togglePreferito(c);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                Canzone c = empty ? null : canzoneDellaRiga(this);
                if (c == null) {
                    setGraphic(null);
                    return;
                }
                btn.setText(preferiti.contains(c) ? "❤" : "♡");
                setGraphic(btn);
            }
        });

        colPlay.setPrefWidth(60);
        colPlay.setCellFactory(col -> new TableCell<Canzone, Void>() {

            private final Button btn = new Button("▶");

            {
                btn.setStyle("-fx-background-color: #38bdf8;"
                        + "-fx-text-fill: black;"
                        + "-fx-background-radius: 10;");
                setStyle("-fx-alignment: CENTER;");

                btn.setOnAction(e -> {
                    Canzone c = canzoneDellaRiga(this);
                    if (c != null) {
                        riproduciDaTabella(c);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
    }

    private static Canzone canzoneDellaRiga(TableCell<Canzone, Void> cella) {
        int i = cella.getIndex();
        List<Canzone> items = cella.getTableView().getItems();
        if (i < 0 || i >= items.size()) {
            return null;
        }
        return items.get(i);
    }

    // ===================== PREFERITI =====================

    private void caricaPreferiti() throws SQLException {

        preferiti.clear();

        Map<Integer, Canzone> perId = new HashMap<>();
        for (Canzone c : lista) {
            perId.put(c.getN(), c);
        }

        for (int id : PreferitiDAO.idPreferiti(u.getId())) {
            Canzone c = perId.get(id);
            if (c != null) {
                preferiti.add(c);
            }
        }
    }

    private void togglePreferito(Canzone c) {
        try {
            if (preferiti.contains(c)) {
                PreferitiDAO.rimuovi(u.getId(), c);
                preferiti.remove(c);
            } else {
                PreferitiDAO.aggiungi(u.getId(), c);
                preferiti.add(c);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            Finestre.errore(Database.messaggioErrore(ex));
            return;
        }

        if (vistaCorrente == preferiti) {
            // nella pagina Preferiti la canzone tolta deve sparire subito
            applicaFiltro();
        } else {
            tableSongs.refresh();
        }
    }

    // ===================== NAVIGAZIONE =====================

    private void mostraTabella(List<Canzone> vista, String titolo) {
        playlistSelezionata = null;
        playlistSection.setVisible(false);
        playlistSection.setManaged(false);
        tableSongs.setVisible(true);
        tableSongs.setManaged(true);
        searchField.setVisible(true);
        searchField.setManaged(true);
        if (sezioneLabel != null) {
            sezioneLabel.setText(titolo);
        }
        vistaCorrente = vista;
        applicaFiltro();
    }

    @FXML
    private void apriHome() {
        mostraTabella(lista, "🏠 Tutte le canzoni");
    }

    @FXML
    private void apriPreferiti() {
        mostraTabella(preferiti, "❤ I tuoi preferiti");
    }

    @FXML
    private void apriPlaylist() {

        try {
            caricaPlaylist();
        } catch (SQLException e) {
            e.printStackTrace();
            Finestre.errore(Database.messaggioErrore(e));
        }
        aggiornaVistaPlaylist();
        nascondiDettaglio();

        if (sezioneLabel != null) {
            sezioneLabel.setText("🎧 Le tue playlist");
        }

        playlistSection.setVisible(true);
        playlistSection.setManaged(true);

        tableSongs.setVisible(false);
        tableSongs.setManaged(false);

        searchField.setVisible(false);
        searchField.setManaged(false);
    }

    @FXML
    private void apriImpostazioni() {
        try {
            FXMLLoader loader = Finestre.apri(Finestre.IMPOSTAZIONI, "Impostazioni", false);
            ImpostazioniController controller = loader.getController();
            controller.setHomeStage((Stage) tableSongs.getScene().getWindow());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void logout() {
        Sessione.logout();
        chiudiPlayer();
        Stage stage = (Stage) tableSongs.getScene().getWindow();
        Finestre.sostituisci(stage, Finestre.LOGIN, "Login");
    }

    // ===================== PLAYLIST =====================

    private void caricaPlaylist() throws SQLException {
        playlistList.clear();
        playlistList.addAll(PlaylistDAO.playlistUtente(u.getId()));
    }

    @FXML
    private void creaPlaylist() {

        String nome = playlistNameField.getText() == null ? "" : playlistNameField.getText().trim();

        if (nome.isEmpty()) {
            errorePlaylist("Il nome non può essere vuoto");
            return;
        }
        if (nome.length() > 100) {
            errorePlaylist("Nome troppo lungo (max 100 caratteri)");
            return;
        }

        for (Playlist p : playlistList) {
            if (p.getNome().equalsIgnoreCase(nome)) {
                errorePlaylist("Playlist già esistente");
                return;
            }
        }

        try {
            Playlist nuova = PlaylistDAO.crea(u.getId(), nome);
            if (nuova != null) {
                playlistList.add(nuova);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            errorePlaylist(Database.messaggioErrore(e));
            return;
        }

        playlistNameField.clear();
        playlistErrorLabel.setText("");
        aggiornaVistaPlaylist();
    }

    private void errorePlaylist(String msg) {
        playlistErrorLabel.setText(msg);
        playlistErrorLabel.setStyle("-fx-text-fill: #f87171;");
    }

    private void nascondiDettaglio() {
        dettaglioHBox.setVisible(false);
        dettaglioHBox.setManaged(false);
        playlistSelezionata = null;
    }

    /** Ridisegna i riquadri delle playlist (senza chiudere quella aperta). */
    private void aggiornaVistaPlaylist() {

        playlistContainer.getChildren().clear();

        if (playlistList.isEmpty()) {
            Label vuota = new Label("Non hai ancora nessuna playlist: creane una qui sopra!");
            vuota.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic;");
            playlistContainer.getChildren().add(vuota);
        }

        for (Playlist p : playlistList) {

            VBox box = new VBox(5);
            String bordo = (p == playlistSelezionata) ? "-fx-border-color: #38bdf8; -fx-border-radius: 10;" : "";
            box.setStyle("-fx-background-color: #1e293b;"
                    + "-fx-padding: 15;"
                    + "-fx-background-radius: 10;"
                    + "-fx-min-width: 120;"
                    + "-fx-alignment: center;"
                    + "-fx-cursor: hand;"
                    + bordo);

            Label nome = new Label(p.getNome());
            nome.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

            Label count = new Label(p.getNumeroCanzoni() + (p.getNumeroCanzoni() == 1 ? " canzone" : " canzoni"));
            count.setStyle("-fx-text-fill: #94a3b8;");

            Button btnElimina = new Button("✕");
            btnElimina.setStyle("-fx-background-color: transparent;"
                    + "-fx-text-fill: #f87171;"
                    + "-fx-font-size: 12;");
            btnElimina.setOnAction(e -> {
                e.consume();
                eliminaPlaylist(p);
            });

            box.getChildren().addAll(nome, count, btnElimina);
            box.setOnMouseClicked(e -> mostraDettaglioPlaylist(p));
            playlistContainer.getChildren().add(box);
        }
    }

    private void eliminaPlaylist(Playlist p) {

        if (!Finestre.conferma("Eliminare la playlist \"" + p.getNome() + "\"?")) {
            return;
        }

        try {
            PlaylistDAO.elimina(p.getId());
        } catch (SQLException e) {
            e.printStackTrace();
            errorePlaylist(Database.messaggioErrore(e));
            return;
        }

        playlistList.remove(p);
        if (p == playlistSelezionata) {
            nascondiDettaglio();
        }
        aggiornaVistaPlaylist();
    }

    private void mostraDettaglioPlaylist(Playlist p) {

        playlistSelezionata = p;
        titoloPlaylistLabel.setText("📋 " + p.getNome());

        dettaglioHBox.setVisible(true);
        dettaglioHBox.setManaged(true);

        aggiornaListaSinistra(p);
        aggiornaListaDestra(p);
        aggiornaVistaPlaylist();
    }

    private void toggleCanzonePlaylist(Playlist p, Canzone c) {
        try {
            if (p.containsCanzone(c)) {
                PlaylistDAO.rimuoviCanzone(p.getId(), c);
                p.removeCanzone(c);
            } else {
                PlaylistDAO.aggiungiCanzone(p.getId(), c);
                p.addCanzone(c);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Finestre.errore(Database.messaggioErrore(e));
            return;
        }

        aggiornaListaSinistra(p);
        aggiornaListaDestra(p);
        aggiornaVistaPlaylist();
    }

    private HBox creaRigaLista() {
        String base = "-fx-padding: 8 10; -fx-background-radius: 8; -fx-alignment: CENTER_LEFT;";
        HBox riga = new HBox(12);
        riga.setPrefHeight(42);
        riga.setStyle(base);
        riga.setOnMouseEntered(e -> riga.setStyle("-fx-background-color: rgba(51,65,85,0.8);" + base));
        riga.setOnMouseExited(e -> riga.setStyle(base));
        return riga;
    }

    private static String testoCanzone(Canzone c) {
        String titolo = c.getTitolo() == null ? "" : c.getTitolo();
        String artista = c.getArtista() == null ? "" : c.getArtista();
        return titolo + " — " + artista;
    }

    /** Colonna sinistra: tutto il catalogo, con ➕ / ✓ per aggiungere o togliere. */
    private void aggiornaListaSinistra(Playlist p) {

        listaAggiungiBox.getChildren().clear();

        String testo = searchAggiungiField.getText();
        String f = testo == null ? "" : testo.toLowerCase().trim();

        for (Canzone c : lista) {

            if (c == null || (!f.isEmpty() && !corrisponde(c, f))) {
                continue;
            }

            boolean inPlaylist = p.containsCanzone(c);

            Button btnAggiungi = new Button(inPlaylist ? "✓" : "➕");
            btnAggiungi.setStyle("-fx-background-color: transparent;"
                    + "-fx-text-fill: " + (inPlaylist ? "#4ade80" : "#38bdf8") + ";"
                    + "-fx-font-size: 16;"
                    + "-fx-font-weight: bold;");
            btnAggiungi.setPrefSize(40, 40);
            btnAggiungi.setOnAction(e -> toggleCanzonePlaylist(p, c));

            Label nomeSong = new Label(testoCanzone(c));
            nomeSong.setStyle("-fx-text-fill: white; -fx-font-size: 14;");
            nomeSong.setPrefWidth(320);

            HBox riga = creaRigaLista();
            riga.getChildren().addAll(nomeSong, btnAggiungi);
            listaAggiungiBox.getChildren().add(riga);
        }
    }

    /** Colonna destra: le canzoni della playlist, con ▶ per riprodurre e ✕ per togliere. */
    private void aggiornaListaDestra(Playlist p) {

        listaPlaylistBox.getChildren().clear();

        if (p.getCanzoni().isEmpty()) {
            Label vuota = new Label("Nessuna canzone aggiunta");
            vuota.setStyle("-fx-text-fill: #64748b; -fx-font-style: italic; -fx-font-size: 14;");
            listaPlaylistBox.getChildren().add(vuota);
            return;
        }

        for (Canzone c : p.getCanzoni()) {

            if (c == null) {
                continue;
            }

            Label nomeSong = new Label(testoCanzone(c));
            nomeSong.setStyle("-fx-text-fill: white; -fx-font-size: 14;");
            nomeSong.setPrefWidth(280);

            Button btnPlay = new Button("▶");
            btnPlay.setStyle("-fx-background-color: transparent; -fx-text-fill: #38bdf8; -fx-font-size: 14;");
            btnPlay.setOnAction(e -> riproduci(c, p.getCanzoni(), p.getNome()));

            Button btnRimuovi = new Button("✕");
            btnRimuovi.setStyle("-fx-background-color: transparent;"
                    + "-fx-text-fill: #f87171;"
                    + "-fx-font-size: 16;"
                    + "-fx-font-weight: bold;");
            btnRimuovi.setPrefSize(40, 40);
            btnRimuovi.setOnAction(e -> toggleCanzonePlaylist(p, c));

            HBox riga = creaRigaLista();
            riga.getChildren().addAll(nomeSong, btnPlay, btnRimuovi);
            listaPlaylistBox.getChildren().add(riga);
        }
    }

    @FXML
    private void shufflePlaylist() {

        if (playlistSelezionata == null || playlistSelezionata.getCanzoni().isEmpty()) {
            return;
        }

        List<Canzone> canzoni = new ArrayList<>(playlistSelezionata.getCanzoni());
        Collections.shuffle(canzoni);
        riproduci(canzoni.get(0), canzoni, playlistSelezionata.getNome());
    }

    @FXML
    private void playPlaylist() {

        if (playlistSelezionata == null || playlistSelezionata.getCanzoni().isEmpty()) {
            return;
        }

        List<Canzone> canzoni = new ArrayList<>(playlistSelezionata.getCanzoni());
        riproduci(canzoni.get(0), canzoni, playlistSelezionata.getNome());
    }

    // ===================== RIPRODUZIONE =====================

    /** Dalla tabella: la coda è la lista visualizzata, così si passa alla canzone dopo. */
    private void riproduciDaTabella(Canzone c) {
        riproduci(c, new ArrayList<>(tableSongs.getItems()), null);
    }

    private void riproduci(Canzone c, List<Canzone> coda, String titoloFinestra) {

        nowPlayingLabel.setText("🎵 " + c.getTitolo() + " — " + c.getArtista());

        try {
            if (playerStage == null || !playerStage.isShowing()) {
                FXMLLoader loader = Finestre.apri(Finestre.RIPRODUZIONE,
                        titoloFinestra != null ? titoloFinestra : c.getTitolo(), false);
                playerController = loader.getController();
                playerStage = (Stage) loader.<javafx.scene.Parent>getRoot().getScene().getWindow();
            } else {
                playerStage.toFront();
            }

            playerController.riproduci(c, coda);

        } catch (Exception e) {
            e.printStackTrace();
            Finestre.errore("Impossibile aprire il lettore");
        }
    }

    /** Pulsante ▶ nella barra in basso: riporta in primo piano il lettore o suona la canzone selezionata. */
    @FXML
    private void mostraPlayer() {
        if (playerStage != null && playerStage.isShowing()) {
            playerStage.toFront();
            playerController.play();
            return;
        }
        Canzone sel = tableSongs.getSelectionModel().getSelectedItem();
        if (sel == null && !tableSongs.getItems().isEmpty() && tableSongs.isVisible()) {
            sel = tableSongs.getItems().get(0);
        }
        if (sel != null) {
            riproduciDaTabella(sel);
        }
    }

    /** Chiude il lettore (usato al logout). */
    public static void chiudiPlayer() {
        if (playerStage != null) {
            playerStage.close();
        }
        playerStage = null;
        playerController = null;
    }
}
