# 📂 FTP File Updater

## 🚀 Descripción
Este proyecto es una aplicación en **Java** que permite actualizar archivos en un servidor **FTP**, asegurando la seguridad de archivos de texto mediante cifrado y manteniendo un historial de versiones anteriores.

## 🛠️ Características
- 🔄 **Actualización automática** de archivos en un servidor FTP.
- 🔐 **Cifrado de archivos de texto** antes de la subida.
- 📜 **Mantenimiento de historial**, moviendo versiones antiguas a una carpeta específica.
- 📊 **Registro de logs** para auditoría y seguimiento.

## 📌 Requisitos
- **Java 8+**
- **Servidor FTP (vsftpd, ProFTPD o Pure-FTPd)**
- **Librerías necesarias:**
  - Apache Commons Net (`org.apache.commons.net.ftp.FTPClient`)

## ⚙️ Instalación y Configuración
### 1️⃣ Clonar el repositorio
```sh
 git clone https://github.com/tu-usuario/ftp-file-updater.git
 cd ftp-file-updater
```

## 📖 Uso
1. Coloca los archivos en la carpeta monitoreada.
2. El programa detectará los cambios y actualizará el servidor FTP.
3. Si el archivo ya existe, se moverá a **/ftp/historial/** antes de actualizarlo.
4. Puedes ver logs en tiempo real para verificar el estado.

## 🛠️ Mejoras Futuras
✅ Soporte para **SFTP** en lugar de FTP.
✅ Implementación de una **base de datos** para el historial.
✅ Interfaz web para gestión de archivos.
✅ Notificaciones en tiempo real (email, Slack, etc.).


## 💡 Autor
👤 Jose Oteros Ruiz  
