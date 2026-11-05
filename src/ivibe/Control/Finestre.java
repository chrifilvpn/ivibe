package ivibe.Control;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.Optional;

/**
 * Metodi di utilità per aprire le finestre dell'app, così il codice
 * non è ripetuto in ogni controller.
 */
public class Finestre {

    public static final String LOGIN = "/ivibe/View/login.fxml";
    public static final String REGISTRAZIONE = "/ivibe/View/registrazione.fxml";
    public static final String UTENTE = "/ivibe/View/utente.fxml";
    public static final String ADMIN = "/ivibe/View/admin.fxml";
    public static final String IMPOSTAZIONI = "/ivibe/View/impostazioni.fxml";
    public static final String RIPRODUZIONE = "/ivibe/View/riproduzione.fxml";

    private static final String ICONA = "/ivibe/resources/images/LogoNS.png";

    /** Aggiunge l'icona di iVibe alla finestra. */
    public static void impostaIcona(Stage stage) {
        InputStream in = Finestre.class.getResourceAsStream(ICONA);
        if (in != null) {
            stage.getIcons().add(new Image(in));
        }
    }

    /**
     * Carica un FXML in una nuova finestra e la mostra.
     * Restituisce il loader, da cui si può prendere il controller.
     */
    public static FXMLLoader apri(String fxml, String titolo, boolean massimizzata) throws Exception {

        FXMLLoader loader = new FXMLLoader(Finestre.class.getResource(fxml));
        Parent root = loader.load();

        Stage stage = new Stage();
        stage.setTitle(titolo);
        stage.setScene(new Scene(root));
        stage.setMaximized(massimizzata);
        impostaIcona(stage);
        stage.show();

        return loader;
    }

    /** Apre una nuova finestra massimizzata e chiude quella attuale. */
    public static void sostituisci(Stage attuale, String fxml, String titolo) {
        try {
            apri(fxml, titolo, true);
            if (attuale != null) {
                attuale.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            errore("Impossibile aprire la finestra \"" + titolo + "\"");
        }
    }

    public static void errore(String messaggio) {
        Alert a = new Alert(Alert.AlertType.ERROR, messaggio, ButtonType.OK);
        a.setHeaderText(null);
        a.setTitle("iVibe");
        a.showAndWait();
    }

    /** Chiede conferma all'utente. true se ha premuto OK. */
    public static boolean conferma(String messaggio) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, messaggio, ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText(null);
        a.setTitle("iVibe");
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }
}
