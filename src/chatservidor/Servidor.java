package chatservidor;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.JOptionPane;

/**
 * Servidor para chat tipo WhatsApp 
 * 
 */
public class Servidor extends Thread {
    private ServerSocket serverSocket;

    private List<HiloCliente> clientes; 
    private final VentanaS ventana;
    private final String puerto;
    static int correlativo;
    
    
    private Map<String, LinkedList<String>> mensajesOffline; 
    private Map<String, LinkedList<String>> contactosPorUsuario; 
    private Map<String, Long> ultimaActividad; 
    private Map<String, String> nicknames; 
    
    public Servidor(String puerto, VentanaS ventana) {
        correlativo = 0;
        this.puerto = puerto;
        this.ventana = ventana;
        this.clientes = Collections.synchronizedList(new LinkedList<>()); 
        
        // Inicializar estructuras de datos (thread-safe)
        this.mensajesOffline = new ConcurrentHashMap<>();
        this.contactosPorUsuario = new ConcurrentHashMap<>();
        this.ultimaActividad = new ConcurrentHashMap<>();
        this.nicknames = new ConcurrentHashMap<>();
        
        this.start();
    }
    
    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(Integer.valueOf(puerto));
            ventana.addServidorIniciado();
            agregarLog("Servidor iniciado en puerto: " + puerto);
            
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Nueva conexión entrante: " + socket);
                agregarLog("Nueva conexión desde: " + socket.getInetAddress());
                
