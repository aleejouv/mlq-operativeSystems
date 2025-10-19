package org.example;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Motor de simulación para el planificador MLQ (Multilevel Queue).
 * Gestiona las colas, el tiempo y la ejecución de procesos.
 */
public class PlanificadorMLQ {

    // Define los quantums para cada cola. Q1=RR(1), Q2=RR(3), Q3=SJF
    // Nota: El quantum de SJF será el tiempo restante del proceso.
    private final int[] QUANTUMS = {1, 3}; // Índices 0 (Q1) y 1 (Q2)

    // Lista de colas de listos.
    // Usamos List<Queue> para generalizar el acceso por nivel.
    private final List<Queue<Proceso>> colasDeListos;

    // Almacenes de procesos
    private final List<Proceso> todosLosProcesos; // Cargados del archivo
    private List<Proceso> procesosPendientes; // Procesos que aún no han llegado
    private final List<Proceso> procesosCompletados; // Procesos terminados

    // Estado de la simulación
    private int tiempoActual;
    private Proceso procesoEnEjecucion;
    private int quantumRestante;

    /**
     * Constructor del planificador.
     */
    public PlanificadorMLQ() {
        this.todosLosProcesos = new ArrayList<>();
        this.procesosCompletados = new ArrayList<>();
        this.tiempoActual = 0;
        this.procesoEnEjecucion = null;
        this.quantumRestante = 0;

        // Inicializar las 3 colas
        this.colasDeListos = new ArrayList<>(3);

        // Q1: RR(1) - Se usa una LinkedList como cola FIFO
        this.colasDeListos.add(new LinkedList<>());

        // Q2: RR(3) - Se usa una LinkedList como cola FIFO
        this.colasDeListos.add(new LinkedList<>());

        // Q3: SJF (Shortest Job First)
        // Se usa una PriorityQueue que ordena por el Burst Time original.
        this.colasDeListos.add(new PriorityQueue<>(Comparator.comparingInt(Proceso::getBurstTime)));
    }

    /**
     * Carga los procesos desde un archivo de texto.
     */
    public void cargarProcesos(String filename) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                if (linea.startsWith("#") || linea.trim().isEmpty()) {
                    continue; // Ignorar comentarios y líneas vacías
                }

