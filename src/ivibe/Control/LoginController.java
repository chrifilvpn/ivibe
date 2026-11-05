package ivibe.Control;

import ivibe.Model.Sessione;
import ivibe.Model.PasswordEncoder;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import ivibe.Model.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
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
    private Utente utenteCorrente = Sessione.getUtenteCorrente();
    @Override
    public void initialize(URL url, ResourceBundle rb) {

        pswd_text.setVisible(false);
        pswd_text.setManaged(false);

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

        String input = nome_txt.getText().trim();

        String pswd;

        if (pswd_field.isVisible()) {
            pswd = pswd_field.getText();
        } else {
            pswd = pswd_text.getText();
        }

        error_lbl.setText("");

        if (input.isEmpty() || pswd.isEmpty()) {
            error_lbl.setText("Compila tutti i campi");
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader("Files/utenti.csv"))) {

            String linea;
            br.readLine(); 

            while ((linea = br.readLine()) != null) {

                if (linea == null || linea.trim().isEmpty()) {
                    continue;
                }

                linea = linea.replace("\"", "").trim();

                String[] dati = linea.split(",");

                if (dati.length < 6) {
                    continue;
                }

                int id;

                try {
                    id = Integer.parseInt(dati[0].trim());
                } catch (NumberFormatException ex) {
                    System.out.println("ID non valido: " + dati[0]);
                    continue;
                }

                String username = dati[1].trim();
                String password = dati[2].trim();
                String email = dati[3].trim();

                boolean isAdmin = Boolean.parseBoolean(dati[5].trim());

                boolean matchUser = username.equals(input) || email.equals(input);
                boolean matchPass = PasswordEncoder.verify(pswd, password);

                if (matchUser && matchPass) {

                    Utente utente = new Utente(
                            id,
                            username,
                            email,
                            password,
                            null,
                            isAdmin
                    );

                    Sessione.setUtenteCorrente(utente);

                    System.out.println("Login riuscito: " + username);

                    if (isAdmin) {
                        finestraAdmin();
                    } else {
                        finestraUtente();
                    }

                    return;
                }
            }

            error_lbl.setText("Credenziali errate");

        } catch (IOException e) {
            error_lbl.setText("Errore lettura utenti.csv");
            e.printStackTrace();
        }
    }

    public void finestraUtente() {
        apriFinestra("/ivibe/View/utente.fxml", "Utente");
    }
    public void setUtenteCorrente(Utente u) {
        this.utenteCorrente = u;
    }
   public void finestraAdmin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ivibe/View/admin.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Admin");
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            Image icon = new Image(getClass().getResourceAsStream("/ivibe/resources/images/LogoNS.png"));
            stage.getIcons().add(icon);
            stage.show();

            Stage current = (Stage) nome_txt.getScene().getWindow();
            current.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void apriFinestra(String path, String titolo) {

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(path));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle(titolo);
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            Image icon = new Image(getClass().getResourceAsStream("/ivibe/resources/images/LogoNS.png"));
            stage.getIcons().add(icon);
            stage.show();

            Stage current = (Stage) nome_txt.getScene().getWindow();
            current.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void registrati() {
        apriFinestra("/ivibe/View/registrazione.fxml", "Registrazione");
    }
}
