package gestorFTP;

import java.io.*;
import java.nio.file.*;
import java.net.SocketException;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

public class GestorFTP {
    private FTPSClient clienteFTP;
    private static final String SERVIDOR = "localhost";
    private static final int PUERTO = 21;
    private static final String USUARIO = "joseftp";
    private static final String PASSWORD = "12345678";
    private static final String LOCAL_FOLDER = "C:\\Users\\otero\\Desktop\\local";

    public GestorFTP() {
        clienteFTP = new FTPSClient();
    }

    private void conectar() throws SocketException, IOException {
        clienteFTP.connect(SERVIDOR, PUERTO);
        clienteFTP.execPBSZ(0);
        clienteFTP.execPROT("P");
        int respuesta = clienteFTP.getReplyCode();
        if (!FTPReply.isPositiveCompletion(respuesta)) {
            clienteFTP.disconnect();
            throw new IOException("Error al conectar con el servidor FTP");
        }
        if (!clienteFTP.login(USUARIO, PASSWORD)) {
            throw new IOException("Error en las credenciales");
        }
        clienteFTP.enterLocalPassiveMode();
        clienteFTP.setFileType(FTP.BINARY_FILE_TYPE);
        System.out.println("Conectado");
        clienteFTP.changeWorkingDirectory("/sincronizada");
    }

    private void desconectar() throws IOException {
        clienteFTP.logout();
        clienteFTP.disconnect();
        System.out.println("Desconectado");
    }

    private void subirFichero(String path) throws IOException {
        File ficheroLocal = new File(path);
        InputStream is = new FileInputStream(ficheroLocal);
        boolean enviado = clienteFTP.storeFile(ficheroLocal.getName(), is);
        is.close();
        if (enviado) {
            System.out.println("Fichero enviado correctamente");
        } else {
            System.out.println("Error al enviar el fichero");
        }
    }

    private void descargarFichero(String ficheroRemoto, String pathLocal) throws IOException {
        OutputStream os = new FileOutputStream(pathLocal);
        boolean recibido = clienteFTP.retrieveFile(ficheroRemoto, os);
        os.close();
        if (recibido) {
            System.out.println("Fichero recibido correctamente");
        } else {
            System.out.println("Error al recibir el fichero");
        }
    }

    private void watchFolder() throws Exception {
        WatchService watchService = FileSystems.getDefault().newWatchService();
        Path path = Paths.get(LOCAL_FOLDER);
        path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        System.out.println("Observando carpeta...");
        while (true) {
            WatchKey key = watchService.take();
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                Path filePath = path.resolve((Path) event.context());
                if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    subirFichero(filePath.toString());
                } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    clienteFTP.deleteFile(filePath.getFileName().toString());
                }
            }
            key.reset();
        }
    }

    public static void main(String[] args) {
        GestorFTP gestorFTP = new GestorFTP();
        try {
            gestorFTP.conectar();
            gestorFTP.watchFolder();
            gestorFTP.desconectar();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
