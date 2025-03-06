package gestorFTP;

import java.io.*;
import java.nio.file.*;
import java.net.SocketException;
import java.security.Key;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

public class GestorFTP {
    private FTPClient clienteFTP; // Cliente FTP para establecer conexión con el servidor
    private static final String SERVIDOR = "172.29.108.147"; // Dirección IP del servidor FTP
    private static final int PUERTO = 21; // Puerto por defecto para conexiones FTP
    private static final String USUARIO = "joseftp"; // Usuario para autenticación en el servidor FTP
    private static final String PASSWORD = "12345678"; // Contraseña del usuario FTP
    private static final String LOCAL_FOLDER = "C:\\Users\\otero\\Desktop\\local"; // Carpeta local que se monitorea
    private static final String CLAVE_AES = "JoseOteros19_123"; // Clave utilizada para cifrar archivos de texto
    private Key claveSecreta; // Objeto que almacena la clave de cifrado AES
    private static final String[] EXTENSIONES_TEXTO = {".txt"}; // Lista de extensiones de archivos de texto que serán cifrados
    private final ExecutorService executorService; // Servicio para lanzar hilos y realizar operaciones en paralelo
    private final ReentrantLock lock = new ReentrantLock(); // Objeto de bloqueo para sincronización de hilos

    // Constructor de la clase
    public GestorFTP() {
        clienteFTP = new FTPClient(); // Se inicializa el cliente FTP
        try {
            // Se genera la clave AES a partir de la clave definida en la constante CLAVE_AES
            claveSecreta = new SecretKeySpec(CLAVE_AES.getBytes(), "AES");
        } catch (Exception e) {
            System.err.println("Error al inicializar la clave AES: " + e.getMessage());
        }
        // Se crea un pool de hilos para realizar operaciones en paralelo
        executorService = Executors.newFixedThreadPool(10);
    }

    // Método para conectar al servidor FTP
    void conectar() throws SocketException, IOException {
        // Establece conexión con el servidor FTP
        clienteFTP.connect(SERVIDOR, PUERTO);
        // Obtiene el código de respuesta del servidor
        int respuesta = clienteFTP.getReplyCode();

        // Verifica si se conectó correctamente
        if (!FTPReply.isPositiveCompletion(respuesta)) {
            clienteFTP.disconnect();
            throw new IOException("Error al conectar con el servidor FTP");
        }

        // Intenta iniciar sesión en el servidor con las credenciales proporcionadas
        if (!clienteFTP.login(USUARIO, PASSWORD)) {
            throw new IOException("Error en las credenciales");
        }

        // Configura el tipo de archivo a binario
        clienteFTP.setFileType(FTP.BINARY_FILE_TYPE);
        System.out.println("Conectado");
        // Cambia a modo pasivo para evitar problemas con firewalls
        clienteFTP.enterLocalPassiveMode();
        // Cambia el directorio de trabajo en el servidor FTP
        clienteFTP.changeWorkingDirectory("/ftp");
    }

    // Método para desconectar del servidor FTP
    private void desconectar() throws IOException {
        clienteFTP.logout(); // Cierra la sesión FTP
        clienteFTP.disconnect(); // Desconecta el cliente FTP del servidor
        System.out.println("Desconectado");
    }

    // Método para verificar si un archivo es de texto basándose en su extensión
    private boolean esArchivoDeTexto(String nombreArchivo) {
        // Convierte el nombre a minúsculas para evitar errores con mayúsculas
        nombreArchivo = nombreArchivo.toLowerCase();
        // Compara la extensión del archivo para comprobar si es un archivo de texto
        for (String extension : EXTENSIONES_TEXTO) {
            if (nombreArchivo.endsWith(extension)) {
                // Si la extensión coincide, se considera un archivo de texto
                return true;
            }
        }
        // Si no coincide con ninguna extensión, no es un archivo de texto
        return false;
    }

    // Método para cifrar el contenido de un archivo usando AES
    private byte[] cifrarContenido(byte[] contenido) throws Exception {
        // Se obtiene una instancia del cifrador AES
        Cipher cipher = Cipher.getInstance("AES");
        // Se inicializa en modo cifrado con la clave secreta
        cipher.init(Cipher.ENCRYPT_MODE, claveSecreta);
        // Se cifra el contenido y se retorna
        return cipher.doFinal(contenido);
    }

