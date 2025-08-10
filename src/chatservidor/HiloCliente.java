package chatservidor;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.LinkedList;

/**
 * HiloCliente para manejar múltiples funcionalidades tipo WhatsApp
 */
public class HiloCliente extends Thread {

    private final Socket socket;
    private ObjectOutputStream objectOutputStream;
    private ObjectInputStream objectInputStream;
    private final Servidor server;
    private String identificador;
    private boolean escuchando;
    private long ultimaActividad;
    private String estado; // "online", "offline", "ocupado", etc.

    public HiloCliente(Socket socket, Servidor server) {
        this.server = server;
        this.socket = socket;
        this.ultimaActividad = System.currentTimeMillis();
        this.estado = "online";

        try {
            objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            objectInputStream = new ObjectInputStream(socket.getInputStream());
        } catch (IOException ex) {
            System.err.println("Error en la inicialización de streams: " + ex.getMessage());
        }
    }

    public void desconnectar() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            escuchando = false;
            server.agregarLog("Cliente desconectado: "
                    + (identificador != null ? identificador : "Desconocido"));
        } catch (IOException ex) {
            System.err.println("Error al cerrar el socket: " + ex.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            Object initialAux = objectInputStream.readObject();
            if (initialAux instanceof LinkedList) {
                LinkedList<String> initialList = (LinkedList<String>) initialAux;
                if (initialList.get(0).equals("SOLICITUD_CONEXION") && initialList.size() > 1) {
                    this.identificador = initialList.get(1); // Asigna el ID del cliente

                    // Esto asegura que el servidor lo reconoce como conectado antes de cualquier otra operación
                    synchronized (server.getClientes()) {
                        server.getClientes().add(this);
                    }

                    server.agregarLog("Cliente " + this.identificador + " conectado y registrado.");

                    // Enviar la confirmación al cliente recién conectado (CONEXION_ACEPTADA)
                    LinkedList<String> respuestaConexion = new LinkedList<>();
                    respuestaConexion.add("CONEXION_ACEPTADA");
                    respuestaConexion.add(this.identificador);
                   
                    server.getUsuariosConectados().stream()
                            .filter(u -> !u.equals(this.identificador)) 
                            .forEach(respuestaConexion::add);
                    enviarMensaje(respuestaConexion);

                    // Entregar mensajes offline (ya que el cliente está confirmado y en la lista)
                    server.entregarMensajesOffline(this.identificador, this);

                    // Notificar a *otros* clientes sobre el nuevo usuario conectado
                    LinkedList<String> nuevoUsuarioNotificacion = new LinkedList<>();
                    nuevoUsuarioNotificacion.add("NUEVO_USUARIO_CONECTADO");
                    nuevoUsuarioNotificacion.add(this.identificador);

                    synchronized (server.getClientes()) {
                        server.getClientes().stream()
                                .filter(cliente -> !cliente.equals(this)) // No enviarse a sí mismo
                                .forEach(cliente -> cliente.enviarMensaje(nuevoUsuarioNotificacion));
                    }

                    // entrar en el bucle principal de escucha para mensajes de chat regulares
                    escuchando = true;
                    while (escuchando) {
                        Object nextAux = objectInputStream.readObject();
                        if (nextAux instanceof LinkedList) {
                            ultimaActividad = System.currentTimeMillis();
                            ejecutar((LinkedList<String>) nextAux);
                        }
                    }

                } else {
                    server.agregarLog("Primer mensaje no es una SOLICITUD_CONEXION válida de " + socket.getInetAddress());
                    desconnectar();
                }
            } else {
                server.agregarLog("Primer objeto recibido no es LinkedList de " + socket.getInetAddress());
                desconnectar();
            }

        } catch (EOFException e) {
            server.agregarLog("Cliente " + (identificador != null ? identificador : "desconocido") + " se ha desconectado (EOFException).");
        } catch (IOException e) {
            server.agregarLog("Error de I/O con cliente " + (identificador != null ? identificador : "desconocido") + ": " + e.getMessage());
        } catch (ClassNotFoundException e) {
            server.agregarLog("Error de clase al leer objeto de cliente " + (identificador != null ? identificador : "desconocido") + ": " + e.getMessage());
        } catch (Exception e) {
            server.agregarLog("Error inesperado en HiloCliente para " + (identificador != null ? identificador : "desconocido") + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (identificador != null) {
                synchronized (server.getClientes()) {
                    server.getClientes().remove(this);
                }
                server.notificarCambioEstado(identificador, "USUARIO_DESCONECTADO"); // Notifica a los demás
                server.agregarLog("Cliente " + identificador + " desconectado y eliminado.");
            }
            // Cerrar streams y socket (método desconnectar)
            desconnectar();
        }
    }

    public void escuchar() {
        escuchando = true;
        while (escuchando) {
            try {
                Object aux = objectInputStream.readObject();
                if (aux instanceof LinkedList) {
                    ultimaActividad = System.currentTimeMillis();
                    ejecutar((LinkedList<String>) aux);
                }
            } catch (Exception e) {
                if (escuchando) {
                    server.agregarLog("Error al leer del cliente "
                            + (identificador != null ? identificador : "desconocido")
                            + ": " + e.getMessage());
                }
                break;
            }
        }
    }

    /**
     * Método para ejecutar comandos del cliente
     */
    public void ejecutar(LinkedList<String> lista) {
        if (lista.isEmpty()) {
            return;
        }

        String tipo = lista.get(0);

        switch (tipo) {
            case "SOLICITUD_CONEXION":
                if (lista.size() > 1) {
                    confirmarConexion(lista.get(1));
                }
                break;

            case "SOLICITUD_DESCONEXION":
                confirmarDesConexion();
                break;

            case "MENSAJE":
                if (lista.size() >= 4) {
                    String emisor = lista.get(1);
                    String receptor = lista.get(2);
                    String mensaje = lista.get(3);
                    String timestamp = lista.size() > 4 ? lista.get(4) : String.valueOf(System.currentTimeMillis());

                    manejarMensaje(emisor, receptor, mensaje, timestamp);
                }
                break;

            case "AGREGAR_CONTACTO":
                if (lista.size() >= 3) {
                    String usuario = lista.get(1);
                    String contacto = lista.get(2);
                    agregarContacto(usuario, contacto);
                }
                break;

            case "SOLICITAR_USUARIOS_ONLINE":
                enviarUsuariosOnline();
                break;

            case "CAMBIAR_ESTADO":
                if (lista.size() > 1) {
                    cambiarEstado(lista.get(1));
                }
                break;

            case "PING":
                // Responder al ping para mantener conexión activa
                LinkedList<String> pong = new LinkedList<>();
                pong.add("PONG");
                enviarMensaje(pong);
                break;

            default:
                server.agregarLog("Comando no reconocido: " + tipo);
                break;
        }
    }

    /**
     * Maneja el envío de mensajes entre usuarios
     */
    private void manejarMensaje(String emisor, String receptor, String mensaje, String timestamp) {
        server.agregarLog("Mensaje de " + emisor + " para " + receptor + ": " + mensaje);

        boolean entregado = server.enviarMensajeAUsuario(emisor, receptor, mensaje, timestamp);

        if (!entregado) {
            // Notificar al emisor que el mensaje no fue entregado inmediatamente
            LinkedList<String> noEntregado = new LinkedList<>();
            noEntregado.add("MENSAJE_NO_ENTREGADO");
            noEntregado.add(receptor);
            noEntregado.add(mensaje);
            enviarMensaje(noEntregado);
        }
    }

    /**
     * Confirma la conexión de un nuevo cliente
     */
    private void confirmarConexion(String identificador) {
        Servidor.correlativo++;
        this.identificador = Servidor.correlativo + " - " + identificador;

        // Preparar respuesta de conexión aceptada
        LinkedList<String> lista = new LinkedList<>();
        lista.add("CONEXION_ACEPTADA");
        lista.add(this.identificador);
        lista.addAll(server.getUsuariosConectados());

        enviarMensaje(lista);
        server.agregarLog("Nuevo cliente conectado: " + this.identificador);

        // Entregar mensajes offline si los hay
        server.entregarMensajesOffline(this.identificador, this);

        // Notificar a otros clientes sobre el nuevo usuario
        LinkedList<String> nuevoUsuario = new LinkedList<>();
        nuevoUsuario.add("NUEVO_USUARIO_CONECTADO");
        nuevoUsuario.add(this.identificador);

        synchronized (server.getClientes()) {
            server.getClientes().stream()
                    .filter(cliente -> !cliente.equals(this))
                    .forEach(cliente -> cliente.enviarMensaje(nuevoUsuario));

            server.getClientes().add(this);
        }
    }

    /**
     * Agrega un contacto a la lista de un usuario
     */
    private void agregarContacto(String usuario, String contacto) {
        server.agregarContactoAUsuario(usuario, contacto);

        // Verificar si el contacto está en línea
        boolean estaEnLinea = server.estaConectado(contacto);

        LinkedList<String> respuesta = new LinkedList<>();
        respuesta.add("CONTACTO_AGREGADO");
        respuesta.add(contacto);
        respuesta.add(String.valueOf(estaEnLinea));

        enviarMensaje(respuesta);
    }

    /**
     * Envía la lista de usuarios en línea
     */
    private void enviarUsuariosOnline() {
        LinkedList<String> usuariosOnline = new LinkedList<>();
        usuariosOnline.add("USUARIOS_ONLINE");
        usuariosOnline.addAll(server.getUsuariosConectados());

        enviarMensaje(usuariosOnline);
    }

    /**
     * Cambia el estado del usuario
     */
    private void cambiarEstado(String nuevoEstado) {
        this.estado = nuevoEstado;
        server.agregarLog("Usuario " + identificador + " cambió estado a: " + nuevoEstado);

        // Notificar a otros usuarios sobre el cambio de estado
        LinkedList<String> cambioEstado = new LinkedList<>();
        cambioEstado.add("CAMBIO_ESTADO");
        cambioEstado.add(identificador);
        cambioEstado.add(nuevoEstado);

        synchronized (server.getClientes()) {
            server.getClientes().stream()
                    .filter(cliente -> !cliente.equals(this))
                    .forEach(cliente -> cliente.enviarMensaje(cambioEstado));
        }
    }

    /**
     * Envía un mensaje al cliente a través del socket
     */
    public void enviarMensaje(LinkedList<String> lista) {
        try {
            if (objectOutputStream != null && !socket.isClosed()) {
                objectOutputStream.writeObject(lista);
                objectOutputStream.flush();
            }
        } catch (Exception e) {
            server.agregarLog("Error al enviar mensaje a cliente "
                    + (identificador != null ? identificador : "desconocido")
                    + ": " + e.getMessage());
            escuchando = false;
        }
    }

    /**
     * Confirma la desconexión del cliente
     */
    private void confirmarDesConexion() {
        if (identificador != null) {
            LinkedList<String> usuarioDesconectado = new LinkedList<>();
            usuarioDesconectado.add("USUARIO_DESCONECTADO");
            usuarioDesconectado.add(identificador);

            server.agregarLog("El cliente \"" + identificador + "\" se ha desconectado.");

            // Remover de la lista de clientes
            synchronized (server.getClientes()) {
                server.getClientes().remove(this);
            }

            // Notificar a otros clientes
            synchronized (server.getClientes()) {
                server.getClientes().stream()
                        .forEach(h -> h.enviarMensaje(usuarioDesconectado));
            }
        }

        desconnectar();
    }

    // Getters y métodos auxiliares
    public String getIdentificador() {
        return identificador;
    }

    public long getUltimaActividad() {
        return ultimaActividad;
    }

    public String getEstado() {
        return estado;
    }

    public boolean estaConectado() {
        return escuchando && !socket.isClosed();
    }

    /**
     * Verifica si el cliente está activo (basado en última actividad)
     */
    public boolean estaActivo() {
        long tiempoInactivo = System.currentTimeMillis() - ultimaActividad;
        return tiempoInactivo < 300000; // 5 minutos de inactividad máxima
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        HiloCliente that = (HiloCliente) obj;
        return identificador != null ? identificador.equals(that.identificador) : that.identificador == null;
    }

    @Override
    public int hashCode() {
        return identificador != null ? identificador.hashCode() : 0;
    }
}
