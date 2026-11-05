package ivibe.Control;

import ivibe.Database.Database;
import ivibe.Database.UtenteDAO;
import ivibe.Model.PasswordEncoder;
import ivibe.Model.Validatore;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class RegistrazioneController implements Initializable {

    @FXML
    private TextField username_txt;
    @FXML
    private TextField email_txt;
    @FXML
    private DatePicker dataNascita_dt;

    @FXML
    private PasswordField password_psw;
    @FXML
    private PasswordField confermaPassword_psw;

    @FXML
    private TextField password_text;
    @FXML
    private TextField conferma_text;

    @FXML
    private CheckBox mostraPswd;

    @FXML
    private Label error_lbl;

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        error_lbl.setText("");

        password_text.setVisible(false);
        password_text.setManaged(false);

        conferma_text.setVisible(false);
        conferma_text.setManaged(false);

        password_text.textProperty().bindBidirectional(password_psw.textProperty());
        conferma_text.textProperty().bindBidirectional(confermaPassword_psw.textProperty());
    }

    @FXML
    public void gestisciMostraPassword() {

        boolean show = mostraPswd.isSelected();

        password_text.setVisible(show);
        password_text.setManaged(show);
        password_psw.setVisible(!show);
        password_psw.setManaged(!show);

        conferma_text.setVisible(show);
        conferma_text.setManaged(show);
        confermaPassword_psw.setVisible(!show);
        confermaPassword_psw.setManaged(!show);
    }

    @FXML
    public void registrati() {

        String user = username_txt.getText() == null ? "" : username_txt.getText().trim();
        String email = email_txt.getText() == null ? "" : email_txt.getText().trim();
        LocalDate data = dataNascita_dt.getValue();

        // campi legati in modo bidirezionale: il testo è lo stesso
        String pass = password_psw.getText() == null ? "" : password_psw.getText();
        String confirm = confermaPassword_psw.getText() == null ? "" : confermaPassword_psw.getText();

        if (user.isEmpty() || email.isEmpty() || pass.isEmpty() || confirm.isEmpty() || data == null) {
            errore("⚠ Compila tutti i campi");
            return;
        }

        if (data.isAfter(LocalDate.now())) {
            errore("⚠ Data non valida (non può essere futura)");
            return;
        }
        if (data.isBefore(LocalDate.now().minusYears(120))) {
            errore("⚠ Data di nascita non valida");
            return;
        }

        String msg = Validatore.controllaNickname(user);
        if (msg == null) {
            msg = Validatore.controllaEmail(email);
        }
        if (msg == null && !pass.equals(confirm)) {
            msg = "⚠ Le password non coincidono";
        }
        if (msg == null) {
            msg = Validatore.controllaPassword(pass);
        }
        if (msg != null) {
            errore(msg);
            return;
        }

        try {
            if (UtenteDAO.esiste(user, email)) {
                errore("⚠ Username o email già registrati");
                return;
            }

            UtenteDAO.inserisci(user, email, PasswordEncoder.encode(pass), data, false);

        } catch (SQLException e) {
            errore("❌ " + Database.messaggioErrore(e));
            e.printStackTrace();
            return;
        }

        vaiLogin();
    }

    private void errore(String msg) {
        error_lbl.setText(msg);
    }

    @FXML
    public void vaiLogin() {
        Stage current = (Stage) username_txt.getScene().getWindow();
        Finestre.sostituisci(current, Finestre.LOGIN, "Login");
    }
}
