package ivibe.Control;

import ivibe.Database.CanzoneDAO;
import ivibe.Database.Database;
import ivibe.Database.UtenteDAO;
import ivibe.Model.Canzone;
import ivibe.Model.Sessione;
import ivibe.Model.Utente;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.*;

public class AdminController implements Initializable {

    @FXML
    private TextField titoloField;
    @FXML
    private TextField artistaField;
    @FXML
    private TextField durataField;
    @FXML
    private TextField urlField;
    @FXML
    private Button aggiungiBtn;
    @FXML
    private Label errorLabel;
    @FXML
    private Label errorLabelUtenti;
    @FXML
    private TableView<Canzone> tableSongs;
    @FXML
    private TableColumn<Canzone, String> colTitolo;
    @FXML
    private TableColumn<Canzone, String> colArtista;
    @FXML
    private TableColumn<Canzone, String> colId;

    @FXML
    private VBox boxCanzoni;

    @FXML
    private TableView<Utente> tableUsers;
    @FXML
    private TableColumn<Utente, String> colUser;
    @FXML
    private TableColumn<Utente, String> colMail;
    @FXML
    private TableColumn<Utente, String> colUserId;
    @FXML
    private TableColumn<Utente, String> colPermesso;
    @FXML
    private VBox boxUtenti;

    private final Utente utenteCorrente = Sessione.getUtenteCorrente();

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        setupCanzoni();
        setupUtenti();
        setupGrafica();
        mostraCanzoni();

