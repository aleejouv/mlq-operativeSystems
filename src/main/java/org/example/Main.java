package org.example;

import java.io.IOException;

/**
 * Punto de entrada principal para ejecutar el simulador MLQ.
 */
public class Main {

    public static void main(String[] args){
        String inputFile = "mlq025.txt";
        String outputFile = "output_" + inputFile;

        PlanificadorMLQ planificador = new PlanificadorMLQ();

        try {
            // 1. Cargar procesos del archivo
            planificador.cargarProcesos(inputFile);

            // 2. Ejecutar la simulación
            planificador.simular();

            // 3. Escribir los resultados en el archivo de salida
            planificador.escribirResultados(outputFile, inputFile);

        } catch (IOException e) {
            System.err.println("Error de E/S al procesar el archivo: " + e.getMessage());
            e.printStackTrace();
        }
    }
}