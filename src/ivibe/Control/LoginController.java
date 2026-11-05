package ivibe.Control;

import ivibe.Database.Database;
import ivibe.Database.UtenteDAO;
import ivibe.Model.PasswordEncoder;
import ivibe.Model.Sessione;
import ivibe.Model.Utente;
import java.net.URL;
import java.sql.SQLException;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class LoginController implements Initializable {

    @FXML
    private Label error_lbl;
    @FXML
    private TextField nome_txt;
    @FXML
    private TextField pswd_text;
    @FXML
    private PasswordField pswd_field;
    @FXML
    private CheckBox mostraPswd;

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        pswd_text.setVisible(false);
        pswd_text.setManaged(false);

        // i due campi condividono lo stesso testo: basta leggere uno dei due
        pswd_text.textProperty().bindBidirectional(pswd_field.textProperty());

        error_lbl.setText("");
    }

    @FXML
    public void gestisciMostraPassword() {

        boolean show = mostraPswd.isSelected();

        pswd_text.setVisible(show);
        pswd_text.setManaged(show);

        pswd_field.setVisible(!show);
        pswd_field.setManaged(!show);
    }

    @FXML
    public void accedi() {

        String input = nome_txt.getText() == null ? "" : nome_txt.getText().trim();
        String pswd = pswd_field.getText() == null ? "" : pswd_field.getText();

        error_lbl.setText("");

        if (input.isEmpty() || pswd.isEmpty()) {
            error_lbl.setText("Compila tutti i campi");
            return;
        }

        try {
            Utente utente = UtenteDAO.trovaPerNicknameOEmail(input);

            if (utente == null || !PasswordEncoder.verify(pswd, utente.getPswd())) {
                error_lbl.setText("Credenziali errate");
                return;
            }

            // le vecchie password in Base64 vengono convertite nel nuovo formato sicuro
            if (PasswordEncoder.isFormatoVecchio(utente.getPswd())) {
                String nuova = PasswordEncoder.encode(pswd);
                UtenteDAO.aggiornaPassword(utente.getId(), nuova);
                utente.setPswd(nuova);
            }

            Sessione.setUtenteCorrente(utente);
            System.out.println("Login riuscito: " + utente.getNickname());

            Stage current = (Stage) nome_txt.getScene().getWindow();

            if (utente.isAdmin()) {
                Finestre.sostituisci(current, Finestre.ADMIN, "Admin");
            } else {
                Finestre.sostituisci(current, Finestre.UTENTE, "iVibe");
            }

        } catch (SQLException e) {
            error_lbl.setText(Database.messaggioErrore(e));
            e.printStackTrace();
        }
    }

    @FXML
    public void registrati() {
        Stage current = (Stage) nome_txt.getScene().getWindow();
        Finestre.sostituisci(current, Finestre.REGISTRAZIONE, "Registrazione");
    }
}