        // le query vengono fatte in un thread separato per non bloccare la grafica
        new Thread(() -> {
            try {
                List<Canzone> canzoni = CanzoneDAO.tutte();
                List<Utente> utenti = UtenteDAO.tutti();

                Platform.runLater(() -> {
                    tableSongs.getItems().setAll(canzoni);
                    tableUsers.getItems().setAll(utenti);
                });
            } catch (SQLException e) {
                e.printStackTrace();
                Platform.runLater(() -> mostraErrore(Database.messaggioErrore(e)));
            }
        }).start();
    }

    @FXML
    public void mostraCanzoni() {
        boxCanzoni.setVisible(true);
        boxUtenti.setVisible(false);
        errorLabel.setText("");
    }

    @FXML
    public void mostraUtenti() {
        boxCanzoni.setVisible(false);
        boxUtenti.setVisible(true);
        errorLabelUtenti.setText("");
    }

    // ===================== CANZONI =====================

    private void setupCanzoni() {
        colTitolo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTitolo()));
        colArtista.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getArtista()));
        colId.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getN())));
    }

    private void ricaricaCanzoni() throws SQLException {
        tableSongs.getItems().setAll(CanzoneDAO.tutte());
    }

    @FXML
    public void aggiungiCanzone() {

        if (vuoto(titoloField) || vuoto(artistaField) || vuoto(durataField) || vuoto(urlField)) {
            mostraErrore("Compila tutti i campi!");
            return;
        }

        String titolo = titoloField.getText().trim();
        String artista = artistaField.getText().trim();
        String url = urlField.getText().trim();

        if (titolo.length() > 150 || artista.length() > 150) {
            mostraErrore("Titolo o artista troppo lunghi (max 150 caratteri)");
            return;
        }

        double durata;
        try {
            // accetta sia 3.45 che 3,45
            durata = Double.parseDouble(durataField.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            mostraErrore("Durata non valida! (es. 3.45)");
            return;
        }

        if (durata <= 0 || durata > 999) {
            mostraErrore("La durata deve essere maggiore di 0!");
            return;
        }

        if (!isValidUrl(url)) {
            mostraErrore("URL non valido! Deve iniziare con http:// o https://");
            return;
        }

        try {
            if (CanzoneDAO.esisteUrl(url)) {
                mostraErrore("URL già presente nella collezione!");
                return;
            }
        } catch (SQLException e) {
            mostraErrore(Database.messaggioErrore(e));
            return;
        }

        // il controllo dell'URL fa una richiesta in rete: lo facciamo in background
        if (aggiungiBtn != null) {
            aggiungiBtn.setDisable(true);
        }
        mostraInfo("Controllo URL in corso...");

        new Thread(() -> {
            boolean raggiungibile = isUrlReachable(url);

            Platform.runLater(() -> {
                if (aggiungiBtn != null) {
                    aggiungiBtn.setDisable(false);
                }

                if (!raggiungibile) {
                    mostraErrore("URL non raggiungibile!");
                    return;
                }

                try {
                    int id = CanzoneDAO.inserisci(titolo, artista, durata, url);
                    ricaricaCanzoni();
                    pulisci();
                    mostraSuccesso("Canzone aggiunta! (ID: " + id + ")");
                } catch (SQLException e) {
                    mostraErrore(Database.messaggioErrore(e));
                    e.printStackTrace();
                }
            });
        }).start();
    }

    private boolean isUrlReachable(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            int code = conn.getResponseCode();
            conn.disconnect();

            return code >= 200 && code < 400;

        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidUrl(String url) {
        try {
            URL u = new URL(url);
            u.toURI();
            String protocollo = u.getProtocol();
            return (protocollo.equals("http") || protocollo.equals("https")) && url.length() <= 255;
        } catch (Exception e) {
            return false;
        }
    }

    @FXML
    public void rimuoviCanzone() {

        Canzone c = tableSongs.getSelectionModel().getSelectedItem();

        if (c == null) {
            mostraErrore("Seleziona una canzone!");
            return;
        }

        if (!Finestre.conferma("Eliminare \"" + c.getTitolo() + "\"?\n"
                + "Verrà tolta anche dai preferiti e dalle playlist di tutti gli utenti.")) {
            return;
        }

        try {
            CanzoneDAO.elimina(c.getN());
            ricaricaCanzoni();
            mostraSuccesso("Canzone rimossa!");
        } catch (SQLException e) {
            mostraErrore(Database.messaggioErrore(e));
            e.printStackTrace();
        }
    }

    // ===================== UTENTI =====================

    private void setupUtenti() {
        colUser.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNickname()));
        colMail.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmail()));
        colUserId.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getId())));

        colPermesso.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().isAdmin() ? "🔑 Admin" : "👤 Utente"));

        colPermesso.setCellFactory(col -> new TableCell<Utente, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    String color = item.contains("Admin") ? "#facc15" : "#94a3b8";
                    setStyle("-fx-alignment: CENTER; -fx-text-fill: " + color + ";");
                }
            }
        });
    }

    private void ricaricaUtenti() throws SQLException {
        tableUsers.getItems().setAll(UtenteDAO.tutti());
    }

    @FXML
    public void rimuoviAdmin() {
        Utente u = tableUsers.getSelectionModel().getSelectedItem();
        if (u == null) {
            mostraErroreUtenti("Seleziona un utente!");
            return;
        }
        if (!u.isAdmin()) {
            mostraErroreUtenti("L'utente non è admin!");
            return;
        }
        if (utenteCorrente == null || u.getId() == utenteCorrente.getId()) {
            mostraErroreUtenti("Non puoi rimuovere te stesso!");
            return;
        }
        try {
            UtenteDAO.impostaAdmin(u.getId(), false);
            ricaricaUtenti();
            mostraSuccessoUtenti("Admin rimosso!");
        } catch (SQLException e) {
            mostraErroreUtenti(Database.messaggioErrore(e));
        }
    }

    @FXML
    public void eliminaUtente() {
        Utente u = tableUsers.getSelectionModel().getSelectedItem();
        if (u == null) {
            mostraErroreUtenti("Seleziona un utente!");
            return;
        }
        if (u.isAdmin()) {
            mostraErroreUtenti("Non puoi eliminare un admin! Prima togli il ruolo.");
            return;
        }
        if (!Finestre.conferma("Eliminare l'utente \"" + u.getNickname() + "\"?\n"
                + "Verranno cancellati anche i suoi preferiti e le sue playlist.")) {
            return;
        }
        try {
            UtenteDAO.elimina(u.getId());
            ricaricaUtenti();
            mostraSuccessoUtenti("Utente eliminato!");
        } catch (SQLException e) {
            mostraErroreUtenti(Database.messaggioErrore(e));
        }
    }

    @FXML
    public void rendiAdmin() {
        Utente u = tableUsers.getSelectionModel().getSelectedItem();
        if (u == null) {
            mostraErroreUtenti("Seleziona un utente!");
            return;
        }
        if (u.isAdmin()) {
            mostraErroreUtenti("L'utente è già admin!");
            return;
        }
        try {
            UtenteDAO.impostaAdmin(u.getId(), true);
            ricaricaUtenti();
            mostraSuccessoUtenti("Utente promosso ad admin!");
        } catch (SQLException e) {
            mostraErroreUtenti(Database.messaggioErrore(e));
        }
    }

    // ===================== GRAFICA / MESSAGGI =====================

    private void mostraErroreUtenti(String msg) {
        errorLabelUtenti.setStyle("-fx-text-fill: #f87171;");
        errorLabelUtenti.setText(msg);
    }

    private void mostraSuccessoUtenti(String msg) {
        errorLabelUtenti.setStyle("-fx-text-fill: #22c55e;");
        errorLabelUtenti.setText(msg);
    }

    private void setupGrafica() {
        String base = "-fx-background-color: rgba(30,41,59,0.7);"
                + "-fx-control-inner-background: rgba(30,41,59,0.3);"
                + "-fx-table-cell-border-color: transparent;"
                + "-fx-text-fill: white;";

        tableSongs.setStyle(base);
        tableUsers.setStyle(base);

        tableSongs.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableUsers.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        tableSongs.setRowFactory(tv -> creaRiga());
        tableUsers.setRowFactory(tv -> creaRiga());
    }

    /** Riga colorata in viola quando è selezionata (aggiornata anche quando la riga viene riutilizzata). */
    private <T> TableRow<T> creaRiga() {
        TableRow<T> row = new TableRow<T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                aggiornaStile(this);
            }
        };
        row.selectedProperty().addListener((obs, o, n) -> aggiornaStile(row));
        return row;
    }

    private <T> void aggiornaStile(TableRow<T> row) {
        if (row.isEmpty() || row.getItem() == null) {
            row.setStyle("");
        } else if (row.isSelected()) {
            row.setStyle("-fx-background-color: #7c3aed;");
        } else {
            row.setStyle("-fx-background-color: rgba(30,41,59,0.7);");
        }
    }

    private void mostraErrore(String msg) {
        errorLabel.setStyle("-fx-text-fill: #f87171;");
        errorLabel.setText(msg);
    }

    private void mostraSuccesso(String msg) {
        errorLabel.setStyle("-fx-text-fill: #22c55e;");
        errorLabel.setText(msg);
    }

    private void mostraInfo(String msg) {
        errorLabel.setStyle("-fx-text-fill: white;");
        errorLabel.setText(msg);
    }

    private boolean vuoto(TextField f) {
        return f.getText() == null || f.getText().trim().isEmpty();
    }

    private void pulisci() {
        titoloField.clear();
        artistaField.clear();
        durataField.clear();
        urlField.clear();
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
}
