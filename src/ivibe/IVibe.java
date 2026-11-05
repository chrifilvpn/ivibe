package ivibe;

import ivibe.Control.Finestre;
import ivibe.Control.YtDlp;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class IVibe extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        Parent root = FXMLLoader.load(getClass().getResource(Finestre.LOGIN));

        stage.setScene(new Scene(root));
        stage.setMaximized(true);
        stage.setTitle("Login");
        Finestre.impostaIcona(stage);
        stage.show();

        // tiene yt-dlp aggiornato (YouTube cambia spesso e le versioni vecchie smettono di funzionare)
        YtDlp.aggiornaInBackground();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