                HiloCliente h = new HiloCliente(socket, this);
                h.start();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(ventana, "El servidor no se ha podido iniciar,\n"
                    + "puede que haya ingresado un puerto incorrecto.\n"
                    + "Esta aplicación se cerrará.");
            System.exit(0);
        }
    }
    
    /**
     * Obtiene la lista de usuarios conectados actualmente
     */
    public LinkedList<String> getUsuariosConectados() {
        LinkedList<String> usuariosConectados = new LinkedList<>();
        synchronized (clientes) {
            clientes.stream().forEach(c -> {
                if (c.getIdentificador() != null) {
                    usuariosConectados.add(c.getIdentificador());
                }
            });
        }
        return usuariosConectados;
    }
    
    /**
     * Método para manejar mensajes offline
     */
    public void almacenarMensajeOffline(String emisor, String receptor, String mensaje, String timestamp) {
        if (!mensajesOffline.containsKey(receptor)) {
            mensajesOffline.put(receptor, new LinkedList<>());
        }
        
        String mensajeCompleto = emisor + "|" + mensaje + "|" + timestamp;
        mensajesOffline.get(receptor).add(mensajeCompleto);
        
        agregarLog("Mensaje offline almacenado para " + receptor + " de " + emisor);
    }
    
    /**
     * Entrega mensajes offline cuando un usuario se conecta
     */
    public void entregarMensajesOffline(String usuario, HiloCliente cliente) {
        if (mensajesOffline.containsKey(usuario)) {
            LinkedList<String> mensajes = mensajesOffline.get(usuario);
            
            for (String mensajeCompleto : mensajes) {
                String[] partes = mensajeCompleto.split("\\|", 3);
                if (partes.length == 3) {
                    String emisor = partes[0];
                    String mensaje = partes[1];
                    String timestamp = partes[2];
                    
                    LinkedList<String> lista = new LinkedList<>();
                    lista.add("MENSAJE");
                    lista.add(emisor);
                    lista.add(usuario);
                    lista.add(mensaje);
                    lista.add(timestamp);
                    
                    cliente.enviarMensaje(lista);
                }
            }
            
            mensajesOffline.remove(usuario);
            agregarLog("Entregados " + mensajes.size() + " mensajes offline a " + usuario);
        }
    }
    
    /**
     * Busca un cliente por su identificador
     */
    public HiloCliente buscarCliente(String identificador) {
        synchronized (clientes) {
            return clientes.stream()
                    .filter(h -> identificador.equals(h.getIdentificador()))
                    .findFirst()
                    .orElse(null);
        }
    }
    
    /**
     * Verifica si un usuario está conectado
     */
    public boolean estaConectado(String identificador) {
        return buscarCliente(identificador) != null;
    }
    
    /**
     * Envía un mensaje a un usuario específico (conectado o desconectado)
     */
    public boolean enviarMensajeAUsuario(String emisor, String receptor, String mensaje, String timestamp) {
        HiloCliente clienteReceptor = buscarCliente(receptor);
        
        if (clienteReceptor != null) {
            // Usuario está conectado, enviar mensaje inmediatamente
            LinkedList<String> lista = new LinkedList<>();
            lista.add("MENSAJE");
            lista.add(emisor);
            lista.add(receptor);
            lista.add(mensaje);
            lista.add(timestamp);
            
            clienteReceptor.enviarMensaje(lista);
            agregarLog("Mensaje enviado de " + emisor + " a " + receptor);
            return true;
        } else {
            // Usuario está desconectado, almacenar mensaje
            almacenarMensajeOffline(emisor, receptor, mensaje, timestamp);
            return false;
        }
    }
    
    /**
     * Notifica a todos los clientes sobre un cambio de estado
     */
    public void notificarCambioEstado(String usuario, String tipo) {
        LinkedList<String> lista = new LinkedList<>();
        lista.add(tipo);
        lista.add(usuario);
        
        synchronized (clientes) {
            clientes.stream().forEach(cliente -> cliente.enviarMensaje(lista));
        }
        
        // Actualizar última actividad
        ultimaActividad.put(usuario, System.currentTimeMillis());
    }
    
    /**
     * Agregar contacto a la lista de un usuario
     */
    public void agregarContactoAUsuario(String usuario, String contacto) {
        if (!contactosPorUsuario.containsKey(usuario)) {
            contactosPorUsuario.put(usuario, new LinkedList<>());
        }
        
        LinkedList<String> contactos = contactosPorUsuario.get(usuario);
        if (!contactos.contains(contacto)) {
            contactos.add(contacto);
            agregarLog("Contacto " + contacto + " agregado a " + usuario);
        }
    }
    
    /**
     * Obtener contactos de un usuario
     */
    public LinkedList<String> getContactosDeUsuario(String usuario) {
        return contactosPorUsuario.getOrDefault(usuario, new LinkedList<>());
    }
    
    /**
     * Método para registrar actividad del servidor con timestamp y salto de línea
     */
    public void agregarLog(String texto) {
        String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
        ventana.agregarLog("[" + timestamp + "] " + texto);
    }
    
    /**
     * Obtiene estadísticas del servidor
     */
    public String getEstadisticas() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== ESTADÍSTICAS DEL SERVIDOR ===\n");
        stats.append("Usuarios conectados: ").append(clientes.size()).append("\n");
        stats.append("Mensajes offline almacenados: ").append(mensajesOffline.size()).append("\n");
        stats.append("Total de usuarios registrados: ").append(nicknames.size()).append("\n");
        
        stats.append("\nUsuarios conectados:\n");
        synchronized (clientes) {
            for (HiloCliente cliente : clientes) {
                if (cliente.getIdentificador() != null) {
                    stats.append("- ").append(cliente.getIdentificador()).append("\n");
                }
            }
        }
        
        return stats.toString();
    }
    
    /**
     * Cierra el servidor de forma segura
     */
    public void cerrarServidor() {
        try {
            // Notifica a todos los clientes que el servidor se cierra
            LinkedList<String> mensaje = new LinkedList<>();
            mensaje.add("SERVIDOR_CERRANDO");
            
            synchronized (clientes) {
                for (HiloCliente cliente : clientes) {
                    cliente.enviarMensaje(mensaje);
                    cliente.desconnectar();
                }
                clientes.clear();
            }
            
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            agregarLog("Servidor cerrado correctamente");
        } catch (Exception e) {
            System.err.println("Error al cerrar el servidor: " + e.getMessage());
        }
    }
    

    public Map<String, LinkedList<String>> getMensajesOffline() {
        return mensajesOffline;
    }
    
    public Map<String, LinkedList<String>> getContactosPorUsuario() {
        return contactosPorUsuario;
    }
    
    public Map<String, Long> getUltimaActividad() {
        return ultimaActividad;
    }
    
    public List<HiloCliente> getClientes() {
        return clientes;
    }
}