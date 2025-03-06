package gestorFTP;

public class Main {
    public static void main(String[] args) {
        GestorFTP gestorFTP = new GestorFTP();
        try {
            gestorFTP.conectar();
            gestorFTP.watchFolder();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
