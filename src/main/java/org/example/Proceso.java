package org.example;

import java.util.Objects;

/**
 * Representa un proceso del sistema con sus atributos y métricas de planificación.
 */
public class Proceso {

    // Atributos de entrada
    private final String etiqueta;
    private final int burstTime; // BT original
    private final int arrivalTime; // AT
    private final int queueLevel; // Nivel de cola (1, 2, o 3)
    private final int priority; // Prioridad (no usada en esta lógica MLQ, pero almacenada)

    // Atributos de simulación
    private int remainingTime; // Tiempo restante de ráfaga
    private int firstRunTime; // Momento en que se ejecuta por primera vez

    // Métricas de salida
    private int completionTime; // CT
    private int turnAroundTime; // TAT
    private int waitingTime; // WT
    private int responseTime; // RT

    /**
     * Constructor para un nuevo proceso.
     */
    public Proceso(String etiqueta, int burstTime, int arrivalTime, int queueLevel, int priority) {
        this.etiqueta = etiqueta;
        this.burstTime = burstTime;
        this.arrivalTime = arrivalTime;
        this.queueLevel = queueLevel;
        this.priority = priority;

        // Inicializar atributos de simulación
        this.remainingTime = this.burstTime;
        this.firstRunTime = -1; // -1 indica que nunca se ha ejecutado
    }

    /**
     * Simula la ejecución del proceso por un "tick" de reloj.
     */
    public void runTick() {
        if (this.remainingTime > 0) {
            this.remainingTime--;
        }
    }

    /**
     * Calcula las métricas finales (TAT y WT) una vez que se conoce el CT.
     */
    public void calcularMetricas() {
        if (this.completionTime < this.arrivalTime) {
            throw new IllegalStateException("El tiempo de finalización no puede ser menor al de llegada.");
        }
        this.turnAroundTime = this.completionTime - this.arrivalTime;
        this.waitingTime = this.turnAroundTime - this.burstTime;

        // El tiempo de respuesta (RT) se calcula cuando se ejecuta por primera vez
        if (this.firstRunTime == -1) {
            // Caso especial: proceso que nunca corrió (ej. BT=0), aunque no aplica aquí
            this.responseTime = 0;
        } else {
            this.responseTime = this.firstRunTime - this.arrivalTime;
        }
    }

    /**
     * Formatea la salida del proceso para el archivo de resultados.
     */
    public String toOutputString() {
        return String.format("%s;%d;%d;%d;%d;%d;%d;%d;%d",
                etiqueta, burstTime, arrivalTime, queueLevel, priority,
                waitingTime, completionTime, responseTime, turnAroundTime);
    }

    /**
     * Devuelve el encabezado para el archivo de salida.
     */
    public static String getHeader() {
        return "# etiqueta; BT; AT; Q; Pr; WT; CT; RT; TAT";
    }

    // --- Getters y Setters ---

    public boolean isFinished() {
        return this.remainingTime == 0;
    }

    public String getEtiqueta() { return etiqueta; }
    public int getBurstTime() { return burstTime; }
    public int getArrivalTime() { return arrivalTime; }
    public int getQueueLevel() { return queueLevel; }
    public int getRemainingTime() { return remainingTime; }
    public int getFirstRunTime() { return firstRunTime; }

    public void setCompletionTime(int completionTime) {
        this.completionTime = completionTime;
    }

    public void setFirstRunTime(int firstRunTime) {
        // Solo establecer la primera vez
        if (this.firstRunTime == -1) {
            this.firstRunTime = firstRunTime;
        }
    }

    // Métricas
    public int getWaitingTime() { return waitingTime; }
    public int getCompletionTime() { return completionTime; }
    public int getResponseTime() { return responseTime; }
    public int getTurnAroundTime() { return turnAroundTime; }

    // Override para facilitar la depuración
    @Override
    public String toString() {
        return String.format("Proceso[%s, Q%d, BT=%d, AT=%d, Rem=%d]",
                etiqueta, queueLevel, burstTime, arrivalTime, remainingTime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Proceso proceso = (Proceso) o;
        return Objects.equals(etiqueta, proceso.etiqueta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(etiqueta);
    }
}











