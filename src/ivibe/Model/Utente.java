package ivibe.Model;

import java.time.LocalDate;
import javafx.beans.property.*;

public class Utente {

    private IntegerProperty id;
    private StringProperty nickname;
    private StringProperty email;
    private StringProperty pswd;
    private ObjectProperty<LocalDate> data;
    private BooleanProperty isAdmin;

    public Utente(int id, String nickname, String email, String pswd, LocalDate data, boolean isAdmin) {
        this.id = new SimpleIntegerProperty(id);
        this.nickname = new SimpleStringProperty(nickname);
        this.email = new SimpleStringProperty(email);
        this.pswd = new SimpleStringProperty(pswd);
        this.data = new SimpleObjectProperty<>(data);
        this.isAdmin = new SimpleBooleanProperty(isAdmin);
    }

    public int getId() {
        return id.get();
    }

    public void setId(int id) {
        this.id.set(id);
    }

    public IntegerProperty idProperty() {
        return id;
    }

    public String getNickname() {
        return nickname.get();
    }

    public void setNickname(String nickname) {
        this.nickname.set(nickname);
    }

    public StringProperty nicknameProperty() {
        return nickname;
    }

    public String getEmail() {
        return email.get();
    }

    public void setEmail(String email) {
        this.email.set(email);
    }

    public StringProperty emailProperty() {
        return email;
    }

    public String getPswd() {
        return pswd.get();
    }

    public void setPswd(String pswd) {
        this.pswd.set(pswd);
    }

    public StringProperty pswdProperty() {
        return pswd;
    }

    public LocalDate getData() {
        return data.get();
    }

    public void setData(LocalDate data) {
        this.data.set(data);
    }

    public ObjectProperty<LocalDate> dataProperty() {
        return data;
    }

    public boolean isAdmin() {
        return isAdmin.get();
    }

    public void setIsAdmin(boolean isAdmin) {
        this.isAdmin.set(isAdmin);
    }

    public BooleanProperty isAdminProperty() {
        return isAdmin;
    }
}