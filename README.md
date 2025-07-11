# 🧵 Servidor de Chat con Sockets en Java

Este repositorio contiene la implementación de un **servidor multicliente** para una aplicación de chat desarrollada en Java, utilizando **Sockets TCP** y **programación concurrente con hilos** (`Threads`).

> 🎓 Este proyecto está orientado a estudiantes que estén aprendiendo redes, sockets o concurrencia. Personalmente, me hubiera servido mucho como referencia, así que decidí compartirlo para que otros también se beneficien.

---

## 🚀 Características

- Soporte para múltiples clientes usando `Threads`
- Conexiones TCP/IP mediante `Sockets`
- Registro de actividad en tiempo real (conexión y desconexión de usuarios)
- Interfaz gráfica simple para el servidor
- Manejo de errores de conexión y desconexiones inesperadas

---

## 🛠 Requisitos

- Java 8 o superior
- IDE como IntelliJ, Eclipse o NetBeans
- JDK configurado en tu sistema

---

## ⚙️ Cómo ejecutar

1. **Inicia la aplicación del servidor**  
   Al ejecutarla, se te pedirá ingresar un puerto de escucha. Puedes usar el puerto por defecto o ingresar otro (que no esté ocupado).

2. **Verifica la ventana del servidor**  
   Si todo va bien, verás una ventana con el registro (log) de eventos como este:

   ```txt
   [Servidor iniciado en el puerto 5000]
   [Cliente conectado desde 192.168.0.5]
   ```

3. **Ejecuta los clientes**  
   Los clientes deben ingresar la IP del servidor y el puerto configurado para conectarse. Si los datos son incorrectos o el servidor no está activo, se mostrará un mensaje de error.

---

## ⚠️ Consideraciones

- Si el servidor se cierra mientras hay clientes conectados, estos mostrarán un mensaje de error y se cerrarán automáticamente.
- El servidor debe estar activo antes de que los clientes se conecten.
- Asegúrate de que el puerto seleccionado esté libre.

---

## 🎯 Objetivo del proyecto

Este proyecto fue creado como una herramienta educativa para ayudar a entender los fundamentos de la comunicación cliente-servidor con Java. Puedes usarlo como base para:

- Aplicaciones de mensajería más avanzadas
- Proyectos académicos
- Prácticas de redes o hilos

---

## 🤝 Contribuciones

Si tienes ideas para mejorar el proyecto o detectas algún problema, no dudes en:

- Crear un fork
- Abrir un Issue
- Enviar un Pull Request

---

## 📄 Licencia

Este proyecto está bajo la Licencia MIT. Consulta el archivo LICENSE para más información.

---

## 📷 Capturas de pantalla
