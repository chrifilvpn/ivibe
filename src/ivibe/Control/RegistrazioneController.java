package ivibe.Control;

import java.io.*;
import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;
import ivibe.Model.PasswordEncoder;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
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

    private final String FILE_PATH = "Files/utenti.csv";

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

    public void registrati() {
        String user = username_txt.getText();
        String email = email_txt.getText();

        LocalDate data = dataNascita_dt.getValue();

        String pass;
        String confirm;

        if (password_psw.isVisible() == true) {
            pass = password_psw.getText();
        } else {
            pass = password_text.getText();
        }

        if (confermaPassword_psw.isVisible() == true) {
            confirm = confermaPassword_psw.getText();
        } else {
            confirm = conferma_text.getText();
        }

        if (user == null || user.trim().isEmpty()
                || email == null || email.trim().isEmpty()
                || pass == null || pass.isEmpty()
                || confirm == null || confirm.isEmpty()
                || data == null) {

            error_lbl.setText("⚠ Compila tutti i campi");
            return;
        }

        if (data.isAfter(LocalDate.now())) {
            error_lbl.setText("⚠ Data non valida (non può essere futura)");
            return;
        }

        if (!pass.equals(confirm)) {
            error_lbl.setText("⚠ Password non coincidono");
            return;
        }

        if (pass.length() < 7) {
            error_lbl.setText("⚠ Password troppo corta");
            return;
        }
        if (pass.contains(",") || pass.contains("|") || pass.contains("\\") || pass.contains("/")) {
            error_lbl.setText("⚠ La password contiene caratteri non validi(, | \\ /)");
            return;
        }
        if (user.contains(",") || user.contains("|") || user.contains("\\") || user.contains("/")) {
            error_lbl.setText("⚠ Il nome utente contiene caratteri non validi");
            return;
        }
        if (email.contains(",") || email.contains("|") || email.contains("\\") || email.contains("/")) {
            error_lbl.setText("⚠ La Email contiene caratteri non validi(, | \\ /)");
            return;
        }
        if (!(email.contains("@"))) {
            error_lbl.setText("⚠ La Email non è valida perchè non contiene la @");
            return;
        }
        if (!(email.endsWith(".com") || email.endsWith(".it"))) {
            error_lbl.setText("⚠ La Email deve finire con .com o .it");
            return;
        }
        boolean maiuscola = false;
        boolean numero = false;
        boolean speciale = false;

        for (int i = 0; i < pass.length(); i++) {

            char c = pass.charAt(i);

            if (Character.isUpperCase(c)) {
                maiuscola = true;
            }

            if (Character.isDigit(c)) {
                numero = true;
            }

            if (!Character.isLetterOrDigit(c)) {
                speciale = true;
            }
        }

        if (!maiuscola || !numero || !speciale) {
            error_lbl.setText("⚠ Password non valida (maiuscola, numero, simbolo)");
            return;
        }

        if (utenteEsiste(user, email)) {
            error_lbl.setText("⚠ Utente già esistente");
            return;
        }

        int id = generaNuovoId();

        String passwordCriptata = PasswordEncoder.encode(pass);

        salvaUtente(id, user, passwordCriptata, email, data.toString());

        vaiLogin();
    }

    private int generaNuovoId() {

        int lastId = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(FILE_PATH))) {

            String line;
            br.readLine(); 

            while ((line = br.readLine()) != null) {

                String[] d = line.split(",");

                if (d.length > 0) {
                    String idStr = d[0].replace("\"", "").trim();

                    if (!idStr.isEmpty()) {
                        lastId = Integer.parseInt(idStr);
                    }
                }
            }

        } catch (IOException e) {
            return 1;
        }

        return lastId + 1;
    }

    private void salvaUtente(int id, String user, String pass, String email, String data) {

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH, true))) {
            bw.write(id + "," + user + "," + pass + "," + email + "," + data + ",false");
            bw.newLine();

        } catch (IOException e) {
            error_lbl.setText("❌ Errore salvataggio file");
        }
    }

    private boolean utenteEsiste(String user, String email) {

        try (BufferedReader br = new BufferedReader(new FileReader(FILE_PATH))) {

            String line;
            br.readLine();

            while ((line = br.readLine()) != null) {

                String[] d = line.split(",");

                if (d.length >= 4) {
                    if (d[1].equals(user) || d[3].equals(email)) {
                        return true;
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    @FXML
    public void vaiLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ivibe/View/login.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            stage.setTitle("Login");
            Image icon = new Image(getClass().getResourceAsStream("/ivibe/resources/images/LogoNS.png"));
            stage.getIcons().add(icon);
            stage.show();

            Stage current = (Stage) username_txt.getScene().getWindow();
            current.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
