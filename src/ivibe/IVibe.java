package ivibe;

import ivibe.Model.Utente;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ivibe.Model.Canzone;
import java.util.ArrayList;
import javafx.scene.image.Image;

public class IVibe extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/ivibe/View/login.fxml"));

        Scene scene = new Scene(root);
        stage.setMaximized(true);
        stage.setScene(scene);
        stage.setTitle("Login");
        Image icon = new Image(getClass().getResourceAsStream("/ivibe/resources/images/LogoNS.png"));
        stage.getIcons().add(icon);
        stage.show();
    }

    public static void main(String[] args) {    
        
        launch(args);
    }

    public static void scriviCSV(ArrayList<Canzone> canzoni) {

        File file = new File("Files/canzoni.csv");
        file.getParentFile().mkdirs();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {

            boolean fileVuoto = !file.exists() || file.length() == 0;

            if (fileVuoto) {
                bw.write("titolo,url,durata,artista,n");
                bw.newLine();
            }

            for (Canzone c : canzoni) {

                bw.write(
                        c.getTitolo() + ","
                        + c.getUrl() + ","
                        + c.getDurata() + ","
                        + c.getArtista() + ","
                        + c.getN()
                );

                bw.newLine();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
