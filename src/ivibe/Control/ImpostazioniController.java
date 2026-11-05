package ivibe.Control;

import ivibe.Database.Database;
import ivibe.Database.UtenteDAO;
import ivibe.Model.PasswordEncoder;
import ivibe.Model.Sessione;
import ivibe.Model.Utente;
import ivibe.Model.Validatore;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.SQLException;
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

    /** Finestra principale (utente o admin) da chiudere in caso di logout. */
    private Stage homeStage;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        statusLabel.setText("");

        // campo visibile e campo nascosto condividono lo stesso testo
        visiblePasswordField.textProperty().bindBidirectional(newPasswordField.textProperty());

        utenteCorrente = Sessione.getUtenteCorrente();

        if (utenteCorrente == null) {
            mostraErrore("Errore: utente non trovato");
            return;
        }

        username_lbl.setText("Username: " + utenteCorrente.getNickname());
        email_lbl.setText("Email: " + utenteCorrente.getEmail());
        admin_lbl.setText(utenteCorrente.isAdmin() ? "Ruolo: ADMIN" : "Ruolo: USER");

        username_lbl.setStyle("-fx-text-fill: white;");
        email_lbl.setStyle("-fx-text-fill: white;");
        admin_lbl.setStyle("-fx-text-fill: #ffd166;");
    }

    @FXML
    public void togglePasswordVisibility() {

        boolean show = showPasswordCheck.isSelected();

        visiblePasswordField.setVisible(show);
        visiblePasswordField.setManaged(show);

        newPasswordField.setVisible(!show);
        newPasswordField.setManaged(!show);
    }

    @FXML
    public void updatePassword() {

        if (utenteCorrente == null) {
            return;
        }

        String oldP = oldPasswordField.getText() == null ? "" : oldPasswordField.getText();
        String newP = newPasswordField.getText() == null ? "" : newPasswordField.getText();

        if (oldP.isEmpty() || newP.isEmpty()) {
            mostraErrore("Compila tutti i campi");
            return;
        }
        if (!PasswordEncoder.verify(oldP, utenteCorrente.getPswd())) {
            mostraErrore("Password attuale errata");
            return;
        }
        if (oldP.equals(newP)) {
            mostraErrore("La nuova password è uguale a quella attuale");
            return;
        }

        String msg = Validatore.controllaPassword(newP);
        if (msg != null) {
            mostraErrore(msg);
            return;
        }

        String encoded = PasswordEncoder.encode(newP);

        try {
            UtenteDAO.aggiornaPassword(utenteCorrente.getId(), encoded);
        } catch (SQLException e) {
            mostraErrore(Database.messaggioErrore(e));
            e.printStackTrace();
            return;
        }

        utenteCorrente.setPswd(encoded);
        mostraSuccesso("Password aggiornata!");
        pulisciCampi();
    }

    @FXML
    public void deleteAccount() {

        if (utenteCorrente == null) {
            return;
        }

        try {
            if (utenteCorrente.isAdmin() && UtenteDAO.contaAdmin() <= 1) {
                mostraErrore("Sei l'unico admin: non puoi eliminare l'account");
                return;
            }

            if (!Finestre.conferma("Vuoi davvero eliminare il tuo account?\n"
                    + "Preferiti e playlist verranno cancellati definitivamente.")) {
                return;
            }

            UtenteDAO.elimina(utenteCorrente.getId());

        } catch (SQLException e) {
            mostraErrore(Database.messaggioErrore(e));
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
    }

    @FXML
    public void logout() {

        Sessione.logout();
        UtenteController.chiudiPlayer();

        if (homeStage != null) {
            homeStage.close();
        }

        Stage current = (Stage) username_lbl.getScene().getWindow();
        Finestre.sostituisci(current, Finestre.LOGIN, "Login");
    }

    public void setHomeStage(Stage stage) {
        this.homeStage = stage;
    }
}
