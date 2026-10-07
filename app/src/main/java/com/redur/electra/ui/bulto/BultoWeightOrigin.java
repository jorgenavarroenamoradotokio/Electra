package com.redur.electra.ui.bulto;

/**
 * Desde dónde se abre la pantalla de peso y medidas. Cada origen decide con qué datos se abre y
 * qué pasa con la lectura del bulto si se cancela. Al ser un único valor, los cuatro checks de
 * peso de la lectura quedan excluyentes entre sí.
 */
public enum BultoWeightOrigin {

    /** Botón KG: se abre con los datos que ya tenga el bulto; cancelar no cambia la lectura. */
    KG_BUTTON(InitialData.CURRENT_BULTO, CancelEffect.KEEP_UNCHANGED, false),
    /** Check "Cubicar peso/volumen": se abre vacía; cancelar deja la lectura sin peso. */
    CUBICAR(InitialData.EMPTY, CancelEffect.KEEP_WITHOUT_WEIGHT, false),
    /** Check "Peso/Volumen anterior": se abre con los datos del bulto anterior, a cero si no hay. */
    PREVIOUS_BULTO(InitialData.PREVIOUS_BULTO, CancelEffect.KEEP_WITHOUT_WEIGHT, false),
    /** Check "Expedición completa": vacía y obligatoria; cancelar anula la lectura del bulto. */
    FULL_EXPEDITION(InitialData.EMPTY, CancelEffect.CANCEL_READING, true);

    /** Datos con los que se rellena la pantalla al abrirse. */
    public enum InitialData {
        EMPTY,
        CURRENT_BULTO,
        PREVIOUS_BULTO
    }

    /** Qué ocurre con la lectura del bulto al cancelar. */
    public enum CancelEffect {
        KEEP_UNCHANGED,
        KEEP_WITHOUT_WEIGHT,
        CANCEL_READING
    }

    private final InitialData initialData;
    private final CancelEffect cancelEffect;
    private final boolean marksFullExpedition;

    BultoWeightOrigin(InitialData initialData, CancelEffect cancelEffect, boolean marksFullExpedition) {
        this.initialData = initialData;
        this.cancelEffect = cancelEffect;
        this.marksFullExpedition = marksFullExpedition;
    }

    public InitialData initialData() {
        return initialData;
    }

    public CancelEffect cancelEffect() {
        return cancelEffect;
    }

    /** Si al aceptar el escaneo se graba con EXPEDICION_COMPLETA = 'S'. */
    public boolean marksFullExpedition() {
        return marksFullExpedition;
    }
}
