package ivibe.Control;

import ivibe.Model.PasswordEncoder;
import ivibe.Model.Sessione;
import ivibe.Model.Utente;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;

import java.io.*;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class ImpostazioniController implements Initializable {

    private Utente utenteCorrente;

    @FXML private Label username_lbl;
    @FXML private Label email_lbl;
    @FXML private Label admin_lbl;

    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private TextField visiblePasswordField;
    @FXML private CheckBox showPasswordCheck;

    @FXML private Label statusLabel;

    private Stage homeStage;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        utenteCorrente = Sessione.getUtenteCorrente();

        if (utenteCorrente == null) {
            mostraErrore("Errore: utente non trovato");
            return;
        }

        username_lbl.setText("Username: " + utenteCorrente.getNickname());
        email_lbl.setText("Email: " + utenteCorrente.getEmail());

        if (utenteCorrente.isAdmin()) {
            admin_lbl.setText("Ruolo: ADMIN");
        } else {
            admin_lbl.setText("Ruolo: USER");
        }

        username_lbl.setStyle("-fx-text-fill: white;");
        email_lbl.setStyle("-fx-text-fill: white;");
        admin_lbl.setStyle("-fx-text-fill: #ffd166;");

        statusLabel.setText("");
    }

    @FXML
    public void togglePasswordVisibility() {

        boolean show = showPasswordCheck.isSelected();

        if (show) {
            visiblePasswordField.setText(newPasswordField.getText());
            visiblePasswordField.setVisible(true);
            visiblePasswordField.setManaged(true);

            newPasswordField.setVisible(false);
            newPasswordField.setManaged(false);
        } else {
            newPasswordField.setText(visiblePasswordField.getText());
            newPasswordField.setVisible(true);
            newPasswordField.setManaged(true);

            visiblePasswordField.setVisible(false);
            visiblePasswordField.setManaged(false);
        }
    }

    @FXML
    public void updatePassword() {

        String oldP = oldPasswordField.getText();

        String newP;
        if (showPasswordCheck.isSelected()) {
            newP = visiblePasswordField.getText();
        } else {
            newP = newPasswordField.getText();
        }

        if (oldP.isEmpty() || newP.isEmpty()) {
            mostraErrore("Compila tutti i campi");
            return;
        }
        if (newP.contains(",") || newP.contains("|") || newP.contains("\\") || newP.contains("/")) {
            mostraErrore("⚠ La nuova password contiene caratteri non validi(, | \\ /)");
            return;
        }
        if (newP.length() < 7) {
            mostraErrore("⚠ Password troppo corta");
            return;
        }
        if (!PasswordEncoder.verify(oldP, utenteCorrente.getPswd())) {
            mostraErrore("Password attuale errata");
            return;
        }

        String encoded = PasswordEncoder.encode(newP);
        utenteCorrente.setPswd(encoded);

        List<Utente> utentiAgg = leggiUtenti();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter("Files/utenti.csv"))) {

            bw.write("id,nickname,pswd,email,data,isAdmin\n");

            for (Utente user : utentiAgg) {

                if (user.getId() == utenteCorrente.getId()) {
                    user.setPswd(encoded);
                }

                String data;
                if (user.getData() != null) {
                    data = user.getData().toString();
                } else {
                    data = "2000-01-01";
                }

                bw.write(
                    user.getId() + "," +
                    user.getNickname() + "," +
                    user.getPswd() + "," +
                    user.getEmail() + "," +
                    data + "," +
                    user.isAdmin() + "\n"
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        mostraSuccesso("Password aggiornata!");
        pulisciCampi();
    }

    @FXML
    public void deleteAccount() {

        if (utenteCorrente == null) return;

        int userId = utenteCorrente.getId();

        List<Utente> utenti = leggiUtenti();
        List<Utente> output = new ArrayList<>();

        for (Utente user : utenti) {
            if (user.getId() != userId) {
                output.add(user);
            }
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter("Files/utenti.csv"))) {

            bw.write("id,nickname,pswd,email,data,isAdmin\n");

            for (Utente user : output) {

                String data;
                if (user.getData() != null) {
                    data = user.getData().toString();
                } else {
                    data = "2000-01-01";
                }

                bw.write(
                        user.getId() + "," +
                        user.getNickname() + "," +
                        user.getPswd() + "," +
                        user.getEmail() + "," +
                        data + "," +
                        user.isAdmin() + "\n"
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        logout();
    }

    @FXML
    public void back() {
        Stage s = (Stage) username_lbl.getScene().getWindow();
        s.close();
    }

    private void mostraErrore(String msg) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: #ff4d4d; -fx-font-weight: bold;");
    }

    private void mostraSuccesso(String msg) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: #22c55e; -fx-font-weight: bold;");
    }

    private void pulisciCampi() {
        oldPasswordField.clear();
        newPasswordField.clear();
        visiblePasswordField.clear();
    }

    @FXML
    public void logout() {
        try {
            Sessione.setUtenteCorrente(null);

            Stage current = (Stage) username_lbl.getScene().getWindow();
            current.close();

            if (homeStage != null) {
                homeStage.close();
            }

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

    public void setHomeStage(Stage stage) {
        this.homeStage = stage;
    }

    private List<Utente> leggiUtenti() {

        List<Utente> list = new ArrayList<>();
        File file = new File("Files/utenti.csv");

        if (!file.exists()) return list;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {

            String line;
            boolean first = true;

            while ((line = br.readLine()) != null) {

                if (first) { first = false; continue; }
                if (line.trim().isEmpty()) continue;

                String[] p = line.replace("\"", "").split(",");

                if (p.length < 6) continue;

                LocalDate data;

                try {
                    String raw = p[4].trim();

                    if (raw.equalsIgnoreCase("null") || raw.isEmpty()) {
                        data = LocalDate.of(2000, 1, 1);
                    } else {
                        data = LocalDate.parse(raw);
                    }

                } catch (Exception e) {
                    data = LocalDate.of(2000, 1, 1);
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
}