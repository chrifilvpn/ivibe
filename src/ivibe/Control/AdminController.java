package ivibe.Control;

import ivibe.Model.Canzone;
import ivibe.Model.Sessione;
import ivibe.Model.Utente;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.io.*;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

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

    private List<Canzone> canzoni = new ArrayList<>();

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
    private Utente utenteCorrente=Sessione.getUtenteCorrente();
    private List<Utente> utenti = new ArrayList<>();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        new Thread(() -> {
            canzoni = leggiCanzoni();
            utenti = leggiUtenti();

            Platform.runLater(() -> {
                setupCanzoni();
                setupUtenti();
                setupGrafica();
                aggiornaCanzoni();
                aggiornaUtenti();
                mostraCanzoni();
            });
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
        errorLabel.setText("");
    }

    private void setupCanzoni() {
        colTitolo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTitolo()));
        colArtista.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getArtista()));
        colId.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getN())));
    }

    private void aggiornaCanzoni() {
        tableSongs.getItems().setAll(canzoni);
    }

    public void aggiungiCanzone() {

        if (vuoto(titoloField) || vuoto(artistaField)
                || vuoto(durataField) || vuoto(urlField)) {
            mostraErrore("Compila tutti i campi!");
            return;
        }
        String url = urlField.getText().trim();

        if (!isValidUrl(url)) {
            mostraErrore("URL non valido!");
            return;
        }

        if (!isUrlReachable(url)) {
            mostraErrore("URL non raggiungibile!");
            return;
        }
        if(!UrlDoppione(url)){
            mostraErrore("URL già presente nella collezione!");
            return;
        }
        double durata;
        try {
            durata = Double.parseDouble(durataField.getText().trim());
        } catch (Exception e) {
            mostraErrore("Durata non valida!");
            return;
        }

        if (durata <= 0) {
            mostraErrore("La durata deve essere maggiore di 0!");
            return;
        }
        try {
            int id = getNextIdFromCSV();
            canzoni.add(new Canzone(
                    titoloField.getText().trim(),
                    artistaField.getText().trim(),
                    durata,
                    urlField.getText().trim(),
                    id
            ));
            salvaCanzoni();
            aggiornaCanzoni();
            pulisci();
            mostraSuccesso("Canzone aggiunta! (ID: " + id + ")");
        } catch (Exception e) {
            mostraErrore("Errore nei dati!");
        }
    }
    private int getNextIdFromCSV() {

        int maxId = 0;
        File file = new File("Files/canzoni.csv");

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {

            String line;
            boolean first = true;

            while ((line = br.readLine()) != null) {

                if (first) {
                    first = false;
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] p = line.replace("\"", "").split(",");

                if (p.length < 5) {
                    continue;
                }

                int id = Integer.parseInt(p[4].trim());

                if (id > maxId) {
                    maxId = id;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return maxId + 1;
    }
    private boolean UrlDoppione(String url) {

        File file = new File("Files/canzoni.csv");

        if (!file.exists()) {
            return true;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {

            String line;
            boolean first = true;

            while ((line = br.readLine()) != null) {

                if (first) {
                    first = false;
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] p = line.replace("\"", "").split(",");

                if (p.length < 4) {
                    continue;
                }

                String urlCsv = p[3].trim();

                if (urlCsv.equalsIgnoreCase(url.trim())) {
                    return false; 
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return true; 
    }
    
    private boolean isUrlReachable(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();

            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            int code = conn.getResponseCode();

            return (code >= 200 && code < 400);

        } catch (Exception e) {
            return false;
        }
    }
    private boolean isValidUrl(String url) {
        try {
            new java.net.URL(url).toURI();
            return true;
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

        canzoni.remove(c);
        salvaCanzoni();
        aggiornaCanzoni();
        mostraSuccesso("Canzone rimossa!");
    }

    private void setupUtenti() {
        colUser.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNickname()));
        colMail.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmail()));
        colUserId.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getId())));

        colPermesso.setCellValueFactory(d -> {
            if (d.getValue().isAdmin()) {
                return new SimpleStringProperty("🔑 Admin");
            } else {
                return new SimpleStringProperty("👤 Utente");
            }
        });
        colPermesso.setCellFactory(col -> new TableCell<Utente, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);

                    String color;
                    if (item.contains("Admin")) {
                        color = "#facc15";
                    } else {
                        color = "#94a3b8";
                    }

                    setStyle("-fx-alignment: CENTER; -fx-text-fill: " + color + ";");
                }
            }
        });
    }

    private void aggiornaUtenti() {
        tableUsers.getItems().setAll(utenti);
    }

    @FXML
     public void rimuoviAdmin() {
         Utente u = tableUsers.getSelectionModel().getSelectedItem();
         if (u == null) { mostraErroreUtenti("Seleziona un utente!"); return; }
         if (!u.isAdmin()) { mostraErroreUtenti("L'utente non è admin!"); return; }
         if (utenteCorrente == null || u.getId() == utenteCorrente.getId()) {
             mostraErroreUtenti("Non puoi rimuovere te stesso!"); return;
         }
         u.setIsAdmin(false);
         salvaUtenti();
         aggiornaUtenti();
         mostraSuccessoUtenti("Admin rimosso!");
     }

   @FXML
    public void eliminaUtente() {
        Utente u = tableUsers.getSelectionModel().getSelectedItem();
        if (u == null) { mostraErroreUtenti("Seleziona un utente!"); return; }
        if (u.isAdmin()) { mostraErroreUtenti("Non puoi eliminare admin!"); return; }
        utenti.remove(u);
        salvaUtenti();
        aggiornaUtenti();
        mostraSuccessoUtenti("Utente eliminato!");
    }

    @FXML
    public void rendiAdmin() {
        Utente u = tableUsers.getSelectionModel().getSelectedItem();
        if (u == null) { mostraErroreUtenti("Seleziona un utente!"); return; }
        if (u.isAdmin()) { mostraErroreUtenti("L'utente è già admin!"); return; }
        u.setIsAdmin(true);
        salvaUtenti();
        aggiornaUtenti();
        mostraSuccessoUtenti("Utente promosso ad admin!");
    }
    private void mostraErroreUtenti(String msg) {
        errorLabelUtenti.setStyle("-fx-text-fill: red;");
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

        tableSongs.setRowFactory(tv -> {
            TableRow<Canzone> row = new TableRow<>();

            row.selectedProperty().addListener((obs, oldSel, newSel) -> {
                if (!row.isEmpty()) {

                    if (newSel) {
                        row.setStyle("-fx-background-color: #7c3aed;");
                    } else {
                        row.setStyle("-fx-background-color: rgba(30,41,59,0.7);");
                    }

                }
            });

            return row;
        });

        tableUsers.setRowFactory(tv -> {
            TableRow<Utente> row = new TableRow<>();

            row.selectedProperty().addListener((obs, oldSel, newSel) -> {
                if (!row.isEmpty()) {

                    if (newSel) {
                        row.setStyle("-fx-background-color: #7c3aed;");
                    } else {
                        row.setStyle("-fx-background-color: rgba(30,41,59,0.7);");
                    }

                }
            });

            return row;
        });
    }

    private List<Canzone> leggiCanzoni() {
        List<Canzone> list = new ArrayList<>();
        File file = new File("Files/canzoni.csv");

        if (!file.exists()) {
            return list;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean first = true;

            while ((line = br.readLine()) != null) {
                if (first) {
                    first = false;
                    continue;
                }
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] p = line.replace("\"", "").split(",");

                list.add(new Canzone(
                        p[0].trim(),
                        p[1].trim(),
                        Double.parseDouble(p[2].trim()),
                        p[3].trim(),
                        Integer.parseInt(p[4].trim())
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    private List<Utente> leggiUtenti() {
        List<Utente> list = new ArrayList<>();
        File file = new File("Files/utenti.csv");

        if (!file.exists()) {
            return list;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean first = true;

            while ((line = br.readLine()) != null) {
                if (first) {
                    first = false;
                    continue;
                }
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] p = line.replace("\"", "").split(",");

                if (p.length < 6) {
                    continue;
                }

                LocalDate data;

                try {
                    data = LocalDate.parse(p[4].trim()); 
                } catch (Exception e) {
                    data = LocalDate.parse(p[4].trim(), formatter);
                }

                list.add(new Utente(
                        Integer.parseInt(p[0].trim()),
                        p[1].trim(),
                        p[3].trim(),
                        p[2].trim(),
                        data,
                        Boolean.parseBoolean(p[5].trim())
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    private void salvaCanzoni() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("Files/canzoni.csv"))) {
            bw.write("titolo,artista,durata,url,id\n");

            for (Canzone c : canzoni) {
                bw.write(c.getTitolo() + ","
                        + c.getArtista() + ","
                        + c.getDurata() + ","
                        + c.getUrl() + ","
                        + c.getN() + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void salvaUtenti() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("Files/utenti.csv"))) {
            bw.write("id,nickname,pswd,email,data,isAdmin\n");

            for (Utente u : utenti) {
                bw.write(u.getId() + ","
                        + u.getNickname() + ","
                        + u.getPswd() + ","
                        + u.getEmail() + ","
                        + u.getData().toString() + ","
                        + u.isAdmin() + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mostraErrore(String msg) {
        errorLabel.setStyle("-fx-text-fill: red;");
        errorLabel.setText(msg);
    }

    private void mostraSuccesso(String msg) {
        errorLabel.setStyle("-fx-text-fill: #22c55e;");
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ivibe/View/impostazioni.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Impostazioni");
            stage.setScene(new Scene(root));

            Image icon = new Image(getClass().getResourceAsStream("/ivibe/resources/images/LogoNS.png"));
            stage.getIcons().add(icon);

            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