    // Método para descifrar el contenido de un archivo usando AES
    private byte[] descifrarContenido(byte[] contenidoCifrado) throws Exception {
        // Se obtiene una instancia del cifrador AES
        Cipher cipher = Cipher.getInstance("AES");
        // Se inicializa en modo descifrado con la clave secreta
        cipher.init(Cipher.DECRYPT_MODE, claveSecreta);
        // Se descifra el contenido y se retorna
        return cipher.doFinal(contenidoCifrado);
    }

    // Método para descifrar un archivo de texto
    private void descifrarArchivo(File file) throws Exception {
        // Se lee el contenido cifrado del archivo
        byte[] contenidoCifrado = Files.readAllBytes(file.toPath());
        // Se descifra el contenido
        byte[] contenidoDescifrado = descifrarContenido(contenidoCifrado);
        // Se escribe el contenido descifrado en el archivo
        Files.write(file.toPath(), contenidoDescifrado);
        System.out.println("Archivo " + file.getName() + " descifrado correctamente");
    }

    // Método para subir un archivo al servidor FTP
    private void subirFichero(Path path) throws IOException {
        // Se envía la subida del archivo en un hilo separado
        executorService.submit(() -> {
            // Se obtiene el archivo desde la ruta
            File file = path.toFile();
            // Verifica si el archivo existe antes de intentar subirlo
            if (!file.exists()) {
                System.out.println("Error: El archivo " + file.getName() + " no existe.");
                return;
            }
            try {
                lock.lock();
                // Establece conexión con el servidor FTP
                conectar();
                System.out.println("Enviando archivo: " + file.getName());
                boolean enviado;
                // Si es un archivo de texto, se cifra antes de enviarlo
                if (esArchivoDeTexto(file.getName())) {
                    // Lee el contenido del archivo y lo convierte a bytes
                    byte[] contenido = Files.readAllBytes(path);
                    // Cifra el contenido
                    byte[] contenidoCifrado = cifrarContenido(contenido);
                    String nombreCifrado = file.getName();

                    System.out.println("Cifrando archivo de texto: " + file.getName());

                    // Convierte el contenido cifrado en un InputStream
                    InputStream is = new ByteArrayInputStream(contenidoCifrado);
                    // Envía el archivo cifrado al servidor FTP
                    enviado = clienteFTP.storeFile(nombreCifrado, is);
                    is.close();
                } else {
                    // Si no es un archivo de texto, se envía sin cifrar
                    FileInputStream is = new FileInputStream(file);
                    enviado = clienteFTP.storeFile(file.getName(), is);
                    is.close();
                }

                // Verifica si el archivo fue enviado correctamente
                int replyCode = clienteFTP.getReplyCode();
                if (enviado) {
                    System.out.println("Fichero " + path.getFileName() + " enviado correctamente");
                } else {
                    System.out.println("Error al enviar el fichero " + path.getFileName() + ". Código de respuesta: " + replyCode);
                }
            } catch (Exception e) {
                System.err.println("Error al procesar el archivo " + file.getName() + ": " + e.getMessage());
            } finally {
                try {
                    // Desconecta del servidor FTP
                    desconectar();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    lock.unlock();
                }
            }
        });
    }

    // Método para eliminar un archivo del servidor FTP
    private void eliminarFichero(String fileName) throws IOException {
        // Se envía la eliminación del archivo en un hilo separado
        executorService.submit(() -> {
            try {
                lock.lock();
                // Establece conexión con el servidor FTP
                conectar();
                boolean deleted = clienteFTP.deleteFile(fileName);
                // Verifica si el archivo fue eliminado correctamente
                if (deleted) {
                    System.out.println("Fichero " + fileName + " eliminado correctamente");
                }
            } catch (Exception e) {
                System.err.println("Error al eliminar el archivo " + fileName + ": " + e.getMessage());
            } finally {
                try {
                    // Desconecta del servidor FTP
                    desconectar();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    lock.unlock();
                }
            }
        });
    }