                try {
                    String[] campos = linea.split(";");
                    String etiqueta = campos[0].trim();
                    int burstTime = Integer.parseInt(campos[1].trim());
                    int arrivalTime = Integer.parseInt(campos[2].trim());
                    int queueLevel = Integer.parseInt(campos[3].trim());
                    int priority = Integer.parseInt(campos[4].trim());

                    if (queueLevel < 1 || queueLevel > 3) {
                        System.err.println("Advertencia: Nivel de cola inválido para " + etiqueta + ". Se ignora.");
                        continue;
                    }

                    this.todosLosProcesos.add(
                            new Proceso(etiqueta, burstTime, arrivalTime, queueLevel, priority)
                    );
                } catch (Exception e) {
                    System.err.println("Error al parsear línea: '" + linea + "'. Error: " + e.getMessage());
                }
            }
        }
        // Clonar y ordenar la lista de pendientes por tiempo de llegada
        this.procesosPendientes = new ArrayList<>(this.todosLosProcesos);
        this.procesosPendientes.sort(Comparator.comparingInt(Proceso::getArrivalTime));
    }

    /**
     * Inicia y ejecuta la simulación completa.
     */
    public void simular() {
        System.out.println("Iniciando simulación...");

        // El bucle principal continúa mientras haya procesos completados
        // o procesos que aún no han llegado.
        while (procesosCompletados.size() < todosLosProcesos.size()) {

            // 1. Mover procesos de "pendientes" a "listos" si han llegado
            moverProcesosNuevos();

            // 2. Verificar preeminencia
            // Si un proceso de mayor prioridad (Q1) llega mientras Q2 o Q3 corre,
            // se debe interrumpir el actual.
            verificarPreeminencia();

            // 3. Si la CPU está ociosa, seleccionar el siguiente proceso
            if (procesoEnEjecucion == null) {
                seleccionarSiguienteProceso();
            }

            // 4. Ejecutar un "tick" de reloj
            if (procesoEnEjecucion != null) {

                // Marcar el tiempo de primera ejecución si es necesario
                if (procesoEnEjecucion.getFirstRunTime() == -1) {
                    procesoEnEjecucion.setFirstRunTime(tiempoActual);
                }

                // Ejecutar el tick
                procesoEnEjecucion.runTick();
                quantumRestante--;

                // 5. Verificar si el proceso terminó
                if (procesoEnEjecucion.isFinished()) {
                    procesoEnEjecucion.setCompletionTime(tiempoActual + 1); // Termina al *final* del tick
                    procesoEnEjecucion.calcularMetricas();
                    procesosCompletados.add(procesoEnEjecucion);
                    procesoEnEjecucion = null;
                    quantumRestante = 0;

                    // 6. Verificar si el quantum expiró (solo para RR Q1 y Q2)
                } else if (quantumRestante == 0 && procesoEnEjecucion.getQueueLevel() <= 2) {
                    // El quantum expiró, mover al final de su cola
                    colasDeListos.get(procesoEnEjecucion.getQueueLevel() - 1).add(procesoEnEjecucion);
                    procesoEnEjecucion = null;
                }

            } // Fin de if (procesoEnEjecucion != null)

            // 7. Avanzar el reloj
            tiempoActual++;

            // Condición de seguridad para evitar bucles infinitos si algo sale mal
            if (tiempoActual > 10000) {
                System.err.println("Simulación detenida: tiempo límite excedido.");
                break;
            }
        }
        System.out.println("Simulación completada en t=" + tiempoActual);
    }

    /**
     * Mueve los procesos que han llegado (AT <= tiempoActual)
     * de la lista de pendientes a la cola de listos correspondiente.
     */
    private void moverProcesosNuevos() {
        // Usamos un iterador para eliminar de forma segura mientras se itera
        var iter = procesosPendientes.iterator();
        while (iter.hasNext()) {
            Proceso p = iter.next();
            if (p.getArrivalTime() <= tiempoActual) {
                int nivel = p.getQueueLevel() - 1; // Nivel 1 -> índice 0
                colasDeListos.get(nivel).add(p);
                iter.remove();
            } else {
                // Como la lista está ordenada por AT, podemos parar
                break;
            }
        }
    }


    /**
     * Verifica si un proceso de mayor prioridad ha llegado
     * y debe apropiarse (preempt) de la CPU.
     */
    private void verificarPreeminencia() {
        if (procesoEnEjecucion == null) {
            return; // CPU ociosa, sin preeminencia
        }

        // Buscar si hay algo en una cola de *mayor* prioridad
        int nivelActual = procesoEnEjecucion.getQueueLevel();
        for (int i = 0; i < nivelActual - 1; i++) { // Iterar colas *por encima* de la actual
            if (!colasDeListos.get(i).isEmpty()) {
                // ¡Preeminencia! Devolver el proceso actual a su cola
                System.out.println(String.format("t=%d: PREEMINENCIA. Proceso %s (Q%d) interrumpido por Q%d",
                        tiempoActual, procesoEnEjecucion.getEtiqueta(), nivelActual, i + 1));

                colasDeListos.get(nivelActual - 1).add(procesoEnEjecucion);
                procesoEnEjecucion = null;
                quantumRestante = 0;
                return; // Salir, la preeminencia ocurrió
            }
        }
    }

    /**
     * Selecciona el siguiente proceso a ejecutar de la cola
     * de mayor prioridad que no esté vacía.
     */
    private void seleccionarSiguienteProceso() {
        for (int i = 0; i < colasDeListos.size(); i++) {
            Queue<Proceso> cola = colasDeListos.get(i);
            if (!cola.isEmpty()) {
                procesoEnEjecucion = cola.poll(); // Saca el proceso de la cola
                int nivel = procesoEnEjecucion.getQueueLevel();

                if (nivel == 1) { // Q1: RR(1)
                    quantumRestante = QUANTUMS[0];
                } else if (nivel == 2) { // Q2: RR(3)
                    quantumRestante = QUANTUMS[1];
                } else { // Q3: SJF
                    // SJF corre hasta terminar o ser interrumpido
                    // El quantum es su tiempo restante
                    quantumRestante = procesoEnEjecucion.getRemainingTime();
                }
                return; // Proceso seleccionado
            }
        }
        // Si llegamos aquí, todas las colas estaban vacías
        procesoEnEjecucion = null;
    }


    /**
     * Escribe los resultados de la simulación en un archivo de salida.
     */
    public void escribirResultados(String outputFilename, String inputFilename) throws IOException {
        // Ordenar los procesos completados por etiqueta (A, B, C...)
        procesosCompletados.sort(Comparator.comparing(Proceso::getEtiqueta));

        double totalWT = 0, totalCT = 0, totalRT = 0, totalTAT = 0;
        int n = procesosCompletados.size();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFilename))) {
            bw.write("# archivo: " + inputFilename);
            bw.newLine();
            bw.write(Proceso.getHeader());
            bw.newLine();

            for (Proceso p : procesosCompletados) {
                bw.write(p.toOutputString());
                bw.newLine();

                totalWT += p.getWaitingTime();
                totalCT += p.getCompletionTime();
                totalRT += p.getResponseTime();
                totalTAT += p.getTurnAroundTime();
            }

            // Escribir promedios
            String promedios = String.format("WT=%.1f; CT=%.1f; RT=%.1f; TAT=%.1f;",
                    (n > 0 ? totalWT / n : 0),
                    (n > 0 ? totalCT / n : 0),
                    (n > 0 ? totalRT / n : 0),
                    (n > 0 ? totalTAT / n : 0)
            ).replace(",", "."); // Asegurar punto decimal

            bw.newLine();
            bw.write(promedios);
            bw.newLine();
        }
        System.out.println("Resultados guardados en: " + outputFilename);
    }
}