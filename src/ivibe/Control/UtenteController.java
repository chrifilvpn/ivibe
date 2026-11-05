package ivibe.Control;

import ivibe.Model.Canzone;
import ivibe.Model.Playlist;
import ivibe.Model.Utente;
import ivibe.Model.Sessione;
import javafx.fxml.*;
import javafx.scene.control.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;
import java.util.*;
import java.io.*;
import javafx.scene.image.Image;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

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
    private FlowPane dettaglioContainer;
    private List<Canzone> lista = new ArrayList<Canzone>();
    private List<Canzone> preferiti = new ArrayList<Canzone>();
    private List<String> playlistRighe = new ArrayList<>();
    private Utente u;
    private List<Canzone> vistaCorrente = new ArrayList<Canzone>();
    private Playlist playlistSelezionata = null;
    @FXML
    private TextField playlistNameField;

    @FXML
    private FlowPane playlistContainer;

    private List<Playlist> playlistList = new ArrayList<>();

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        u = Sessione.getUtenteCorrente();
        if (u == null) {
            return;
        }

        lista = leggiCSV();
        caricaPreferiti();
        caricaPlaylist();
        aggiornaVistaPlaylist();

        setupColonne();
        setupGrafica();

        mostra(lista);
        vistaCorrente = lista;

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {

            if (newVal == null || newVal.trim().isEmpty()) {
                mostra(vistaCorrente);
                return;
            }

            String f = newVal.toLowerCase();
            List<Canzone> filtrate = new ArrayList<Canzone>();

            for (Canzone c : vistaCorrente) {

                if (c == null) {
                    continue;
                }

                String titolo = c.getTitolo();
                String artista = c.getArtista();

                if (titolo != null && artista != null) {

                    if (titolo.toLowerCase().contains(f) || artista.toLowerCase().contains(f)) {
                        filtrate.add(c);
                    }
                }
            }

            mostra(filtrate);
        });
    }

    private void setupGrafica() {

        tableSongs.setRowFactory(tv -> new TableRow<Canzone>() {

            @Override
            protected void updateItem(Canzone item, boolean empty) {

                super.updateItem(item, empty);

                if (empty || item == null) {
                    setStyle("");
                    return;
                }

                if (isSelected()) {
                    setStyle("-fx-background-color: #7c3aed;");
                } else {
                    setStyle("-fx-background-color: rgba(30,41,59,0.7);");
                }

                setOnMouseEntered(e -> {
                    if (!empty && !isSelected() && getItem() != null) {
                        setStyle("-fx-background-color: rgba(51,65,85,0.8);");
                    }
                });

                setOnMouseExited(e -> {
                    if (!empty && !isSelected() && getItem() != null) {
                        setStyle("-fx-background-color: rgba(30,41,59,0.7);");
                    }
                });
            }
        });
    }

    private void setupColonne() {

        colTitolo.setCellValueFactory(d -> {
            if (d == null || d.getValue() == null) {
                return new SimpleStringProperty("");
            }
            String t = d.getValue().getTitolo();
            if (t != null) {
                return new SimpleStringProperty(t);
            } else {
                return new SimpleStringProperty("");
            }
        });

        colTitolo.setPrefWidth(750);
        colTitolo.setMinWidth(300);

        colArtista.setCellValueFactory(d -> {
            if (d == null || d.getValue() == null) {
                return new SimpleStringProperty("");
            }
            String a = d.getValue().getArtista();
            if (a != null) {
                return new SimpleStringProperty(a);
            } else {
                return new SimpleStringProperty("");
            }
        });

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

                    if (getIndex() < 0) {
                        return;
                    }

                    Canzone c = getTableView().getItems().get(getIndex());
                    if (c == null) {
                        return;
                    }

                    if (preferiti.contains(c)) {
                        preferiti.remove(c);
                    } else {
                        preferiti.add(c);
                    }

                    aggiornaPreferiti(u.getId(), preferiti);
                    tableSongs.refresh();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0) {
                    setGraphic(null);
                    return;
                }
                Canzone c = getTableView().getItems().get(getIndex());
                if (c == null) {
                    setGraphic(null);
                    return;
                }
                if (preferiti.contains(c)) {
                    btn.setText("❤");
                } else {
                    btn.setText("♡");
                }
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

                    if (getIndex() < 0) {
                        return;
                    }

                    Canzone c = getTableView().getItems().get(getIndex());
                    if (c == null) {
                        return;
                    }

                    String titolo = c.getTitolo();
                    if (titolo == null) {
                        titolo = "";
                    }

                    nowPlayingLabel.setText("🎵 " + titolo);
                    apriRiproduzione(c);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {

                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(btn);
                }
            }
        });
    }

    private void mostra(List<Canzone> list) {

        if (list == null) {
            tableSongs.getItems().clear();
            return;
        }

        tableSongs.getItems().setAll(list);
    }

    public static ArrayList<Canzone> leggiCSV() {

        ArrayList<Canzone> list = new ArrayList<Canzone>();
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

                if (line == null || line.trim().isEmpty()) {
                    continue;
                }

                line = line.replace("\"", "").trim();

                String[] p = line.split(",");

                if (p.length < 5) {
                    continue;
                }

                try {

                    int n = Integer.parseInt(p[4].trim());

                    list.add(new Canzone(
                            p[0].trim(),
                            p[1].trim(),
                            Double.parseDouble(p[2].trim()),
                            p[3].trim(),
                            n
                    ));

                } catch (NumberFormatException ex) {
                    System.out.println("RIGA SKIPPATA: " + line);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    public static void aggiornaPreferiti(int userId, List<Canzone> preferiti) {

        File file = new File("Files/preferiti.csv");

        List<String> output = new ArrayList<String>();
        boolean trovato = false;

        try {

            if (!file.exists()) {
                file.createNewFile();
            }

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {

                String line;
                boolean first = true;

                while ((line = br.readLine()) != null) {

                    if (first) {
                        output.add(line);
                        first = false;
                        continue;
                    }

                    if (line == null || line.trim().isEmpty()) {
                        continue;
                    }

                    String[] p = line.split(",");
                    if (p.length < 1) {
                        continue;
                    }

                    int id = Integer.parseInt(p[0].trim());

                    if (id == userId) {

                        trovato = true;

                        String ids = "";

                        for (int i = 0; i < preferiti.size(); i++) {

                            Canzone c = preferiti.get(i);

                            if (c != null) {
                                ids += c.getN();
                            }

                            if (i < preferiti.size() - 1) {
                                ids += "|";
                            }
                        }

                        output.add(userId + "," + ids);

                    } else {
                        output.add(line);
                    }
                }
            }

            if (!trovato) {

                if (output.isEmpty()) {
                    output.add("userId,songIds");
                }

                String ids = "";

                for (int i = 0; i < preferiti.size(); i++) {

                    Canzone c = preferiti.get(i);

                    if (c != null) {
                        ids += c.getN();
                    }

                    if (i < preferiti.size() - 1) {
                        ids += "|";
                    }
                }

                output.add(userId + "," + ids);
            }

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {

                for (String l : output) {
                    if (l != null) {
                        bw.write(l);
                        bw.newLine();
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void caricaPreferiti() {

        preferiti.clear();

        File file = new File("Files/preferiti.csv");

        if (!file.exists()) {
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {

            String line;
            boolean first = true;

            while ((line = br.readLine()) != null) {

                if (first) {
                    first = false;
                    continue;
                }

                if (line == null || line.trim().isEmpty()) {
                    continue;
                }

                String[] p = line.split(",");

                if (p.length < 2) {
                    continue;
                }

                int id = Integer.parseInt(p[0].trim());

                if (id == u.getId()) {

                    String[] ids = p[1].split("\\|");

                    for (String s : ids) {

                        if (s == null || s.trim().isEmpty()) {
                            continue;
                        }

                        int songId = Integer.parseInt(s.trim());

                        for (Canzone c : lista) {
                            if (c != null && c.getN() == songId) {
                                preferiti.add(c);
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void apriImpostazioni() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ivibe/View/impostazioni.fxml"));
            Parent root = loader.load();

            ImpostazioniController controller = loader.getController();

            Stage homeStage = (Stage) tableSongs.getScene().getWindow();
            controller.setHomeStage(homeStage);

            Stage stage = new Stage();
            stage.setTitle("Impostazioni");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @FXML
    public void logout() {

        try {
            Sessione.setUtenteCorrente(null);
            Stage stage = (Stage) tableSongs.getScene().getWindow();
            stage.close();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ivibe/View/Login.fxml"));
            Parent root = loader.load();

            Stage loginStage = new Stage();
            loginStage.setScene(new Scene(root));
            loginStage.setTitle("Login");
            loginStage.setMaximized(true);

            Image icon = new Image(getClass().getResourceAsStream("/ivibe/resources/images/LogoNS.png"));
            loginStage.getIcons().add(icon);

            loginStage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void salvaPlaylist() {

        File file = new File("Files/playlist.csv");
        List<String> output = new ArrayList<>();

        try {

            if (!file.exists()) {
                file.createNewFile();
            }

            output.add("userId,playlist");

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {

                String line;
                boolean first = true;

                while ((line = br.readLine()) != null) {

                    if (first) {
                        first = false;
                        continue;
                    }

                    if (line == null || line.trim().isEmpty()) {
                        continue;
                    }

                    String[] p = line.split(",", 2);

                    if (p.length < 2) {
                        continue;
                    }

                    int id;

                    try {
                        id = Integer.parseInt(p[0].trim());
                    } catch (Exception e) {
                        continue;
                    }

                    if (id != u.getId()) {
                        output.add(line);
                    }
                }
            }

            for (Playlist p : playlistList) {

                StringBuilder sb = new StringBuilder();

                sb.append(p.getNome());

                for (Canzone c : p.getCanzoni()) {
                    if (c != null) {
                        sb.append("|").append(c.getN());
                    }
                }

                output.add(u.getId() + "," + sb);
            }

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {

                for (String l : output) {
                    bw.write(l);
                    bw.newLine();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void caricaPlaylist() {

        playlistList.clear();

        File file = new File("Files/playlist.csv");

        if (!file.exists()) {
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {

            String line;
            boolean first = true;

            while ((line = br.readLine()) != null) {

                if (first) {
                    first = false;
                    continue;
                }

                if (line == null || line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.split(",", 2);

                if (parts.length < 2) {
                    continue;
                }

                int id;

                try {
                    id = Integer.parseInt(parts[0].trim());
                } catch (Exception e) {
                    continue;
                }

                if (id != u.getId()) {
                    continue;
                }

                String[] dati = parts[1].split("\\|");

                if (dati.length == 0) {
                    continue;
                }

                String nomePlaylist = dati[0].trim();
                Playlist playlist = new Playlist(nomePlaylist);

                for (int i = 1; i < dati.length; i++) {

                    try {
                        int songId = Integer.parseInt(dati[i].trim());

                        for (Canzone c : lista) {
                            if (c != null && c.getN() == songId) {
                                playlist.addCanzone(c);
                                break;
                            }
                        }

                    } catch (Exception ignored) {
                    }
                }

                playlistList.add(playlist);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void creaPlaylist() {

        String nome = playlistNameField.getText();

        if (nome == null || nome.trim().isEmpty()) {
            playlistErrorLabel.setText("Il nome non può essere vuoto");
            playlistErrorLabel.setStyle("-fx-text-fill: #f87171;");
            return;
        }

        nome = nome.trim();

        for (int i = 0; i < nome.length(); i++) {
            char c = nome.charAt(i);

            if (c == '"' || c == ',' || c == '|' || c == '\\' || c == '/') {
                playlistErrorLabel.setText("Il nome contiene caratteri non validi");
                playlistErrorLabel.setStyle("-fx-text-fill: #f87171;");
                return;
            }
        }

        for (Playlist p : playlistList) {
            if (p.getNome().equalsIgnoreCase(nome)) {
                playlistErrorLabel.setText("Playlist già esistente");
                playlistErrorLabel.setStyle("-fx-text-fill: #f87171;");
                return;
            }
        }

        Playlist playlist = new Playlist(nome);
        playlistList.add(playlist);

        playlistNameField.clear();

        playlistErrorLabel.setText("");

        salvaPlaylist();
        aggiornaVistaPlaylist();
    }

    private void aggiornaVistaPlaylist() {

        playlistContainer.getChildren().clear();
        dettaglioHBox.setVisible(false);
        dettaglioHBox.setManaged(false);
        playlistSelezionata = null;

        for (Playlist p : playlistList) {

            VBox box = new VBox(5);
            box.setStyle(
                    "-fx-background-color: #1e293b;"
                    + "-fx-padding: 15;"
                    + "-fx-background-radius: 10;"
                    + "-fx-min-width: 120;"
                    + "-fx-alignment: center;"
            );

            Label nome = new Label(p.getNome());
            nome.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

            Label count = new Label(p.getCanzoni().size() + " canzoni");
            count.setStyle("-fx-text-fill: #94a3b8;");

            Button btnElimina = new Button("✕");
            btnElimina.setStyle(
                    "-fx-background-color: transparent;"
                    + "-fx-text-fill: #f87171;"
                    + "-fx-font-size: 12;"
            );
            btnElimina.setOnAction(e -> {
                e.consume();
                playlistList.remove(p);
                salvaPlaylist();
                aggiornaVistaPlaylist();
            });

            box.getChildren().addAll(nome, count, btnElimina);
            box.setOnMouseClicked(e -> mostraDettaglioPlaylist(p));
            playlistContainer.getChildren().add(box);
        }
    }

    private void mostraDettaglioPlaylist(Playlist p) {

        playlistSelezionata = p;

        titoloPlaylistLabel.setText("📋 " + p.getNome());

        dettaglioHBox.setVisible(true);
        dettaglioHBox.setManaged(true);

        aggiornaListaSinistra(p, "");
        aggiornaListaDestra(p);
        searchAggiungiField.textProperty().addListener((obs, oldVal, newVal) -> {

            String filtro;

            if (newVal == null) {
                filtro = "";
            } else {
                filtro = newVal;
            }

            aggiornaListaSinistra(p, filtro);
        });
    }

    @FXML
    private void apriPlaylist() {

        caricaPlaylist();
        aggiornaVistaPlaylist();
        playlistSelezionata = null;

        playlistSection.setVisible(true);
        playlistSection.setManaged(true);

        tableSongs.setVisible(false);
        tableSongs.setManaged(false);

        searchField.setVisible(false);
        searchField.setManaged(false);
    }

    @FXML
    private void apriHome() {
        playlistSelezionata = null;
        playlistSection.setVisible(false);
        playlistSection.setManaged(false);
        tableSongs.setVisible(true);
        tableSongs.setManaged(true);
        searchField.setVisible(true);
        searchField.setManaged(true);
        vistaCorrente = lista;
        mostra(lista);
    }

    @FXML
    private void apriPreferiti() {
        playlistSelezionata = null;
        playlistSection.setVisible(false);
        playlistSection.setManaged(false);
        tableSongs.setVisible(true);
        tableSongs.setManaged(true);
        searchField.setVisible(true);
        searchField.setManaged(true);
        vistaCorrente = preferiti;
        mostra(preferiti);
    }

    private void aggiornaListaSinistra(Playlist p, String filtro) {

        listaAggiungiBox.getChildren().clear();

        String f;

        if (filtro == null) {
            f = "";
        } else {
            f = filtro.toLowerCase().trim();
        }

        for (Canzone c : lista) {

            if (c == null) {
                continue;
            }

            String titolo;

            if (c.getTitolo() != null) {
                titolo = c.getTitolo();
            } else {
                titolo = "";
            }
            String artista;

            if (c.getArtista() != null) {
                artista = c.getArtista();
            } else {
                artista = "";
            }

            if (!f.isEmpty()
                    && !titolo.toLowerCase().contains(f)
                    && !artista.toLowerCase().contains(f)) {
                continue;
            }

            boolean inPlaylist = p.getCanzoni().contains(c);

            Button btnAggiungi;

            if (inPlaylist) {
                btnAggiungi = new Button("✓");
            } else {
                btnAggiungi = new Button("➕");
            }

            String colore;

            if (inPlaylist) {
                colore = "#4ade80";
            } else {
                colore = "#38bdf8";
            }

            btnAggiungi.setStyle(
                    "-fx-background-color: transparent;"
                    + "-fx-text-fill: " + colore + ";"
                    + "-fx-font-size: 16;"
                    + "-fx-font-weight: bold;"
            );

            btnAggiungi.setPrefSize(40, 40);

            HBox riga = new HBox(12);
            riga.setPrefHeight(42);
            riga.setStyle(
                    "-fx-padding: 8 10;"
                    + "-fx-background-radius: 8;"
                    + "-fx-alignment: CENTER_LEFT;"
            );

            riga.setOnMouseEntered(e
                    -> riga.setStyle("-fx-background-color: rgba(51,65,85,0.8);"
                            + "-fx-padding: 8 10;"
                            + "-fx-background-radius: 8;"
                            + "-fx-alignment: CENTER_LEFT;")
            );

            riga.setOnMouseExited(e
                    -> riga.setStyle("-fx-padding: 8 10;"
                            + "-fx-background-radius: 8;"
                            + "-fx-alignment: CENTER_LEFT;")
            );

            Label nomeSong = new Label(titolo + " — " + artista);
            nomeSong.setStyle("-fx-text-fill: white; -fx-font-size: 14;");
            nomeSong.setPrefWidth(320);

            btnAggiungi.setOnAction(e -> {
                if (p.getCanzoni().contains(c)) {
                    p.removeCanzone(c);
                } else {
                    p.addCanzone(c);
                }

                salvaPlaylist();
                aggiornaListaSinistra(p, searchAggiungiField.getText());
                aggiornaListaDestra(p);
                aggiornaVistaPlaylist();
                mostraDettaglioPlaylist(p);
            });

            riga.getChildren().addAll(nomeSong, btnAggiungi);
            listaAggiungiBox.getChildren().add(riga);
        }
    }

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

            String titolo;

            if (c.getTitolo() != null) {
                titolo = c.getTitolo();
            } else {
                titolo = "";
            }

            String artista;

            if (c.getArtista() != null) {
                artista = c.getArtista();
            } else {
                artista = "";
            }

            HBox riga = new HBox(12);
            riga.setPrefHeight(42);
            riga.setStyle(
                    "-fx-padding: 8 10;"
                    + "-fx-background-radius: 8;"
                    + "-fx-alignment: CENTER_LEFT;"
            );

            riga.setOnMouseEntered(e
                    -> riga.setStyle("-fx-background-color: rgba(51,65,85,0.8);"
                            + "-fx-padding: 8 10;"
                            + "-fx-background-radius: 8;"
                            + "-fx-alignment: CENTER_LEFT;")
            );

            riga.setOnMouseExited(e
                    -> riga.setStyle("-fx-padding: 8 10;"
                            + "-fx-background-radius: 8;"
                            + "-fx-alignment: CENTER_LEFT;")
            );

            Label nomeSong = new Label(titolo + " — " + artista);
            nomeSong.setStyle("-fx-text-fill: white; -fx-font-size: 14;");
            nomeSong.setPrefWidth(280);

            Button btnRimuovi = new Button("✕");

            btnRimuovi.setStyle(
                    "-fx-background-color: transparent;"
                    + "-fx-text-fill: #f87171;"
                    + "-fx-font-size: 16;"
                    + "-fx-font-weight: bold;"
            );

            btnRimuovi.setPrefSize(40, 40);

            btnRimuovi.setOnAction(e -> {
                p.removeCanzone(c);
                salvaPlaylist();
                aggiornaListaSinistra(p, searchAggiungiField.getText());
                aggiornaListaDestra(p);
                aggiornaVistaPlaylist();
                mostraDettaglioPlaylist(p);
            });

            riga.getChildren().addAll(nomeSong, btnRimuovi);
            listaPlaylistBox.getChildren().add(riga);
        }
    }

    private void apriRiproduzione(Canzone c, List<Canzone> coda, String titoloFinestra) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ivibe/View/riproduzione.fxml"));
            Parent root = loader.load();

            RiproduzioneController controller = loader.getController();

            Stage stage = new Stage();

            if (titoloFinestra != null) {
                stage.setTitle(titoloFinestra);
            } else {
                stage.setTitle(c.getTitolo());
            }

            stage.setScene(new Scene(root));
            Image icon = new Image(getClass().getResourceAsStream("/ivibe/resources/images/LogoNS.png"));
            stage.getIcons().add(icon);
            stage.show();

            controller.startDownload(c.getUrl(), coda);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void apriRiproduzione(Canzone c) {
        apriRiproduzione(c, new ArrayList<Canzone>(), c.getTitolo());
    }

    @FXML
    private void shufflePlaylist() {

        if (playlistSelezionata == null || playlistSelezionata.getCanzoni().isEmpty()) {
            return;
        }

        List<Canzone> canzoni = new ArrayList<>(playlistSelezionata.getCanzoni());
        Collections.shuffle(canzoni);

        Canzone prima = canzoni.get(0);
        nowPlayingLabel.setText("🎵 " + prima.getTitolo());
        apriRiproduzione(prima, canzoni, "" + playlistSelezionata.getNome());
    }

    @FXML
    private void playPlaylist() {

        if (playlistSelezionata == null || playlistSelezionata.getCanzoni().isEmpty()) {
            return;
        }

        List<Canzone> canzoni = new ArrayList<>(playlistSelezionata.getCanzoni());
        Canzone prima = canzoni.get(0);
        nowPlayingLabel.setText("🎵 " + prima.getTitolo());
        apriRiproduzione(prima, canzoni, "" + playlistSelezionata.getNome());
    }
}
