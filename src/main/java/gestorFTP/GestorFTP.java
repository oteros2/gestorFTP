package gestorFTP;

import java.io.*;
import java.nio.file.*;
import java.net.SocketException;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

public class GestorFTP {
    private FTPClient clienteFTP;
    private static final String SERVIDOR = "172.29.108.147";
    private static final int PUERTO = 21;
    private static final String USUARIO = "joseftp";
    private static final String PASSWORD = "12345678";
    private static final String LOCAL_FOLDER = "C:\\Users\\otero\\Desktop\\local";

    public GestorFTP() {
        clienteFTP = new FTPClient();
    }

    private void conectar() throws SocketException, IOException {
        clienteFTP.connect(SERVIDOR, PUERTO);
        int respuesta = clienteFTP.getReplyCode();
        if (!FTPReply.isPositiveCompletion(respuesta)) {
            clienteFTP.disconnect();
            throw new IOException("Error al conectar con el servidor FTP");
        }
        if (!clienteFTP.login(USUARIO, PASSWORD)) {
            throw new IOException("Error en las credenciales");
        }
        clienteFTP.setFileType(FTP.BINARY_FILE_TYPE);
        System.out.println("Conectado");
        clienteFTP.enterLocalPassiveMode();
        clienteFTP.changeWorkingDirectory("/ftp");
    }

    private void desconectar() throws IOException {
        clienteFTP.logout();
        clienteFTP.disconnect();
        System.out.println("Desconectado");
    }

    private void subirFichero(Path path) throws IOException {
        conectar();
        File file = path.toFile();
        System.out.println("Enviando archivo: " + file.getName());
        FileInputStream is = new FileInputStream(file);
        boolean enviado = clienteFTP.storeFile(path.getFileName().toString(), is);
        is.close();
        int replyCode = clienteFTP.getReplyCode();
        if (enviado) {
            System.out.println("Fichero " + path.getFileName() + " enviado correctamente");
        } else {
            System.out.println("Error al enviar el fichero " + path.getFileName() + ". Código de respuesta: " + replyCode);
        }
        desconectar();
    }


    private void eliminarFichero(String fileName) throws IOException {
        conectar();
        boolean deleted = clienteFTP.deleteFile(fileName);
        if (deleted) {
            System.out.println("Fichero " + fileName + " eliminado correctamente");
        } else {
            System.out.println("Error al eliminar el fichero " + fileName);
        }
        desconectar();
    }

    private void actualizarFichero(Path path) throws IOException {
        File file = path.toFile();
        if (!file.exists()) {
            System.out.println("Error: El archivo " + file.getName() + " no existe.");
            return;
        }
        conectar();
        String remoteFilePath = "/ftp/" + file.getName();
        FileInputStream fis = new FileInputStream(file);
        boolean actualizado = clienteFTP.storeFile(remoteFilePath, fis);
        fis.close();
        int replyCode = clienteFTP.getReplyCode();
        if (actualizado) {
            System.out.println("Fichero " + file.getName() + " actualizado correctamente en el servidor.");
        } else {
            System.out.println("Error al actualizar el fichero " + file.getName() + ". Código de respuesta: " + replyCode);
        }
        desconectar();
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

    private void watchFolder() {
        try {
            Path path = Paths.get(LOCAL_FOLDER);
            WatchService ws = FileSystems.getDefault().newWatchService();
            path.register(ws,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY
            );
            while (true) {
                WatchKey key = ws.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    Path archivoCambiado = (Path) event.context();
                    Path rutaCompleta = path.resolve(archivoCambiado);
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        subirFichero(rutaCompleta);
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        eliminarFichero(archivoCambiado.toString());
                    } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        actualizarFichero(rutaCompleta);
                    }
                }
                key.reset();
            }
        } catch (IOException ex) {
            System.err.println("Error al encontrar el directorio. Excepción: " + ex.getMessage());
        } catch (InterruptedException ex) {
            System.err.println("Error relacionado con hilos y procesos (Interrumpidos). Excepción: " + ex.getMessage());
        }
    }

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
