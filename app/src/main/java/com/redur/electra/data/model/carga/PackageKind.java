package com.redur.electra.data.model.carga;

/** Tipo de expedición según sus bultos: basta un bulto irregular o un palet para que lo sea. */
public enum PackageKind {

    /** Todos los bultos son paquetes. */
    PACKAGE,
    /** Al menos un bulto es irregular. */
    IRREGULAR,
    /** Al menos un bulto es un palet. */
    PALLET
}
