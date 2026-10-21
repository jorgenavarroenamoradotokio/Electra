package com.redur.electra.data.model.carga;

/** Instrucciones de entrega que el repartidor debe conocer al cargar la expedición. */
public enum DeliveryInstruction {

    /** Hay que cobrar al entregar. */
    CASH_ON_DELIVERY,
    /** Se entrega en un punto de recogida (PUDO). */
    PICKUP_POINT,
    /** Solo se puede entregar completa, con todos sus bultos. */
    COMPLETE_DELIVERY,
    /** Se entrega dentro de un horario concertado. */
    TIME_WINDOW
}