    // Método para actualizar un archivo en el servidor FTP
    private void actualizarFichero(Path path) throws IOException {
        // Se envía la actualización del archivo en un hilo separado
        executorService.submit(() -> {
            // Se obtiene el archivo desde la ruta
            File file = path.toFile();
            // Verifica si el archivo existe antes de intentar actualizarlo
            if (!file.exists()) {
                System.out.println("Error: El archivo " + file.getName() + " no existe.");
                return;
            }
            try {
                lock.lock();
                // Establece conexión con el servidor FTP
                conectar();
                // Si es un archivo de texto, se cifra antes de actualizarlo
                boolean actualizado;
                if (esArchivoDeTexto(file.getName())) {
                    // Lee el contenido del archivo y lo convierte a bytes
                    byte[] contenido = Files.readAllBytes(path);
                    // Cifra el contenido
                    byte[] contenidoCifrado = cifrarContenido(contenido);
                    String nombreCifrado = file.getName();
                    String remoteFilePath = "/ftp/" + nombreCifrado;
                    System.out.println("Actualizando archivo de texto cifrado: " + nombreCifrado);
                    InputStream is = new ByteArrayInputStream(contenidoCifrado);
                    actualizado = clienteFTP.storeFile(remoteFilePath, is);
                    is.close();
                } else {
                    // Si no es un archivo de texto, se actualiza sin cifrar
                    String remoteFilePath = "/ftp/" + file.getName();
                    FileInputStream fis = new FileInputStream(file);
                    actualizado = clienteFTP.storeFile(remoteFilePath, fis);
                    fis.close();
                }
                int replyCode = clienteFTP.getReplyCode();
                // Verifica si el archivo fue actualizado correctamente
                if (actualizado) {
                    System.out.println("Fichero " + file.getName() + " actualizado correctamente en el servidor.");
                } else {
                    System.out.println("Error al actualizar el fichero " + file.getName() + ". Código de respuesta: " + replyCode);
                }
            } catch (Exception e) {
                System.err.println("Error al procesar el archivo " + file.getName() + ": " + e.getMessage());
            } finally {
                try {
                    desconectar();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    lock.unlock();
                }
            }
        });
    }

    // Método para descargar un archivo del servidor FTP
    void descargarFichero(String fileName) throws IOException {
        // Se envía la descarga del archivo en un hilo separado
        executorService.submit(() -> {
            try {
                lock.lock();
                // Establece conexión con el servidor FTP
                conectar();
                String remoteFilePath = "/ftp/" + fileName;
                File downloadFile = new File(LOCAL_FOLDER + File.separator + fileName);
                OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(downloadFile));
                boolean success = clienteFTP.retrieveFile(remoteFilePath, outputStream);
                outputStream.close();

                if (success) {
                    System.out.println("Fichero " + fileName + " descargado correctamente");
                    // Si es un archivo de texto, se descifra
                    if (esArchivoDeTexto(fileName)) {
                        descifrarArchivo(downloadFile);
                    }
                } else {
                    System.out.println("Error al descargar el fichero " + fileName);
                }
            } catch (Exception e) {
                System.err.println("Error al descargar el archivo " + fileName + ": " + e.getMessage());
            } finally {
                try {
                    desconectar();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    lock.unlock();
                    executorService.shutdown();
                }
            }
        });
    }

    // Método para descargar todos los archivos de la carpeta local
    void descargarTodosLosArchivos() {
        executorService.submit(() -> {
            try {
                lock.lock();
                // Establece conexión con el servidor FTP
                conectar();
                // Lista los archivos en el directorio remoto
                FTPFile[] files = clienteFTP.listFiles("/ftp");
                for (FTPFile file : files) {
                    if (!file.isDirectory()) {
                        descargarFichero(file.getName());
                    }
                }
            } catch (IOException ex) {
                System.err.println("Error al descargar los archivos. Excepción: " + ex.getMessage());
            } finally {
                try {
                    desconectar();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    lock.unlock();
                    executorService.shutdown();
                }
            }
        });
    }

    // Método que monitorea la carpeta local y sincroniza los cambios con el servidor FTP
    void watchFolder() {
        try {
            // Se obtiene la ruta de la carpeta local que se va a monitorear
            Path path = Paths.get(LOCAL_FOLDER);
            // Se crea un servicio de monitoreo
            WatchService ws = FileSystems.getDefault().newWatchService();

            // Registra los tipos de eventos a monitorear (creación, eliminación y modificación)
            path.register(ws,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY
            );

            // Bucle infinito para monitorear constantemente la carpeta
            while (true) {
                // Espera a que ocurra un evento en la carpeta
                WatchKey key = ws.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    Path archivoCambiado = (Path) event.context();
                    Path rutaCompleta = path.resolve(archivoCambiado);
                    // Determina qué acción realizar según el tipo de evento detectado
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