package gestorFTP;

import java.io.*;
import java.nio.file.*;
import java.net.SocketException;
import java.security.Key;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
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
    private static final String CLAVE_AES = "JoseOteros19_123";
    private Key claveSecreta;
    private static final String[] EXTENSIONES_TEXTO = {".txt"};

    public GestorFTP() {
        clienteFTP = new FTPClient();
        try {
            claveSecreta = new SecretKeySpec(CLAVE_AES.getBytes(), "AES");
        } catch (Exception e) {
            System.err.println("Error al inicializar la clave AES: " + e.getMessage());
        }
    }

    void conectar() throws SocketException, IOException {
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

    private boolean esArchivoDeTexto(String nombreArchivo) {
        nombreArchivo = nombreArchivo.toLowerCase();
        for (String extension : EXTENSIONES_TEXTO) {
            if (nombreArchivo.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private byte[] cifrarContenido(byte[] contenido) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, claveSecreta);
        return cipher.doFinal(contenido);
    }

    private byte[] descifrarContenido(byte[] contenidoCifrado) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, claveSecreta);
        return cipher.doFinal(contenidoCifrado);
    }

    private void subirFichero(Path path) throws IOException {
        File file = path.toFile();
        if (!file.exists()) {
            System.out.println("Error: El archivo " + file.getName() + " no existe.");
            return;
        }
        conectar();
        System.out.println("Enviando archivo: " + file.getName());
        try {
            boolean enviado;
            if (esArchivoDeTexto(file.getName())) {
                byte[] contenido = Files.readAllBytes(path);
                byte[] contenidoCifrado = cifrarContenido(contenido);
                String nombreCifrado = file.getName();
                System.out.println("Cifrando archivo de texto: " + file.getName());
                InputStream is = new ByteArrayInputStream(contenidoCifrado);
                enviado = clienteFTP.storeFile(nombreCifrado, is);
                is.close();
            } else {
                FileInputStream is = new FileInputStream(file);
                enviado = clienteFTP.storeFile(file.getName(), is);
                is.close();
            }

            int replyCode = clienteFTP.getReplyCode();
            if (enviado) {
                System.out.println("Fichero " + path.getFileName() + " enviado correctamente");
            } else {
                System.out.println("Error al enviar el fichero " + path.getFileName() + ". Código de respuesta: " + replyCode);
            }
        } catch (Exception e) {
            System.err.println("Error al procesar el archivo " + file.getName() + ": " + e.getMessage());
        } finally {
            desconectar();
        }
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
        try {
            boolean actualizado;
            if (esArchivoDeTexto(file.getName())) {
                byte[] contenido = Files.readAllBytes(path);
                byte[] contenidoCifrado = cifrarContenido(contenido);
                String nombreCifrado = file.getName();
                String remoteFilePath = "/ftp/" + nombreCifrado;
                System.out.println("Actualizando archivo de texto cifrado: " + nombreCifrado);
                InputStream is = new ByteArrayInputStream(contenidoCifrado);
                actualizado = clienteFTP.storeFile(remoteFilePath, is);
                is.close();
            } else {
                String remoteFilePath = "/ftp/" + file.getName();
                FileInputStream fis = new FileInputStream(file);
                actualizado = clienteFTP.storeFile(remoteFilePath, fis);
                fis.close();
            }

            int replyCode = clienteFTP.getReplyCode();
            if (actualizado) {
                System.out.println("Fichero " + file.getName() + " actualizado correctamente en el servidor.");
            } else {
                System.out.println("Error al actualizar el fichero " + file.getName() + ". Código de respuesta: " + replyCode);
            }
        } catch (Exception e) {
            System.err.println("Error al procesar el archivo " + file.getName() + ": " + e.getMessage());
        } finally {
            desconectar();
        }
    }

    void watchFolder() {
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
}