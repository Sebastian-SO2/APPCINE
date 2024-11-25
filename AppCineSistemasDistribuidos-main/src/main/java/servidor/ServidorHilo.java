package servidor;

import controlador.Controlador;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;


/**
 * @author Christian Morga
 */

public class ServidorHilo extends Thread {
    private final Socket socket;
    private final String idTransaccion;
    private final int contadorClientes;
    
    // Guardar el temporizador como una variable de instancia
    private Timer timer;

    public ServidorHilo(Socket socket, String idTransaccion, int contadorClientes) {
        this.socket = socket;
        this.idTransaccion = idTransaccion;
        this.contadorClientes = contadorClientes;
        
         this.timer = new Timer(); // Inicializamos el temporizador
    }

    @Override
    public void run() {
        
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            // Iniciamos el temporizador al comienzo de la conexión para asegurarnos de que 
            // el cliente no se quede inactivo más de 60 segundos sin interactuar.
            iniciarTimeout(out);

            boolean asientosReservados;
            int eleccionFuncion;
            int cantidadAsientosPorReservar;
            List<String> listaPosicionesAsientos;

            do {
                enviarMensajeBienvenidaFuncion(out);

                // Reiniciamos el temporizador después de cada paso importante
                reiniciarTimer(out);

                eleccionFuncion = procesarEleccionFuncion(in, out);

                out.writeUTF(Controlador.mostrarDisposicionAsientos(eleccionFuncion, Servidor.sala1));

                // Reiniciamos el temporizador
                reiniciarTimer(out);

                cantidadAsientosPorReservar = procesarCantidadAsientosCompra(in, out, eleccionFuncion);

                // Reiniciamos el temporizador
                reiniciarTimer(out);

                listaPosicionesAsientos = new ArrayList<>(obtenerListaAsientosPorComprar(in, out, cantidadAsientosPorReservar));

                // Reiniciamos el temporizador
                reiniciarTimer(out);

                asientosReservados = Servidor.sala1.getFunciones().get(eleccionFuncion - 1).reservarAsientos(
                        listaPosicionesAsientos, idTransaccion);
                if (!asientosReservados) {
                    out.writeUTF("\n¡Lo sentimos! Alguno de los asientos seleccionados ya está ocupado.\n" +
                            "Por favor, intenta con otros asientos.");
                }
            } while (!asientosReservados);

            out.writeUTF("exito");
            Servidor.sala1.getFunciones().get(eleccionFuncion - 1).confirmarCompra(idTransaccion);
            System.out.println("\nCliente " + contadorClientes + " - Compra exitosa de " +
                    cantidadAsientosPorReservar + " asiento(s) de la función " + eleccionFuncion);
            out.writeUTF("\n¡Reserva exitosa! Gracias por tu compra. 😊");

        } catch (IOException e) {
            System.out.println("\nError en la conexión con el cliente: " + contadorClientes + " - " + e.getMessage());
        } finally {
            // Aseguramos que el temporizador se detenga y se cierre correctamente el socket
            timer.cancel();
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.out.println("Error al cerrar el socket: " + e.getMessage());
            }
            Servidor.mensajeClienteDesconectado(idTransaccion, contadorClientes);
        }
    }

    // Método para iniciar el temporizador con un tiempo de espera de 60 segundos
    private void iniciarTimeout(DataOutputStream out) {
        System.out.println("inicializando ");
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    // Si el temporizador se activa (es decir, el cliente no ha interactuado en 60 segundos),
                    // imprimimos un mensaje en consola para indicar que se ha excedido el tiempo.
                    System.out.println("\nCliente " + contadorClientes + " - Tiempo excedido general.");

                    // Informamos al cliente que ha superado el tiempo de espera.
                    out.writeUTF("Te pasaste de tiempo.");

                    // Cerramos la conexión con el cliente debido a la inactividad prolongada.
                    socket.close();
                } catch (IOException e) {
                    // Si ocurre un error al intentar cerrar el socket, lo capturamos e imprimimos un mensaje de error.
                    System.err.println("Error cerrando conexión por timeout: " + e.getMessage()+" gracias al general");
                }

                // Interrumpimos el hilo actual para detener su ejecución, asegurándonos de que no se siga procesando.
                interrupt();
            }
        }, 10000); // 60 segundos de timeout
    }

    // Método para reiniciar el temporizador cada vez que el cliente interactúa
    private void reiniciarTimer(DataOutputStream out) {
        // Cancelamos el temporizador anterior y lo reiniciamos
        timer.cancel();
        timer = new Timer();
        System.out.println("Reiniciando temporizador por inactividad del cliente.");

        // Iniciamos un nuevo temporizador con un tiempo de espera de 60 segundos
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    // Si el temporizador se activa (es decir, el cliente no ha interactuado en 60 segundos),
                    // imprimimos un mensaje en consola para indicar que se ha excedido el tiempo.
                    System.out.println("\nCliente " + contadorClientes + " - Tiempo excedido de reinicio.");

                    // Informamos al cliente que ha superado el tiempo de espera.
                    out.writeUTF("Te pasaste de tiempo.");

                    // Cerramos la conexión con el cliente debido a la inactividad prolongada.
                    socket.close();
                } catch (IOException e) {
                    // Si ocurre un error al intentar cerrar el socket, lo capturamos e imprimimos un mensaje de error.
                    System.err.println("Error cerrando conexión por timeout: " + e.getMessage()+" gracias al de reincio");
                }

                // Interrumpimos el hilo actual para detener su ejecución, asegurándonos de que no se siga procesando.
                interrupt();
            }
        }, 10000); // 60 segundos de timeout
    }



    private void enviarMensajeBienvenidaFuncion(DataOutputStream out) throws IOException {
        out.writeUTF("¡Gracias por conectarte alservidor del cine!\n\n" +
                "Funciones disponibles:\n" +
                Servidor.sala1.listarFunciones() +
                "\nPor favor, selecciona una función escribiendo el número correspondiente." +
                "\nTu elección: ");
    }

    private int procesarEleccionFuncion(DataInputStream in, DataOutputStream out) throws IOException {
        int eleccionFuncion;
        while (!Controlador.validarFuncionElegida(in.readInt(), Servidor.sala1)) {
            out.writeUTF("invalida");
        }
        out.writeUTF("valida");
        eleccionFuncion = in.readInt();
        System.out.println("\nCliente " + contadorClientes + " - eligió la función " + eleccionFuncion);
        return eleccionFuncion;
    }

    private int procesarCantidadAsientosCompra(DataInputStream in, DataOutputStream out, int eleccionFuncion)
            throws IOException
    {
        out.writeUTF("\nPor favor, ingresa la cantidad de asientos que deseas reservar: ");
        int cantidadAsientosPorReservar;
        while (!Controlador.validarCantidadAsientos(in.readInt(), Servidor.sala1, eleccionFuncion)) {
            out.writeUTF("invalida");
        }
        out.writeUTF("valida");
        cantidadAsientosPorReservar = in.readInt();
        System.out.println("\nCliente " + contadorClientes + " - quiere comprar " +
                cantidadAsientosPorReservar + " asiento(s) de la funcion " + eleccionFuncion);
        return cantidadAsientosPorReservar;
    }

    private Set<String> obtenerListaAsientosPorComprar(DataInputStream in, DataOutputStream out, int cantidadAsientosPorReservar)
            throws IOException{
        Set<String> posicionesAsientos = new HashSet<>();
        out.writeUTF("\nAhora, introduce la posición de los asientos que deseas reservar.\n" +
                "El formato a seguir es f-c (Ejemplo: 1-1).");
        for (int i = 0; i < cantidadAsientosPorReservar; i++) {
            out.writeUTF("\nPosición del asiento " + (i + 1) + ": ");
            String p;
            while (!Controlador.validarFormatoAsiento(in.readUTF())) {
                out.writeUTF("invalida");
            }
            out.writeUTF("valida");
            p = in.readUTF();
            posicionesAsientos.add(p);
        }
        return posicionesAsientos;
    }

    public String getIdTransaccion() {
        return idTransaccion;
    }

    public int getContadorClientes() {
        return contadorClientes;
    }
}
